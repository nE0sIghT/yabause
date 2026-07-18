package org.uoyabause.yabasanshiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.uoyabause.android.game.submitScoreToFirestore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SonicRE2ETest {
    @Test
    fun submitScoreToFirestore_e2eTest() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val latch = CountDownLatch(1)
        val userName = "E2Eテストユーザー"
        val score = 98765L
        val leaderboardId = "test_leaderboard_e2e"
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()

        // 匿名認証でサインイン
        auth.signInAnonymously().addOnSuccessListener { authResult ->
            val user = authResult.user
            assertTrue(user != null)
            // スコア送信
            val gameId = "test_game_id"
            submitScoreToFirestore(
                gameId,
                leaderboardId,
                score,
                userName,
                onSuccess = {
                    // Firestoreからデータ取得して検証
                    firestore.collection("games/$gameId/leaderboards")
                        .document(leaderboardId)
                        .collection("scores")
                        .document(user!!.uid)
                        .get()
                        .addOnSuccessListener { doc ->
                            assertTrue(doc.exists())
                            assertEquals(userName, doc.getString("name"))
                            assertEquals(score, doc.getLong("score"))
                            // photoUrlはnullかもしれないが、フィールドは存在するはず
                            assertTrue(doc.contains("photoUrl"))
                            latch.countDown()
                        }
                        .addOnFailureListener {
                            // エラーの内容をlogcatに出力
                            Log.e("SonicRE2ETest", "Error getting document: ${it.message}")
                            fail("Firestoreからのドキュメント取得に失敗: ${it.message}")
                            latch.countDown()
                        }
                },
                onFailure = {
                    // エラーの内容をlogcatに出力
                    Log.e("SonicRE2ETest", "Error submitting score: ${it.message}")
                    fail("スコア送信に失敗: ${it.message}")
                    latch.countDown()
                }
            )
        }.addOnFailureListener {
            latch.countDown()
        }
        // 最大10秒待つ
        assertTrue(latch.await(30, TimeUnit.SECONDS))
    }

    @Test
    fun submitScoreToFirestore_updateOnlyIfBetterScore_e2e() {
        val latch = CountDownLatch(1)
        val userName = "E2Eテストユーザー"
        val leaderboardId = "test_leaderboard_e2e_update"
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val initialScore = 30000L
        val betterScore = 20000L
        val worseScore = 40000L

        auth.signInAnonymously().addOnSuccessListener { authResult ->
            val user = authResult.user
            assertTrue(user != null)
            // まず初回スコア登録
            val gameId = "test_game_id"
            submitScoreToFirestore(
                gameId,
                leaderboardId,
                initialScore,
                userName,
                onSuccess = {
                    // より良いスコアで上書き
                    submitScoreToFirestore(
                        gameId,
                        leaderboardId,
                        betterScore,
                        userName,
                        onSuccess = {
                            // さらに悪いスコアで試す（上書きされないはず）
                            submitScoreToFirestore(
                                gameId,
                                leaderboardId,
                                worseScore,
                                userName,
                                onSuccess = {
                                    firestore.collection("games/$gameId/leaderboards")
                                        .document(leaderboardId)
                                        .collection("scores")
                                        .document(user!!.uid)
                                        .get()
                                        .addOnSuccessListener { doc ->
                                            assertTrue(doc.exists())
                                            assertEquals(userName, doc.getString("name"))
                                            assertEquals(betterScore, doc.getLong("score"))
                                            // photoUrlはnullかもしれないが、フィールドは存在するはず
                                            assertTrue(doc.contains("photoUrl"))
                                            latch.countDown()
                                        }
                                        .addOnFailureListener {
                                            fail("Firestoreからのドキュメント取得に失敗: ${it.message}")
                                            latch.countDown()
                                        }
                                },
                                onFailure = {
                                    fail("悪いスコアでのsubmitScoreToFirestore失敗: ${it.message}")
                                    latch.countDown()
                                }
                            )
                        },
                        onFailure = {
                            fail("良いスコアでのsubmitScoreToFirestore失敗: ${it.message}")
                            latch.countDown()
                        }
                    )
                },
                onFailure = {
                    fail("初回submitScoreToFirestore失敗: ${it.message}")
                    latch.countDown()
                }
            )
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
    }
}
