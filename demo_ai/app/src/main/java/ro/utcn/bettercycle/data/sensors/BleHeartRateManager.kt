package ro.utcn.bettercycle.data.sensors

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

@SuppressLint("MissingPermission")
class BleHeartRateManager(private val context: Context) {

    companion object {
        // GATT Heart Rate Service (0x180D) & Measurement Characteristic (0x2A37)
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

        // GATT Cycling Speed and Cadence Service (0x1816) & Measurement Characteristic (0x2A5B)
        val CSC_SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        val CSC_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")

        val CLIENT_CHAR_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val _heartRateBpm = MutableStateFlow(0)
    val heartRateBpm: StateFlow<Int> = _heartRateBpm

    private val _cadenceRpm = MutableStateFlow(0)
    val cadenceRpm: StateFlow<Int> = _cadenceRpm

    private val _connectionState = MutableStateFlow("Deconectat")
    val connectionState: StateFlow<String> = _connectionState

    fun startScanning() {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _connectionState.value = "Bluetooth Indisponibil (foloseste modul Simulare BLE)"
            return
        }
        try {
            _connectionState.value = "Scanare senzori BLE (0x180D / 0x1816)..."
            val hrFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HR_SERVICE_UUID))
                .build()
            val cscFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(CSC_SERVICE_UUID))
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(listOf(hrFilter, cscFilter), settings, scanCallback)
        } catch (e: Exception) {
            _connectionState.value = "Permisiune BLE necesara (${e.localizedMessage ?: "Eroare"})"
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            adapter?.bluetoothLeScanner?.stopScan(this)
            _connectionState.value = "Conectare la ${result.device.name ?: "Senzor BLE"}..."
            bluetoothGatt = result.device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = "Conectat (${gatt.device.name ?: "BLE Sensor"})"
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = "Deconectat"
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gatt.getService(HR_SERVICE_UUID)?.getCharacteristic(HR_MEASUREMENT_CHAR_UUID)?.let { hrChar ->
                gatt.setCharacteristicNotification(hrChar, true)
                hrChar.getDescriptor(CLIENT_CHAR_CONFIG_UUID)?.let { desc ->
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                }
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HR_MEASUREMENT_CHAR_UUID) {
                val data = characteristic.value
                if (data != null && data.isNotEmpty()) {
                    val flags = data[0].toInt()
                    val is16Bit = (flags and 0x01) != 0
                    val bpm = if (is16Bit && data.size >= 3) {
                        ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    } else if (data.size >= 2) {
                        data[1].toInt() and 0xFF
                    } else 0
                    _heartRateBpm.value = bpm
                }
            }
        }
    }

    // Simulare telemetrie BLE pentru testare pe masa de lucru / emulator
    fun simulateTelemetry(bpm: Int, cadence: Int) {
        _heartRateBpm.value = bpm
        _cadenceRpm.value = cadence
        _connectionState.value = "Mod Simulare Senzor BLE Activ ($bpm BPM, $cadence RPM)"
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {
        }
        bluetoothGatt = null
        _connectionState.value = "Deconectat"
    }
}
