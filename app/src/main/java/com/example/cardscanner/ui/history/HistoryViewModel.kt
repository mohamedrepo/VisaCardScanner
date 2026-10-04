package com.example.cardscanner.ui.history

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardscanner.data.CardStatus
import com.example.cardscanner.data.FleetCard
import com.example.cardscanner.data.FleetRepository
import com.example.cardscanner.export.EncryptedExport
import com.example.cardscanner.export.ExcelExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fleet history ViewModel: search + filters + export (encrypted by default).
 */
class HistoryViewModel(
    private val repository: FleetRepository,
    private val appContext: Context,
) : ViewModel() {

    var searchQuery by mutableStateOf("")
        private set

    var filterStatus by mutableStateOf("")
        private set
    var filterProvider by mutableStateOf("")
        private set
    var filterFuelType by mutableStateOf("")
        private set
    var filterDepartment by mutableStateOf("")
        private set

    private val allRecords: StateFlow<List<FleetCard>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var filteredRecords by mutableStateOf<List<FleetCard>>(emptyList())
        private set

    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    sealed interface ExportState {
        data object Idle : ExportState

        /** Security-scanned workbook (or encrypted container) held purely in RAM. */
        data class Ready(
            val bytes: ByteArray,
            val fileName: String,
            val encrypted: Boolean,
        ) : ExportState
        data object Success : ExportState
        data class Failure(val messageKey: String) : ExportState
    }

    init {
        viewModelScope.launch {
            allRecords.collect { list -> filteredRecords = applyFilters(list) }
        }
    }

    fun onSearchChanged(query: String) {
        searchQuery = query
        filteredRecords = applyFilters(allRecords.value)
    }

    fun onFilterChanged(status: String? = null, provider: String? = null, fuelType: String? = null, department: String? = null) {
        status?.let { filterStatus = it }
        provider?.let { filterProvider = it }
        fuelType?.let { filterFuelType = it }
        department?.let { filterDepartment = it }
        filteredRecords = applyFilters(allRecords.value)
    }

    private fun applyFilters(list: List<FleetCard>): List<FleetCard> {
        val q = searchQuery.trim()
        var result = if (q.isEmpty()) list else list.filter { card ->
            card.last4.contains(q) ||
                card.bin6.contains(q) ||
                card.cardholderName?.contains(q, ignoreCase = true) == true ||
                card.vehicleNumber?.contains(q, ignoreCase = true) == true ||
                card.plateNumber?.contains(q, ignoreCase = true) == true ||
                card.driverName?.contains(q, ignoreCase = true) == true ||
                card.employeeId?.contains(q, ignoreCase = true) == true ||
                card.department?.contains(q, ignoreCase = true) == true ||
                card.provider?.contains(q, ignoreCase = true) == true ||
                card.fuelType?.contains(q, ignoreCase = true) == true ||
                card.status.contains(q, ignoreCase = true)
        }
        if (filterStatus.isNotEmpty()) result = result.filter { it.status == filterStatus }
        if (filterProvider.isNotEmpty()) result = result.filter { it.provider == filterProvider }
        if (filterFuelType.isNotEmpty()) result = result.filter { it.fuelType == filterFuelType }
        if (filterDepartment.isNotEmpty()) result = result.filter { it.department == filterDepartment }
        return result
    }

    suspend fun delete(card: FleetCard) {
        repository.delete(card)
    }

    /**
     * Encrypted export (default, spec §18 Mode A): build workbook, security
     * scan, then wrap in an AES-GCM container keyed by the export passphrase.
     */
    fun prepareEncryptedExport(passphrase: CharArray) {
        prepareExportInternal(passphrase)
    }

    /** Plain export (Mode B) — UI must show the warning and audit before calling. */
    fun preparePlainExport() {
        prepareExportInternal(null)
    }

    private fun prepareExportInternal(passphrase: CharArray?) {
        val current = allRecords.value
        if (current.isEmpty()) {
            exportState = ExportState.Failure("export_no_records")
            return
        }
        viewModelScope.launch {
            exportState = withContext(Dispatchers.Default) {
                val exporter = ExcelExporter(appContext)
                when (val result = exporter.buildWorkbook(current)) {
                    is ExcelExporter.Result.Rejected -> ExportState.Failure("export_security_rejected")
                    is ExcelExporter.Result.Success -> {
                        val bytes = if (passphrase != null && passphrase.isNotEmpty()) {
                            EncryptedExport.encrypt(result.bytes, passphrase)
                        } else {
                            result.bytes
                        }
                        ExportState.Ready(
                            bytes = bytes,
                            fileName = fleetFileName(encrypted = passphrase != null && passphrase.isNotEmpty()),
                            encrypted = passphrase != null && passphrase.isNotEmpty(),
                        )
                    }
                }
            }
            passphrase?.fill('0')
        }
    }

    private fun fleetFileName(encrypted: Boolean): String {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
        val base = "Fleet_Fuel_Cards_${java.time.LocalDateTime.now().format(fmt)}"
        return if (encrypted) "$base.xlsxenc" else "$base.xlsx"
    }

    /** Streams the validated bytes to the SAF target chosen by the user. */
    fun writeReadyExport(target: android.net.Uri) {
        val ready = exportState as? ExportState.Ready ?: return
        viewModelScope.launch {
            exportState = withContext(Dispatchers.IO) {
                try {
                    ExcelExporter(appContext).writeToTarget(target, ready.bytes)
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
}
