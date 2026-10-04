package com.example.cardscanner.ui.history

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardscanner.data.CardRecord
import com.example.cardscanner.data.CardRepository
import com.example.cardscanner.export.ExcelExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(
    private val repository: CardRepository,
    private val appContext: Context,
) : ViewModel() {

    var searchQuery by mutableStateOf("")
        private set

    private val allRecords: StateFlow<List<CardRecord>> =
        repository.search("").stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var filteredRecords by mutableStateOf<List<CardRecord>>(emptyList())
        private set

    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    var exportMessage by mutableStateOf<String?>(null)
        private set

    sealed interface ExportState {
        data object Idle : ExportState

        /** Security-scanned workbook held purely in RAM, awaiting a SAF target. */
        data class Ready(val bytes: ByteArray, val fileName: String) : ExportState
        data object Success : ExportState
        data class Failure(val messageKey: String) : ExportState
    }

    init {
        viewModelScope.launch {
            allRecords.collect { list -> filteredRecords = applySearch(list) }
        }
    }

    fun onSearchChanged(query: String) {
        searchQuery = query
        filteredRecords = applySearch(allRecords.value)
    }

    private fun applySearch(list: List<CardRecord>): List<CardRecord> {
        val q = searchQuery.trim()
        if (q.isEmpty()) return list
        return list.filter { record ->
            record.last4.contains(q) ||
                record.cardholderName?.contains(q, ignoreCase = true) == true ||
                record.expiryYear?.toString() == q ||
                record.brand.contains(q, ignoreCase = true)
        }
    }

    suspend fun delete(record: CardRecord) = repository.delete(record)

    /**
     * Builds the workbook in memory and runs the security scan. Bytes stay in
     * RAM; they are written only after the user picks a SAF target.
     */
    fun prepareExport() {
        val current = allRecords.value
        if (current.isEmpty()) {
            exportState = ExportState.Failure("export_no_records")
            return
        }
        viewModelScope.launch {
            exportState = withContext(Dispatchers.Default) {
                val exporter = ExcelExporter(appContext)
                when (val result = exporter.buildWorkbook(current)) {
                    is ExcelExporter.Result.Success -> ExportState.Ready(
                        bytes = result.bytes,
                        fileName = ExcelExporter.defaultFileName(),
                    )
                    is ExcelExporter.Result.Rejected -> ExportState.Failure("export_security_rejected")
                }
            }
        }
    }

    /** Streams the validated bytes to the SAF target chosen by the user. */
    fun writeReadyExport(target: android.net.Uri) {
        val ready = exportState as? ExportState.Ready ?: return
        viewModelScope.launch {
            exportState = withContext(Dispatchers.IO) {
                try {
                    val exporter = ExcelExporter(appContext)
                    exporter.writeToTarget(target, ready.bytes)
                    ExportState.Success
                } catch (t: Throwable) {
                    ExportState.Failure("export_failed")
                }
            }
        }
    }

    fun cancelExport() {
        exportState = ExportState.Idle
    }

    fun consumeExportMessage() {
        exportMessage = null
    }

    fun currentRecords(): List<CardRecord> = allRecords.value
}
