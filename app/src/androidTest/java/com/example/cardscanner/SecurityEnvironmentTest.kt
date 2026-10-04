package com.example.cardscanner

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Security sanity checks that need a real Android runtime:
 *  - Test 4: the app writes no card image files (no image file exists in storage)
 *  - MainActivity runs with FLAG_SECURE (screenshot/recording protection)
 */
@RunWith(AndroidJUnit4::class)
class SecurityEnvironmentTest {

    /** Test 4: app files directory stays free of image files during normal ops. */
    @Test
    fun noCardImagesAreWrittenToDisk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageFiles = context.filesDir.walkTopDown()
            .filter { it.isFile }
            .filter {
                it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "bmp", "heic")
            }
            .toList()
        assertTrue(
            "found image files in app storage: $imageFiles",
            imageFiles.isEmpty(),
        )
    }

    /** MainActivity is launched with FLAG_SECURE applied by SensitiveDataGuard. */
    @Test
    fun mainActivityAppliesSecureFlag() {
        ActivityScenario.launch(MainActivity::class.java).onActivity { activity ->
            val secureFlag = android.view.WindowManager.LayoutParams.FLAG_SECURE
            assertTrue(
                "FLAG_SECURE not applied to MainActivity window",
                (activity.window.attributes.flags and secureFlag) == secureFlag,
            )
        }
    }
}
