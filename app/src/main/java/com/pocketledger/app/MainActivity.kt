@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pocketledger.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PocketLedger() }
    }
}

private val categories = listOf("Snacks", "Dairy", "Restaurant", "Groceries", "Travel", "Shopping", "Other")
private val green = Color(0xFF245C43)
private fun categoryIcon(category: String): ImageVector = when (category) {
    "Snacks", "Restaurant" -> Icons.Rounded.Restaurant
    "Dairy" -> Icons.Rounded.LocalCafe
    "Groceries", "Shopping" -> Icons.Rounded.ShoppingBag
    "Travel" -> Icons.Rounded.DirectionsBus
    else -> Icons.AutoMirrored.Rounded.ReceiptLong
}

@Composable private fun PocketLedger() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { LedgerDb(context) }
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var currency by rememberSaveable { mutableStateOf(prefs.getString("currency", "INR")!!) }
    var appearance by rememberSaveable { mutableStateOf(prefs.getString("theme", "System")!!) }
    val dark = appearance == "Dark" || (appearance == "System" && isSystemInDarkTheme())
    SideEffect {
        val barStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        (context as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
    }
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFABDBBA), background = Color(0xFF111813), surface = Color(0xFF111813))
        else lightColorScheme(primary = green, onPrimary = Color.White, primaryContainer = Color(0xFFD5EEDD), secondaryContainer = Color(0xFFE7EDE5), background = Color(0xFFF7F9F2), surface = Color(0xFFF7F9F2))
    MaterialTheme(colorScheme = scheme) {
        val scope = rememberCoroutineScope()
        var entries by remember { mutableStateOf(emptyList<Entry>()) }
        var loading by remember { mutableStateOf(true) }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var query by rememberSaveable { mutableStateOf("") }
        var filter by rememberSaveable { mutableStateOf("Open") }
        var person by rememberSaveable { mutableStateOf<String?>(null) }
        var editor by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<Entry?>(null) }
        var selected by remember { mutableStateOf<Entry?>(null) }
        var busy by remember { mutableStateOf(false) }
        val message: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        fun refresh() { entries = db.all() }
        fun work(onSuccess: () -> Unit = {}, block: () -> Unit) {
            if (busy) return
            scope.launch {
                busy = true
                try { withContext(Dispatchers.IO) { block() }; refresh(); onSuccess() }
                catch (e: Exception) { message(e.message ?: "Could not save. Please try again.") }
                finally { busy = false }
            }
        }
        LaunchedEffect(Unit) { entries = withContext(Dispatchers.IO) { db.all() }; loading = false }
        fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(if (currency == "INR") "en-IN" else "en-US")).apply {
            this.currency = Currency.getInstance(currency)
            minimumFractionDigits = if (cents % 100 == 0L) 0 else 2
        }.format(java.math.BigDecimal.valueOf(cents, 2))
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) work {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(db.export(currency)) }
                    ?: error("Could not open backup file")
                scope.launch { message("Backup saved") }
            }
        }
        var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
        val receivable = entries.filter { it.receivable }.sumOf { it.remaining }
        val payable = entries.filter { !it.receivable }.sumOf { it.remaining }
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(if (person != null) person!! else listOf("Pocket Ledger", "Your people", "Settings")[tab], fontWeight = FontWeight.Bold) },
                    navigationIcon = { if (person != null) IconButton(onClick = { person = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    actions = { if (tab != 2) Icon(Icons.Rounded.CloudOff, "Stored offline", modifier = Modifier.padding(end = 22.dp), tint = MaterialTheme.colorScheme.primary) })
            },
            bottomBar = {
                NavigationBar {
                    listOf("Overview" to Icons.Rounded.SpaceDashboard, "People" to Icons.Rounded.People, "Settings" to Icons.Rounded.Tune).forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i; person = null }, icon = { Icon(icon, label) }, label = { Text(label) })
                    }
                }
            },
            floatingActionButton = {
                if (tab != 2) ExtendedFloatingActionButton(onClick = { editing = null; editor = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add expense") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (tab == 2) {
                    item { Text("A little order. A lot less awkward.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    item { Text("Private by default. Your ledger lives on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { SectionLabel("MAKE IT YOURS") }
                    item { Text("Appearance"); ChipRow(listOf("System", "Light", "Dark"), appearance) { appearance = it; prefs.edit().putString("theme", it).apply() } }
                    item { Text("Currency"); ChipRow(listOf("INR", "USD", "EUR", "GBP"), currency) { currency = it; prefs.edit().putString("currency", it).apply() }; Text("Changes the display currency; amounts are not converted.", style = MaterialTheme.typography.bodySmall) }
                    item { SectionLabel("YOUR DATA") }
                    item { ActionCard(Icons.Rounded.FileUpload, "Export backup", "Save a JSON copy somewhere safe", !busy) { export.launch("pocket-ledger-${LocalDate.now()}.json") } }
                    item { ActionCard(Icons.Rounded.FileDownload, "Restore backup", "Merge a previous Pocket Ledger backup", !busy) { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } }
                    item { Text("No accounts. No ads. No internet permission.\nUninstalling removes local data. Export a backup before changing phones. Backup files contain names and expense details.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { Text("Pocket Ledger · 1.0", style = MaterialTheme.typography.labelMedium) }
                } else {
                    if (person == null && tab == 0) {
                        item { Text("Small spends.\nClear friendships.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp)) {
                                Column(Modifier.padding(24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("YOUR NET BALANCE", style = MaterialTheme.typography.labelMedium)
                                    Text(money(receivable - payable), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                                    Text(if (receivable == 0L && payable == 0L) "All square. Keep the good times going." else if (receivable >= payable) "Overall, you’re owed more than you owe." else "Overall, you owe more than you’re owed.", style = MaterialTheme.typography.bodyMedium)
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        Balance("You’re owed", money(receivable), Icons.AutoMirrored.Rounded.CallReceived, Modifier.weight(1f))
                                        Balance("You owe", money(payable), Icons.AutoMirrored.Rounded.CallMade, Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    item { OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search people, items, notes…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) }
                    if (tab == 1 && person == null) {
                        item { SectionLabel("EVERYONE IN YOUR LEDGER") }
                        val groups = entries.groupBy { it.person }.filterKeys { it.contains(query, true) }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
                        if (groups.isEmpty()) item { EmptyState("No people here yet", "Add an expense with a friend to get started.") }
                        items(groups.entries.toList(), key = { it.key }) { (name, records) ->
                            val net = records.sumOf { if (it.receivable) it.remaining else -it.remaining }
                            Card(onClick = { person = name; query = ""; filter = "Open" }, shape = RoundedCornerShape(24.dp)) {
                                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(name); Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text("${records.count { it.remaining > 0 }} open expenses", style = MaterialTheme.typography.bodySmall) }
                                    Column(horizontalAlignment = Alignment.End) { Text(money(kotlin.math.abs(net)), fontWeight = FontWeight.Bold); Text(if (net > 0) "owes you" else if (net < 0) "you owe" else "all square", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    } else {
                        item { ChipRow(listOf("Open", "Owed to me", "I owe", "Settled", "All"), filter) { filter = it } }
                        val visible = entries.filter { e -> (person == null || person == e.person) && listOf(e.person, e.title, e.note, e.category).any { it.contains(query, true) } && when (filter) { "Open" -> e.remaining > 0; "Owed to me" -> e.receivable && e.remaining > 0; "I owe" -> !e.receivable && e.remaining > 0; "Settled" -> e.remaining == 0L; else -> true } }
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { SectionLabel("${if (person == null) "RECENT" else "PERSON’S"} EXPENSES"); Text("${visible.size} items", style = MaterialTheme.typography.labelMedium) } }
                        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        else if (visible.isEmpty()) item { EmptyState(if (entries.isEmpty()) "Start with the small stuff" else "Nothing to show", if (entries.isEmpty()) "Chips, a coffee, a shared dinner.\nTap Add expense to keep track." else "Try another filter or search.") }
                        items(visible, key = { it.id }) { e ->
                            Card(onClick = { selected = e }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(categoryIcon(e.category), null, tint = MaterialTheme.colorScheme.primary) }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(e.person, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(LocalDate.parse(e.date).format(DateTimeFormatter.ofPattern("d MMM yyyy")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(money(if (e.remaining == 0L) e.cents else e.remaining), fontWeight = FontWeight.Bold)
                                        Text(if (e.remaining == 0L) "Settled ✓" else if (e.receivable) "owes you" else "you owe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        if (e.paid > 0 && e.remaining > 0) Text("Part-paid", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (editor) ExpenseEditor(editing, person, entries.map { it.person }.distinct(), currency, busy, onDismiss = { editor = false }) { e -> work(onSuccess = { editor = false }) { db.save(e) } }
        selected?.let { e -> EntryDetails(e, ::money, busy, onDismiss = { selected = null }, onEdit = { editing = e; selected = null; editor = true }, onDelete = { work(onSuccess = { selected = null }) { db.delete(e) } }, onPay = { amount -> work(onSuccess = { selected = null }) { db.save(e.copy(paid = e.paid + amount)) } }) }
        restoreUri?.let { uri ->
            AlertDialog(onDismissRequest = { restoreUri = null }, title = { Text("Restore this backup?") }, text = { Text("New expenses will be added. Matching expense IDs will be replaced by the backup, including repayment amounts. Other entries stay as they are. Export your current ledger first if you need a copy.") }, confirmButton = { TextButton(onClick = {
                restoreUri = null
                work {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            require(output.size() + count <= 10_000_000) { "Backup exceeds 10 MB" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("Could not read file")
                    require(bytes.size <= 10_000_000) { "Backup exceeds 10 MB" }
                    val restoredCurrency = db.restore(bytes.toString(Charsets.UTF_8))
                    prefs.edit().putString("currency", restoredCurrency).apply()
                    with(scope) { launch { currency = restoredCurrency; message("Backup restored") } }
                }
            }) { Text("Restore") } }, dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } })
        }
    }
}

@Composable private fun SectionLabel(text: String) { Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
@Composable private fun Balance(label: String, amount: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(label, style = MaterialTheme.typography.labelMedium) }; Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
}
@Composable private fun ChipRow(values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { values.forEach { FilterChip(selected = selected == it, onClick = { onSelect(it) }, label = { Text(it) }) } }
}
@Composable private fun Avatar(name: String) { Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) } }
@Composable private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Savings, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable private fun ActionCard(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(22.dp)) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(16.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) } } }
}
