package com.aibuilt.claudeclone.ui

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aibuilt.claudeclone.model.Role
import com.aibuilt.claudeclone.ui.components.ClaudeLogo
import com.aibuilt.claudeclone.ui.components.Composer
import com.aibuilt.claudeclone.ui.components.ConversationDrawer
import com.aibuilt.claudeclone.ui.components.MessageBubble
import com.aibuilt.claudeclone.viewmodel.ChatError
import com.aibuilt.claudeclone.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onOpenSettings: () -> Unit) {
    val conversations by viewModel.conversations.collectAsState(initial = emptyList())
    val activeId by viewModel.activeId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val input by viewModel.input.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val streaming by viewModel.streaming.collectAsState()
    val error by viewModel.error.collectAsState()
    val attaching by viewModel.attaching.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    val photoLauncher = rememberResultLauncher(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attach(uri)
    }

    val pdfLauncher = rememberResultLauncher(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.attach(uri)
    }

    val lastText = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastText) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = conversations,
                activeId = activeId,
                onSelect = { id ->
                    viewModel.openConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNew = {
                    viewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onRename = { id, title -> viewModel.rename(id, title) },
                onDelete = { id -> viewModel.delete(id) }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Chats")
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ClaudeLogo(size = 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Claude",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Start
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::newChat) {
                            Icon(Icons.Filled.Add, contentDescription = "New chat")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "API settings")
                        }
                    }
                )
            },
            bottomBar = {
                Composer(
                    input = input,
                    onInput = viewModel::onInputChange,
                    pending = pending,
                    attaching = attaching,
                    streaming = streaming,
                    onAttachPhoto = {
                        photoLauncher?.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onAttachPdf = { pdfLauncher?.launch(arrayOf("application/pdf")) },
                    onClearAttachment = viewModel::clearAttachment,
                    onSend = viewModel::send,
                    onStop = viewModel::stop
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (messages.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(messages, key = { _, item -> item.id }) { index, message ->
                            MessageBubble(
                                message = message,
                                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                                onRetry = viewModel::retry,
                                canRegenerate = index == messages.lastIndex &&
                                    message.role == Role.ASSISTANT &&
                                    !message.streaming
                            )
                        }
                    }
                }

                error?.let { chatError ->
                    ErrorLine(
                        error = chatError,
                        onRetry = {
                            viewModel.dismissError()
                            viewModel.retry()
                        },
                        onDismiss = viewModel::dismissError
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ClaudeLogo(size = 54.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "How can I help you today?",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Type a message below, or attach a photo or PDF.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorLine(error: ChatError, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Send failed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            SelectionContainer {
                Column {
                    if (error.url.isNotBlank()) {
                        Text(
                            text = error.url,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (error.exceptionClass.isNotBlank()) {
                        Text(
                            text = "${error.exceptionClass}: ${error.text}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = error.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            TextButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}
