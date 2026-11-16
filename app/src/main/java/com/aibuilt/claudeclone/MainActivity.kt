package com.aibuilt.claudeclone

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aibuilt.claudeclone.data.AppContainer
import com.aibuilt.claudeclone.ui.ChatScreen
import com.aibuilt.claudeclone.ui.SettingsScreen
import com.aibuilt.claudeclone.ui.chatViewModelFactory
import com.aibuilt.claudeclone.ui.settingsViewModelFactory
import com.aibuilt.claudeclone.ui.theme.ClaudeCloneTheme
import com.aibuilt.claudeclone.viewmodel.ChatViewModel
import com.aibuilt.claudeclone.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    /** True when the app was started from the "Claude API" shortcut icon. */
    private fun startedFromApiAlias(): Boolean {
        val component = intent?.component ?: return false
        if (component.className == MainActivity::class.java.name) return false
        return runCatching {
            val info = packageManager.getActivityInfo(component, PackageManager.GET_META_DATA)
            info.metaData?.getBoolean("open_settings") == true
        }.getOrDefault(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container: AppContainer = (application as ClaudeCloneApp).container
        val startInSettings = startedFromApiAlias() && savedInstanceState == null

        setContent {
            ClaudeCloneTheme {
                val navController = rememberNavController()
                val startRoute = remember { if (startInSettings) "settings" else "chat" }

                NavHost(navController = navController, startDestination = startRoute) {
                    composable("chat") {
                        val vm: ChatViewModel = viewModel(factory = chatViewModelFactory(container))
                        ChatScreen(viewModel = vm, onOpenSettings = { navController.navigate("settings") })
                    }
                    composable("settings") {
                        val vm: SettingsViewModel = viewModel(factory = settingsViewModelFactory(container))
                        SettingsScreen(
                            viewModel = vm,
                            onBack = {
                                if (!navController.popBackStack()) navController.navigate("chat")
                            }
                        )
                    }
                }
            }
        }
    }
}
