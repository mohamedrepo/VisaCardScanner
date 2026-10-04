package com.example.cardscanner.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cardscanner.R
import com.example.cardscanner.data.CardRecord
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Scan history + Excel export. Search covers last-4, cardholder name,
 * expiration year and brand. A complete PAN does not exist anywhere in the
 * record model, so it cannot appear here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records = viewModel.filteredRecords

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        if (uri != null) {
            viewModel.writeReadyExport(uri)
        } else {
            viewModel.cancelExport()
        }
    }

    LaunchedEffect(viewModel.exportState) {
        val state = viewModel.exportState
        if (state is HistoryViewModel.ExportState.Ready) {
            exportLauncher.launch(state.fileName)
        }
    }

    var recordToDelete by remember { mutableStateOf<CardRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.a11y_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.prepareExport() },
                        enabled = records.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = stringResource(R.string.export_to_excel),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = viewModel::onSearchChanged,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchChanged("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.records_count, records.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_records))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(records, key = { it.id }) { record ->
                        RecordCard(
                            record = record,
                            onDelete = { recordToDelete = record },
                        )
                    }
                }
            }
        }
    }

    if (recordToDelete != null) {
        val record = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(stringResource(R.string.delete_record)) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { viewModel.delete(record) }
                    recordToDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    when (val state = viewModel.exportState) {
        is HistoryViewModel.ExportState.Failure -> {
            AlertDialog(
                onDismissRequest = { viewModel.cancelExport() },
                text = { Text(stringResource(exportMessageRes(state.messageKey))) },
                confirmButton = {
                    TextButton(onClick = { viewModel.cancelExport() }) {
                        Text(stringResource(R.string.ok))
                    }
                },
            )
        }
        is HistoryViewModel.ExportState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.cancelExport() },
                text = {
                    Column {
                        Text(stringResource(R.string.export_success))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.export_scan_again),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.cancelExport() }) {
                        Text(stringResource(R.string.ok))
                    }
                },
            )
        }
        else -> Unit
    }
}

@Composable
private fun RecordCard(record: CardRecord, onDelete: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
    val scannedText = record.scannedAt.atZone(ZoneId.systemDefault()).format(formatter)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = record.brand,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = record.maskedPan,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = buildString {
                        record.expiryMonth?.let { m ->
                            record.expiryYear?.let { y ->
                                append("%02d/%04d".format(m, y))
                            }
                        } ?: append("—")
                        record.cardholderName?.let { name ->
                            append("  ·  ")
                            append(name)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = scannedText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete_record),
                )
            }
        }
    }
}

private fun exportMessageRes(key: String): Int = when (key) {
    "export_failed" -> R.string.export_failed
    "export_security_rejected" -> R.string.export_security_rejected
    "export_no_records" -> R.string.export_no_records
    else -> R.string.error_generic
}
