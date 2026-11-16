package com.aibuilt.claudeclone.data

import android.content.Context
import com.aibuilt.claudeclone.network.HttpEngine

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val profileStore = ProfileStore(appContext)
    val conversationStore = ConversationStore(appContext)
    val httpEngine = HttpEngine()
}
