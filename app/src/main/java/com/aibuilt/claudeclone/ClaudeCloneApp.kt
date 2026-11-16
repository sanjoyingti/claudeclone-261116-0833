package com.aibuilt.claudeclone

import android.app.Application
import com.aibuilt.claudeclone.data.AppContainer

class ClaudeCloneApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
