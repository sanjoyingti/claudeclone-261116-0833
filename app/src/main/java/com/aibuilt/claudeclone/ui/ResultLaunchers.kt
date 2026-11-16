package com.aibuilt.claudeclone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

/**
 * Wraps the activity-result API in one composable call so the chat screen can
 * open the photo or PDF picker and hand the picked file straight to the view model.
 */
@Composable
fun <I, O> rememberResultLauncher(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): ActivityResultLauncher<I> {
    val callback = rememberUpdatedState(onResult)
    return rememberLauncherForActivityResult(contract) { result -> callback.value(result) }
}
