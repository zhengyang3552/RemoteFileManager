package com.filemanager.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.filemanager.model.SortField

/**
 * 轻量设置存储：外观模式、排序偏好、上次使用的连接。
 * 与连接配置分开存放，避免「清空连接」时把界面偏好一并丢失。
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 外观模式，取值为 [AppCompatDelegate] 的 MODE_NIGHT_* 常量 */
    var themeMode: Int
        get() {
            val stored = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            return if (stored in VALID_THEME_MODES) stored else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        set(value) {
            prefs.edit().putInt(KEY_THEME, value).apply()
        }

    /**
     * 动态取色（Android 12+ 壁纸取色，Material DynamicColors）。
     * 默认关闭以保持统一品牌色；关闭/开启后由界面触发重建立即生效。
     */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        }

    var sortField: SortField
        get() = SortField.fromNameOrDefault(prefs.getString(KEY_SORT_FIELD, null))
        set(value) {
            prefs.edit().putString(KEY_SORT_FIELD, value.name).apply()
        }

    var sortAscending: Boolean
        get() = prefs.getBoolean(KEY_SORT_ASC, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SORT_ASC, value).apply()
        }

    var lastConnectionId: String?
        get() = prefs.getString(KEY_LAST_CONNECTION, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_CONNECTION, value).apply()
        }

    private companion object {
        const val PREF_NAME = "rfm_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_SORT_FIELD = "sort_field"
        const val KEY_SORT_ASC = "sort_ascending"
        const val KEY_LAST_CONNECTION = "last_connection_id"

        val VALID_THEME_MODES = setOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
    }
}
