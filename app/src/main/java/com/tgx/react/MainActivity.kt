package com.tgx.react

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

private val Purple = Color(0xFF8A4BFF)
private val Pink = Color(0xFFFF4D8D)

class MainActivity : ComponentActivity() {
    private var keyboardState by mutableStateOf(KeyboardState(enabled = false, selected = false))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Purple, secondary = Pink,
                    background = Color(0xFF15121C), surface = Color(0xFF15121C),
                    surfaceVariant = Color(0xFF231F2E), primaryContainer = Color(0xFF3A2A66),
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ManagerScreen(
                        keyboard = keyboardState,
                        onOpenSettings = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                        onPickKeyboard = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
                    )
                }
            }
        }
    }

    override fun onResume() { super.onResume(); refreshKeyboardState() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshKeyboardState()
    }

    private fun refreshKeyboardState() {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        keyboardState = KeyboardState(enabled, current.startsWith("$packageName/"))
    }
}

data class KeyboardState(val enabled: Boolean, val selected: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagerScreen(keyboard: KeyboardState, onOpenSettings: () -> Unit, onPickKeyboard: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val items by ReactionRepository.items.collectAsState()
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { ReactionRepository.import(context.contentResolver, it) }
            }
            ReactionRepository.add(added)
            importing = false
            val failed = uris.size - added.size
            if (failed > 0) snackbar.showSnackbar("$failed image(s) couldn't be added")
        }
    }

    fun deleteSelected() {
        val removed = ReactionRepository.remove(selection)
        selection = emptySet()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val res = snackbar.showSnackbar(
                "Removed ${removed.size} reaction${if (removed.size == 1) "" else "s"}",
                actionLabel = "Undo", duration = SnackbarDuration.Short,
            )
            if (res == SnackbarResult.ActionPerformed) ReactionRepository.restore(removed)
            else withContext(Dispatchers.IO) { ReactionRepository.purgeOrphans() }
        }
    }

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyGridState
        val toId = to.key as? String ?: return@rememberReorderableLazyGridState
        ReactionRepository.move(fromId, toId)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = { Text("TGXReact", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            } else {
                TopAppBar(
                    title = { Text("${selection.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, "Clear selection") }
                    },
                    actions = {
                        IconButton(onClick = ::deleteSelected) { Icon(Icons.Filled.Delete, "Delete") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                icon = {
                    if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Add, null)
                },
                text = { Text("Add reactions") },
                containerColor = Purple, contentColor = Color.White,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!keyboard.enabled || !keyboard.selected) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "setup") {
                    SetupCard(keyboard, onOpenSettings, onPickKeyboard)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "height") { HeightCard() }
            if (items.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "hint") {
                    Text(
                        "Long-press and drag to reorder · tap to select",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Text(
                        "No reactions yet.\nTap “Add reactions” to pick images or GIFs from your device.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    )
                }
            }
            items(items, key = { it.id }) { r ->
                ReorderableItem(reorderState, key = r.id) { isDragging ->
                    val selected = r.id in selection
                    val scale by animateFloatAsState(if (isDragging) 1.08f else if (selected) 0.92f else 1f)
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .scale(scale)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (selected) Modifier.border(BorderStroke(3.dp, Pink), RoundedCornerShape(14.dp))
                                else Modifier
                            )
                            .longPressDraggableHandle(
                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            )
                            .clickable {
                                selection = if (selected) selection - r.id else selection + r.id
                            }
                    ) {
                        Thumbnail(r)
                        if (selected) {
                            Icon(
                                Icons.Filled.CheckCircle, null, tint = Pink,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                                    .clip(CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(r: Reaction) {
    val px = with(LocalDensity.current) { 120.dp.roundToPx() }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF231F2E.toInt()) } },
        update = { ThumbnailLoader.load(it, ReactionRepository.fileFor(r), px) },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun HeightCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var height by remember { mutableStateOf(KeyboardPrefs.heightDp(context).toFloat()) }
    val steps = (KeyboardPrefs.MAX_HEIGHT_DP - KeyboardPrefs.MIN_HEIGHT_DP) / 10 - 1
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keyboard height", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${height.toInt()} dp", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = height,
                onValueChange = { height = it },
                onValueChangeFinished = { KeyboardPrefs.setHeightDp(context, height.toInt()) },
                valueRange = KeyboardPrefs.MIN_HEIGHT_DP.toFloat()..KeyboardPrefs.MAX_HEIGHT_DP.toFloat(),
                steps = steps,
            )
            Text("Applies the next time the keyboard opens.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupCard(state: KeyboardState, onOpenSettings: () -> Unit, onPickKeyboard: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_kb_globe), null, tint = Purple)
                Spacer(Modifier.size(10.dp))
                Text("Set up the keyboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Step(1, "Enable TGXReact in keyboard settings", done = state.enabled)
            Step(2, "Switch to TGXReact when you want to react", done = state.selected)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.enabled) Button(onClick = onOpenSettings) { Text("Enable") }
                else OutlinedButton(onClick = onPickKeyboard) { Text("Choose keyboard") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Tip: use the keyboard button in your navigation bar to switch back to your normal keyboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Step(n: Int, text: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).then(
                if (done) Modifier else Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(22.dp))
            else Text("$n", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
