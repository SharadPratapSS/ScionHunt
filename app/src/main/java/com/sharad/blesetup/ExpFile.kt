package com.sharad.blesetup

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yucheng.ycbtsdk.Constants
import com.yucheng.ycbtsdk.YCBTClient
import com.yucheng.ycbtsdk.bean.ScanDeviceBean
import com.yucheng.ycbtsdk.response.BleConnectResponse
import com.yucheng.ycbtsdk.response.BleScanResponse

@Composable
fun BleDeviceScanner() {
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    val devices = remember { mutableStateListOf<ScanDeviceBean>() }
    var connectingMac by remember { mutableStateOf<String?>(null) }
    var connectedMac by remember { mutableStateOf<String?>(null) }
    var connectionState by remember { mutableStateOf("Disconnected") }

    fun startScan() {
        devices.clear()
        scanning = true
        YCBTClient.startScanBle(object : BleScanResponse {
            override fun onScanResponse(i: Int, scanDeviceBean: ScanDeviceBean?) {
                scanDeviceBean?.let { device ->
                    if (devices.none { it.deviceMac == device.deviceMac }) {
                        devices.add(device)
                    }
                }
            }
        }, 6)
    }

    fun connectToDevice(mac: String) {
        connectingMac = mac
        YCBTClient.connectBle(mac, object : BleConnectResponse {
            override fun onConnectResponse(code: Int) {
                when (code) {
                    Constants.BLEState.ReadWriteOK -> {
                        connectedMac = mac
                        connectionState = "Connected"
                    }
                    Constants.BLEState.Disconnect -> {
                        if (connectedMac == mac) {
                            connectedMac = null
                            connectionState = "Disconnected"
                        }
                    }
                    Constants.BLEState.Connecting -> {
                        connectionState = "Connecting"
                    }
                    Constants.BLEState.TimeOut -> {
                        connectionState = "Timeout"
                    }
                    else -> {
                        connectionState = "Unknown"
                    }
                }
            }
        })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            enabled = !scanning,
            onClick = { startScan() }
        ) {
            Text("Search BLE Devices")
        }
        Spacer(Modifier.height(16.dp))
        if (devices.isEmpty()) {
            Text("No devices found. Tap 'Search' to scan.", color = MaterialTheme.colorScheme.onBackground)
        } else {
            Text("Tap device to connect:", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(devices) { device ->
                    val isConnecting = connectingMac == device.deviceMac
                    val isConnected = connectedMac == device.deviceMac
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (!isConnected) connectToDevice(device.deviceMac)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(text = device.deviceName ?: "(Unnamed)", style = MaterialTheme.typography.bodyLarge)
                                Text("MAC: ${device.deviceMac}, RSSI: ${device.deviceRssi}")
                            }
                            if (isConnected) {
                                Text("Connected", color = MaterialTheme.colorScheme.primary)
                            } else if (isConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Status: $connectionState", style = MaterialTheme.typography.labelLarge)
    }
}
