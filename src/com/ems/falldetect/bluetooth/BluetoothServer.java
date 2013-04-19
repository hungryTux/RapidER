package com.ems.falldetect.bluetooth;

import java.io.IOException;
import java.util.UUID;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

class BluetoothServer extends Thread {
	
	private static final String TAG = "FallDetector";
	
	private final Context mContext;
	private final BluetoothAdapter mBluetoothAdapter;
  private static final UUID APP_UUID = UUID.fromString("6d341340-89ed-11e2-9e96-0800200c9a66");
  private BluetoothServerSocket mListeningSocket;
  
  public BluetoothServer(Context ctx){
  	
  	this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
  	this.mContext = ctx;
  	
  }
  
  @Override
  public void run() {
  	
  	Log.d(TAG, "Starting BluetoothServer..");
  	
  	while(!Thread.currentThread().isInterrupted()) {
  		
  		try {
  			
  			mListeningSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(mBluetoothAdapter.getAddress(),
		        APP_UUID);
  			
  			//Immediately cancel discovery, so we have greater bandwidth
  	  	mBluetoothAdapter.cancelDiscovery();
  			
  			//Listen for incoming connections
				BluetoothSocket socket = mListeningSocket.accept();
				
				//Stop listening until we have taken care of this message
				mListeningSocket.close();
			
				ConnectionHandlerThread connThread = new ConnectionHandlerThread(mContext, socket);
				connThread.start();
				
				
  		} catch (IOException e) {
  			
  			Log.d(TAG, "IOException while accept()-"+e.getMessage());
  			
			}
  		
  		
  	}
  	
  	
  }
  
  
  public void stopServer() {
  	
  	Log.d(TAG, "Stopping BluetoothServer..");
  	
  	this.interrupt();
  	
  	if(mListeningSocket != null) {
  		
  		try {
  		
  			mListeningSocket.close();
  		
  		} catch(IOException e) {
  			
  		}
  		
  	}
  	
  }
  
}
