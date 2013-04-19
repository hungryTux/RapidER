package com.ems.falldetect.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;


public class BluetoothClientWrapper {
		
	private static final String TAG = "FallDetector";
	
	private static final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	
	//Client-side listeners
	private static BroadcastReceiver client_stateChangeListener;
	private static BroadcastReceiver client_discoveryResultListener;
	
	
	//Non Static
	private Handler client_conn_status_handler;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	
	public BluetoothClientWrapper(Handler connStatusHandler) {
		
		client_conn_status_handler = connStatusHandler;
			
	}
	
	
	public static int enableBluetooth(Context mContext, BroadcastReceiver stateChangeListener) {
		
		//Check if device has bluetooth capabilities
		if(mBluetoothAdapter == null)
			return -1;
		
		//Stop BluetoothStateListenerService before doing this
		//as that would interfere with the discovery of peer devices
		Intent intent = new Intent("com.ems.falldetect.stop_bluetooth_server");
		mContext.sendBroadcast(intent);
		
		if(!mBluetoothAdapter.isEnabled()) {
			
			//We have the BLUETOOTH_ADMIN priviliges to do this.
			//Ideally it is not a good practice to implicitly
			//enable bluetooth without user consent. But in our
			//case the user may not be in a state to explcitly
			//grant our application, the permission to use bluetooth.
			if(mBluetoothAdapter.enable()) {
				
				Log.d(TAG, "Bluetooth startup has begun");
				
			} else {
				
				Log.d(TAG, "Bluetooth startup failed!!");
				return -1;
				
			}
			
			//Allow the user of this class to listen for changes
			//in state of the bluetooth adapter once we explicitly
			//enable it. When it finds that it is enabled, it can
			//call other discovery and communication APIs provided
			//by this class
			client_stateChangeListener = stateChangeListener;
			
			mContext.registerReceiver(client_stateChangeListener, 
					new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
			
		} else {
			
			//Bluetooth is already enabled.
			//Start communication immediately!
			return 1;
			
		}
		
		return 0;
		
	}
	
	public static int disableBluetooth(Context mContext) {
		
		if(mBluetoothAdapter == null)
			return -1;
		
		if(client_stateChangeListener != null){
			mContext.unregisterReceiver(client_stateChangeListener);
			client_stateChangeListener = null;
		}
		
		if(mBluetoothAdapter.disable()) {
			Log.d(TAG, "Bluetooth shutdown has begun");
		}
		
		return 0;
	}
	
	
	public static int startDiscovery(Context mContext, BroadcastReceiver discoveryResultListener, boolean retry) {
		
		if(mBluetoothAdapter == null)
			return -1;
		
		if(!retry) {
			
			client_discoveryResultListener = discoveryResultListener;
			mContext.registerReceiver(client_discoveryResultListener, 
				new IntentFilter(BluetoothDevice.ACTION_FOUND));
			mContext.registerReceiver(client_discoveryResultListener, 
				new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
			mContext.registerReceiver(client_discoveryResultListener, 
				new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
		}
		
		if(mBluetoothAdapter.isEnabled() && !mBluetoothAdapter.isDiscovering()) {
			
			mBluetoothAdapter.startDiscovery();
			Log.d(TAG, "Remote bluetooth device discovery started");
			
		} 
		
		return 0;
		
	}
	
	public static int stopDiscovery(Context mContext) {
		
		if(client_discoveryResultListener != null) {
			mContext.unregisterReceiver(client_discoveryResultListener);
			client_discoveryResultListener = null;
		}
		
		return 0;
	}
	
	public int establishConnectionWithDevice(String deviceName, String deviceAddr){
		
		if(mConnectThread != null && mConnectThread.isAlive()) {
			Log.e(TAG, "Still attempting to establish connection with "+mConnectThread.getRemoteDeviceName());
			return -1;
		}
		
		if(mConnectedThread != null && mConnectedThread.isAlive()) {
			Log.e(TAG, "Connection already established with "+mConnectThread.getRemoteDeviceName()+". Close that first!!");
			return -1;
		}
		
		//Create fresh connection thread objects for the remote device
		mConnectedThread = new ConnectedThread(client_conn_status_handler);
		mConnectThread = new ConnectThread(client_conn_status_handler, 
				deviceName, deviceAddr, mConnectedThread);
		
		mConnectThread.start();
		
		return 0;
	}
	
	public int cancelConnectionAttemptWithDevice(String deviceName) {
		
		if(mConnectThread != null && mConnectThread.isAlive() && 
				mConnectThread.getRemoteDeviceName().equals(deviceName)) {
			
			mConnectThread.cancel();
			
		}
		
		if(mConnectedThread != null && mConnectedThread.isAlive() &&
				mConnectedThread.getRemoteDeviceName().equals(deviceName)) {
			
			mConnectedThread.cancel();
		
		}
		
		return 0;
	}
	
	public int closeConnectionWithDevice(String deviceName) {
		
		if(mConnectedThread != null && mConnectedThread.isAlive() &&
				mConnectedThread.getRemoteDeviceName().equals(deviceName)) {
			
			mConnectedThread.cancel();
		
		}
		
		return 0;
	}
	
	public int sendDataToDevice(String deviceName, String data) {
		
		Log.d(TAG, "Attempting to send data to device-"+deviceName);
		
		if(mConnectedThread != null && mConnectedThread.isAlive() && 
				mConnectedThread.getRemoteDeviceName().equals(deviceName)) {
			
			Log.d(TAG, "Sending..");
			mConnectedThread.sendData(data);
			
		} else {
			
			Log.d(TAG, "Device Address doesn't match with that of connected device!!");
			return -1;
		}
		
		return 0;
		
	}

}
