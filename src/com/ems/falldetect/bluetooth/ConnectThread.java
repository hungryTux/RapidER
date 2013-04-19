package com.ems.falldetect.bluetooth;

import java.io.IOException;
import java.util.UUID;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

class ConnectThread extends Thread {
	
	private static final String TAG = "FallDetector";
	
	private static final UUID APP_UUID = UUID.fromString("6d341340-89ed-11e2-9e96-0800200c9a66");

	private String mRemoteDeviceName;
	private String mRemoteDeviceAddr;
		
	private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	private BluetoothDevice mRemoteDevice;
	private BluetoothSocket mBTSocket;
	
	private ConnectedThread mConnectedThread;
	
	private Handler mHandler;
	
	private boolean cancelled = false;
	
	public ConnectThread(Handler connStatusHandler, String deviceName, String deviceAddr, 
			ConnectedThread connectedThread) {
		
		super();
		
		mHandler = connStatusHandler;
		mRemoteDeviceName = deviceName;
		mRemoteDeviceAddr = deviceAddr;
		mConnectedThread = connectedThread;
		
	}
	
	public String getRemoteDeviceName() {
		return mRemoteDeviceName;
	}
	
	
	@Override
	public void run() {
		
		int counter = 0;
		Bundle b = new Bundle();
		Message msg = new Message();
		
		while (true) {
				
				try {
				
					if(mRemoteDevice == null)
						mRemoteDevice = mBluetoothAdapter.getRemoteDevice(mRemoteDeviceAddr);
				
				} catch (Exception e) {
					
					//Could be InterruptedException caused by cancel().
					//If that's the case return
					if(cancelled)
						return;
					
					Log.d(TAG, "Failed to get remote device - "+e.getMessage());
					
				}
				
				
				try {
					
					if(counter < 3) {
						//Try three times to create connection with the remote device
						mBTSocket = mRemoteDevice.createInsecureRfcommSocketToServiceRecord(APP_UUID);
					}
					
				}	catch(Exception e) {
					
					//Could be InterruptedException caused by cancel().
					//If that's the case return
					if(cancelled)
						return;
					
					//Maybe an IOException. Lets try again..
					Log.d(TAG, "Failed to createInsecureRfcommSocketToServiceRecord()-"+e.getMessage());
					
				}
				
				
				
				try {
					
					//Cancel any ongoing discovery first. Apparently this slows down the connection 
					mBluetoothAdapter.cancelDiscovery();
					mBTSocket.connect();
					break;
				
				} catch (Exception e) {
					
					//Could be InterruptedException caused by cancel().
					//If that's the case return
					if(cancelled)
						return;
					
					//Maybe an IOException (possible because mBTSocket is still null). 
					//Lets try again..
					Log.d(TAG, "Failed to connect to remote device-"+e.getMessage());
					
					counter++;
					
					if(counter >= 15) {
						//Try atleast 12 times to connect to remote device, before giving up
						
						//Let the caller know that all connection attempts have failed
						b.putString("name", mRemoteDeviceName);
						msg.what = BluetoothConstants.STATUS_CONNECTION_ERROR;
						msg.setData(b);
						mHandler.sendMessage(msg);
						
						return;
					}
					
				}
						
		}
		
		//Hooray! We are connected. Send across the data
		Log.d(TAG, "Connection Attempt Success with device-"+mRemoteDeviceName);
		mConnectedThread.setSocket(mBTSocket);
		mConnectedThread.setRemoteDeviceName(mRemoteDeviceName);
		mConnectedThread.start();
		
		//Let the caller know that we are attempting to send
		//data to this remote device, so it doesn't try out
		//other devices
		b.putString("name", mRemoteDeviceName);
		msg.what = BluetoothConstants.STATUS_CONNECTED;
		msg.setData(b);
		mHandler.sendMessage(msg);
		
	}
	
	public void cancel() {
		
		cancelled = true;
		
		if (mBTSocket != null){
			
			try {
				
				mBTSocket.close();
			
			}
			catch (IOException e) {
				
				Log.d(TAG, "Failed to close mBTSocket-"+e.getMessage());
				
			}
			
		}
			
		this.interrupt();
		
	}
	
}
