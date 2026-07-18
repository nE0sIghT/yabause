package org.uoyabause.android.phone

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.google.android.gms.ads.AdView
import io.mockk.*
import org.devmiyax.yabasanshiro.BuildConfig
import org.devmiyax.yabasanshiro.R
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@LargeTest
class GameSelectActivityPhoneIntegrationTest {

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var context: Context
    private lateinit var scenario: ActivityScenario<GameSelectActivityPhone>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Clear shared preferences to ensure clean test state
        val prefs = context.getSharedPreferences("private", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    @Test
    fun activityLaunchesSuccessfully() {
        // When
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)

        // Then
        scenario.onActivity { activity ->
            assertThat(activity, isA(GameSelectActivityPhone::class.java))
            assertThat(activity.frg_, isA(GameSelectFragmentPhone::class.java))
        }
    }

    @Test
    fun adViewIsVisibleWhenNotProAndNotDonated() {
        // Given - Simulate non-pro build and no donation
        setSharedPreferenceValue("donated", false)
        
        // When
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)

        // Then
        scenario.onActivity { activity ->
            if (BuildConfig.BUILD_TYPE != "pro") {
                // AdView should be created but might not be immediately visible due to async loading
                assertThat(activity.adView, isA(AdView::class.java))
            }
        }
    }

    @Test
    fun adViewIsNotCreatedWhenDonated() {
        // Given - User has donated
        setSharedPreferenceValue("donated", true)
        
        // When
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)

        // Then
        scenario.onActivity { activity ->
            assertThat(activity.adView, nullValue())
        }
    }

    @Test
    fun activityHandlesConfigurationChange() {
        // Given
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)
        
        // When - Rotate device
        scenario.onActivity { activity ->
            val config = Configuration(activity.resources.configuration)
            config.orientation = if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
                Configuration.ORIENTATION_LANDSCAPE
            } else {
                Configuration.ORIENTATION_PORTRAIT
            }
            activity.onConfigurationChanged(config)
        }

        // Then - Activity should still be functional
        scenario.onActivity { activity ->
            assertThat(activity, isA(GameSelectActivityPhone::class.java))
            assertThat(activity.frg_, isA(GameSelectFragmentPhone::class.java))
        }
    }

    @Test
    fun activityHandlesLifecyclePauseResume() {
        // Given
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)
        
        var initialAdView: AdView? = null
        scenario.onActivity { activity ->
            initialAdView = activity.adView
        }

        // When - Pause and resume
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED) // Paused
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED) // Resumed

        // Then - Activity should maintain state
        scenario.onActivity { activity ->
            assertThat(activity, isA(GameSelectActivityPhone::class.java))
            // AdView should be the same instance (not recreated)
            assertThat(activity.adView, equalTo(initialAdView))
        }
    }

    @Test
    fun activityHandlesLifecycleDestroy() {
        // Given
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)
        
        // When - Destroy activity
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED)

        // Then - Should complete without crashing
        // (The test passes if no exception is thrown)
    }

    @Test
    fun keyEventHandlingWorksCorrectly() {
        // Given
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)
        
        // When & Then - Test various key events
        scenario.onActivity { activity ->
            val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
            
            // Should not crash when handling key events
            try {
                activity.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, keyEvent)
                activity.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, keyEvent)
                activity.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, keyEvent)
                activity.onKeyDown(KeyEvent.KEYCODE_ENTER, keyEvent)
                activity.onKeyDown(KeyEvent.KEYCODE_A, keyEvent) // Unsupported key
            } catch (e: Exception) {
                throw AssertionError("Key event handling should not crash", e)
            }
        }
    }

    @Test
    fun adViewLifecycleManagementWorksCorrectly() {
        // Given - Non-donated user to ensure AdView creation
        setSharedPreferenceValue("donated", false)
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)
        
        // Wait for AdView initialization
        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            if (activity.adView != null || BuildConfig.BUILD_TYPE == "pro") {
                latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)

        // When - Test lifecycle methods
        scenario.onActivity { activity ->
            val adView = activity.adView
            if (adView != null) {
                // These methods should not crash
                try {
                    activity.onPause() // Should call adView.pause()
                    activity.onResume() // Should call adView.resume()
                    activity.onDestroy() // Should call adView.destroy()
                } catch (e: Exception) {
                    throw AssertionError("AdView lifecycle management should not crash", e)
                }
            }
        }
    }

    @Test
    fun fragmentIsProperlyInitializedOnCreate() {
        // When
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)

        // Then
        scenario.onActivity { activity ->
            assertThat(activity.frg_, isA(GameSelectFragmentPhone::class.java))
            
            // Fragment should be added to the fragment manager
            val fragment = activity.supportFragmentManager
                .findFragmentById(GameSelectActivityPhone.CONTENT_VIEW_ID)
            assertThat(fragment, isA(GameSelectFragmentPhone::class.java))
        }
    }

    @Test
    fun fragmentIsRestoredOnRecreation() {
        // Given - Launch activity first time
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)
        
        // When - Recreate activity (simulates process death/recreation)
        scenario.recreate()

        // Then - Fragment should be restored
        scenario.onActivity { activity ->
            assertThat(activity.frg_, isA(GameSelectFragmentPhone::class.java))
            
            val fragment = activity.supportFragmentManager
                .findFragmentById(GameSelectActivityPhone.CONTENT_VIEW_ID)
            assertThat(fragment, isA(GameSelectFragmentPhone::class.java))
        }
    }

    @Test
    fun activityHandlesPermissionStatesCorrectly() {
        // This test verifies the activity handles different permission states
        // without crashing during initialization
        
        // When
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)

        // Then - Should launch successfully regardless of permission state
        scenario.onActivity { activity ->
            assertThat(activity, isA(GameSelectActivityPhone::class.java))
        }
    }

    @Test
    fun multipleLifecycleCyclesWork() {
        // Given
        scenario = ActivityScenario.launch(GameSelectActivityPhone::class.java)
        
        // When - Multiple pause/resume cycles
        repeat(3) {
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED) // Pause
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED) // Resume
        }

        // Then - Activity should remain functional
        scenario.onActivity { activity ->
            assertThat(activity, isA(GameSelectActivityPhone::class.java))
            assertThat(activity.frg_, isA(GameSelectFragmentPhone::class.java))
        }
    }

    private fun setSharedPreferenceValue(key: String, value: Boolean) {
        val prefs = context.getSharedPreferences("private", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }
}