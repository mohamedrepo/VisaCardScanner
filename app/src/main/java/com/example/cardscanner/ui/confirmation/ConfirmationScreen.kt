package com.example.cardscanner.ui.confirmation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cardscanner.R
import com.example.cardscanner.ui.AppViewModel

/**
 * Confirmation screen: shows the SAFE fields, offers Confirm & Save / Edit.
 * There is deliberately no input field anywhere on this screen that could
 * accept a complete PAN.
 */
@Composable
fun ConfirmationRoute(
    viewModel: AppViewModel,
    onSaved: () -> Unit,
) {
    val scanner = viewModel.scanner
    val confirmation = viewModel.confirmation
    val result = scanner.pendingResult

    LaunchedEffect(result) {
        if (result != null) confirmation.beginEdit()
    }

    val saved = confirmation.saved
    LaunchedEffect(saved) {
        if (saved != null) onSaved()
    }

    var isEditing by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (result == null || result.safeData == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.error_card_not_detected))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { scanner.beginScan() }) {
                    Text(stringResource(R.string.rescan))
                }
            }
            return@Surface
        }

        val safe = result.safeData

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.card_detected_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    FieldRow(stringResource(R.string.field_brand), "VISA")
                    Spacer(Modifier.height(10.dp))
                    FieldRow(stringResource(R.string.field_card), safe.maskedPan)
                    Spacer(Modifier.height(10.dp))

                    val expiryText: String = confirmation.editable.expiryText.ifBlank {
                        result.expiryMonth?.let { m ->
                            result.expiryYear?.let { y -> "%02d/%04d".format(m, y) }
                        } ?: "—"
                    }
                    FieldRow(stringResource(R.string.field_expiry), expiryText)
                    Spacer(Modifier.height(10.dp))
                    FieldRow(
                        label = stringResource(R.string.field_cardholder),
                        value = confirmation.editable.cardholderName
                            .ifBlank { result.cardholderName ?: "—" },
                    )
                }
            }

            if (result.cardholderLowConfidence) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.verify_cardholder_name),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (result.expiryLowConfidence) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.verify_expiration_date),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(24.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = confirmation.editable.cardholderName,
                    onValueChange = confirmation::onCardholderChanged,
                    label = { Text(stringResource(R.string.cardholder_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmation.editable.expiryText,
                    onValueChange = confirmation::onExpiryChanged,
                    label = { Text(stringResource(R.string.expiration_date)) },
                    isError = !confirmation.isExpiryValid(),
                    supportingText = {
                        if (!confirmation.isExpiryValid()) {
                            Text(stringResource(R.string.invalid_expiry_format))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmation.editable.notes,
                    onValueChange = confirmation::onNotesChanged,
                    label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = { confirmation.confirmAndSave() },
                enabled = confirmation.isExpiryValid(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.confirm_and_save))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { isEditing = !isEditing },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isEditing) stringResource(R.string.save) else stringResource(R.string.edit))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { scanner.beginScan() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.rescan))
            }
        }

        if (confirmation.duplicateDetected) {
            AlertDialog(
                onDismissRequest = { confirmation.dismissDuplicate() },
                title = { Text(stringResource(R.string.duplicate_title)) },
                text = { Text(stringResource(R.string.duplicate_message)) },
                confirmButton = {
                    TextButton(onClick = { confirmation.confirmAndSave(acceptDuplicate = true) }) {
                        Text(stringResource(R.string.save_another_record))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmation.dismissDuplicate() }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        val saveError = confirmation.saveError
        if (saveError != null) {
            AlertDialog(
                onDismissRequest = { confirmation.consumeSaveError() },
                text = { Text(stringResource(saveErrorRes(saveError))) },
                confirmButton = {
                    TextButton(onClick = { confirmation.consumeSaveError() }) {
                        Text(stringResource(R.string.ok))
                    }
                },
            )
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(96.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun saveErrorRes(key: String): Int = when (key) {
    "error_invalid_expiry" -> R.string.error_invalid_expiry
    "error_duplicate_record" -> R.string.error_duplicate_record
    else -> R.string.error_generic
}
