package com.example.smartwatch
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import java.util.UUID

class HeartRateService : LifecycleService() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    private val heartRateCharacteristic: BluetoothGattCharacteristic =
        BluetoothGattCharacteristic(
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

    private val heartRateService: BluetoothGattService =
        BluetoothGattService(
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!hasBluetoothPermissions()) {
            Log.e("HeartRateService", "Insufficient Bluetooth permissions; service cannot start.")
            stopSelf()
            return
        }

        if (bluetoothAdapter.isEnabled) {
            advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            setupServer()
            startAdvertising()
        } else {
            Log.e("HeartRateService", "Bluetooth is disabled. Cannot start service.")
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupServer() {
        if (!hasBluetoothPermissions()) return

        try {
            bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)
            heartRateService.addCharacteristic(heartRateCharacteristic)
            bluetoothGattServer?.addService(heartRateService)
        } catch (e: SecurityException) {
            Log.e("HeartRateService", "Error setting up GATT server: ${e.message}")
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (!hasBluetoothPermissions()) return

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")))
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e("HeartRateService", "Error starting advertising: ${e.message}")
            stopSelf()
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("HeartRateService", "Advertising started successfully.")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("HeartRateService", "Advertising failed with error code: $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("HeartRateService", "Device connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("HeartRateService", "Device disconnected: ${device.address}")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (!hasBluetoothPermissions()) return

            if (characteristic.uuid == heartRateCharacteristic.uuid) {
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    generateHeartRateData()
                )
            }
        }
    }

    private fun generateHeartRateData(): ByteArray {
        val heartRate = (60..100).random() // Simulated heart rate value
        return byteArrayOf(0x00, heartRate.toByte())
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGattServer?.close()
        advertiser?.stopAdvertising(advertiseCallback)
    }
}
