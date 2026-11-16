package com.aibuilt.claudeclone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aibuilt.claudeclone.model.Dialect
import com.aibuilt.claudeclone.model.Presets
import com.aibuilt.claudeclone.model.Profile
import com.aibuilt.claudeclone.util.AppInfo
import com.aibuilt.claudeclone.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showKey by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Claude API", style = MaterialTheme.typography.titleLarge) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Paste your base URL and your API key. That is all the app needs — it works out the request shape from the address.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.errors.forEach { message ->
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            SectionLabel("Saved connections")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.profiles, key = { it.id }) { profile ->
                    FilterChip(
                        selected = profile.id == state.draft.id && !state.isNew,
                        onClick = { viewModel.selectProfile(profile.id) },
                        label = { Text(profile.name, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (profile.id == state.defaultId) {
                            {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = "Default",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
                item {
                    AssistChip(
                        onClick = viewModel::newProfile,
                        label = { Text("New", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                        }
                    )
                }
            }

            SectionLabel("Base URL")
            OutlinedTextField(
                value = state.draft.baseUrl,
                onValueChange = viewModel::editBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://api.example.com/v1") },
                supportingText = {
                    Text("Full server address with scheme, host, port or path prefix. Trailing slashes and a pasted endpoint are tidied up on save.")
                }
            )

            SectionLabel("API key")
            OutlinedTextField(
                value = state.draft.apiKey,
                onValueChange = { value -> viewModel.edit { it.copy(apiKey = value) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("your key") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showKey) "Hide key" else "Show key"
                        )
                    }
                },
                supportingText = { Text("Leave it blank if your server needs no key. Missing auth is fine.") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.save() }, modifier = Modifier.weight(1f)) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !state.testing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Test connection")
                }
            }

            if (state.savedFlash) {
                Text(
                    "Saved. This connection is now the one the chat uses.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (state.test.ran) {
                TestReport(
                    url = state.test.url,
                    status = state.test.status,
                    exceptionClass = state.test.exceptionClass,
                    bodyHead = state.test.bodyHead,
                    onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                    onClear = viewModel::clearTest
                )
            }

            TextButton(onClick = viewModel::toggleAdvanced) {
                Text(if (state.advanced) "Hide advanced settings" else "Advanced settings")
            }

            if (state.advanced) {
                AdvancedSection(viewModel = viewModel, state = state)
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Text(
                text = "Claude Clone ${AppInfo.VERSION}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Same look, swappable brain. Every request is built from the active connection.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AdvancedSection(viewModel: SettingsViewModel, state: com.aibuilt.claudeclone.viewmodel.SettingsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Only needed when a server does not fit the usual shapes.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionLabel("Connection name")
        OutlinedTextField(
            value = state.draft.name,
            onValueChange = { value -> viewModel.edit { it.copy(name = value) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("My server") }
        )

        SectionLabel("Model")
        OutlinedTextField(
            value = state.draft.model,
            onValueChange = { value -> viewModel.edit { it.copy(model = value) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("any-model-name") },
            supportingText = { Text("Plain text. Filled in for you already; change it if your server wants another name.") }
        )

        SectionLabel("Request shape")
        DialectPicker(selected = state.draft.dialect, onSelect = viewModel::setDialect)

        SectionLabel("Endpoint path template")
        OutlinedTextField(
            value = state.draft.pathTemplate,
            onValueChange = { value -> viewModel.edit { it.copy(pathTemplate = value) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("{base}/v1/messages") },
            supportingText = { Text("{base} is the base URL, {model} is the URL-encoded model name.") }
        )

        SectionLabel("Auth header name")
        OutlinedTextField(
            value = state.draft.authHeaderName,
            onValueChange = viewModel::setAuthHeader,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Authorization") },
            supportingText = { Text("Leave empty for no auth header at all.") }
        )
        ChipRow(values = Presets.authHeaderPresets, onPick = viewModel::setAuthHeader)

        SectionLabel("Auth value prefix")
        OutlinedTextField(
            value = state.draft.authValuePrefix,
            onValueChange = viewModel::setAuthPrefix,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Bearer") },
            supportingText = { Text("\"raw\" or empty sends the key with no prefix.") }
        )
        ChipRow(values = Presets.authPrefixPresets, onPick = viewModel::setAuthPrefix)

        SectionLabel("Extra headers")
        state.draft.extraHeaders.forEachIndexed { index, pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = pair.key,
                    onValueChange = { value -> viewModel.updateHeader(index, key = value) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Header") }
                )
                OutlinedTextField(
                    value = pair.value,
                    onValueChange = { value -> viewModel.updateHeader(index, value = value) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Value") }
                )
                IconButton(onClick = { viewModel.removeHeader(index) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove header")
                }
            }
        }
        OutlinedButton(onClick = viewModel::addHeader) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add header")
        }

        if (state.draft.dialect == Dialect.CUSTOM) {
            SectionLabel("Custom JSON body")
            OutlinedTextField(
                value = state.draft.customBody,
                onValueChange = { value -> viewModel.edit { it.copy(customBody = value) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                placeholder = { Text("{\"model\": \"{model}\", \"prompt\": \"{text}\"}") },
                supportingText = { Text("Sent exactly as written. {model}, {text} and {system} are substituted.") }
            )
        }

        SectionLabel("Response JSON path")
        OutlinedTextField(
            value = state.draft.responsePath,
            onValueChange = viewModel::setResponsePath,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("content[0].text") },
            supportingText = { Text("If the path misses, the raw body is shown in the bubble instead of an error.") }
        )
        ChipRow(values = Presets.responsePathPresets, onPick = viewModel::setResponsePath)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Streaming")
                Text(
                    text = "Off waits for the whole reply and shows it once.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.draft.stream,
                onCheckedChange = { value -> viewModel.edit { it.copy(stream = value) } }
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        SectionLabel("Manage saved connections")
        state.profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                isDefault = profile.id == state.defaultId,
                isSelected = profile.id == state.draft.id && !state.isNew,
                onSelect = { viewModel.selectProfile(profile.id) },
                onDefault = { viewModel.setDefault(profile.id) },
                onDelete = { viewModel.delete(profile.id) }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ChipRow(values: List<String>, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(values) { value ->
            AssistChip(
                onClick = { onPick(value) },
                label = { Text(value, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun DialectPicker(selected: Dialect, onSelect: (Dialect) -> Unit) {
    val labels = mapOf(
        Dialect.OPENAI to "OpenAI compatible",
        Dialect.ANTHROPIC to "Anthropic compatible",
        Dialect.GOOGLE to "Google style",
        Dialect.CUSTOM to "Custom JSON"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Dialect.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { dialect ->
                    FilterChip(
                        selected = selected == dialect,
                        onClick = { onSelect(dialect) },
                        label = { Text(labels.getValue(dialect), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    isDefault: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = profile.baseUrl.ifBlank { "no base URL yet" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDefault) {
                Icon(
                    imageVector = if (isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Make default",
                    tint = if (isDefault) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isSelected) {
                AssistChip(onClick = onSelect, label = { Text("Use", style = MaterialTheme.typography.labelSmall) })
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete connection")
            }
        }
    }
}

@Composable
private fun TestReport(
    url: String,
    status: String,
    exceptionClass: String,
    bodyHead: String,
    onCopy: (String) -> Unit,
    onClear: () -> Unit
) {
    val report = buildString {
        appendLine("URL: $url")
        appendLine("Status: $status")
        if (exceptionClass.isNotBlank()) appendLine("Exception: $exceptionClass")
        if (bodyHead.isNotBlank()) append("Body: $bodyHead")
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Test result",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onCopy(report) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy result",
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = report,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (exceptionClass.isNotBlank()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.padding(top = 6.dp)) {
                Text("Clear")
            }
        }
    }
}
