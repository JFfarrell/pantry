package com.pantry.app.tesco

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pantry.app.ui.theme.PantryTheme
import java.net.URLEncoder

enum class ItemState { PENDING, ADDED, SKIPPED }

data class QueueItem(
    val name: String,
    val quantity: String,
    /** Editable independently of [name], so fixing a search does not rename the item. */
    val searchTerm: String,
    val state: ItemState = ItemState.PENDING
)

/**
 * Feature 9, deep-link flavour. Opens Tesco's groceries site and walks the
 * shopping list one item at a time, putting each item's search page in front of
 * you. You tap Add on Tesco's own page -- their button, their quantity stepper,
 * your choice of brand and pack size -- and then Added here to move on.
 *
 * The only thing this depends on is the shape of the search URL, which is a link
 * you could bookmark. There is no DOM automation left, so a Tesco redesign
 * cannot quietly break it.
 *
 * The trade: the app cannot verify that anything went in the basket. "Added"
 * means you said so. Tesco's own basket counter, visible on their page
 * throughout, is the real check.
 */
class TescoBasketActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var consentScript: String = ""

    private var items by mutableStateOf<List<QueueItem>>(emptyList())
    private var index by mutableStateOf(0)
    private var started by mutableStateOf(false)
    private var pageLoading by mutableStateOf(false)
    private var finished by mutableStateOf(false)
    private var summaryOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consentScript = runCatching {
            assets.open("tesco_consent.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")

        val names = intent.getStringArrayListExtra(EXTRA_ITEMS).orEmpty()
        val quantities = intent.getStringArrayListExtra(EXTRA_QUANTITIES).orEmpty()
        items = names.mapIndexed { i, name ->
            QueueItem(
                name = name,
                quantity = quantities.getOrNull(i).orEmpty(),
                searchTerm = name
            )
        }

        setContent { PantryTheme { RunScreen() } }
    }

    // ------------------------------------------------------------ navigation

    private fun searchUrl(term: String) =
        "$GROCERIES_URL/search?query=" + URLEncoder.encode(term, "UTF-8")

    private fun goTo(position: Int) {
        if (position !in items.indices) return
        index = position
        pageLoading = true
        webView?.loadUrl(searchUrl(items[position].searchTerm))
    }

    private fun start() {
        started = true
        goTo(0)
    }

    /** Records how the current item ended and moves on, or finishes the run. */
    private fun settle(state: ItemState) {
        items = items.toMutableList().also { it[index] = it[index].copy(state = state) }
        if (index + 1 in items.indices) {
            goTo(index + 1)
        } else {
            finished = true
            summaryOpen = true
        }
    }

    /** Undo: step back and clear the decision made on that item. */
    private fun stepBack() {
        if (index == 0) return
        items = items.toMutableList().also {
            it[index - 1] = it[index - 1].copy(state = ItemState.PENDING)
        }
        goTo(index - 1)
    }

    private fun retermCurrent(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        items = items.toMutableList().also { it[index] = it[index].copy(searchTerm = trimmed) }
        goTo(index)
    }

    private fun finishTickingOff() {
        val added = items.filter { it.state == ItemState.ADDED }.map { it.name }
        setResult(
            Activity.RESULT_OK,
            Intent().putStringArrayListExtra(EXTRA_ADDED, ArrayList(added))
        )
        finish()
    }

    // ---------------------------------------------------------------- screen

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun RunScreen() {
        var showQueue by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf(false) }

        val done = items.count { it.state != ItemState.PENDING }
        val current = items.getOrNull(index)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Fill Tesco basket") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = { showQueue = true }) {
                            Text(if (started) "$done of ${items.size}" else "${items.size} items")
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {

                Column(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        factory = { ctx -> createWebView(ctx) }
                    )

                    when {
                        !started -> StartBar(items.firstOrNull()) { start() }
                        editing && current != null -> EditBar(
                            item = current,
                            onCancel = { editing = false },
                            onSearch = { term -> editing = false; retermCurrent(term) }
                        )
                        current != null -> ActionBar(
                            item = current,
                            position = index,
                            total = items.size,
                            loading = pageLoading,
                            finished = finished,
                            onBack = { stepBack() },
                            onEdit = { editing = true },
                            onSkip = { settle(ItemState.SKIPPED) },
                            onAdded = { settle(ItemState.ADDED) },
                            onReopenSummary = { summaryOpen = true }
                        )
                    }
                }

                if (showQueue) {
                    QueueSheet(
                        items = items,
                        current = index,
                        started = started,
                        onDismiss = { showQueue = false },
                        onJump = { target -> showQueue = false; if (started) goTo(target) }
                    )
                }

                if (finished && summaryOpen) {
                    SummarySheet(
                        items = items,
                        onViewBasket = {
                            summaryOpen = false
                            pageLoading = true
                            webView?.loadUrl("$GROCERIES_URL/trolley")
                        },
                        onTickOff = { finishTickingOff() },
                        onClose = { summaryOpen = false }
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView =
        WebView(context).apply {
            webView = this
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(consentScript, null)
                    pageLoading = false
                }
            }
            loadUrl(GROCERIES_URL)
        }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ITEMS = "items"
        const val EXTRA_QUANTITIES = "quantities"
        const val EXTRA_ADDED = "added"
        private const val GROCERIES_URL = "https://www.tesco.com/groceries/en-GB"
    }
}

// ------------------------------------------------------------------ bottom bars

@Composable
private fun BarSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

@Composable
private fun StartBar(first: QueueItem?, onStart: () -> Unit) {
    BarSurface {
        Text("Sign in above, then start", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            "Nothing is ordered for you. Each item opens its Tesco search; you tap Add there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            enabled = first != null
        ) { Text(first?.let { "Start with ${it.name}" } ?: "Nothing to add") }
    }
}

@Composable
private fun ActionBar(
    item: QueueItem,
    position: Int,
    total: Int,
    loading: Boolean,
    finished: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit,
    onAdded: () -> Unit,
    onReopenSummary: () -> Unit
) {
    BarSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else (position + 1).toFloat() / total },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "${position + 1} of $total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = position > 0, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ChevronLeft, "Previous item", Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = TextDecoration.Underline
                )
                if (item.searchTerm != item.name) {
                    Text(
                        "searching: ${item.searchTerm}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.quantity.isNotBlank()) {
                Text(
                    item.quantity,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (finished) {
            Button(onClick = onReopenSummary, modifier = Modifier.fillMaxWidth()) {
                Text("Show summary")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.width(108.dp)) { Text("Skip") }
                Button(
                    onClick = onAdded,
                    modifier = Modifier.weight(1f),
                    // Disabled while the next page loads, so an impatient double
                    // tap cannot skip an item without you seeing it.
                    enabled = !loading
                ) {
                    if (loading) {
                        Text("Loading...")
                    } else {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Text("  Added")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditBar(item: QueueItem, onCancel: () -> Unit, onSearch: (String) -> Unit) {
    var draft by remember(item.searchTerm) { mutableStateOf(item.searchTerm) }
    BarSurface {
        Text("Search Tesco for", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(draft) })
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "For \"${item.name}\". Changing this does not rename the item on your list.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.width(108.dp)) { Text("Cancel") }
            Button(onClick = { onSearch(draft) }, modifier = Modifier.weight(1f)) { Text("Search") }
        }
    }
}

// --------------------------------------------------------------------- sheets

@Composable
private fun Scrim(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss)
    )
}

@Composable
private fun BottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun QueueSheet(
    items: List<QueueItem>,
    current: Int,
    started: Boolean,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    val done = items.count { it.state != ItemState.PENDING }
    BottomSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your list", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "$done of ${items.size} done",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            itemsIndexed(items) { position, item ->
                val isCurrent = started && position == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onJump(position) }
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StateDot(item.state, isCurrent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                            item.state == ItemState.PENDING -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                    Text(
                        item.quantity,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
    }
}

@Composable
private fun StateDot(state: ItemState, isCurrent: Boolean) {
    val size = 18.dp
    when {
        isCurrent -> Box(
            Modifier.size(size).clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onPrimary)
            )
        }
        state == ItemState.ADDED -> Box(
            Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(Color(0xFF2E7D32)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Check, null, Modifier.size(12.dp), tint = Color.White) }
        state == ItemState.SKIPPED -> Box(
            Modifier.size(size).clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline) }
        else -> Box(
            Modifier.size(size).clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
private fun SummarySheet(
    items: List<QueueItem>,
    onViewBasket: () -> Unit,
    onTickOff: () -> Unit,
    onClose: () -> Unit
) {
    val added = items.filter { it.state == ItemState.ADDED }
    val skipped = items.filter { it.state == ItemState.SKIPPED }

    BottomSheet(onClose) {
        Text(
            "${added.size} added, ${skipped.size} skipped",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Pantry cannot see inside your basket, so these are the items you marked. " +
                "Check the basket before you pay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (skipped.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Skipped, add these yourself", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    skipped.forEach {
                        Text(
                            "-  ${it.name}${if (it.quantity.isBlank()) "" else "  (${it.quantity})"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onViewBasket, modifier = Modifier.fillMaxWidth()) {
            Text("View basket in Tesco")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onTickOff,
            modifier = Modifier.fillMaxWidth(),
            enabled = added.isNotEmpty()
        ) { Text("Tick ${added.size} items off my list") }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Keep shopping")
        }
    }
}
