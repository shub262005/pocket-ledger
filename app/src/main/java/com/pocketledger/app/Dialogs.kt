@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pocketledger.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable internal fun ExpenseEditor(existing: Entry?, initialPerson: String?, names: List<String>, currency: String, busy: Boolean, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.person ?: initialPerson.orEmpty()) }
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(existing?.let { java.math.BigDecimal.valueOf(it.cents, 2).toPlainString() }.orEmpty()) }
    var direction by rememberSaveable { mutableStateOf(existing?.receivable ?: true) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "Snacks") }
    var date by rememberSaveable { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var split by rememberSaveable { mutableStateOf(false) }
    var count by rememberSaveable { mutableStateOf("2") }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val parsed = parseMoney(amount)
    val divisor = count.toIntOrNull()
    val finalAmount = if (!split) parsed else if (parsed != null && divisor != null && divisor in 2..100) splitMoney(parsed, divisor) else null
    val valid = name.isNotBlank() && title.isNotBlank() && finalAmount != null && finalAmount > 0 && finalAmount >= (existing?.paid ?: 0)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(topBar = { TopAppBar(title = { Text(if (existing == null) "Add expense" else "Edit expense", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") } }) }, bottomBar = {
                Surface(shadowElevation = 4.dp) { Button(onClick = {
                    attempted = true
                    if (valid) onSave(Entry(id = existing?.id ?: java.util.UUID.randomUUID().toString(), person = names.firstOrNull { it.equals(name.trim(), true) } ?: name.trim(), title = title.trim(), cents = finalAmount!!, paid = existing?.paid ?: 0, receivable = direction, category = category, date = date, note = note.trim()))
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(54.dp)) { Text(if (busy) "Saving…" else "Save expense") } }
            }) { padding ->
                Column(Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Who’s picking up the tab?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = direction, onClick = { direction = true }, label = { Text("They owe me") })
                        FilterChip(selected = !direction, onClick = { direction = false }, label = { Text("I owe them") })
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Person’s name") }, isError = attempted && name.isBlank(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    val suggestions = names.filter { it.contains(name, true) && !it.equals(name, true) }.take(3)
                    if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { suggestions.forEach { SuggestionChip(onClick = { name = it }, label = { Text(it.take(18)) }) } }
                    OutlinedTextField(value = title, onValueChange = { title = it.take(120) }, label = { Text("What was it for?") }, placeholder = { Text("e.g. Chips and cold coffee") }, isError = attempted && title.isBlank(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amount, onValueChange = { amount = it.take(16) }, label = { Text("Amount ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = attempted && !validAmount(finalAmount, existing), modifier = Modifier.fillMaxWidth(), supportingText = { Text(if (existing != null && existing.paid > 0) "Must cover the amount already repaid: ${java.math.BigDecimal.valueOf(existing.paid, 2)}" else "Enter a positive amount, up to 2 decimal places") })
                    if (existing == null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text("Split a bill equally", fontWeight = FontWeight.Medium); Text("Save one person’s share", style = MaterialTheme.typography.bodySmall) }; Switch(checked = split, onCheckedChange = { split = it }) }
                        if (split) {
                            OutlinedTextField(value = count, onValueChange = { count = it.take(3) }, label = { Text("People sharing (including you)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = attempted && (divisor == null || divisor !in 2..100), modifier = Modifier.fillMaxWidth())
                            Text("This entry records one share for $name: ${finalAmount?.let { java.math.BigDecimal.valueOf(it, 2) } ?: "—"} $currency. Add an entry for each other person. Shares round to the nearest minor unit.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Snacks", "Dairy", "Restaurant", "Groceries", "Travel", "Shopping", "Other").forEach { FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it) }) }
                    }
                    OutlinedButton(onClick = { calendar = true }) { Text("Expense date: $date") }
                    OutlinedTextField(value = note, onValueChange = { note = it.take(2000) }, label = { Text("Note (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    if (attempted && !valid) Text("Check the name, item and amount before saving.", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
    if (calendar) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; calendar = false }) { Text("Choose date") } }, dismissButton = { TextButton(onClick = { calendar = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}

private fun validAmount(amount: Long?, existing: Entry?) = amount != null && amount > 0 && amount >= (existing?.paid ?: 0)

@Composable internal fun EntryDetails(e: Entry, money: (Long) -> String, busy: Boolean, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onPay: (Long) -> Unit) {
    var payment by rememberSaveable(e.id) { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(e.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${e.person} · ${e.category} · ${e.date}", style = MaterialTheme.typography.bodyMedium)
            Text(if (e.remaining == 0L) "All settled" else if (e.receivable) "${e.person} owes you" else "You owe ${e.person}")
            Text(money(e.remaining), style = MaterialTheme.typography.displaySmall)
            Text("Original: ${money(e.cents)}  ·  Repaid: ${money(e.paid)}", style = MaterialTheme.typography.bodyMedium)
            if (e.note.isNotBlank()) Text(e.note)
            if (e.remaining > 0) {
                HorizontalDivider()
                OutlinedTextField(value = payment, onValueChange = { payment = it.take(16); error = false }, label = { Text("Record a repayment") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error, supportingText = { if (error) Text("Enter an amount up to ${money(e.remaining)}") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { val cents = parseMoney(payment); if (cents != null && cents <= e.remaining) onPay(cents) else error = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Record repayment") }
                OutlinedButton(onClick = { onPay(e.remaining) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Mark fully settled") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = onEdit, enabled = !busy) { Text("Edit expense") }; TextButton(onClick = { delete = true }, enabled = !busy) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete this expense?") }, text = { Text("This removes the expense and its repayment total from your ledger.") }, confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
}
