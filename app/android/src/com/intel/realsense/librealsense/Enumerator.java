package com.intel.realsense.librealsense;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Simplified Enumerator for RTABMap integration - no AndroidX dependency.
 * Notifies regarding attach/detach events of a RealSense device.
 */
class Enumerator {
    private static final String TAG = "librs Enumerator";

    private Context mContext;
    private DeviceListener mListener;
    private HandlerThread mMessagesHandler;
    private Handler mHandler;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "onReceive: " + action);

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) ||
                UsbUtilities.ACTION_USB_PERMISSION.equals(action)) {
                onDeviceAttach(context);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                onDeviceDetach();
            }
        }
    };

    public Enumerator(Context context, DeviceListener listener) {
        if (listener == null) {
            Log.e(TAG, "Enumerator: provided listener is null");
            throw new NullPointerException("provided listener is null");
        }
        if (context == null) {
            Log.e(TAG, "Enumerator: provided context is null");
            throw new NullPointerException("provided context is null");
        }

        mMessagesHandler = new HandlerThread("DeviceManager device availability message thread");
        mMessagesHandler.start();
        mHandler = new MessagesHandler(mMessagesHandler.getLooper());

        mListener = listener;
        mContext = context;

        context.registerReceiver(mBroadcastReceiver, new IntentFilter(UsbUtilities.ACTION_USB_PERMISSION));
        context.registerReceiver(mBroadcastReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
        context.registerReceiver(mBroadcastReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));

        onDeviceAttach(context);
    }

    public synchronized void close() {
        if (mContext != null)
            mContext.unregisterReceiver(mBroadcastReceiver);
        mMessagesHandler.quitSafely();
        try {
            mMessagesHandler.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "close: " + e.getMessage());
        }
        mMessagesHandler = null;
    }

    private synchronized void notifyOnAttach() throws Exception {
        if (mListener != null) {
            Log.i(TAG, "notifyOnAttach");
            mListener.onDeviceAttach();
        }
    }

    private synchronized void notifyOnDetach() throws Exception {
        if (mListener != null) {
            Log.i(TAG, "notifyOnDetach");
            mListener.onDeviceDetach();
        }
    }

    private void onDeviceAttach(Context context) {
        Message msg = Message.obtain();
        UsbUtilities.grantUsbPermissionIfNeeded(context);
        msg.what = MessagesHandler.ON_DEVICE_AVAILABLE;
        mHandler.sendMessage(msg);
    }

    private void onDeviceDetach() {
        Message msg = Message.obtain();
        msg.what = MessagesHandler.ON_DEVICE_UNAVAILABLE;
        mHandler.sendMessage(msg);
    }

    private class MessagesHandler extends Handler {
        private static final String TAG = "librs MessagesHandler";
        public static final int ON_DEVICE_AVAILABLE = 0;
        public static final int ON_DEVICE_UNAVAILABLE = 1;

        public MessagesHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            try {
                switch (msg.what) {
                    case ON_DEVICE_AVAILABLE:
                        Log.i(TAG, "handleMessage: realsense device attached");
                        notifyOnAttach();
                        break;
                    case ON_DEVICE_UNAVAILABLE:
                        Log.i(TAG, "handleMessage: realsense device detached");
                        notifyOnDetach();
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "handleMessage: failed to open device, error: " + e.getMessage());
            }
        }
    }
}
