package com.aibuilt.claudeclone.ui

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibuilt.claudeclone.data.AppContainer
import com.aibuilt.claudeclone.viewmodel.ChatViewModel
import com.aibuilt.claudeclone.viewmodel.SettingsViewModel

fun chatViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { ChatViewModel(container) }
}

fun settingsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { SettingsViewModel(container) }
}
