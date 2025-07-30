package com.sharad.blesetup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.BOND_BONDING
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.UUID

@androidx.annotation.RequiresPermission(allOf =[android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_SCAN])

@Composable
fun GattAndroid(){

    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var connectingMac by remember { mutableStateOf<String?>(null) }
    var connectedMac by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf(emptyList<BLEInfo>()) }
    var connectionState by remember { mutableIntStateOf(0) }
    var blueToothGatt: BluetoothGatt? by remember { mutableStateOf(null) }
    var currentDevice:BluetoothDevice? by remember { mutableStateOf(null) }
    var services by remember { mutableStateOf(blueToothGatt?.services) }
    var data: Int? by remember { mutableStateOf(null) }

    val bleClass= ScanDevice(context){
        Log.d("Device Results:", "Mac: ${it.device.address}, Name: ${it.device.name}")
        if (!devices.any { device -> device.device.address == it.device.address }){ devices = devices + it }
    }

    Box(modifier = Modifier.fillMaxSize()){
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(Modifier.padding(20.dp)) {
                Button(
                    onClick = {
                        devices = emptyList()
                        bleClass.startScan()
                    }
                ) {
                    Text("Search BLE Devices")
                }
                Text(connectionState.toString())
            }
            Spacer(Modifier.height(16.dp))
            if (devices.isEmpty()) {
                Text(
                    "No devices found. Tap 'Search' to scan.",
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                Text("Tap device to connect:", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(devices) { device ->
                        var bondState by remember { mutableStateOf(device.device.bondState) }
                        LaunchedEffect(device) {
                            while (true) {
                                bondState = device.device.bondState
                                delay(1000)
                            }
                        }
                        val isConnecting by remember { derivedStateOf { BOND_BONDING == device.device.bondState } }
                        val isConnected by remember { derivedStateOf { bondState == BOND_BONDED } }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    bleClass.stopScan()
                                    Log.d("GattConnectionStatus", blueToothGatt.toString())
                                    device.device.connectGatt(context, false, object :
                                        BluetoothGattCallback() {
                                        override fun onConnectionStateChange(
                                            gatt: BluetoothGatt,
                                            status: Int,
                                            newState: Int,
                                        ) {
                                            super.onConnectionStateChange(
                                                gatt,
                                                status,
                                                newState
                                            )
                                            Log.d(
                                                "Device Results:",
                                                "Connection Status: $newState , Status: $status"
                                            )
                                            Log.d("GattConnectionStatus:", gatt.toString())
                                            blueToothGatt = gatt
                                            blueToothGatt?.discoverServices()
                                            currentDevice = device.device
                                            connectionState = newState

                                        }

                                        override fun onServicesDiscovered(
                                            gatt: BluetoothGatt,
                                            status: Int
                                        ) {
                                            super.onServicesDiscovered(gatt, status)
                                            services = gatt.services
                                            Log.d("Device Results:", "Services Discovered")

                                        }

                                        override fun onCharacteristicRead(
                                            gatt: BluetoothGatt,
                                            characteristic: BluetoothGattCharacteristic,
                                            value: ByteArray,
                                            status: Int
                                        ) {
                                            super.onCharacteristicRead(
                                                gatt,
                                                characteristic,
                                                value,
                                                status
                                            )

                                            Log.d("Device Results:", "Characteristic Read")
                                            Log.d("Device Results: Value", value.toString())
                                            data = parseHeartRate(value)
                                        }

                                        override fun onCharacteristicChanged(
                                            gatt: BluetoothGatt,
                                            characteristic: BluetoothGattCharacteristic,
                                            value: ByteArray
                                        ) {
                                            super.onCharacteristicChanged(
                                                gatt,
                                                characteristic,
                                                value
                                            )
                                            data = parseHeartRate(value)
                                        }
                                    }, BluetoothDevice.TRANSPORT_LE)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = device.device.name ?: "(Unnamed)",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "MAC: ${device.device.address}, RSSI: ${device.rssi}, Bond State: ${device.device.bondState}, Type: ${device.device.type}, Class: ${device.device.bluetoothClass}" +
                                            ", UUID: ${device.device.uuids?.joinToString(", ") { it.toString() }}"
                                    )
                                }
                                if (isConnected) {

                                    Icon(Icons.Outlined.Check, contentDescription = "Connected")
                                } else if (isConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Status: $connectionState", style = MaterialTheme.typography.labelLarge)
        }
        if (connectionState==2){
            Log.d("GattConnectionStatus:", blueToothGatt.toString())
            ConnectedDevice(blueToothGatt,currentDevice!!, services, data)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@SuppressLint("MissingPermission")
@Composable
fun ConnectedDevice(
    bluetoothGatt: BluetoothGatt?,
    device: BluetoothDevice,
    services: List<BluetoothGattService>?,
    data1: Int?
){
    Column(modifier= Modifier
        .fillMaxSize()
        .background(Color.Black)
        .verticalScroll(rememberScrollState())
        .padding(vertical = 30.dp, horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Text(text = device.name ?: "(Unnamed)", style = MaterialTheme.typography.bodyLarge)
                Text("MAC: ${device.address}, Bond State: ${device.bondState}, Type: ${device.type}, Class: ${device.bluetoothClass}" +
                        ", UUID: ${device.uuids?.joinToString(", ") { it.toString() }}")
                Text(text = "Services", style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(16.dp))
        if (services!= null) {
            services.forEach { service ->

                val showCharacteristics = remember { mutableStateOf(false) }
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Service: ${getServiceName(service.uuid.toString())}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            Icons.Outlined.ArrowDropDown,
                            null,
                            Modifier.clickable {
                                showCharacteristics.value = !showCharacteristics.value
                            }
                        )
                    }
                    if (showCharacteristics.value){
                        val characteristics = service.characteristics
                        Column(modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)) {
                            characteristics.forEach { characteristic ->
                                LaunchedEffect(Unit){
                                    bluetoothGatt?.setCharacteristicNotification(
                                        characteristic,
                                        true
                                    )
                                    val cccdUuid =
                                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                                    val descriptor = characteristic.getDescriptor(cccdUuid)
                                    if (descriptor != null) {
                                        // No need to set descriptor.value (it's ignored)
                                        bluetoothGatt?.writeDescriptor(
                                            descriptor,
                                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                        )
                                    } else {
                                        Log.e(
                                            "GattSetup",
                                            "CCCD descriptor not found for characteristic: ${characteristic.uuid}"
                                        )
                                    }
                                }

                                val shoeData = remember { mutableStateOf(false) }
                                Row(horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()){
                                    Text(
                                        text = "Characteristic: ${
                                            characteristic.uuid.toString().substring(4, 8)
                                        }",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Icon(
                                        Icons.Outlined.ArrowDropDown,
                                        null,
                                        Modifier.clickable {
                                            shoeData.value = !shoeData.value
                                        }
                                    )
                                }
                                if (shoeData.value){
                                    Text("Data: $data1")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseHeartRate(value: ByteArray): Int? {
    val TAG = "CharacteristicData"
    if (value.isEmpty()) return null

    val flag = value[0].toInt()
    val isUint16 = (flag and 0x01) != 0

    val heartRate = if (isUint16) {
        // UINT16: bytes 1 and 2, little endian
        if (value.size >= 3) {
            val result = ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
            Log.d(TAG, "Heart rate format UINT16: $result")
            result
        } else {
            Log.w(TAG, "Value too short for UINT16")
            null
        }
    } else {
        // UINT8: byte 1
        if (value.size >= 2) {
            val result = value[1].toInt() and 0xFF
            Log.d(TAG, "Heart rate format UINT8: $result")
            result
        } else {
            Log.w(TAG, "Value too short for UINT8")
            null
        }
    }
    return heartRate
}
