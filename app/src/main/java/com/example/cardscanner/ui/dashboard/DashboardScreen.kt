package com.example.cardscanner.ui.dashboard

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cardscanner.R
import com.example.cardscanner.data.FleetRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Fleet dashboard (spec §33-34): status/fuel/provider/department breakdowns
 * and expiring-soon counters. Chart bars are simple weighted rows — no chart
 * library, keeps the app dependency-light and offline.
 */
@Composable
fun DashboardRoute(fleetRepository: FleetRepository) {
    val statusCounts by fleetRepository.countByStatus().collectAsStateWithLifecycle(initialValue = emptyList())
    val fuelCounts by fleetRepository.countByFuelType().collectAsStateWithLifecycle(initialValue = emptyList())
    val providerCounts by fleetRepository.countByProvider().collectAsStateWithLifecycle(initialValue = emptyList())
    val departmentCounts by fleetRepository.countByDepartment().collectAsStateWithLifecycle(initialValue = emptyList())
    val expiring by androidx.compose.runtime.produceState(FleetRepository.ExpiringSoon(0, 0, 0)) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fleetRepository.expiringSoonCounts()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            ExpiringCard(expiring)
        }
        item { SectionTitle(stringResource(R.string.dashboard_by_status)) }
        items(statusCounts.size) { i -> CountBar(statusCounts[i].status, statusCounts[i].n, maxOf(statusCounts.maxOf { it.n }, 1)) }
        item { SectionTitle(stringResource(R.string.dashboard_by_fuel)) }
        items(fuelCounts.size) { i -> CountBar(fuelCounts[i].label, fuelCounts[i].n, maxOf(fuelCounts.maxOf { it.n }, 1)) }
        item { SectionTitle(stringResource(R.string.dashboard_by_provider)) }
        items(providerCounts.size) { i -> CountBar(providerCounts[i].label, providerCounts[i].n, maxOf(providerCounts.maxOf { it.n }, 1)) }
        item { SectionTitle(stringResource(R.string.dashboard_by_department)) }
        items(departmentCounts.size) { i -> CountBar(departmentCounts[i].label, departmentCounts[i].n, maxOf(departmentCounts.maxOf { it.n }, 1)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ExpiringCard(data: FleetRepository.ExpiringSoon) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.dashboard_expiring_title),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ExpiringCell("30", data.next30)
                ExpiringCell("60", data.next60)
                ExpiringCell("90", data.next90)
            }
        }
    }
}

@Composable
private fun ExpiringCell(window: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.dashboard_expiring_days, window), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CountBar(label: String, count: Int, max: Int) {
    val fraction = (count.toFloat() / max).coerceIn(0.05f, 1f)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
    }
}
