package com.aibuilt.claudeclone.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.UUID

private fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Registers a picker against the activity result registry directly, so it works
 * without depending on a particular version of the Compose launcher helper.
 */
@Composable
fun <I, O> rememberResultLauncher(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): ActivityResultLauncher<I>? {
    val activity = LocalContext.current.findActivity()
    val latestResult = rememberUpdatedState(onResult)
    val key = remember { "claude-launch-" + UUID.randomUUID().toString() }
    var launcher by remember { mutableStateOf<ActivityResultLauncher<I>?>(null) }

    DisposableEffect(activity, contract) {
        if (activity == null) {
            onDispose { }
        } else {
            val created = activity.activityResultRegistry.register(key, contract) { value ->
                latestResult.value(value)
            }
            launcher = created
            onDispose {
                launcher = null
                runCatching { created.unregister() }
            }
        }
    }

    return launcher
}
