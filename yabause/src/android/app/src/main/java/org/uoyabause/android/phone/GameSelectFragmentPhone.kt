/*  Copyright 2019 devMiyax(smiyaxdev@gmail.com)

    This file is part of YabaSanshiro.

    YabaSanshiro is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    YabaSanshiro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with YabaSanshiro; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/

package org.uoyabause.android.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.provider.DocumentsContract
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider.NewInstanceFactory.Companion.instance
import androidx.lifecycle.lifecycleScope
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import android.widget.Filter
import android.widget.LinearLayout
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.coroutines.withContext
import com.google.android.gms.analytics.HitBuilders.ScreenViewBuilder
import com.google.android.gms.analytics.Tracker
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import io.noties.markwon.Markwon
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import org.devmiyax.yabasanshiro.BuildConfig
import org.devmiyax.yabasanshiro.R
import org.devmiyax.yabasanshiro.StartupActivity
import org.uoyabause.android.*
import org.uoyabause.android.FileDialog.FileSelectedListener
import org.uoyabause.android.GameSelectPresenter.GameSelectPresenterListener
import org.uoyabause.android.AutoBackupManager
import org.uoyabause.android.YabauseStorage.Companion.dao
import org.uoyabause.android.backup.GameBackupManager
import org.uoyabause.android.tv.GameSelectFragment
import java.io.File
import java.util.*


internal class GameListPage(val pageTitle: String, var gameList: GameItemAdapter)

internal class GameViewPagerAdapter(fm: FragmentManager?) :
    FragmentStatePagerAdapter(fm!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    private var gameListPageFragments: MutableList<Fragment>? = null
    private var gameListPages: MutableList<GameListPage>? = null

    fun setGameList(gameListPages: MutableList<GameListPage>?) {

        var position = 0
        if (gameListPages != null) {
            gameListPageFragments = ArrayList()
            for (item in gameListPages) {
                gameListPageFragments?.add(
                    GameListFragment.getInstance(
                        position,
                        gameListPages[position].pageTitle,
                        gameListPages[position].gameList
                    )
                )
                position += 1
            }
        }
        this.gameListPages = gameListPages
        this.notifyDataSetChanged()
    }

    override fun getItem(position: Int): Fragment {
        return gameListPageFragments!![position]
    }

    override fun getCount(): Int {
        return gameListPages?.size ?: return 0
    }

    override fun getPageTitle(position: Int): CharSequence {
        return gameListPages!![position].pageTitle
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        super.destroyItem(container, position, `object`)
    }

    override fun getItemPosition(xobj: Any): Int {
        return POSITION_NONE
    }
}

class GameSelectFragmentPhone : Fragment(),
    GameItemAdapter.OnItemClickListener,
    NavigationView.OnNavigationItemSelectedListener,
    FileSelectedListener,
    GameSelectPresenterListener {
    lateinit var presenter: GameSelectPresenter
    private var observer: Observer<*>? = null
    private var drawerLayout: DrawerLayout? = null
    private var tracker: Tracker? = null
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var isFirstUpdate = true
    private var navigationView: NavigationView? = null
    private lateinit var tabPageAdapter: GameViewPagerAdapter

    private lateinit var rootView: View
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var tabLayout: TabLayout
    private lateinit var progressBar: View
    private lateinit var progressMessage: TextView
    private lateinit var boxartImage: ImageView
    //private lateinit var gameInfoOverlay: LinearLayout
    private lateinit var selectedGameTitle: TextView
    private lateinit var selectedGameInfo: TextView
    //private lateinit var selectedGameIcon: ImageView
    private lateinit var selectedGameVersion: TextView
    private lateinit var selectedGameMenu: ImageButton
    private var isBackGroundComplete = false
    private var isAutoSelecting = false // 自動選択中フラグ
    private var lastScrollTime = 0L // スクロール更新の間引き用
    private var isDPadNavigating = false // D-pad navigation mode flag
    private var lastInputSource = 0 // Track last input source to distinguish touch vs D-pad
    private var isManuallySelected = false // 手動/削除後選択フラグ
    private var currentSortMode = SortMode.NAME // 現在のソート方法を追跡

    private var isBillingConnected = false
    private val viewModel by viewModels<BillingViewModel>()
    val connectionObserver = androidx.lifecycle.Observer<Boolean> { isConnecteed ->
        Log.d(BackupBackupItemFragment.TAG,"isConnected ${isConnecteed}")
        isBillingConnected = isConnecteed
    }


    private val alphabet = arrayOf(
        "A",
        "B",
        "C",
        "D",
        "E",
        "F",
        "G",
        "H",
        "I",
        "J",
        "K",
        "L",
        "M",
        "N",
        "O",
        "P",
        "Q",
        "R",
        "S",
        "T",
        "U",
        "V",
        "W",
        "X",
        "Y",
        "Z"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        presenter = GameSelectPresenter(this as Fragment, yabauseActivityLauncher,this)
        tabPageAdapter = GameViewPagerAdapter(this@GameSelectFragmentPhone.childFragmentManager)

        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.config)

        if(!remoteConfig.getBoolean("is_enable_subscription")){
            presenter.isOnSubscription = true
        }else {
            presenter.isOnSubscription = false
            viewModel.billingConnectionState.observe(this, connectionObserver)
            lifecycleScope.launchWhenStarted {
                viewModel.userCurrentSubscriptionFlow.collect { collectedSubscriptions ->
                    when {
                        collectedSubscriptions.hasPrepaidBasic == true -> {
                            Log.d(BackupBackupItemFragment.TAG, "hasPrepaidBasic")
                            if (presenter.isOnSubscription == false) {
                                presenter.isOnSubscription = true
                                presenter.syncBackup()
                            }

                        }
                        collectedSubscriptions.hasRenewableBasic == true -> {
                            Log.d(BackupBackupItemFragment.TAG, "hasRenewableBasic")
                            if (presenter.isOnSubscription == false) {
                                presenter.isOnSubscription = true
                                presenter.syncBackup()
                            }
                        }
                        else -> {
                            Log.d(BackupBackupItemFragment.TAG, "else")
                            presenter.isOnSubscription = false
                        }
                    }
                }
            }
        }

    }

    private var readRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if ( result.resultCode == Activity.RESULT_OK) {
            if (result.data != null) {
                val uri = result.data!!.data
                if (uri != null) {
                    presenter.onSelectFile(uri)
                }
            }
        }
    }

    // Permission request launcher for SAF write permissions
    private var permissionRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                // Take persistable permission
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d(TAG, "Successfully obtained write permission for URI: $uri")

                    // Notify pending deletion callback
                    pendingDeletionCallback?.invoke(true)
                    pendingDeletionCallback = null
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to take persistable permission: ${e.message}")
                    pendingDeletionCallback?.invoke(false)
                    pendingDeletionCallback = null
                }
            } else {
                Log.w(TAG, "No URI returned from permission request")
                pendingDeletionCallback?.invoke(false)
                pendingDeletionCallback = null
            }
        } else {
            Log.w(TAG, "Permission request cancelled or failed")
            pendingDeletionCallback?.invoke(false)
            pendingDeletionCallback = null
        }
    }

    // Callback for pending deletion operations
    private var pendingDeletionCallback: ((Boolean) -> Unit)? = null

    /**
     * Request write permission for SAF URI
     */
    fun requestWritePermission(uri: Uri, callback: (Boolean) -> Unit) {
        pendingDeletionCallback = callback

        // Show dialog to explain why permission is needed
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.permission_required))
            .setMessage(getString(R.string.write_permission_explanation))
            .setPositiveButton(R.string.ok) { _, _ ->
                // Launch document tree picker to get write permission
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    // Try to start with the same directory if possible
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                    }
                }
                permissionRequestLauncher.launch(intent)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                callback(false)
                pendingDeletionCallback = null
            }
            .show()
    }

    private fun selectGameFile(){
        val prefs = requireActivity().getSharedPreferences("private",
            MultiDexApplication.MODE_PRIVATE)
        val installCount = prefs.getInt("InstallCount", 3)
        if( installCount > 0){
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            readRequestLauncher.launch(intent)
        }else {
            val message = resources.getString(org.devmiyax.yabasanshiro.R.string.or_place_file_to, YabauseStorage.storage.gamePath)
            val rtn = YabauseApplication.checkDonated(requireActivity(), message)
            if ( rtn == 0) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                readRequestLauncher.launch(intent)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        rootView = inflater.inflate(org.devmiyax.yabasanshiro.R.layout.fragment_game_select_fragment_phone, container, false)
        progressBar = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.llProgressBar)
        progressBar.visibility = View.GONE
        progressMessage = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.pbText)

        // RecyclerViewの設定
        recyclerView = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.recycler_view_games)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // RecyclerViewのフォーカス設定を調整
        // キーイベントの重複を防ぐため、RecyclerViewのフォーカスを無効化
        recyclerView.isFocusable = false
        recyclerView.isFocusableInTouchMode = false
        recyclerView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        // タッチリスナーを追加してD-pad navigation modeをリセット
        recyclerView.setOnTouchListener { _, event ->
            if (isDPadNavigating && event.action == android.view.MotionEvent.ACTION_DOWN) {
                Log.d(TAG, "Touch detected, switching from D-pad to touch navigation mode")
                isDPadNavigating = false
            }
            // タッチ操作時に手動選択フラグをリセット（スクロール操作による自動選択を再開）
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                isManuallySelected = false
            }
            false // イベントを消費しない
        }

        // スクロールリスナーを追加（自動選択機能）
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                // スクロールが停止したときに一番上のアイテムを選択（D-pad navigation中は無効）
                if (newState == RecyclerView.SCROLL_STATE_IDLE && !isDPadNavigating) {
                    Log.d(TAG, "Touch scroll idle - selecting top visible item")
                    selectTopVisibleItem()
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && isDPadNavigating) {
                    Log.d(TAG, "D-pad scroll idle - maintaining D-pad selection")
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // スクロール中も一番上のアイテムを更新（パフォーマンスを考慮して間引き）
                // D-pad navigation中は無効
                val currentTime = System.currentTimeMillis()
                if (!isAutoSelecting && !isDPadNavigating && currentTime - lastScrollTime > 100) { // 100ms間隔で更新
                    lastScrollTime = currentTime
                    selectTopVisibleItem()
                } else if (isDPadNavigating && currentTime - lastScrollTime > 100) {
                    // D-pad navigation中はログのみ出力（デバッグ用）
                    lastScrollTime = currentTime
                    Log.d(TAG, "Scroll during D-pad navigation - auto-selection disabled")
                }
            }
        })

        // 検索バーの設定
        searchView = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.search_view)

        // ソートボタンの設定
        sortButton = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.sort_button)
        sortButton.setOnClickListener {
            showSortMenu(it)
        }

        // Boxart表示エリアの設定
        boxartImage = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.boxart_image)
        //gameInfoOverlay = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.game_info_overlay)
        //selectedGameTitle = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.selected_game_title)
        //selectedGameInfo = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.selected_game_info)

        // 中央のゲーム情報エリアの設定
        //selectedGameIcon = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.selected_game_icon)
        selectedGameVersion = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.selected_game_version)
        selectedGameMenu = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.selected_game_menu)

        // Boxartエリアのクリックリスナー（ゲーム開始）
        val boxartContainer = rootView.findViewById<FrameLayout>(org.devmiyax.yabasanshiro.R.id.boxart_container)
        boxartContainer.setOnClickListener {
            startSelectedGame()
        }

        // 再生ボタンのクリックリスナー（ゲーム開始）
        val playGameButton = rootView.findViewById<ImageButton>(org.devmiyax.yabasanshiro.R.id.play_game_button)
        playGameButton.setOnClickListener {
            startSelectedGame()
        }

        val fab: View = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.fab)
        if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            fab.setOnClickListener {
                selectGameFile()
            }
        } else {
            fab.visibility = View.GONE
        }



        if( adHeight != 0 ) {
            onAdViewIsShown(adHeight)
        }
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // D-PadナビゲーションはActivityレベルで処理するため、
        // RecyclerViewのキーリスナーは削除
        // フォーカスは設定しておく（必要に応じて）
        recyclerView.post {
            // RecyclerViewにフォーカスを設定しないことで、
            // Activityレベルでのキーイベント処理を優先
            Log.d(TAG, "RecyclerView setup completed")
        }

        // UI setup
        setupUI(view, savedInstanceState)
    }

    private fun handleDPadNavigation(keyCode: Int): Boolean {
        if (!::gameAdapter.isInitialized || !::recyclerView.isInitialized) {
            Log.d(TAG, "handleDPadNavigation: adapter or recyclerView not initialized")
            return false
        }

        val currentPosition = gameAdapter.getSelectedPosition()
        val itemCount = gameAdapter.itemCount

        if (itemCount == 0) {
            Log.d(TAG, "handleDPadNavigation: no items in adapter")
            return false
        }

        Log.d(TAG, "handleDPadNavigation: keyCode=$keyCode, currentPosition=$currentPosition, itemCount=$itemCount")

        var newPosition = currentPosition
        var handled = false

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (currentPosition > 0) {
                    newPosition = currentPosition - 1
                    handled = true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (currentPosition < itemCount - 1) {
                    newPosition = currentPosition + 1
                    handled = true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // D-pad center or Enter key pressed - start the selected game
                startSelectedGame()
                return true
            }
        }

        if (handled && newPosition != currentPosition) {
            Log.d(TAG, "D-pad navigation: moving from position $currentPosition to $newPosition")
            // D-pad navigation mode を有効にして自動選択を無効化
            isDPadNavigating = true
            isAutoSelecting = true

            // D-pad navigationでのスクロール方向に応じてAppBarを制御（移動が実際に発生する場合のみ）
            val appBar = activity?.findViewById<com.google.android.material.appbar.AppBarLayout>(org.devmiyax.yabasanshiro.R.id.main_appbar)
            if (appBar != null) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // 下にスクロールする場合はAppBarを隠す
                        appBar.setExpanded(false, true)
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // 上にスクロールする場合はAppBarを表示
                        appBar.setExpanded(true, true)
                    }
                }
            }

            // D-pad navigation中はアニメーションを無効化してtmpDetachedエラーを防ぐ
            val originalAnimator = recyclerView.itemAnimator
            recyclerView.itemAnimator = null

            // 選択位置を更新
            gameAdapter.setSelectedPosition(newPosition)

            // RecyclerViewをスクロールして選択されたアイテムを表示
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            layoutManager?.let {
                // 選択されたアイテムが見えるようにスクロール
                val firstVisible = it.findFirstVisibleItemPosition()
                val lastVisible = it.findLastVisibleItemPosition()

                // AppBarの高さを取得してオフセットに反映
                val appBarHeight = appBar?.height ?: 0

                if (newPosition < firstVisible || newPosition > lastVisible) {
                    // アイテムが見えない場合はスムーズスクロール
                    recyclerView.smoothScrollToPosition(newPosition)
                } else {
                    // アイテムが見える場合は、より良い位置に表示されるようにスクロール
                    // 最後のアイテムの場合はAppBarの高さを考慮してマージンを確保
                    val isLastItem = newPosition == itemCount - 1
                    val offset = if (isLastItem) {
                        // 最後のアイテムの場合はAppBarの高さ分を確保
                        -appBarHeight
                    } else {
                        // 通常は画面の上部1/3の位置に表示
                        recyclerView.height / 3
                    }
                    it.scrollToPositionWithOffset(newPosition, offset)
                }
            }

            // フラグをリセット（少し遅延させてスクロール完了を待つ）
            recyclerView.post {
                isAutoSelecting = false
                // アニメーションを復元
                recyclerView.itemAnimator = originalAnimator
                // D-pad navigation mode は一定時間後にリセット（タッチ操作に戻る準備）
                recyclerView.postDelayed({
                    // タッチ操作がない場合のみリセット
                    if (isDPadNavigating) {
                        isDPadNavigating = false
                    }
                }, 3000) // 3秒後にD-pad modeを解除
            }
        }

        return handled
    }

    // ActivityからのキーイベントをハンドルするためのPublicメソッド
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "Fragment onKeyDown called: keyCode=$keyCode, isDPadNavigating=$isDPadNavigating")
        return handleDPadNavigation(keyCode)
    }



    /**
     * Fetches cloud-backed games that aren't downloaded locally and adds them to the game list
     */
    private suspend fun fetchCloudOnlyGames(): List<GameInfo> {
        // Check if user is signed in
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            return emptyList()
        }

        try {
            // Get backed up games
            val gameBackupManager = org.uoyabause.android.backup.GameBackupManager(requireContext())
            val backedUpGames = gameBackupManager.getBackedUpGames()

            if (backedUpGames.isEmpty()) {
                return emptyList()
            }

            // Get local games to filter out games that are already downloaded
            val localGames = YabauseStorage.dao.getAll()
            val localProductNumbers = localGames.map { it.product_number }

            // Filter out games that are already downloaded
            val cloudOnlyGames = backedUpGames.filter { backupGame ->
                !localProductNumbers.contains(backupGame.productNumber)
            }

            // Convert to GameInfo objects
            return cloudOnlyGames.map { backupGame ->
                CloudGameInfo(backupGame).toGameInfo()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching cloud-only games: ${e.message}")
            return emptyList()
        }
    }

    private fun showSortMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.sort_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sort_by_name -> {
                    currentSortMode = SortMode.NAME
                    gameAdapter.sortByName()
                    true
                }
                R.id.sort_by_date -> {
                    currentSortMode = SortMode.DATE
                    gameAdapter.sortByDate()
                    true
                }
                R.id.sort_by_recently_played -> {
                    currentSortMode = SortMode.RECENTLY_PLAYED
                    gameAdapter.sortByRecentlyPlayed()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }



    private fun updateGameInfoSection(gameInfo: GameInfo?) {
        if (gameInfo == null) {
            // ゲーム情報がない場合はデフォルト表示
            selectedGameVersion.text = ""
            selectedGameMenu.setOnClickListener(null)
            return
        }

        // バージョン情報を表示
        if (gameInfo.isCloudOnly) {
            selectedGameVersion.text = getString(R.string.cloud_only_game)
            selectedGameVersion.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.cloud_upload_48px, 0, 0, 0
            )
            selectedGameVersion.compoundDrawablePadding = 8
        } else {
            selectedGameVersion.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)

            // レーティングとデバイス情報を表示
            lifecycleScope.launch(Dispatchers.IO) {
                gameInfo.updateState()
                var rate = ""
                for (i in 0 until gameInfo.rating) rate += "★"
                if (gameInfo.device_infomation != "CD-1/1") {
                    rate += " " + gameInfo.device_infomation
                }

                withContext(Dispatchers.Main) {
                    selectedGameVersion.text = rate
                }
            }
        }

        // メニューボタンのクリックリスナーを設定
        selectedGameMenu.setOnClickListener {
            if (::gameAdapter.isInitialized) {
                val selectedPosition = gameAdapter.getSelectedPosition()
                if (selectedPosition >= 0) {
                    // GameItemAdapterのshowPopupMenuメソッドを呼び出す
                    gameAdapter.showPopupMenu(selectedGameMenu, selectedPosition)
                }
            }
        }
    }

    private fun updateBoxartDisplay(gameInfo: GameInfo?) {
        // デフォルト背景色のプレースホルダーを作成
        val placeholderDrawable = androidx.core.content.ContextCompat.getDrawable(
            requireContext(), 
            android.R.color.transparent
        )
        
        if (gameInfo == null) {
            // Glideを使ってエラー画像を設定（setImageResourceは使わない）
            Glide.with(boxartImage)
                .load(R.drawable.missing)
                .placeholder(placeholderDrawable) // 読み込み中は透明
                .into(boxartImage)
            //gameInfoOverlay.visibility = View.GONE
            return
        }

        // ゲーム情報を表示
        //selectedGameTitle.text = gameInfo.game_title
        //var infoText = ""
        //if (gameInfo.device_infomation != "CD-1/1") {
        //    infoText = gameInfo.device_infomation
        //}

        // レーティングを追加
        lifecycleScope.launch(Dispatchers.IO) {
            gameInfo.updateState()
            var rate = ""
            for (i in 0 until gameInfo.rating) rate += "★"
            if (gameInfo.device_infomation != "CD-1/1") {
                rate += " " + gameInfo.device_infomation
            }

            withContext(Dispatchers.Main) {
                //selectedGameInfo.text = rate
                //gameInfoOverlay.visibility = View.VISIBLE
            }
        }

        // Boxart画像を読み込み
        if (gameInfo.image_url != null && gameInfo.image_url != "") {
            if (gameInfo.image_url!!.startsWith("http")) {
                var url = gameInfo.image_url
                if (gameInfo.isCloudOnly) {
                    url += "?" + GameInfo.sigin
                }

                val glideRequest = Glide.with(boxartImage)
                    .load(url)
                    .placeholder(placeholderDrawable) // 読み込み中は透明
                    .error(R.drawable.missing) // エラー時のフォールバック画像を設定

                // クラウドゲームの場合はブラー効果を適用
                if (gameInfo.isCloudOnly) {
                    glideRequest.apply(RequestOptions.bitmapTransform(BlurTransformation(8)))
                }

                glideRequest.into(boxartImage)
            } else {
                Glide.with(requireContext())
                    .load(gameInfo.image_url?.let { File(it) })
                    .placeholder(placeholderDrawable) // 読み込み中は透明
                    .error(R.drawable.missing) // エラー時のフォールバック画像を設定
                    .into(boxartImage)
            }
        } else {
            // Glideを使ってエラー画像を設定（setImageResourceは使わない）
            Glide.with(boxartImage)
                .load(R.drawable.missing)
                .placeholder(placeholderDrawable) // 読み込み中は透明
                .into(boxartImage)
        }
    }

    private var adHeight = 0
    fun onAdViewIsShown(height: Int) {
        try {
            val parentLayout = rootView.findViewById<DrawerLayout>(org.devmiyax.yabasanshiro.R.id.drawer_layout_game_select)
            val param = parentLayout.layoutParams as FrameLayout.LayoutParams
            param.bottomMargin = height + 4
            parentLayout.layoutParams = param
        } catch (e: Exception) {
            adHeight = height
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Unit {
        val menuItem = menu.findItem(R.id.menu_auto_backupsync)
        menuItem.isEnabled = isBillingConnected // メニューアイテムが選択可能かどうかを判定し、isEnabled プロパティに設定する
        return
    }

    suspend fun startSub(){
        if( viewModel.billingConnectionState.value == true) {
            val YEARLY_BASIC_PLANS_TAG = "yearly-basic"
            viewModel.productsForSaleFlows.collectLatest { it ->
                it.let {
                    viewModel.buy(
                        productDetails = it,
                        currentPurchases = null,
                        tag = YEARLY_BASIC_PLANS_TAG,
                        activity = requireActivity()
                    )
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout!!.closeDrawers()
        when (item.itemId) {
            org.devmiyax.yabasanshiro.R.id.menu_auto_backupsync -> {
                firebaseAnalytics?.logEvent("game_select_fragment"){
                    param("event", "menu_auto_backupsync")
                }

                if( presenter.isOnSubscription ) {
                    val fragment = BackupBackupItemFragment.newInstance(1, presenter)
                    val transaction = requireActivity().supportFragmentManager.beginTransaction()
                    transaction.replace(org.devmiyax.yabasanshiro.R.id.ext_fragment, fragment)
                    transaction.addToBackStack(null)
                    transaction.commit()
                }else{

                    AlertDialog.Builder(requireActivity())
                        .setTitle("Subscribe Auto backup")
                        .setMessage("fee: $1/year \n *Automatically backup data to cloud \n *Rollback \n *Share backup data between devices")
                        .setPositiveButton(R.string.yes){ _, _->
                            lifecycleScope.launch {
                                startSub()
                            }
                        }.setNegativeButton(R.string.no){ _, _->

                        }
                        .show()

                }

            }
            org.devmiyax.yabasanshiro.R.id.menu_item_setting -> {

                firebaseAnalytics?.logEvent("game_select_fragment"){
                    param("event", "menu_item_setting")
                }

                val intent = Intent(activity, SettingsActivity::class.java)
                settingActivityLauncher.launch(intent)
            }
            org.devmiyax.yabasanshiro.R.id.menu_item_load_game -> {

                firebaseAnalytics?.logEvent("game_select_fragment"){
                    param("event", "menu_item_load_game")
                }

                if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
                    selectGameFile()
                } else {
                    val sharedPref =
                        PreferenceManager.getDefaultSharedPreferences(activity)
                    val lastDir =
                        sharedPref.getString("pref_last_dir", YabauseStorage.storage.gamePath)
                    val fd =
                        FileDialog(requireActivity(), lastDir)
                    fd.addFileListener(this)
                    fd.showDialog()
                }
            }
            org.devmiyax.yabasanshiro.R.id.menu_item_update_game_db -> {

                firebaseAnalytics?.logEvent("game_select_fragment"){
                    param("event", "menu_item_update_game_db")
                }

                if (checkStoragePermission() == 0) {
                    updateGameList()
                }
            }
            org.devmiyax.yabasanshiro.R.id.menu_item_login -> if (item.title == getString(org.devmiyax.yabasanshiro.R.string.sign_out)) {

                firebaseAnalytics?.logEvent("game_select_fragment"){
                    param("event", "menu_item_login")
                }

                presenter.signOut()
                item.setTitle(org.devmiyax.yabasanshiro.R.string.sign_in)
            } else {
                presenter.signIn(signInActivityLauncher)
            }
            org.devmiyax.yabasanshiro.R.id.menu_privacy_policy -> {

                firebaseAnalytics?.logEvent("game_select_fragment"){
                    param("event", "menu_privacy_policy")
                }

                val uri =
                    Uri.parse("https://www.yabasanshiro.com/privacy")
                val i = Intent(Intent.ACTION_VIEW, uri)
                startActivity(i)
            }

        }
        return false
    }

    private fun checkStoragePermission(): Int {
        if ( Build.VERSION.SDK_INT < VERSION_CODES.Q ) { // Verify that all required contact permissions have been granted.
            if (ActivityCompat.checkSelfPermission(
                    requireActivity().applicationContext,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    requireActivity().applicationContext,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                != PackageManager.PERMISSION_GRANTED
            ) { // Contacts permissions have not been granted.
                Log.i(
                    TAG,
                    "Storage permissions has NOT been granted. Requesting permissions."
                )
                requestStoragePermission.launch(PERMISSIONS_STORAGE)
                return -1
            }
        }
        return 0
    }

    private val requestStoragePermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        result.entries.forEach{
            if(!it.value){
                showRestartMessage()
                return@registerForActivityResult
            }
        }
        updateGameList(0)
    }

    private fun showSnackBar(id: Int) {
        Snackbar
            .make(rootView.rootView, getString(id), Snackbar.LENGTH_SHORT)
            .show()
    }

    private fun updateRecent() {
        // 最近プレイしたゲームのリストを取得して表示を更新
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val recentList = YabauseStorage.dao.getRecentGames()

                // Get cloud-only games
                val cloudOnlyGames = fetchCloudOnlyGames()

                launch(Dispatchers.Main) {
                    // 全ゲームリストを再取得
                    val localGames = YabauseStorage.dao.getAllSortedByTitle()

                    // Create a new mutable list with the correct type
                    val combinedGames: MutableList<GameInfo?> = mutableListOf()

                    // Add local games
                    combinedGames.addAll(localGames)

                    // Add cloud-only games if there are any
                    if (cloudOnlyGames.isNotEmpty()) {
                        combinedGames.addAll(cloudOnlyGames)
                    }

                    // Assign to allGames
                    allGames = combinedGames

                    gameAdapter = GameItemAdapter(allGames)
                    gameAdapter.setOnItemClickListener(this@GameSelectFragmentPhone)

                    // スリムモードに固定
                    gameAdapter.setViewMode(GameItemAdapter.Companion.VIEW_TYPE_SLIM)

                    // 最初のアイテムを選択
                    allGames?.let { games ->
                        if (games.isNotEmpty()) {
                            // 初期選択は最初のアイテム
                            gameAdapter.setSelectedPosition(0)
                            // レイアウト完了後に一番上のアイテムを選択
                            recyclerView.post {
                                selectTopVisibleItem()
                            }
                        }
                    }

                    recyclerView.adapter = gameAdapter

                    // 現在のソート順を適用
                    applySortMode()
                }
            } catch (e: Exception) {
                Log.d(TAG, e.localizedMessage ?: "Error updating recent games")
            }
        }
    }

    private var adActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateRecent()
    }

    private var settingActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == GameSelectFragment.GAMELIST_NEED_TO_UPDATED) {
            if (checkStoragePermission() == 0) {

                firebaseAnalytics?.logEvent("game_select_fragment"){
                    param("event", "GAMELIST_NEED_TO_UPDATED")
                }

                updateGameList(3)
            }
        }else if (result.resultCode == GameSelectFragment.GAMELIST_NEED_TO_RESTART) {

            firebaseAnalytics?.logEvent("game_select_fragment"){
                param("event", "GAMELIST_NEED_TO_RESTART")
            }

            val intent = Intent(activity, StartupActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            activity?.finish()
        }
    }

    private var signInActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        firebaseAnalytics?.logEvent("game_select_fragment"){
            param("event", "onSignIn")
        }

        presenter.onSignIn(result.resultCode, result.data)
        if (presenter.currentUserName != null) {
            val m = navigationView!!.menu
            val miLogin = m.findItem(org.devmiyax.yabasanshiro.R.id.menu_item_login)
            miLogin.setTitle(org.devmiyax.yabasanshiro.R.string.sign_out)
        }
    }

    private var yabauseActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {

        val playtime = it.data?.getLongExtra("playTime",0) ?: 0L

        Log.d(TAG, "Play time is ${playtime}")

        firebaseAnalytics?.logEvent("game_select_fragment"){
            param("event", "On Game Finished")
            param("playTime",playtime)
        }
        val prefs = requireActivity().getSharedPreferences(
            "private",
            Context.MODE_PRIVATE
        )
        val hasDonated = prefs?.getBoolean("donated", false)

        if (BuildConfig.BUILD_TYPE != "pro" && hasDonated == false ) {

                val rn = Math.random()
                if (rn <= 0.3) {
                    val uiModeManager =
                        activity?.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                    if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION) {
                        val intent = Intent(
                            activity,
                            AdActivity::class.java
                        )
                        adActivityLauncher.launch(intent)
                        // }
                    } else {
                        val intent =
                            Intent(activity, AdActivity::class.java)
                        adActivityLauncher.launch(intent)
                    }
                } else if (rn <= 0.6) {
                    val intent =
                        Intent(activity, AdActivity::class.java)
                    adActivityLauncher.launch(intent)
                } else {

                    val lastReviewDateTime = prefs.getInt("last_review_date_time",0)
                    val unixTime = System.currentTimeMillis() / 1000L

                    // ３ヶ月に一度レビューしてもらう
                    if( (unixTime - lastReviewDateTime) > 60*60*24*30 ) {

                        // 5分以上遊んだ？
                        if( playtime < 5*60 ) return@registerForActivityResult

                        var manager : ReviewManager? = null
                        if( BuildConfig.DEBUG ){
                            manager = FakeReviewManager(requireContext())
                        }else{
                            val editor = prefs.edit()
                            editor.putInt("last_review_date_time",lastReviewDateTime)
                            editor.commit()
                            manager = ReviewManagerFactory.create(requireContext())
                        }
                        val request = manager.requestReviewFlow()
                        request.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // We got the ReviewInfo object
                                val reviewInfo = task.result
                                val flow = manager?.launchReviewFlow(requireActivity(), reviewInfo)
                                flow?.addOnCompleteListener { _ ->

                                }
                            } else {
                                task.getException()?.message?.let {
                                        it1 -> Log.d( TAG, it1)
                                }
                            }
                        }

                    }else{
                        val intent =
                            Intent(activity, AdActivity::class.java)
                        adActivityLauncher.launch(intent)
                    }
                }

            updateRecent()

        } else {

            val rn = Math.random()
            val lastReviewDateTime = prefs.getInt("last_review_date_time",0)
            val unixTime = System.currentTimeMillis() / 1000L

            // ３ヶ月に一度レビューしてもらう
            if( rn < 0.3 && (unixTime - lastReviewDateTime) > 60*60*24*30 ){

                // 5分以上遊んだ？
                if( playtime < 5*60 ) return@registerForActivityResult

                var manager : ReviewManager? = null
                if( BuildConfig.DEBUG ){
                    manager = FakeReviewManager(requireContext())
                }else{
                    val editor = prefs.edit()
                    editor.putInt("last_review_date_time",lastReviewDateTime)
                    editor.commit()
                    manager = ReviewManagerFactory.create(requireContext())
                }
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // We got the ReviewInfo object
                        val reviewInfo = task.result
                        val flow = manager.launchReviewFlow(requireActivity(), reviewInfo)
                        flow.addOnCompleteListener { _ ->

                        }
                    } else {
                        task.getException()?.message?.let {
                                it1 -> Log.d( TAG, it1)
                        }
                    }
                }
            }
            updateRecent()
        }
    }

    override fun fileSelected(file: File?) {

        firebaseAnalytics?.logEvent("game_select_fragment"){
            param("event", "fileSelected")
        }

        if( file != null ) {
            presenter.fileSelected(file)
        }
    }

    fun showDialog(message: String?) {
        if (message != null) {
            progressMessage.text = message
        } else {
            progressMessage.text = getString(org.devmiyax.yabasanshiro.R.string.updating)
        }
        progressBar.visibility = VISIBLE
    }

    fun updateDialogString(msg: String) {
        progressMessage.text = msg
    }

    fun dismissDialog() {
        progressBar.visibility = View.GONE
    }


    private fun setupUI(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as AppCompatActivity
        firebaseAnalytics = FirebaseAnalytics.getInstance(activity)
        val application = activity.application as YabauseApplication
        tracker = application.defaultTracker
        val toolbar =
            rootView.findViewById<View>(org.devmiyax.yabasanshiro.R.id.toolbar) as Toolbar
        toolbar.setLogo(org.devmiyax.yabasanshiro.R.mipmap.ic_launcher)
        toolbar.title = getString(org.devmiyax.yabasanshiro.R.string.app_name)
        toolbar.subtitle = getVersionName(activity)
        activity.setSupportActionBar(toolbar)
        tabLayout = rootView.findViewById(org.devmiyax.yabasanshiro.R.id.tab_game_index)
        tabLayout.removeAllTabs()

        drawerLayout =
            rootView.findViewById<View>(org.devmiyax.yabasanshiro.R.id.drawer_layout_game_select) as DrawerLayout




        drawerToggle = object : ActionBarDrawerToggle(
            getActivity(), /* host Activity */
            drawerLayout, /* DrawerLayout object */
            org.devmiyax.yabasanshiro.R.string.drawer_open, /* "open drawer" description */
            org.devmiyax.yabasanshiro.R.string.drawer_close /* "close drawer" description */
        ) {


            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                // activity.getSupportActionBar().setTitle("bbb");

                val tx = rootView.findViewById<TextView?>(org.devmiyax.yabasanshiro.R.id.menu_title)
                val uname = presenter.currentUserName

                if( tx?.text != uname ) {
                    if (tx != null && uname != null) {
                        tx.text = uname
                    } else {
                        tx.text = ""
                    }
                    val iv =
                        rootView.findViewById<ImageView?>(org.devmiyax.yabasanshiro.R.id.navi_header_image)
                    val uri = presenter.currentUserPhoto
                    if (iv != null && uri != null) {
                        Glide.with(drawerView.context)
                            .load(uri)
                            .into(iv)
                    } else {
                        iv.setImageResource(org.devmiyax.yabasanshiro.R.mipmap.ic_launcher)
                    }
                }
            }

        }
        // Set the drawer toggle as the DrawerListener
        drawerLayout!!.addDrawerListener(drawerToggle)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar!!.setHomeButtonEnabled(true)
        drawerToggle.syncState()
        navigationView =
            rootView.findViewById<View>(org.devmiyax.yabasanshiro.R.id.nav_view) as NavigationView
        if (navigationView != null) {
            navigationView!!.setNavigationItemSelectedListener(this)

            val headerView = navigationView!!.getHeaderView(0)
            val drawerView = headerView!!.findViewById<ImageView>(org.devmiyax.yabasanshiro.R.id.navi_header_image)
            val uri = presenter.currentUserPhoto

            if( uri != null ) {
                val icon: Icon = Icon.createWithResource(
                    requireContext(),
                    org.devmiyax.yabasanshiro.R.mipmap.ic_launcher
                )
                val drawable = icon.loadDrawable(context)
                if (drawable != null) {
                    val width = drawable.intrinsicWidth
                    val height = drawable.intrinsicHeight

                    Glide.with(requireActivity())
                        .load(uri)
                        .override(width, height) // 幅500px、高さ500pxにリサイズ
                        .centerCrop() // センタークロップ
                        .into(drawerView)
                }
            }else{
                drawerView.setImageResource(org.devmiyax.yabasanshiro.R.mipmap.ic_launcher)
            }

            val tx = headerView.findViewById<TextView?>(org.devmiyax.yabasanshiro.R.id.menu_title)
            val uname = presenter.currentUserName
            if (tx != null && uname != null) {
                tx.text = uname
            } else {
                tx.text = ""
            }

        }



        if (presenter.currentUserName != null) {
            val m = navigationView!!.menu
            val miLogin = m.findItem(org.devmiyax.yabasanshiro.R.id.menu_item_login)
            miLogin.setTitle(org.devmiyax.yabasanshiro.R.string.sign_out)
        } else {
            val m = navigationView!!.menu
            val miLogin = m.findItem(org.devmiyax.yabasanshiro.R.id.menu_item_login)
            miLogin.setTitle(org.devmiyax.yabasanshiro.R.string.sign_in)
        }
        if (checkStoragePermission() == 0) {
            updateGameList()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)

        // 画面の向きに応じてRecyclerViewのレイアウトを変更
        //if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        //    recyclerView.layoutManager = GridLayoutManager(context, 2)
        //} else {
        //    recyclerView.layoutManager = LinearLayoutManager(context)
        //}
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { // Pass the event to ActionBarDrawerToggle, if it returns
// true, then it has handled the app icon touch event
        return if (drawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(
            item
        )
    }

    private fun updateGameList() {
        if (observer != null) return
        isBackGroundComplete = false
        val tmpObserver = object : Observer<String> {
            // GithubRepositoryApiCompleteEventEntity eventResult = new GithubRepositoryApiCompleteEventEntity();
            override fun onSubscribe(d: Disposable) {
                observer = this
                showDialog(null)
            }

            override fun onNext(response: String) {
                updateDialogString("${getString(org.devmiyax.yabasanshiro.R.string.updating)} $response")
            }

            override fun onError(e: Throwable) {

                firebaseAnalytics?.logEvent("game_select_fragment"){
                    param("event", "updateGameList onError")
                }

                observer = null
                dismissDialog()
                presenter.syncBackup()
            }

            override fun onComplete() {

                firebaseAnalytics?.logEvent("game_select_fragment"){
                    param("event", "updateGameList onComplete")
                }

                if (!isFront) {
                    observer = null
                    dismissDialog()
                    isBackGroundComplete = true
                    return
                }

                loadRows()

                dismissDialog()
                if (isFirstUpdate) {
                    isFirstUpdate = false
                    if (this@GameSelectFragmentPhone.requireActivity().intent!!.getBooleanExtra(
                            "showPin",
                            false
                        )
                    ) {
                        ShowPinInFragment.newInstance().show(
                            childFragmentManager,
                            "sample"
                        )
                    } else {
                        presenter.checkSignIn(signInActivityLauncher)
                    }
                }

                observer = null
                presenter.syncBackup()


            }
        }
        presenter.updateGameList(refreshLevel, tmpObserver)
        refreshLevel = 0
    }

    private fun showRestartMessage() { // need_to_accept
        val viewMessageParent = rootView.findViewById<ScrollView?>(org.devmiyax.yabasanshiro.R.id.empty_message_parent)
        val viewMessage = rootView.findViewById<TextView?>(org.devmiyax.yabasanshiro.R.id.empty_message)
        val viewPager = rootView.findViewById<ViewPager?>(org.devmiyax.yabasanshiro.R.id.view_pager_game_index)
        viewMessageParent?.visibility = VISIBLE
        viewPager?.visibility = View.GONE

        val welcomeMessage = resources.getString(org.devmiyax.yabasanshiro.R.string.need_to_accept)
        viewMessage.text = welcomeMessage
    }

    internal var gameListPages: MutableList<GameListPage>? = null

    fun getGameItemAdapter(index: String): GameItemAdapter? {

        if (gameListPages == null) {
            loadRows()
        }

        if (gameListPages != null) {
            for (page in this.gameListPages!!) {
                if (page.pageTitle == index) {
                    return page.gameList
                }
            }
        }
        return null
    }

    @SuppressLint("StringFormatInvalid")
    private fun loadRows() {

        Log.d("GameSelect", "loadRows"  )

        GlobalScope.launch(Dispatchers.IO) {

            // Recent Play Game
            var dataCount = 0
            try {
                val glist: List<GameInfo> = YabauseStorage.dao.getAll()
                dataCount = YabauseStorage.dao.getRowCount()
                if (glist.size != dataCount) {
                    Log.d(TAG, "dataCount is not match")
                }
            } catch (e: Exception) {
                Log.d(TAG, e.localizedMessage!!)
            }

            // Get cloud-only games
            val cloudOnlyGames = fetchCloudOnlyGames()
            val totalGameCount = dataCount + cloudOnlyGames.size

            if (totalGameCount == 0) {
                // ゲームがない場合はウェルカムメッセージを表示
                launch(Dispatchers.Main) {
                    val viewMessageParent =
                        rootView.findViewById<ScrollView?>(org.devmiyax.yabasanshiro.R.id.empty_message_parent)
                    val viewMessage =
                        rootView.findViewById<TextView?>(org.devmiyax.yabasanshiro.R.id.empty_message)
                    val viewPager =
                        rootView.findViewById<ViewPager?>(org.devmiyax.yabasanshiro.R.id.view_pager_game_index)
                    viewMessageParent!!.visibility = VISIBLE
                    viewPager!!.visibility = View.GONE

                    val markwon = Markwon.create(this@GameSelectFragmentPhone.activity as Context)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val welcomeMessage = resources.getString(
                            org.devmiyax.yabasanshiro.R.string.welcome_11,
                            YabauseStorage.storage.gamePath,
                            "",
                        )
                        markwon.setMarkdown(viewMessage, welcomeMessage)

                    }
                    else if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
                        val packageName = requireActivity().packageName
                        val welcomeMessage = resources.getString(
                            org.devmiyax.yabasanshiro.R.string.welcome_11,
                            "Android/data/$packageName/files/yabause/games",
                            "Android/data/$packageName/files",
                        )
                        markwon.setMarkdown(viewMessage, welcomeMessage)
                    } else {
                        val welcomeMessage = resources.getString(
                            org.devmiyax.yabasanshiro.R.string.welcome,
                            YabauseStorage.storage.gamePath
                        )
                        markwon.setMarkdown(viewMessage, welcomeMessage)
                    }
                }
                return@launch
            }

            launch(Dispatchers.Main) {


                val viewMessageParent =
                    rootView.findViewById<ScrollView?>(org.devmiyax.yabasanshiro.R.id.empty_message_parent)
                val viewMessage =
                    rootView.findViewById(org.devmiyax.yabasanshiro.R.id.empty_message) as? View
                val viewPager =
                    rootView.findViewById(org.devmiyax.yabasanshiro.R.id.view_pager_game_index) as? ViewPager
                viewMessageParent?.visibility = View.GONE
                viewPager?.visibility = VISIBLE

                // -----------------------------------------------------------------
                // Recent Play Game
                GlobalScope.launch(Dispatchers.IO) {
                    var recentList: List<GameInfo> = emptyList()
                    try {
                        // Get local games
                        val localGames = YabauseStorage.dao.getAllSortedByTitle()

                        // Create a new mutable list with the correct type
                        val combinedGames: MutableList<GameInfo?> = mutableListOf()

                        // Add local games
                        combinedGames.addAll(localGames)

                        // Add cloud-only games if there are any
                        if (cloudOnlyGames.isNotEmpty()) {
                            combinedGames.addAll(cloudOnlyGames)
                        }

                        // Assign to allGames
                        allGames = combinedGames

                        launch(Dispatchers.Main) {
                            gameAdapter = GameItemAdapter(allGames)
                            gameAdapter.setOnItemClickListener(this@GameSelectFragmentPhone)

                            // スリムモードに固定
                            gameAdapter.setViewMode(GameItemAdapter.Companion.VIEW_TYPE_SLIM)

                            // 最初のアイテムを選択
                            allGames?.let { games ->
                                if (games.isNotEmpty()) {
                                    // 初期選択は最初のアイテム
                                    gameAdapter.setSelectedPosition(0)
                                    // レイアウト完了後に一番上のアイテムを選択
                                    recyclerView.post {
                                        selectTopVisibleItem()
                                    }
                                }
                            }

                            recyclerView.adapter = gameAdapter

                            // 検索バーのリスナーを設定
                            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                                override fun onQueryTextSubmit(query: String?): Boolean {
                                    return false
                                }

                                override fun onQueryTextChange(newText: String?): Boolean {
                                    gameAdapter.filter.filter(newText)
                                    return true
                                }
                            })

                            // 現在のソート順を適用（デフォルトは名前順）
                            applySortMode()
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "${e.localizedMessage}")
                    }
                }
            }
        }
    }

    override fun onItemClick(position: Int, item: GameInfo?, v: View?) {
        // リストアイテムのクリックは選択のみ（ゲーム開始はboxartエリアのクリックで実行）
        // 実際の処理はGameItemAdapterで直接setSelectedPositionを呼び出している
    }

    override fun onItemSelected(position: Int, item: GameInfo?) {
        // 削除後の選択や手動選択の場合、フラグを設定
        if (!isAutoSelecting) {
            isManuallySelected = true
            // 一定時間後にフラグをリセット（スクロール時の自動選択を再開）
            recyclerView.postDelayed({
                isManuallySelected = false
            }, 2000) // 2秒後にリセット
        }
        updateBoxartDisplay(item)
        updateGameInfoSection(item)
    }

    override fun onGameStart(item: GameInfo?) {
        startSelectedGame()
    }

    private fun startSelectedGame() {
        if (::gameAdapter.isInitialized) {
            val selectedGame = gameAdapter.getSelectedGame()
            if (selectedGame != null) {
                if (selectedGame.isCloudOnly && selectedGame.cloudBackupInfo != null) {
                    // Handle cloud-only game click - download it first
                    downloadCloudGame(selectedGame.cloudBackupInfo!!)
                } else {
                    // Normal game click - start the game
                    presenter.startGame(selectedGame, yabauseActivityLauncher)
                }
            }
        }
    }

    private fun selectTopVisibleItem() {
        if (!::recyclerView.isInitialized || !::gameAdapter.isInitialized) {
            return
        }

        // 手動選択や削除後の選択がある場合は、自動選択をスキップ
        if (isManuallySelected) {
            return
        }

        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return

        // 現在表示されている一番上のアイテムの位置を取得
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()

        if (firstVisiblePosition == RecyclerView.NO_POSITION) {
            return
        }

        // 現在の選択と異なる場合のみ更新
        if (firstVisiblePosition != gameAdapter.getSelectedPosition() && firstVisiblePosition >= 0) {
            isAutoSelecting = true
            gameAdapter.setSelectedPosition(firstVisiblePosition)
            isAutoSelecting = false
        }
    }

    /**
     * Downloads a cloud-backed game
     */
    private fun downloadCloudGame(backupGameInfo: org.uoyabause.android.backup.GameBackupManager.BackupGameInfo) {
        // Show progress dialog
        val progressDialog = ProgressDialog(requireContext())
        progressDialog.setMessage("Downloading game...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Get game backup manager
        val gameBackupManager = org.uoyabause.android.backup.GameBackupManager(requireContext())

        // Launch coroutine to restore game
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = gameBackupManager.restoreGame(backupGameInfo)

                // Dismiss progress dialog
                progressDialog.dismiss()

                // Show result
                if (result.success) {
                    Toast.makeText(
                        requireContext(),
                        getString(org.devmiyax.yabasanshiro.R.string.restore_success),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Refresh game list
                    updateGameList(YabauseStorage.REFRESH_LEVEL_REBUILD)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "${getString(org.devmiyax.yabasanshiro.R.string.restore_failed)}: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                // Dismiss progress dialog
                progressDialog.dismiss()

                Toast.makeText(
                    requireContext(),
                    "${getString(org.devmiyax.yabasanshiro.R.string.restore_failed)}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onGameRemoved(item: GameInfo?) {

        firebaseAnalytics?.logEvent("game_select_fragment"){
            param("event", "onGameRemoved")
        }

        if (item == null) return

        // アダプターから削除
        gameAdapter.removeItem(item.id)
    }

    override fun onResume() {
        super.onResume()
        if (tracker != null) { // mTracker.setScreenName(TAG);
            tracker!!.send(ScreenViewBuilder().build())
        }
        if (presenter.currentUserName != null) {
            val m = navigationView!!.menu
            val miLogin = m.findItem(org.devmiyax.yabasanshiro.R.id.menu_item_login)
            miLogin.setTitle(org.devmiyax.yabasanshiro.R.string.sign_out)
        } else {
            val m = navigationView!!.menu
            val miLogin = m.findItem(org.devmiyax.yabasanshiro.R.id.menu_item_login)
            miLogin.setTitle(org.devmiyax.yabasanshiro.R.string.sign_in)
        }
        isFront = true
        if (isBackGroundComplete) {
            updateGameList()
        }
        presenter.onResume()
    }

    var isFront = true

    override fun onPause() {
        isFront = false
        super.onPause()
        this.presenter.onPause()
    }



    override fun onDestroy() {
        System.gc()
        super.onDestroy()
    }

    override fun onShowMessage(string_id: Int) {
        showSnackBar(string_id)
    }

    override fun onShowDialog(message: String) {
        showDialog(message)
    }

    override fun onUpdateDialogMessage(message: String) {
        updateDialogString(message)
    }

    override fun onDismissDialog() {
        dismissDialog()
    }

    override fun onLoadRows() {
        loadRows()
    }


    override fun onStartSyncBackUp(){
    }

    override fun onFinishSyncBackUp(result: AutoBackupManager.SyncResult, message: String) {
        if( result == AutoBackupManager.SyncResult.SUCCESS ){
            Snackbar.make(rootView.rootView, message, Snackbar.LENGTH_LONG).show();
        }

        if( result == AutoBackupManager.SyncResult.FAIL ){
            val color = ContextCompat.getColor(requireContext(), org.devmiyax.yabasanshiro.R.color.design_default_color_error)
            val snackbar = Snackbar.make(rootView.rootView, message, Snackbar.LENGTH_LONG)
            snackbar.setTextColor( color )
            snackbar.show()
        }


    }

    override fun onSignOut() {

    }

    companion object {
        private const val TAG = "GameSelectFragmentPhone"
        private var instance: GameSelectFragmentPhone? = null

        @JvmField
        var myOnClickListener: View.OnClickListener? = null
        private val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        fun newInstance(): GameSelectFragmentPhone {
            val fragment = GameSelectFragmentPhone()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }

        fun getInstance(): GameSelectFragmentPhone? {
            return instance
        }

        fun getVersionName(context: Context): String {
            val pm = context.packageManager
            var versionName = ""
            try {
                val packageInfo = pm.getPackageInfo(context.packageName, 0)
                versionName = packageInfo.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            return versionName
        }
    }

    // ソートモードを定義する列挙型
    private enum class SortMode {
        NAME,
        DATE,
        RECENTLY_PLAYED
    }

    // 現在のソートモードを適用するメソッド
    private fun applySortMode() {
        if (::gameAdapter.isInitialized) {
            when (currentSortMode) {
                SortMode.NAME -> gameAdapter.sortByName()
                SortMode.DATE -> gameAdapter.sortByDate()
                SortMode.RECENTLY_PLAYED -> gameAdapter.sortByRecentlyPlayed()
            }
        }
    }
}
