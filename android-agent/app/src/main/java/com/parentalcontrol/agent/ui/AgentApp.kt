@file:OptIn(ExperimentalMaterial3Api::class)
package com.parentalcontrol.agent.ui

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parentalcontrol.agent.util.PermissionStateChecker
import com.parentalcontrol.agent.viewmodel.AgentUiState
import com.parentalcontrol.agent.viewmodel.AgentViewModel

@Composable
fun AgentApp(
    viewModel: AgentViewModel,
    permissionStateChecker: PermissionStateChecker,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.startProtection()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Parental Control Agent") })
        },
    ) { padding ->
        if (!uiState.local.paired) {
            OnboardingScreen(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                onPair = viewModel::pairDevice,
                onOpenUsage = { context.startActivity(permissionStateChecker.usageAccessIntent()) },
                onOpenOverlay = { context.startActivity(permissionStateChecker.overlayIntent()) },
                onOpenAccessibility = { context.startActivity(permissionStateChecker.accessibilityIntent()) },
                onOpenBattery = { context.startActivity(permissionStateChecker.batteryOptimizationIntent()) },
                onRequestVpn = {
                    val intent = VpnService.prepare(context)
                    if (intent == null) {
                        viewModel.startProtection()
                    } else {
                        vpnLauncher.launch(intent)
                    }
                },
            )
        } else {
            DashboardScreen(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                permissionStateChecker = permissionStateChecker,
                onRefresh = viewModel::refreshNow,
                onStartProtection = {
                    val intent = VpnService.prepare(context)
                    if (intent == null) {
                        viewModel.startProtection()
                    } else {
                        vpnLauncher.launch(intent)
                    }
                },
                onOpenUsage = { context.startActivity(permissionStateChecker.usageAccessIntent()) },
                onOpenOverlay = { context.startActivity(permissionStateChecker.overlayIntent()) },
                onOpenAccessibility = { context.startActivity(permissionStateChecker.accessibilityIntent()) },
                onOpenBattery = { context.startActivity(permissionStateChecker.batteryOptimizationIntent()) },
            )
        }
    }
}

@Composable
private fun OnboardingScreen(
    modifier: Modifier,
    uiState: AgentUiState,
    onPair: (String, String, String, String, String) -> Unit,
    onOpenUsage: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
    onRequestVpn: () -> Unit,
) {
    var deviceName by remember { mutableStateOf("Child Device") }
    var qrPayload by remember { mutableStateOf("") }
    var tokenId by remember { mutableStateOf("") }
    var pairingSecret by remember { mutableStateOf("") }
    var backupCode by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ربط الجهاز", style = MaterialTheme.typography.headlineSmall)
                    Text("الصق JSON القادم من QR أو أدخل tokenId + pairing secret / backup code يدويًا.")
                    OutlinedTextField(value = deviceName, onValueChange = { deviceName = it }, label = { Text("اسم الجهاز") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = qrPayload, onValueChange = { qrPayload = it }, label = { Text("QR JSON") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tokenId, onValueChange = { tokenId = it }, label = { Text("tokenId") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pairingSecret, onValueChange = { pairingSecret = it }, label = { Text("pairing secret") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = backupCode, onValueChange = { backupCode = it }, label = { Text("backup code") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onPair(deviceName, qrPayload, tokenId, pairingSecret, backupCode) }, enabled = !uiState.loading) {
                        Text(if (uiState.loading) "جارٍ الربط..." else "ربط الجهاز")
                    }
                }
            }
        }

        item {
            PermissionChecklist(
                onOpenUsage = onOpenUsage,
                onOpenOverlay = onOpenOverlay,
                onOpenAccessibility = onOpenAccessibility,
                onOpenBattery = onOpenBattery,
                onRequestVpn = onRequestVpn,
            )
        }

        if (uiState.message != null) {
            item {
                Text(uiState.message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    modifier: Modifier,
    uiState: AgentUiState,
    permissionStateChecker: PermissionStateChecker,
    onRefresh: () -> Unit,
    onStartProtection: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiState.local.deviceId ?: "Unknown device", fontWeight = FontWeight.Bold)
                    Text("Status: ${uiState.local.locked} | Lock reason: ${uiState.local.lockReason ?: "none"}")
                    Text("Estimated usage: ${uiState.local.latestUsageBytes} bytes via ${uiState.local.latestUsageSource}")
                    Text("Policy v${uiState.local.policy.policyVersion} | limit ${uiState.local.policy.dailyLimitBytes} bytes")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onStartProtection) { Text("Start protection") }
                        Button(onClick = onRefresh) { Text("Sync now") }
                    }
                }
            }
        }

        item {
            PermissionChecklist(
                onOpenUsage = onOpenUsage,
                onOpenOverlay = onOpenOverlay,
                onOpenAccessibility = onOpenAccessibility,
                onOpenBattery = onOpenBattery,
                onRequestVpn = onStartProtection,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الحالة الحالية", style = MaterialTheme.typography.titleMedium)
                    Text("VPN: ${permissionStateChecker.hasVpnPermission()}")
                    Text("Overlay: ${permissionStateChecker.hasOverlayPermission()}")
                    Text("Usage access: ${permissionStateChecker.hasUsageStatsPermission()}")
                    Text("Accessibility: ${permissionStateChecker.isAccessibilityEnabled()}")
                }
            }
        }

        item {
            Text("Estimated usage history", style = MaterialTheme.typography.titleMedium)
        }

        items(uiState.recentUsage) { usage ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(usage.dayKey, fontWeight = FontWeight.Bold)
                    Text("${usage.estimatedBytes} bytes | ${usage.source}")
                    Text("locks ${usage.lockedCount}")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Latest events", style = MaterialTheme.typography.titleMedium)
        }

        items(uiState.recentEvents) { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(event.type, fontWeight = FontWeight.Bold)
                    Text(event.severity)
                    Text(event.payload)
                }
            }
        }
    }
}

@Composable
private fun PermissionChecklist(
    onOpenUsage: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
    onRequestVpn: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Required permissions", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onRequestVpn, modifier = Modifier.fillMaxWidth()) { Text("Grant VPN") }
            Button(onClick = onOpenUsage, modifier = Modifier.fillMaxWidth()) { Text("Grant Usage Access") }
            Button(onClick = onOpenOverlay, modifier = Modifier.fillMaxWidth()) { Text("Grant Overlay") }
            Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Open Accessibility Settings") }
            Button(onClick = onOpenBattery, modifier = Modifier.fillMaxWidth()) { Text("Ignore Battery Optimization") }
        }
    }
}
