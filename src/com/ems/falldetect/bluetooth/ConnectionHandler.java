package com.ems.falldetect.bluetooth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.ems.falldetect.HttpPostTask;
import com.ems.falldetect.MapActivity;
import com.ems.falldetect.message.MessageUtil;
import com.ems.falldetect.message.UserLocation;


class ConnectionHandlerThread extends Thread {
	
	private static final String TAG = "FallDetector";
	
	private final Context mContext;
	
	private BluetoothSocket mSocket;
	private InputStream mInputStream;
	private OutputStream mOutputStream;
	private PrintWriter mOutputWriter;
	
	private ConnectivityManager mConnectivityManager;
	
	public ConnectionHandlerThread(Context context, BluetoothSocket socket) {
		
		super();
		
		this.mContext = context;
		this.mSocket = socket;
		
		try {
			
			this.mInputStream = mSocket.getInputStream();
			this.mOutputStream = mSocket.getOutputStream();
			this.mOutputWriter = new PrintWriter(mOutputStream, true);
			
		}
		catch (IOException e) {
			
		}
		

		
		this.mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		
	}
	
	@Override
	public void run() {
		
		Log.d(TAG, "Starting ConnectionHandlerThread..");
		
		StringBuffer message = new StringBuffer();
		
		try {
				
			BufferedReader br = new BufferedReader(new InputStreamReader(mInputStream));
			String line = null;
			
			Log.d(TAG, "Reading line by line from socket");
			
			//Read till we reach the end of message
			while((line = br.readLine()).equals(MessageUtil.END_OF_MSG) != true){
					
				message.append(line);
				
			}
			
			Log.d(TAG,"BluetoothServer: Received message from client-"+message);
			
			//Start activity to display location of emergency on the map.
			//De-serialze the message first
			MessageUtil msgUtil = new MessageUtil(mContext);
			List<UserLocation> locations = msgUtil.parseMessage(message.toString());
			int numLocations = locations.size();
			Log.d(TAG, "Num locations in message-"+numLocations);
			if(numLocations != 0) {
				//Get location coordinates of emergency location
				//from 0th index
				double longitude = locations.get(0).longitude;
				double latitude = locations.get(0).latitude;
				double accuracy = locations.get(0).accuracy;
				
				Intent mapIntent = new Intent(mContext, 
 					 MapActivity.class);
				mapIntent.putExtra("longitude", longitude);
				mapIntent.putExtra("latitude", latitude);
				mapIntent.putExtra("accuracy", accuracy);
				mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				mContext.startActivity(mapIntent);
				
			}
			
			NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
			boolean isConnected = (activeNetwork != null) && activeNetwork.isConnectedOrConnecting();
		
			if(!isConnected) {
				
				//We are not connected to the network
				
				//TODO: Save the message on filesystem and register a 
				//receiver for change in connectivity. If later the device
				//gets connected and we find pending messages on filesystem
				//not older than a certain date, send them to server
				
				
				
				
				//Let the client know that we won't be able to send SOS presently
				//so that it can try out other peers
				Log.d(TAG, "Sending failure response");
				mOutputWriter.println(BluetoothConstants.MSG_FAILURE_STR);
				mOutputStream.flush();
			
			} else {
				
				//We are connected to the network
				
				//Start an async task from 'context' to
				//upload this message to server
				new HttpPostTask().execute(message.toString());
				
				//Let the client know that we are sending the SOS
				Log.d(TAG, "Sending success response");
				mOutputWriter.println(BluetoothConstants.MSG_SUCCESS_STR);
				mOutputStream.flush();
				
			}
			
			mOutputWriter.close();
			
			br.close();
			
			if(mInputStream != null) {
				
				try {
					mInputStream.close();
				} catch (IOException e) {
					
				}
				
				mInputStream = null;
			}
			
			if(mOutputStream != null) {
				
				try {
					mOutputStream.close();
				} catch (IOException e) {
					
				}
				
				mOutputStream = null;
			}
			
			if(mSocket != null) {
				
				try {
					mSocket.close();
				} catch (IOException e) {
					
				}
				
				mSocket = null;
			}
			
		} catch(IOException e) {
			
			
		}
		
	}

}
