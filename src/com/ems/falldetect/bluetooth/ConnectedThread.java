package com.ems.falldetect.bluetooth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

class ConnectedThread extends Thread {
	
	private static final String TAG = "FallDetector";
	
	private BluetoothSocket mBTSocket;
	private InputStream mInputStream;
	private OutputStream mOutputStream;
	private PrintWriter mOutputWriter;
	
	private Handler mHandler;
	private String mRemoteDeviceName;
	
	private boolean cancelled = false;
	
	public ConnectedThread(Handler connStatusHandler) {
		
		super();
		
		mHandler = connStatusHandler;
		
	}
	
	public String getRemoteDeviceName() {
		return mRemoteDeviceName;
	}
	
	public void setRemoteDeviceName(String name){
		mRemoteDeviceName = name;
	}
	
	public void setSocket(BluetoothSocket socket) {
		mBTSocket = socket;
		
		try {
			mInputStream = mBTSocket.getInputStream();
			mOutputStream = mBTSocket.getOutputStream();
		
			mOutputWriter = new PrintWriter(mOutputStream, true);
		
		}
		catch (IOException e) {
			Log.d(TAG, "Failed to get IO streams-"+e.getMessage());
		}
		
	}
	
	public void sendData(String data) {
		
		try{
			Log.d(TAG, "Writing on output stream");
			
			mOutputWriter.println(data);
			mOutputStream.flush();
			
		} catch(IOException e) {
			
			Log.i(TAG, "Connection lost with "+mRemoteDeviceName+" during write");
			
			if(mOutputWriter != null)
				mOutputWriter.close();
			
			if(mInputStream != null) {
				
				try {
					mInputStream.close();
				} catch (IOException ex) {
					
				}
				
				mInputStream = null;
			}
			
			if(mOutputStream != null) {
				
				try {
					mOutputStream.close();
				} catch (IOException ex) {
					
				}
				
				mOutputStream = null;
			}
			
			if(mBTSocket != null) {
				try {
					
					mBTSocket.close();
				
				} catch (IOException ex) {
					
				}
				mBTSocket = null;
			}
			
			if(cancelled)
				return;
			
			Bundle b = new Bundle();
			b.putString("name", mRemoteDeviceName);
			
			Message msg = new Message();
			msg.what = BluetoothConstants.STATUS_CONNECTION_LOST_ERROR;
			msg.setData(b);
			mHandler.sendMessage(msg);
						
		} 
		
	}
	
	@Override
	public void run() {
			
		BufferedReader br = null;
		
		while (!Thread.currentThread().isInterrupted()) {
			
			//Keep reading from socket and sending it to caller
			try{
				
				//Only one line is sent in response
				br = new BufferedReader(new InputStreamReader(mInputStream));
				String dataStr = br.readLine();
				
				Log.d(TAG, "Response - "+dataStr);
				
				Bundle b = new Bundle();
				b.putString("name", mRemoteDeviceName);
				b.putString("data", dataStr);
				
				Message msg = new Message();
				msg.what = BluetoothConstants.STATUS_DATA_AVAILABLE;
				msg.setData(b);
				mHandler.sendMessage(msg);
		
				//Response has come, no need to block on read() anymore
				break;
				
			} catch (IOException e) {
				
				Log.i(TAG, "Connection lost with "+mRemoteDeviceName+" during read");
				
				//Only if the close of connection was not
				//initiated by us, send the error
				if(!cancelled) {
					
					Bundle b = new Bundle();
					b.putString("name", mRemoteDeviceName);
				
					Message msg = new Message();
					msg.what = BluetoothConstants.STATUS_CONNECTION_LOST_ERROR;
					msg.setData(b);
					mHandler.sendMessage(msg);
				
				}
				
				break;
			}
			
			
		}
		
		if(mOutputWriter != null)
			mOutputWriter.close();
		
		if(br != null) {
			
			try {
				br.close();
			}
			catch (IOException e) {
				Log.d(TAG, e.getMessage());
			}
			
		}
		
		if(mInputStream != null) {
			
			try {
				mInputStream.close();
			} catch (IOException ex) {
				
			}
			
			mInputStream = null;
		}
		
		if(mOutputStream != null) {
			
			try {
				mOutputStream.close();
			} catch (IOException ex) {
				
			}
			
			mOutputStream = null;
		}
		
		if(mBTSocket != null) {
			try {
				
				mBTSocket.close();
			
			} catch (IOException ex) {
				
			}
			mBTSocket = null;
		}
		
	}
	
	public void cancel() {
		
		Log.i(TAG, "Closing Connection with "+mRemoteDeviceName);
		
		this.interrupt();
		
		cancelled = true;
		
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
		
		if(mBTSocket != null) {
			try {
				mBTSocket.close();
			} catch (IOException e) {
				
			}
			mBTSocket = null;
		}
		
	}

}
