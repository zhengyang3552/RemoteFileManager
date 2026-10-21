package com.filemanager

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.filemanager.data.AppSettings

/**
 * 应用入口：在 Activity 创建之前应用外观模式，
 * 避免「先按系统主题渲染、再闪一下切到用户选择的主题」。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppSettings(this).themeMode)
    }
}
