package com.example.car

import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.UUID

object BleConnectionManager {
    // TODO: Replace with your service and characteristic UUIDs
    private val SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    private var bluetoothGatt: BluetoothGatt? = null
    private var writableCharacteristic: BluetoothGattCharacteristic? = null
    private var onConnected: (() -> Unit)? = null

    fun connect(context: Context, device: BluetoothDevice, onConnected: () -> Unit) {
        this.onConnected = onConnected
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    fun write(data: ByteArray) {
        if (writableCharacteristic != null) {
            writableCharacteristic?.value = data
            bluetoothGatt?.writeCharacteristic(writableCharacteristic)
        } else {
            Log.e("BleConnectionManager", "Writable characteristic not found")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BleConnectionManager", "Connected to GATT server.")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BleConnectionManager", "Disconnected from GATT server.")
                disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BleConnectionManager", "Services discovered.")
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    writableCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    if (writableCharacteristic != null) {
                        Log.d("BleConnectionManager", "Writable characteristic found")
                        onConnected?.invoke()
                    } else {
                        Log.e("BleConnectionManager", "Writable characteristic not found")
                    }
                } else {
                    Log.e("BleConnectionManager", "Service not found")
                }
            } else {
                Log.w("BleConnectionManager", "onServicesDiscovered received: $status")
            }
        }
    }
}