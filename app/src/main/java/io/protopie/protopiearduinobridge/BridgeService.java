package io.protopie.protopiearduinobridge;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

public class BridgeService extends Service {
    private static final String TAG = BridgeService.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = BridgeService.class.getCanonicalName() + ".USB_PERMISSION";

    private UsbManager usbManager;
    private BytesMessageReader bytesMessageReader = new BytesMessageReader();

    private UsbDevice currentDevice;
    private UsbSerialDevice connectedSerialPort;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(ProtoPieUtils.PROTOPIE_RECEIVE_ACTION);
        filter.addAction(ProtoPieUtils.PROTOPIE_RECEIVE_ACTION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
    }

    private void checkPermission(UsbDevice device) {
        currentDevice = device;
        if (usbManager.hasPermission(device)) {
            communicate();
        } else {
            Log.i(TAG, "Requesting permission");
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(device, pi);
        }
    }

    private void communicate() {
        Log.d(TAG, "Opening connection");

        UsbDeviceConnection connection = usbManager.openDevice(currentDevice);
        connectedSerialPort = UsbSerialDevice.createUsbSerialDevice(currentDevice, connection);
        connectedSerialPort.open();
        connectedSerialPort.setBaudRate(9600);
        connectedSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
        connectedSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
        connectedSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
        connectedSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);

        connectedSerialPort.read(new UsbSerialInterface.UsbReadCallback() {
            @Override
            public void onReceivedData(final byte[] bytes) {
                if (bytes.length > 0) {
                    Log.i(TAG, "Byte received: " + new String(bytes));

                    for (String messageId : bytesMessageReader.onData(bytes, bytes.length)) {
                        MainActivity.sendToConsole(BridgeService.this, "Message from USB: " + messageId);
                        ProtoPieUtils.sendToProtoPie(BridgeService.this, messageId);
                    }
                }
            }
        });

        connectedSerialPort.getCTS(new UsbSerialInterface.UsbCTSCallback() {
            @Override
            public void onCTSChanged(boolean b) {
                Log.i(TAG, "CTS Changed: " + b);
            }
        });

        connectedSerialPort.getDSR(new UsbSerialInterface.UsbDSRCallback() {
            @Override
            public void onDSRChanged(boolean b) {
                Log.i(TAG, "DSR changed: " + b);
            }
        });
    }

    private void sendToUsb(byte[] data) {
        if (connectedSerialPort != null) {
            connectedSerialPort.write(data);
        }
    }

    private void disconnect() {
        if (connectedSerialPort != null) {
            connectedSerialPort.close();
            connectedSerialPort = null;
        }

        if (currentDevice != null) {
            currentDevice = null;
        }
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Broadcast received: " + intent.getAction());

            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                Log.i(TAG, "USB permission: " + granted);

                if (granted) {
                    communicate();
                }
            } else if (ProtoPieUtils.PROTOPIE_RECEIVE_ACTION.equals(intent.getAction())) {
                String messageId = intent.getStringExtra("messageId");
                Log.i(TAG, "Received a message from ProtoPie: " + messageId);
                MainActivity.sendToConsole(BridgeService.this, "Message from ProtoPie: " + messageId);
                sendToUsb(messageId.getBytes());
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                MainActivity.sendToConsole(BridgeService.this, "USB device attached");

                Parcelable device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device instanceof UsbDevice) {
                    checkPermission((UsbDevice) device);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                MainActivity.sendToConsole(BridgeService.this, "USB device detached");
                disconnect();
            }
        }
    };
}
