package com.sharad.blesetup

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid


object BLEObserver {
    private lateinit var bluetoothManager: BluetoothManager
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private val bluetoothLeScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private val TAG= "BLEObserver"

    private var bluetoothGatt: BluetoothGatt?= null

    private val _connected= MutableStateFlow(false)
    val connected= _connected.asStateFlow()

    private val _isConnecting= MutableStateFlow(false)
    val isConnecting= _isConnecting.asStateFlow()

    private val _connectedDevice= MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice= _connectedDevice.asStateFlow()

    private val _devices= MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices= _devices.asStateFlow()

    private val _scanStatus= MutableStateFlow(false)
    val scanStatus= _scanStatus.asStateFlow()

    private val _services= MutableStateFlow<List<BluetoothGattService>>(emptyList())
    val services= _services.asStateFlow()


    private var scanJob: Job?= null
    private val scope= CoroutineScope(Dispatchers.Main)


    val bluetoothGattCallBack= object : BluetoothGattCallback(){

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState== BluetoothGatt.STATE_CONNECTING){
                _isConnecting.value= true
            }else if (newState== BluetoothGatt.STATE_CONNECTED){
                _isConnecting.value= false
                _connected.value= true
                _connectedDevice.value= gatt?.device
                bluetoothGatt=gatt
                bluetoothGatt?.let {
                    it.discoverServices()
                }
            }
            else if (newState== BluetoothGatt.STATE_DISCONNECTED){
                _isConnecting.value= false
                _connected.value= false
                bluetoothGatt=null
            }

        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            gatt?.let {
                _services.value= it.services
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
        }
    }

    fun init(context: Context) {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(addDevice:(BluetoothDevice)->Unit, scanStatus:(Boolean)->Unit){
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let {
                    addDevice(result.device)
                    Log.d(TAG, "Device : ${result.device}")
                }
            }

            override fun onBatchScanResults(results: List<ScanResult?>?) {
                super.onBatchScanResults(results)
                results?.let {
                    for (result in it) {
                        result?.let {
                            addDevice(result.device)
                            Log.d(TAG, "Device : ${result.device}")
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with errorCode: $errorCode")
            }

        }

        if (!_scanStatus.value){
            _scanStatus.value= true
            scanStatus(true)
            bluetoothLeScanner.startScan(scanCallback)
            scanJob= scope.launch {
                delay(5000)
                bluetoothLeScanner.stopScan(scanCallback)
                _scanStatus.value= false
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan(){
        if (_scanStatus.value){
            scanJob?.cancel()
            bluetoothLeScanner.stopScan(object: ScanCallback(){
            })
            _scanStatus.value=false
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectDevice(device: BluetoothDevice, context: Context){
        device.connectGatt(context, false, bluetoothGattCallBack)
        _isConnecting.value= true
    }

}