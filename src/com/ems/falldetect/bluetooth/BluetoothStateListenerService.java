package com.ems.falldetect.bluetooth;

import com.ems.falldetect.BluetoothAlertActivity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

public class BluetoothStateListenerService extends Service {

	private static final String TAG = "FallDetector";
	
	private BluetoothServer mServerThread;
	
	private BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context context, Intent intent) {
			
			String stateExtra = BluetoothAdapter.EXTRA_STATE; 
			int currState = intent.getIntExtra(stateExtra, -1); 
			
			String state = null;
			
			switch(currState) {
			
				case BluetoothAdapter.STATE_TURNING_ON:
					break;
					
				case BluetoothAdapter.STATE_ON:
					state="**Bluetooth is now on!**";
					Intent btAlertActivityIntent = new Intent(getApplicationContext(),BluetoothAlertActivity.class);
					btAlertActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(btAlertActivityIntent);
					break;
					
				case BluetoothAdapter.STATE_TURNING_OFF:
					state="Bluetooth is turning off!";
					//Stop Server thread
					if(mServerThread != null && mServerThread.isAlive()){
						mServerThread.stopServer();
						mServerThread = null;
					}
					break;
					
				case BluetoothAdapter.STATE_OFF:
					state="Bluetooth is now off!";
					break;
			
				default:
					break;
					
			}
			
			if(state != null)
				Log.d(TAG, state);
			
		}
		
	};
	
	private BroadcastReceiver mServerReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context context, Intent intent) {
			
			String action = intent.getAction();
			
			if(action.equals("com.ems.falldetect.start_bluetooth_server")) {
				
				if(mServerThread == null || (mServerThread.isAlive() == false)){
				  //Start Server thread
					mServerThread = new BluetoothServer(getApplicationContext());
					mServerThread.start();
				}
				
			} else if (action.equals("com.ems.falldetect.stop_bluetooth_server")) {
			
			  if(mServerThread != null && mServerThread.isAlive()){
				 mServerThread.stopServer();
				 mServerThread = null;
			  }
			  //stopSelf();
			}
					
		}
		
	};
	
	@Override
  public void onCreate() 
  {
		super.onCreate();
		
  	Log.v(TAG, "BluetoothStateListenerService Created");
  	
  	IntentFilter filter = new IntentFilter();
  	filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
  	registerReceiver(mBluetoothStateReceiver, filter);
  	
  	IntentFilter startfilter = new IntentFilter();
  	startfilter.addAction("com.ems.falldetect.start_bluetooth_server");
		registerReceiver(mServerReceiver, startfilter);
  	
  	IntentFilter stopFilter = new IntentFilter();
  	stopFilter.addAction("com.ems.falldetect.stop_bluetooth_server");
  	registerReceiver(mServerReceiver, stopFilter);
  	
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) 
  {
  		super.onStartCommand(intent, flags, startId);
  		
  		Log.d(TAG, "BluetoothStateListenerService started");
  		
  		if(BluetoothAdapter.getDefaultAdapter().isEnabled()) {
  			
  			
  			
  			//If bluetooth is already enabled, ask for discoverability permissions
  			Intent btAlertActivityIntent = new Intent(getApplicationContext(),BluetoothAlertActivity.class);
				btAlertActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(btAlertActivityIntent);
  			
  		}
  		
      // We want this service to continue running until it is explicitly
      // stopped, so return sticky.
      return START_STICKY;
  }
	
  @Override
  public void onDestroy() {
      
  		Log.d(TAG, "Stopping BluetoothStateListenerService");
  	
  		unregisterReceiver(mServerReceiver);
  		unregisterReceiver(mBluetoothStateReceiver);
      
      super.onDestroy();
  }
  
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	
	
}
