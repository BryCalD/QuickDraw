package com.example.ubicompproj;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BLEService extends Service {

    // Bluetooth variables
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;
    BluetoothManager bluetoothManager;
    ScanCallback bluetoothScanCallback;
    BluetoothGatt gattClient;
    BLEBinder bleBinder;
    long numMeasurements, t0, t;
    boolean filter = false;
    //arrays for low pass filtering
    float [] accel_input;
    float [] accel_output;

    //microbit accelerometer BLE service UUIDs
    final UUID ACC_SERVICE_SERVICE_UUID = UUID.fromString("E95D0753-251D-470A-A062-FA1922DFA9A8");
    final UUID ACC_DATA_CHARACTERISTIC_UUID = UUID.fromString("E95DCA4B-251D-470A-A062-FA1922DFA9A8");
    final UUID ACC_PERIOD_CHARACTERISTIC_UUID = UUID.fromString("E95DFB24-251D-470A-A062-FA1922DFA9A8");
    final String TAG = "MicroBitConnectService";
    //final String uBit_name = "BBC micro:bit [tovuv]";
    //final String uBit_name = "BBC micro:bit [givez]";
    final String uBit_name = "BBC micro:bit";

    //list of listeners for data received events
    private List<com.example.ubicompproj.BLEListener> listeners = new ArrayList<com.example.ubicompproj.BLEListener>();

    public BLEService() {
        bleBinder = new BLEBinder();
        accel_input = new float[3];
        accel_output = new float[3];
    }

    public void addBLEListener(com.example.ubicompproj.BLEListener listener) {
        listeners.add(listener);
    }

    /**
     * Class used for the client Binder. The Binder object is responsible for returning an instance
     * of "BLEService" to the client.
     */
    public class BLEBinder extends Binder {
        BLEService getService() {
            // Return this instance of MyService so clients can call public methods
            return BLEService.this;
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return bleBinder;
    }

    @Override
    public int onStartCommand (Intent intent,
                               int flags,
                               int startId)
    {
        return START_STICKY;
    }

    public void startScan(){
        bluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothScanCallback = new BluetoothScanCallback();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        bluetoothLeScanner.startScan(bluetoothScanCallback);
        Log.i(TAG,"startScan()");
    }

    // BLUETOOTH CONNECTION
    private void connectDevice(BluetoothDevice device) {
        if (device == null) {
            Log.i(TAG, "Device is null");
            return;
        }
        GattClientCallback gattClientCallback = new GattClientCallback();
        gattClient = device.connectGatt(this,false,gattClientCallback);
    }

    // BLE Scan Callbacks
    private class BluetoothScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i(TAG, "onScanResult"+result.getDevice().getName());
            if (result.getDevice().getName() != null){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Log.i(TAG,result.getDevice().getAlias());
                else
                    Log.i(TAG,result.getDevice().getName());
                if (result.getDevice().getName().equals(uBit_name)) {
                    // When find your device, connect.
                    connectDevice(result.getDevice());
                    bluetoothLeScanner.stopScan(bluetoothScanCallback); // stop scan
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i(TAG, "onBatchScanResults");
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.i(TAG, "ErrorCode: " + errorCode);
        }
    }

    // Bluetooth GATT Client Callback
    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.i(TAG, "onConnectionStateChange");
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "GATT operation unsuccessful (status): " + status);
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "onConnectionStateChange CONNECTED");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "onConnectionStateChange DISCONNECTED");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.i(TAG, "onServicesDiscovered");
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            gattClient = gatt;
            BluetoothGattService service = gatt.getService(ACC_SERVICE_SERVICE_UUID);
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            //List and display in log the characteristics of this service
            for(BluetoothGattCharacteristic characteristic : characteristics)
            {
                Log.i(TAG,characteristic.getUuid().toString());
            }

            // Reference your UUIDs
            BluetoothGattCharacteristic ACC_DATA_characteristicID = gatt.getService(ACC_SERVICE_SERVICE_UUID).getCharacteristic(ACC_DATA_CHARACTERISTIC_UUID);
            gatt.setCharacteristicNotification(ACC_DATA_characteristicID, true);


            //activate any descriptors for the characteristics
            List<BluetoothGattDescriptor> ACC_DATA_descriptors = ACC_DATA_characteristicID.getDescriptors();
            for (BluetoothGattDescriptor descriptor : ACC_DATA_descriptors)
            {
                Log.i(TAG,descriptor.getUuid().toString());
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }

        }
        //this is the callback that receives the accelerometer data
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte [] bytes = characteristic.getValue();
            byte[] xG_bytes = new byte[2];
            byte[] yG_bytes = new byte[2];
            byte[] zG_bytes = new byte[2];
            System.arraycopy(bytes, 0, xG_bytes, 0, 2);
            System.arraycopy(bytes, 2, yG_bytes, 0, 2);
            System.arraycopy(bytes, 4, zG_bytes, 0, 2);
            float xG = (float)byteArray2short(xG_bytes);
            float yG = (float)byteArray2short(yG_bytes);
            float zG = (float)byteArray2short(zG_bytes);
            Log.i(TAG, "acceleration: " + xG + " : " + yG + " : " + zG + " : " + (int)Math.sqrt(xG*xG+yG*yG+zG*zG)+".0");
            //calculate the refresh rate (Hz)
            if (numMeasurements == 0) {
                t0 = System.currentTimeMillis();
                numMeasurements++;
            }
            else if (numMeasurements == 100) {
                t = System.currentTimeMillis();
                float hz = 100.0f/(t-t0)*1000;
                Log.i(TAG, "HZ: " + hz);
                numMeasurements=0;
            }
            else
                numMeasurements++;

            accel_input[0] = xG;accel_input[1]=yG;accel_input[2]=zG;
            if (filter)
            {
                accel_output = lowPass(accel_input,accel_output);
            }
            else
            {
                accel_output[0] = accel_input[0];
                accel_output[1] = accel_input[1];
                accel_output[2] = accel_input[2];
            }
            double pitch = Math.atan((accel_output[0]/1024.0f) / Math.sqrt(Math.pow((accel_output[1]/1024.0f), 2) + Math.pow((accel_output[2]/1024.0f), 2)));
            double roll = Math.atan((accel_output[1]/1024.0f) / Math.sqrt(Math.pow((accel_output[0]/1024.0f), 2) + Math.pow((accel_output[2]/1024.0f), 2)));
            //convert radians into degrees
            pitch = pitch * (180.0 / Math.PI);
            roll = -1 * roll * (180.0 / Math.PI);
            Log.i(TAG, "pitch : roll: " + (int)pitch + " : " + (int)roll);
            for (com.example.ubicompproj.BLEListener listener : listeners)
            {
                listener.dataReceived(accel_output[0],accel_output[1],accel_output[2],(float)pitch,(float)roll);
            }
            //Log.i("TAG", Thread.currentThread().getName());
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i(TAG, "onCharacteristicRead: " + byteArray2short(characteristic.getValue()));
        }
    }

    //convert byte array[2 bytes] to short
    private short byteArray2short(byte [] bytes)
    {
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(bytes[0]);
        bb.put(bytes[1]);
        return bb.getShort(0);
    }
    public void changePeriod(short period)
    {
        if (gattClient != null) {
            BluetoothGattCharacteristic ACC_PERIOD_characteristicID = gattClient.getService(ACC_SERVICE_SERVICE_UUID).getCharacteristic(ACC_PERIOD_CHARACTERISTIC_UUID);
            //boolean result = gatt.readCharacteristic(ACC_PERIOD_characteristicID);
            //convert short to byte array
            byte[] value = new byte[2];
            value[0] = (byte) (period & 0xff);
            value[1] = (byte) ((period >> 8) & 0xff);
            ACC_PERIOD_characteristicID.setValue(value);
            gattClient.writeCharacteristic(ACC_PERIOD_characteristicID);
        }
    }

    /*
     * @see http://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation
     * @see http://developer.android.com/reference/android/hardware/SensorEvent.html#values
     */
    public float[] lowPass( float[] input, float[] output ) {
        float ALPHA = 0.15f;
        for ( int i=0; i<input.length; i++ ) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

    public void setFilter(boolean filter)
    {
        this.filter = filter;
    }

}

