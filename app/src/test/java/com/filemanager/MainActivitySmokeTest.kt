package com.filemanager

import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 界面冒烟测试：
 * 1. 主界面能在 M3 主题下完整 inflate（含 Material 组件、空状态、抽屉）；
 * 2. 主题中的语义色角色与令牌文件取值一致（浅色 / 深色两套都要成立）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class MainActivitySmokeTest {

    @Test
    fun `main screen inflates with material 3 theme`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        assertNotNull(activity.findViewById<View>(R.id.recycler_files))
        assertNotNull(activity.findViewById<View>(R.id.state_container))
        assertNotNull(activity.findViewById<View>(R.id.recycler_connections))
        assertNotNull(activity.findViewById<View>(R.id.fab_upload))

        val toolbar = activity.findViewById<View>(R.id.toolbar)
        assertEquals(
            ContextCompat.getColor(activity, R.color.color_primary),
            MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorPrimary)
        )
        assertEquals(
            ContextCompat.getColor(activity, R.color.color_on_surface),
            MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        )

        controller.destroy()
    }

    @Test
    @Config(qualifiers = "night")
    fun `dark mode resolves dark color roles`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val toolbar = activity.findViewById<View>(R.id.toolbar)

        val surface = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorSurface)
        assertEquals(ContextCompat.getColor(activity, R.color.color_surface), surface)
        // 深色模式下表面亮度应显著低于浅色模式
        val luminance = (0..2).sumOf { shift ->
            val channel = (surface shr (shift * 8)) and 0xFF
            channel / 255.0
        } / 3.0
        assert(luminance < 0.3) { "深色模式 surface 亮度过高: $luminance" }

        controller.destroy()
    }
}
