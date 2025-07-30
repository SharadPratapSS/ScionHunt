package com.sharad.blesetup

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sharad.blesetup.ui.theme.BLESetupTheme
import com.yucheng.ycbtsdk.Constants
import com.yucheng.ycbtsdk.YCBTClient
import com.yucheng.ycbtsdk.bean.ScanDeviceBean
import com.yucheng.ycbtsdk.response.BleConnectResponse
import com.yucheng.ycbtsdk.response.BleDataResponse
import com.yucheng.ycbtsdk.response.BleRealDataResponse
import com.yucheng.ycbtsdk.response.BleScanResponse
import kotlinx.coroutines.delay
import java.util.HashMap

class MainActivity : ComponentActivity() {

    val list = mutableListOf<ScanDeviceBean>()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YCBTClient.initClient(applicationContext,true, true)
        YCBTClient.startScanBle(object : BleScanResponse {
            override fun onScanResponse(i: Int, scanDeviceBean: ScanDeviceBean?) {
                if (scanDeviceBean != null) {
                    Log.d("BEEEE", scanDeviceBean?.device?.name ?: "")
                    Log.d("BEEEE", "Device Mac" + scanDeviceBean.deviceMac)
                    list.add(scanDeviceBean)
                    YCBTClient.connectBle(scanDeviceBean.deviceMac, object : BleConnectResponse {
                        override fun onConnectResponse(p0: Int) {
                            Log.d("BEEEE", p0.toString())
                            Constants.BLEState.Disconnect
                        }
                    });
                }
            }
        }, 1000)


        YCBTClient.registerBleStateChange(object : BleConnectResponse {
            override fun onConnectResponse(code: Int) {
                Log.d("BEEE", code.toString())
                if (code == 10) {

                    val dataResponse = object : BleDataResponse {
                        override fun onDataResponse(
                            p0: Int,
                            p1: Float,
                            p2: HashMap<*, *>?
                        ) {
                            Log.d("BEEE onDataResponse", "$p0 p1 $p1 map $p2")
                        }
                    }
                    YCBTClient.settingTemperatureMonitor(true, 100000, dataResponse);
                }
            }
        })

//        YCBTClient.settingHandWear(Constants.HandWear.Left, object : BleDataResponse {
//            override fun onDataResponse(
//                p0: Int,
//                p1: Float,
//                p2: HashMap<*, *>?
//            ) {
//                TODO("Not yet implemented")
//            }
//        })

//        YCBTClient.appEcgTestStart(dataResponse, object : BleRealDataResponse {
//
//
//            override fun onRealDataResponse(p0: Int, p1: HashMap<*, *>?) {
//                Log.d("BEEE onRealDataResponse", "$p0 p1  map $p1")
//            }
//        });
        requestBluetoothPermissions(this)
        enableEdgeToEdge()
        setContent {
            BLESetupTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GattAndroid()
                }
            }
        }
    }
}

@Composable
fun BleConnect(){

    var bck by remember { mutableStateOf(Color.White) }
    var bleDevices by remember { mutableStateOf(emptyList<ScanDeviceBean>()) }
    var disableSearch by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        while (true){
            delay(5000)
            Log.d("Device Results:", "Connection Status: ${YCBTClient.connectState()}")
        }
    }

    LaunchedEffect(disableSearch) {
        if (disableSearch){
            delay(10000)
            YCBTClient.stopScanBle()
            Log.d("Device Results:", "Scan Stopped")
            disableSearch = false
        }
    }
    Box(modifier = Modifier
        .fillMaxSize()
        .background(bck), contentAlignment = Alignment.Center){

        Column() {
            Button(onClick = {
                bleDevices = emptyList()
                StartSearch() {
                    bleDevices = bleDevices + it
                }
                disableSearch = true
            }) {
                Text("Start Search")
            }
            Column {
                bleDevices.forEach {
                    Text(it.deviceMac + "\n" + it.deviceName + "\n" + it.deviceRssi + "\n" + it.device.toString(),
                        modifier = Modifier.clickable(
                            onClick = {
                                YCBTClient.disconnectBle()
                                YCBTClient.stopScanBle()
                                YCBTClient.connectBle(it.deviceMac,object : BleConnectResponse{

                                    override fun onConnectResponse(p0: Int) {
                                        when (p0) {
                                            Constants.BLEState.ReadWriteOK -> {
                                                bck = Color.Green
                                                Log.d("BLE_STATE", "Connection successful: Read/Write OK")
                                            }
                                            Constants.BLEState.TimeOut -> {
                                                bck = Color.Gray
                                                Log.d("BLE_STATE", "Connection timeout")
                                            }
                                            Constants.BLEState.Disconnect -> {
                                                bck = Color.Blue
                                                Log.d("BLE_STATE", "Disconnected")
                                            }
                                            Constants.BLEState.Disconnecting -> {
                                                bck = Color.Red
                                                Log.d("BLE_STATE", "Disconnecting")
                                            }
                                            Constants.BLEState.Connecting -> {
                                                bck = Color.Yellow
                                                Log.d("BLE_STATE", "Connecting...")
                                            }
                                            else -> {
                                                bck = Color.Red
                                                Log.w("BLE_STATE", "Unknown BLE state: $p0")
                                            }
                                    }
                                    }
                                } )
                            }
                        ))
                }
            }
        }
    }

}

fun StartSearch(onSearch: (ScanDeviceBean)-> Unit){
    Log.d("Device Results:", "Starting Scan")
    YCBTClient.startScanBle(BleScanResponse { p0, p1 ->
        if (p1 != null){
            val mac = p1.deviceMac
            val name = p1.deviceName
            val rssi = p1.deviceRssi
            val bluetoothDevice = p1.device
            onSearch(p1)
            Log.d("Device Results:", "Mac: $mac, Name: $name, RSSI: $rssi")
        }
    },6)
}

fun requestBluetoothPermissions(context: Context) {
    val activity = context as? Activity ?: return // Ensure it's an Activity context

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val missingPermissions = permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    if (missingPermissions.isNotEmpty()) {
        ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), 1001)
    }
}
