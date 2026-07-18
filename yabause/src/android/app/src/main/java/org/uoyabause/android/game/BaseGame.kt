package org.uoyabause.android.game

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.google.android.gms.tasks.Tasks
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.devmiyax.yabasanshiro.R
import org.uoyabause.android.YabauseApplication

class LeaderBoard(val title: String, val id: String)

abstract interface GameUiEvent {
    abstract fun onNewRecord(leaderBoardId: String)
}

abstract class BaseGame {

    // gameCodeからgameIdの取得
    suspend fun initGameId(gameCode: String) = withContext(Dispatchers.IO) {
        val db = FirebaseFirestore.getInstance()
        val task = db.collection("games")
            .whereEqualTo("product_number", gameCode)
            .get()

        // タスクが完了するまで待機
        val documents = Tasks.await(task)

        if (!documents.isEmpty) {
            // leaderboardIdフィールドがある場合はその値を使用
            if (documents.documents[0].get("leaderboardId") != null) {
                gameId = documents.documents[0].getString("leaderboardId")
                    ?: documents.documents[0].id
            } else {
                // なければドキュメントIDを使用
                gameId = documents.documents[0].id
            }
        }
    }

    var gameId: String = ""
    var leaderBoards: MutableList<LeaderBoard>? = null

    lateinit var uievent: GameUiEvent
    fun setUiEvent(uievent: GameUiEvent) {
        this.uievent = uievent
    }
    abstract fun onBackUpUpdated(fname: String, before: ByteArray, after: ByteArray)

    // Shared leaderboard functionality
    protected fun submitScoreToFirestore(
        gameId: String,
        leaderboardId: String,
        score: Long,
        userName: String,
        webhookConfigKey: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
            onFailure?.invoke(Exception("ユーザーが認証されていません"))
            return
        }
        val userId = currentUser.uid

        // ユーザーの画像URLを取得
        val photoUrl = currentUser.photoUrl?.toString()

        // Ensure we're using the Firebase user ID for the document ID
        // but still display the user's name (which might be from Discord)
        val scoreData = hashMapOf(
            "name" to userName,
            "score" to score,
            "timestamp" to System.currentTimeMillis(),
            "photoUrl" to photoUrl,
            "firebaseUid" to userId  // Store the Firebase UID explicitly
        )
        val scoreDocRef = db.collection("games/${gameId}/leaderboards")
            .document(leaderboardId)
            .collection("scores")
            .document(userId)

        scoreDocRef.get()
            .addOnSuccessListener { document ->
                val currentScore = document.getLong("score")
                if (currentScore == null || score < currentScore) {
                    // 新記録（より短いタイム）の場合のみ上書き
                    scoreDocRef.set(scoreData)
                        .addOnSuccessListener {
                            // 新記録が登録されたら、それが1位かどうかを確認する
                            checkIfNewTopScore(gameId, leaderboardId, score, userName, photoUrl, webhookConfigKey)
                            onSuccess?.invoke()
                        }
                        .addOnFailureListener { e -> onFailure?.invoke(e) }
                } else {
                    // 記録を更新しない場合も成功扱い
                    onSuccess?.invoke()
                }
            }
            .addOnFailureListener { e ->
                onFailure?.invoke(e)
            }
    }

    /**
     * 新しいスコアが1位かどうかを確認し、1位の場合はDiscordに投稿する
     *
     * @param gameId ゲームID
     * @param leaderboardId リーダーボードID
     * @param score スコア（タイム）
     * @param userName ユーザー名
     * @param photoUrl ユーザーのアバターURL
     * @param webhookConfigKey Remote Configのwebhook URLキー
     */
    private fun checkIfNewTopScore(gameId: String, leaderboardId: String, score: Long, userName: String, photoUrl: String?, webhookConfigKey: String) {
        val db = FirebaseFirestore.getInstance()
        val context = YabauseApplication.appContext

        // Firebase Remote ConfigからDiscord Webhook URLを取得
        val remoteConfig = com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance()
        val webhookUrl = remoteConfig.getString(webhookConfigKey)

        // Webhook URLが空または無効な場合は処理を終了
        if (webhookUrl.isEmpty()) {
            Log.d("BaseGame", "Discord webhook URL is not set in Remote Config for key: $webhookConfigKey")
            return
        }

        // リーダーボードのスコアを取得して、新しいスコアが1位かどうかを確認
        db.collection("games/${gameId}/leaderboards")
            .document(leaderboardId)
            .collection("scores")
            .orderBy("score", Query.Direction.ASCENDING) // タイムアタックなので昇順（小さい方が良い）
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    // スコアがない場合は、新しいスコアが1位
                    postNewTopScoreToDiscord(gameId, leaderboardId, score, userName, photoUrl, webhookUrl)
                    return@addOnSuccessListener
                }

                // 1位のスコアを取得
                val topScore = querySnapshot.documents[0].getLong("score") ?: Long.MAX_VALUE
                val topScoreUserId = querySnapshot.documents[0].id

                // 自分のスコアが1位かどうかを確認
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null && (score <= topScore || topScoreUserId == currentUser.uid)) {
                    // 新しいスコアが1位の場合、またはすでに自分が1位の場合
                    // リーダーボード名を取得
                    db.collection("games/${gameId}/leaderboards")
                        .document(leaderboardId)
                        .get()
                        .addOnSuccessListener { leaderboardDoc ->
                            val leaderboardName = leaderboardDoc.getString("name") ?: leaderboardId
                            postNewTopScoreToDiscord(gameId, leaderboardName, score, userName, photoUrl, webhookUrl)
                        }
                        .addOnFailureListener { e ->
                            Log.e("BaseGame", "Error getting leaderboard name", e)
                        }
                } else {
                    Log.d("BaseGame", "New score is not the top score. Top: $topScore, New: $score")
                }
            }
            .addOnFailureListener { e ->
                Log.e("BaseGame", "Error checking if new score is top score", e)
            }
    }

    /**
     * 新記録（1位）をDiscordに投稿する
     *
     * @param gameId ゲームID
     * @param leaderboardName リーダーボード名
     * @param score スコア（タイム）
     * @param userName ユーザー名
     * @param photoUrl ユーザーのアバターURL
     * @param webhookUrl Discord WebhookのURL
     */
    private fun postNewTopScoreToDiscord(gameId: String, leaderboardName: String, score: Long, userName: String, photoUrl: String?, webhookUrl: String) {
        // バックグラウンドスレッドで実行
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = org.uoyabause.android.util.DiscordWebhook.sendNewRecordMessage(
                    webhookUrl = webhookUrl,
                    gameId = gameId,
                    leaderboardName = leaderboardName,
                    userName = userName,
                    score = score,
                    avatarUrl = photoUrl
                )

                if (result) {
                    Log.d("BaseGame", "Successfully posted new top score to Discord")
                    // 成功通知を表示
                    withContext(Dispatchers.Main) {
                        val context = YabauseApplication.appContext
                        Toast.makeText(
                            context,
                            context.getString(R.string.discord_webhook_notification_title) + ": " +
                                    context.getString(R.string.discord_webhook_notification_message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.e("BaseGame", "Failed to post new top score to Discord")
                }
            } catch (e: Exception) {
                Log.e("BaseGame", "Error posting new top score to Discord", e)
            }
        }
    }

    /**
     * Firebase Analyticsにスコアイベントを送信する
     */
    protected fun logScoreEvent(score: Long, leaderboardId: String) {
        val context = YabauseApplication.appContext
        val bundle = Bundle()
        bundle.putLong(FirebaseAnalytics.Param.SCORE, score)
        bundle.putString("leaderboard_id", leaderboardId)
        val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.POST_SCORE, bundle)
    }
}