package com.sharad.blesetup

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.*

class ScanDevice(context: Context, addDevice: (BLEInfo)-> Unit) {
    val scope = CoroutineScope(Dispatchers.Main)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

    var scanning = false
    val SCAN_PERIOD: Long = 5000

    var scanJob: Job? = null
    val leDeviceListAdapter = mutableStateListOf<BluetoothDevice>()

    companion object {
        private const val TAG = "ScanDevice"
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (leDeviceListAdapter.none { it.address == result.device.address }) {
                addDevice(BLEInfo(
                    result.device,
                    result.rssi
                ))
                Log.d(TAG, "Device found: ${result.device.name ?: "Unnamed"} / ${result.device.address}")
            } else {
                Log.d(TAG, "Duplicate device: ${result.device.address} (ignored)")
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            for (result in results) {
                if (leDeviceListAdapter.none { it.address == result.device.address }) {
                    addDevice(BLEInfo(
                        result.device,
                        result.rssi
                    ))
                    Log.d(TAG, "Batch device found: ${result.device.name ?: "Unnamed"} / ${result.device.address}")
                } else {
                    Log.d(TAG, "Duplicate in batch: ${result.device.address} (ignored)")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan failed with errorCode: $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (!scanning) {
            scanning = true
            leDeviceListAdapter.clear()
            bluetoothLeScanner.startScan(scanCallback)
            Log.d(TAG, "BLE scan started.")
            scanJob = scope.launch {
                delay(SCAN_PERIOD)
                scanning = false
                bluetoothLeScanner.stopScan(scanCallback)
                Log.d(TAG, "BLE scan stopped after $SCAN_PERIOD ms.")
            }
        } else {
            scanJob?.cancel()
            scanning = false
            bluetoothLeScanner.stopScan(scanCallback)
            Log.d(TAG, "BLE scan stopped by user/request.")
        }
        Log.d(TAG, "Scanning state changed: $scanning")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        scanJob?.cancel()
        bluetoothLeScanner.stopScan(scanCallback)
        scanning = false
        bluetoothLeScanner.stopScan(scanCallback)
        Log.d(TAG, "BLE scan stopped via stopScan().")
        Log.d(TAG, "Scanning state changed: $scanning")
    }


}

data class BLEInfo(
    val device:BluetoothDevice,
    val rssi:Int
)