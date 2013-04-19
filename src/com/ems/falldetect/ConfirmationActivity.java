package com.ems.falldetect;

import java.lang.ref.WeakReference;
import java.util.Formatter;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import com.ems.falldetect.bluetooth.BluetoothClientWrapper;
import com.ems.falldetect.bluetooth.BluetoothConstants;
import com.ems.falldetect.bluetooth.BluetoothStateListenerService;
import com.ems.falldetect.message.MessageUtil;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class ConfirmationActivity extends Activity {
	
	private static final String TAG = "FallDetector";
		
	//User may cancel the alert in 10 seconds
	private static int CANCEL_ALERT_TIMEOUT = 10;
	
	private Window mWindow;
	private Button mCancelButton;
	private TextView mRemainingTime;
	
	private Vibrator mVibrator;
	
	//Handler to update remaining time on the UI thread
	private final Handler mHandler = new Handler();
	
	private final Timer mTimer = new Timer();
	private boolean isTimedOut = false;
	
	CountdownTimerTask mCountDownTask;
	
	//Location specific
	private static final long MAX_AGE_NANOS = 5 * 60 * 1000000000L;
	private static final long MAX_AGE_MILLIS = 5 * 60 * 1000L;
	LocationManager mLocationManager;
	private final Criteria mCriteria = new Criteria();
	private Location mLocationOfEmergency;
	private long emergencyId;  //Uniquely identifies an emergency
	private String emergencyMessage;
	
	//Internet Connectivity specific
	private ConnectivityManager mConnectivityManager;
	
	//Bluetooth specific
	private ConcurrentHashMap<String, BluetoothClientWrapper> mBluetoothDevices = new ConcurrentHashMap<String, BluetoothClientWrapper>(20);
	private static BTConnectionStatusHandler connStatusHandler;
	private int trials = 0;
	private int numDelivered = 0;
	
	private BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context context, Intent intent) {
			
			String stateExtra = BluetoothAdapter.EXTRA_STATE; 
			int currState = intent.getIntExtra(stateExtra, -1); 
			
			String state = null;
			
			switch(currState) {
			
				case BluetoothAdapter.STATE_TURNING_ON:
					state="Bluetooth turning on..";
					break;
					
				case BluetoothAdapter.STATE_ON:
					state="Bluetooth is now on!";
					//Start discovery of peer devices
					BluetoothClientWrapper.startDiscovery(ConfirmationActivity.this.getApplicationContext(), mBluetoothDiscoveryResultReceiver, false);
					break;
					
				case BluetoothAdapter.STATE_TURNING_OFF:
					state="Bluetooth turning off..";
					break;
					
				case BluetoothAdapter.STATE_OFF:
					state="Bluetooth is now off!";
					break;
			
				default:
					break;
					
			}
			
			Log.d(TAG, state);
			
		}
		
	};
	
	private BroadcastReceiver mBluetoothDiscoveryResultReceiver = new BroadcastReceiver() {

		
		
		@Override
		public void onReceive(Context context, Intent intent) {
			
			if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
			
				String remoteDeviceName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
				BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			
				Log.d(TAG, "Discovered bluetooth device - "+remoteDeviceName);
			
				//Add the newly discovered device to the list and 
				//try to connect to it. We may have multiple parallel
				//connections		
				if(mBluetoothDevices.get(remoteDeviceName) == null) {
							
							//Create a new client to work with each device
							BluetoothClientWrapper btClient = new BluetoothClientWrapper(ConfirmationActivity.connStatusHandler);
							mBluetoothDevices.put(remoteDeviceName, btClient);
							Log.d(TAG, "Establishing connection with "+remoteDeviceName);
							btClient.establishConnectionWithDevice(remoteDeviceName, remoteDevice.getAddress());
						
				}
				
				Log.d(TAG, "Size of Map="+mBluetoothDevices.size());
				
			} else if(intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
				
				Log.d(TAG, "Bluetooth Discovery started");
				
			} else if(intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
				
				Log.d(TAG, "Bluetooth Discovery finished");
				
				//If we haven't delivered to at least 5 devices so far, 
				//initiate discovery again, but try only 15 times
				if(trials < 15 && numDelivered < 5) {
				
					BluetoothClientWrapper.startDiscovery(ConfirmationActivity.this.getApplicationContext(), this, true);
					//trials++;
					
				} else {
					
					//Trials may have exceeded 15
					//Give up!!
					Log.d(TAG, "Giving up after trying 15 times!");
					BluetoothClientWrapper.stopDiscovery(ConfirmationActivity.this.getApplicationContext());
					BluetoothClientWrapper.disableBluetooth(ConfirmationActivity.this.getApplicationContext());
					
				  //Restart BTStateListener service.
					//We want this device to start acting as a server now
					Intent btStateListenerService = new Intent(getApplicationContext(),BluetoothStateListenerService.class);
					getApplicationContext().startService(btStateListenerService);
					
					
					ConfirmationActivity.this.getApplicationContext().deleteFile(LocationUpdateReceiver.LOCATION_INFO_FILE); 
					ConfirmationActivity.this.finish();
				
				}
				
			}
			
			
		}
		
	};
	
	
	@Override
  protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_confirmation);
		
		Intent intent = getIntent();
		CANCEL_ALERT_TIMEOUT = intent.getIntExtra("sos_timeout", 10);
		
		this.setFinishOnTouchOutside(false);
		
		mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		
		mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		mCriteria.setAccuracy(Criteria.ACCURACY_FINE);
		mCriteria.setPowerRequirement(Criteria.POWER_LOW);
		mCriteria.setAltitudeRequired(false);
		mCriteria.setBearingRequired(false);
		mCriteria.setSpeedRequired(false);
		mCriteria.setCostAllowed(true);
		
		connStatusHandler = new BTConnectionStatusHandler(this);
				
		mRemainingTime = (TextView) findViewById(R.id.remainingTime);
		mCancelButton = (Button) findViewById(R.id.cancelAlert);
		
		mCancelButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
			
				ConfirmationActivity.this.finish();
				
			}
			
		});
		
	}
	
	@Override
  public void onStart() {
		
		super.onStart();
		
		if(!isTimedOut) {
			mCountDownTask = new CountdownTimerTask();
		
			mTimer.scheduleAtFixedRate(mCountDownTask, 1000, 1000);
			long[] pattern = { 100, 200 };
			int repeat = 0;
			mVibrator.vibrate(pattern, repeat);
		}
		
	}
	
	@Override
  protected void onResume() {
		
		super.onResume();
		
		mWindow = this.getWindow();
		mWindow.addFlags(LayoutParams.FLAG_DISMISS_KEYGUARD);
		mWindow.addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		mWindow.addFlags(LayoutParams.FLAG_TURN_SCREEN_ON);
		
		
	}
	
	
	@Override
  protected void onDestroy() {
    
    cleanUp();
    
    super.onDestroy();
    
  }
	
	
	private void sendSOS() {
		
		cleanUp();
		
		Toast.makeText(this, "Sending Alert..", Toast.LENGTH_LONG).show();
		
	  /*
	   * Fill out mLocationOfEmergency here so we can attempt to send it
	   * We have to get the location as quickly as possible. Here's our strategy:
				
			(1) First we try to fetch the last recorded location from our
					own LocationUpdateReceiver class. This location is accurate
					upto 100 mts of distance and upto 5 mins of time.
			(2) If we receive NULL location from (1) or if we receive
			 		a location older than 5 mins, the user had no location
			 		providers(GPS/Wifi/Network) enabled in the last 5 mins. 
			 		As a last effort, we see if we can get better coordinates
			 		from any of these providers may have been now. If yes, 
			 		we get the lastKnownLocation from that provider and check
			 		that it is more recent. 
			(3) If step(2) also doesn't provide us with a location, the best we can
					do is alert peers in the vicinity of an emergency in a certain
					radius, and also provide the stale locations that we have recorded
					so far(if any).
		*/
		mLocationOfEmergency = LocationUpdateReceiver.getLastRecordedLocation();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
			
			long currTime = SystemClock.elapsedRealtime();
			
			if(mLocationOfEmergency == null ||
					(currTime - (mLocationOfEmergency.getTime())) > MAX_AGE_MILLIS){
				
				Location location = null;
				String bestAvailableProvider = mLocationManager.getBestProvider(mCriteria, true);
				location = mLocationManager.getLastKnownLocation(bestAvailableProvider);
			
				long currTimeInMillis = SystemClock.elapsedRealtime();
				if ((currTimeInMillis - (location.getTime())) > MAX_AGE_MILLIS) {
					//This location is less than 5 mins old and more recent.
					mLocationOfEmergency = location;
				}
				
			}
			
		} else {
			
			long currTime = SystemClock.elapsedRealtimeNanos();
			if(mLocationOfEmergency == null || 
					(currTime-mLocationOfEmergency.getElapsedRealtimeNanos())>MAX_AGE_NANOS) {
				
				Location location = null;
				String bestAvailableProvider = mLocationManager.getBestProvider(mCriteria, true);
				location = mLocationManager.getLastKnownLocation(bestAvailableProvider);
			
				long currTimeInNano = SystemClock.elapsedRealtimeNanos();
				if(currTimeInNano-location.getElapsedRealtimeNanos() <= MAX_AGE_NANOS) {
					//This location is less than 5 mins old and more recent.
					mLocationOfEmergency = location;
				}
				
			}
			
		}
		
		
		
		//This broadcast stops fall detection service, while we attempt
		//to send location info. <Battery Saver>
		Intent alerted = new Intent("com.ems.falldetect.alert_sent");
		sendBroadcast(alerted);
		
		if(mLocationOfEmergency == null) {
			
			
			
		} else {
		
			//Stop BTStateListener service.
			//We don't want this device to act as a server anymore
			Intent btStateListenerService = new Intent(this,BluetoothStateListenerService.class);
			getApplicationContext().stopService(btStateListenerService);
			
			//If we have found a good location estimate,
			//1) Send it to server through HTTP,  if we are connected
			
			emergencyId = System.currentTimeMillis();
			
			emergencyMessage = new MessageUtil(getApplicationContext()).buildMessage(mLocationOfEmergency,emergencyId);
			
			NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
			boolean isConnected = (activeNetwork != null) && activeNetwork.isConnectedOrConnecting();
		
			if(isConnected) {
				
				new HttpPostTask().execute(emergencyMessage);
				
			}
		
			//2) Send it to peers through Bluetooth
			int ret = BluetoothClientWrapper.enableBluetooth(ConfirmationActivity.this.getApplicationContext(), mBluetoothStateReceiver);
			
			if(ret > 0) {
				//Bluetooth is already enabled
				BluetoothClientWrapper.startDiscovery(ConfirmationActivity.this.getApplicationContext(), mBluetoothDiscoveryResultReceiver, false);
			}
		
		}
	}
	
	private void cleanUp() {
    mTimer.cancel();
    mCountDownTask.cancel();
    mVibrator.cancel();
  }
	
	
	private class CountdownTimerTask extends TimerTask {

		private long startTime;
		private String remTime;
		
		
		private StringBuilder sFormatBuilder = new StringBuilder();
	  private Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
	  private final Object[] sTimeArgs = new Object[5];
	  
	  public CountdownTimerTask() {
	  	
	  	super();
	  	
	  	startTime = CANCEL_ALERT_TIMEOUT;
	  	remTime = makeTimeString(startTime);
	  	
	  	updateTime();
	  
	  }
	  
	  
	  
	  public String makeTimeString(long secs) {
	  	
	      String durationformat = ConfirmationActivity.this.getString(R.string.durationformat);
	  		
	      /* Provide multiple arguments so the format can be changed easily
	       * by modifying the xml.
	       */
	      sFormatBuilder.setLength(0);

	      final Object[] timeArgs = sTimeArgs;
	      timeArgs[0] = secs / 3600;
	      timeArgs[1] = secs / 60;
	      timeArgs[2] = (secs / 60) % 60;
	      timeArgs[3] = secs;
	      timeArgs[4] = secs % 60;

	      return sFormatter.format(durationformat, timeArgs).toString();
	  }
		
		@Override
		public void run() {
			
			startTime--;
			
			remTime = makeTimeString(startTime);
			updateTime();
			
			if(startTime <= 0) {
				
					ConfirmationActivity.this.isTimedOut = true;
				
				 	mHandler.post(new Runnable() {
	          public void run() {
	            sendSOS();
	          }
	        });
				
			}
			
		}
		
		private void updateTime() {
			
      mHandler.post(new Runnable() {
        public void run() {
          mRemainingTime.setText(remTime+" secs");
        }
      });
      
    }
		
			
	}
	
		
	//Handles status of connections with remote bluetooth devices
	static class BTConnectionStatusHandler extends Handler {
		
		//Keep a reference of activity, in case we want
		//to update UI to reflect status of message delivery
		WeakReference<ConfirmationActivity> myActivity;
		  	
		BTConnectionStatusHandler(ConfirmationActivity mActivity) {
  		
  		myActivity = new WeakReference<ConfirmationActivity>(mActivity); 
  		
  	}
		
		public synchronized void handleMessage (Message m) {
			
			super.handleMessage(m);
			
			Bundle data = null;
			
			switch(m.what) {
			
				case BluetoothConstants.STATUS_CONNECTION_ERROR:
				case BluetoothConstants.STATUS_CONNECTION_LOST_ERROR:
					data = m.getData();
					Log.i(TAG, "Handler: Received conn error("+m.what+") for device-"+data.getString("name"));

					String remoteErroredDevice = data.getString("name");
					BluetoothClientWrapper erroredBtClient =  myActivity.get().mBluetoothDevices.get(remoteErroredDevice);
					erroredBtClient.cancelConnectionAttemptWithDevice(remoteErroredDevice);
					
					myActivity.get().mBluetoothDevices.remove(remoteErroredDevice);
					
					break;
					
				case BluetoothConstants.STATUS_CONNECTED:
					data = m.getData();
					String remoteConnectedDevice = data.getString("name");
					BluetoothClientWrapper connectedBtClient =  myActivity.get().mBluetoothDevices.get(remoteConnectedDevice);
					
					Log.d(TAG, "Sending SOS to "+remoteConnectedDevice);
					
					//Send mLocationOfEmergency data to remote device
					MessageUtil msgUtil = new MessageUtil(myActivity.get());
					if(myActivity.get().mLocationOfEmergency != null) {
						
						Log.d(TAG, "Sending non-null location of emergency");
						
						
						//Build Message
						StringBuffer strToTransmit = new StringBuffer(myActivity.get().emergencyMessage);
						
						connectedBtClient.sendDataToDevice(remoteConnectedDevice, strToTransmit.toString());
						connectedBtClient.sendDataToDevice(remoteConnectedDevice, MessageUtil.END_OF_MSG);
						
					} else {
						//If we don't have the location info, send the location info
						//from the file we have been saving to so far
						Log.d(TAG, "Null Location of Emergency");
						
						//Build Message
						StringBuffer strToTransmit = new StringBuffer(msgUtil.buildMessage(null, myActivity.get().emergencyId));
						
						connectedBtClient.sendDataToDevice(remoteConnectedDevice, strToTransmit.toString());
						connectedBtClient.sendDataToDevice(remoteConnectedDevice, MessageUtil.END_OF_MSG);
						
					}
					break;
					
				case BluetoothConstants.STATUS_DATA_AVAILABLE:
					//Finish this activity only when SOS has been sent 
					//successfully(which is most definitely an asynchronous event)
					
					//Check if the remote device has sent the SOS info
					//if YES, finish
					//else Try out the next device
					data = m.getData();
					String respondingRemoteDevice = data.getString("name");
					BluetoothClientWrapper respondingBTClient = myActivity.get().mBluetoothDevices.get(respondingRemoteDevice);
					Log.i(TAG, "Message from device-"+respondingRemoteDevice+":"+data.getString("data"));
					
					respondingBTClient.closeConnectionWithDevice(respondingRemoteDevice); 					
					myActivity.get().numDelivered++;
					
					//NOTE: Do not remove this device from the HashMap, so that we
					//don't try to contact it again with duplicate SOS
					
					break;
			
				default:
					break;
					
			}
			
			if(m.what == BluetoothConstants.STATUS_CONNECTION_ERROR ||
					m.what == BluetoothConstants.STATUS_CONNECTION_LOST_ERROR) {
			
				//One of the remote devices didn't respond. Keep trying!
				
				if(myActivity.get().trials < 15 && myActivity.get().numDelivered < 5) {
				
					BluetoothClientWrapper.startDiscovery(myActivity.get().getApplicationContext(), myActivity.get().mBluetoothDiscoveryResultReceiver, true);
					myActivity.get().trials++;
				
				} else {
				
					//Trials may have exceeded 15
					//Give up!!
					Log.d(TAG, "Done trying 15 times. Giving up!");
					BluetoothClientWrapper.stopDiscovery(myActivity.get().getApplicationContext());
					BluetoothClientWrapper.disableBluetooth(myActivity.get().getApplicationContext());
					
				  //Restart BTStateListener service.
					//We want this device to start acting as a server now
					Intent btStateListenerService = new Intent(myActivity.get().getApplicationContext(),BluetoothStateListenerService.class);
					myActivity.get().getApplicationContext().startService(btStateListenerService);
					
					myActivity.get().getApplicationContext().deleteFile(LocationUpdateReceiver.LOCATION_INFO_FILE); 
					myActivity.get().finish();
			
				}
				
			}
			
		}
		
		
	}
	
	

}
