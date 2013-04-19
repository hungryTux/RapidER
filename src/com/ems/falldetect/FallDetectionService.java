package com.ems.falldetect;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;


public class FallDetectionService extends Service implements 
OnSharedPreferenceChangeListener {
	
	private static final String TAG = "FallDetector";
	
	//User Preferences
	private static final String PREF_FALL_TIMEOUT = "PREF_FALL_TIMEOUT";
	private static final String PREF_SOS_TIMEOUT = "PREF_SOS_TIMEOUT";
	private static final String PREF_SENSITIVITY = "PREF_SENSITIVITY";
	private static final String PREF_LOCATION_TRACKING = "PREF_LOCATION_TRACKING";
	public int fallTimeout = 5;
	public int sosTimeout = 10;
	public boolean low_sensitivity = false;
	public boolean location_tracking = true;
	private SharedPreferences mPrefs;
	
	//Constants for Fall Detection Algorithm
	private static double LOWER_THRESHOLD = 5.0;
	private static final double UPPER_THRESHOLD = 15.0;
	private static final double GRAVITATIONAL_PULL = 9.81;
	
	private static long WAIT_TIME = 5000;
	private static final long SETTLE_TIME = 1500;
	
	//Specific to Notification Bar
	private static final int SERVICE_STATUS = 1;
	NotificationManager mNotificationManager;
	
	
	//Specific to Location Updates
	private final Criteria mCriteria = new Criteria();
	private LocationManager mLocationManager;
	private PendingIntent mLocationUpdatePendingIntent;
	private final static long MIN_UPDATE_TIME = 5 * 60 * 1000L;
	private final static int MIN_UPDATE_DISTANCE = 0;
	
	
	//Specific to Sensing(Accelerometer)
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	
	
	private enum State{
		
		STATE_INIT,
		STATE_FREE_FALL,
		STATE_HIT_GROUND,
		STATE_MOTIONLESS
		
	};
	
	private State mCurrentState;
	
	private int lower_threshold_counter;
	private long hit_ground_time = -1;
	private long motionless_state_start_time = -1;
	
	private SensorEventListener mEventListener = new SensorEventListener() {

		@Override
		public void onAccuracyChanged(Sensor arg0, int arg1) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			
			double acc_x = event.values[0];
      double acc_y = event.values[1];
      double acc_z = event.values[2];
      
      if(mCurrentState == State.STATE_HIT_GROUND &&
      		(System.currentTimeMillis() - hit_ground_time) < SETTLE_TIME) {
      	
      	//Give 1.5 secs to let the device settle, after falling on the ground
      	return;
      	
      }
      
      double acc_result = Math.sqrt(
      		Math.pow(acc_x, 2.0) + Math.pow(acc_y, 2.0) + Math.pow(acc_z, 2.0));
      
      
      if (((acc_result >= (0.9 * GRAVITATIONAL_PULL)) && 
      		 (acc_result <= (1.2 * GRAVITATIONAL_PULL))) &&
      		 (mCurrentState == State.STATE_HIT_GROUND ||
      		 mCurrentState == State.STATE_MOTIONLESS)) {
      	
      	//Device is stationary after hitting the ground
      	
      	if(motionless_state_start_time > 0) {
      		
      		if((System.currentTimeMillis() - motionless_state_start_time) > WAIT_TIME) {
      			
      			Log.d(TAG, "~~~~~~~ Motionless for 5 secs after hitting ground ~~~~~~");
      			
      			 Intent alertIntent = new Intent(FallDetectionService.this, 
      					 ConfirmationActivity.class);
      			 alertIntent.putExtra("sos_timeout", FallDetectionService.this.sosTimeout);
      			 alertIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      			 FallDetectionService.this.startActivity(alertIntent);
      			
      			
      			//TODO: Remove this after debugging maybe
      			mCurrentState = State.STATE_INIT;
      			motionless_state_start_time = -1;
      		} 
      		
      	} else {
      		
    			Log.d(TAG, "~~~~~~~ Motionless right after hitting ground. Recording time ~~~~~~~");
      		motionless_state_start_time = System.currentTimeMillis();
      		mCurrentState = State.STATE_MOTIONLESS;
      		hit_ground_time = -1;
      	
      	}
      	
      } else {
      
      	if (acc_result < LOWER_THRESHOLD) {
      	
      		//Device is in free fall
      	
      		if(lower_threshold_counter > 5 && mCurrentState == State.STATE_INIT) {
      	
      			Log.d(TAG, "~~~~~~~ Free Fall ~~~~~~");
      			mCurrentState = State.STATE_FREE_FALL;
      	  
      		} else {
      	
      			lower_threshold_counter++;
      	
      		}
      	
      	} else if (acc_result > UPPER_THRESHOLD) {
      	
      		//Device hit the ground hard!!
      		
      		if(mCurrentState == State.STATE_FREE_FALL) {
      			
      			Log.d(TAG, "~~~~~~~ Hit Ground ~~~~~~");
      			hit_ground_time = System.currentTimeMillis();
      			
      			mCurrentState = State.STATE_HIT_GROUND;
      			lower_threshold_counter = 0;
      			
      		}
      		
      	} else {
      	 	      		
      		if (mCurrentState == State.STATE_MOTIONLESS) {
      		
      			//Device may have been picked up after the fall
      			//All's OK!
      		
      			Log.d(TAG, "~~~~~~~ All's OK! Resetting to Init State. Acceleration:"+acc_result+" ~~~~~~");
      			mCurrentState = State.STATE_INIT;
      			motionless_state_start_time = -1;
      		
      		}
      	
      	}
      
      }
			
		}
		
		
		
	};
	
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
	   
		@Override
	   public void onReceive(Context context, Intent intent) {
	  	 
	      String action = intent.getAction();
	      if(action.equals("com.ems.falldetect.alert_sent")){
	      	
	      	FallDetectionService.this.stopSelf();
	      		
	      }
	      
	   }
		
	};
	
	private final BroadcastReceiver mLocationProviderDisabledReceiver = 
		new BroadcastReceiver(){

			@Override
			public void onReceive(Context context, Intent intent) {
				
				boolean providerDisabled = !intent.getBooleanExtra(LocationManager.KEY_PROVIDER_ENABLED, false);
	      
				// Look for the next best location provider and register
				// for updates from that provider
				
	      if (providerDisabled) {
	      
	  			Log.d(TAG, "Current location provider disabled");
	      	
	      	disableLocationUpdates();
	      	
	      	enableLocationUpdates();
	        
	      }	
				
			}
		
	};
	
	private final LocationListener bestInactiveLocationProviderListener = new LocationListener() {
    
		public void onLocationChanged(Location l) {}
    
		public void onProviderDisabled(String provider) {}
    
		public void onStatusChanged(String provider, int status, Bundle extras) {}
    
    public void onProviderEnabled(String provider) {
    
			Log.d(TAG, "Better Location provider-"+provider+" enabled!!");
    	
    	disableLocationUpdates();
    	
    	// Re-register the location listeners using the better Location Provider.
      enableLocationUpdates();
    
    }
    
  };
	
	
	public void onCreate() {
		
		super.onCreate();
		
		//Listen for change in user preferences
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		mPrefs.registerOnSharedPreferenceChangeListener(this);
		
		mNotificationManager = 
			(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		
		
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
		
		mSensorManager.registerListener(mEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
		
		showNotification();
		
		IntentFilter filter = new IntentFilter();
		filter.addAction("com.ems.falldetect.alert_sent");
		registerReceiver(mReceiver, filter);
		
		
		
		//Initialize handles for Location updates
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		Intent intent = new Intent(FallDetectionService.this, LocationUpdateReceiver.class);
		mLocationUpdatePendingIntent = 
				PendingIntent.getBroadcast(FallDetectionService.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		mCriteria.setAccuracy(Criteria.ACCURACY_FINE);
		mCriteria.setPowerRequirement(Criteria.POWER_LOW);
		mCriteria.setAltitudeRequired(false);
		mCriteria.setBearingRequired(false);
		mCriteria.setSpeedRequired(false);
		mCriteria.setCostAllowed(true);
			
		enableLocationUpdates();
		this.location_tracking = true;
		
	}
	
	public int onStartCommand(Intent intent, int flags, int startId) {
    
		Log.d(TAG, "~~~~~~ Starting Fall Detection Service ~~~~~~");
		
		//Set thresholds based on user preferences
		this.fallTimeout = Integer.parseInt(mPrefs.getString(PREF_FALL_TIMEOUT, "5"));
		WAIT_TIME = this.fallTimeout*1000;
		
		this.sosTimeout = Integer.parseInt(mPrefs.getString(PREF_SOS_TIMEOUT, "10"));
		
		int sensitivity_setting = Integer.parseInt(mPrefs.getString(PREF_SENSITIVITY, "0"));
		
		if(sensitivity_setting != 0){
			LOWER_THRESHOLD = 6.0;
		} else {
			LOWER_THRESHOLD = 5.0;
		}
		
		//If the user disabled location updates before starting service
		//disable them. By default we enable them
		boolean enableLocationUpdates = mPrefs.getBoolean(PREF_LOCATION_TRACKING, true);
		if(this.location_tracking == false && enableLocationUpdates==true){
			Toast.makeText(this, "This service tracks your location at regular intervals for your own safety.", Toast.LENGTH_LONG).show();
			enableLocationUpdates();
			this.location_tracking = true;
		}	else if(this.location_tracking == true && enableLocationUpdates==false){
			disableLocationUpdates();
			this.location_tracking = false;
		}
		
		//Initialise State
		mCurrentState = State.STATE_INIT;
		
    return START_STICKY;

	}
	
	
	public void onDestroy() {
		
    Log.d(TAG, "FallDetectionService onDestroy");
    mSensorManager.unregisterListener(mEventListener, mAccelerometer);
    
    cancelNotification();
    
    if(this.location_tracking) {
    	disableLocationUpdates();
    	this.location_tracking = false;
    }
    
    unregisterReceiver(mReceiver);
    
    super.onDestroy();

  }
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		
		Log.d(TAG, "Updating Setting - "+key);
		
		if (key.equals(PREF_FALL_TIMEOUT)) {
			
			this.fallTimeout = Integer.parseInt(prefs.getString(key, "5"));
			WAIT_TIME = this.fallTimeout*1000;
			
		} else if (key.equals(PREF_SOS_TIMEOUT)) {
			
			this.sosTimeout = Integer.parseInt(prefs.getString(key, "10"));
			
		} else if (key.equals(PREF_SENSITIVITY)) {
			
			int value = Integer.parseInt(prefs.getString(PREF_SENSITIVITY, "0"));
			
			if(value != 0){
				LOWER_THRESHOLD = 6.0;
			} else {
				LOWER_THRESHOLD = 5.0;
			}
			
		} else if (key.equals(PREF_LOCATION_TRACKING)) {
			
			boolean enableLocationUpdates = prefs.getBoolean(key, true);
			
			if(this.location_tracking == false && enableLocationUpdates==true){
				enableLocationUpdates();
				this.location_tracking = true;
			}	else if(this.location_tracking == true && enableLocationUpdates==false){
				disableLocationUpdates();
				this.location_tracking = false;
			}
		}
		
	}
	
	private void enableLocationUpdates() {
				
		String locationProvider = mLocationManager.getBestProvider(mCriteria, true);
		
		if(locationProvider != null) {
						
			mLocationManager.requestLocationUpdates(locationProvider, 
					MIN_UPDATE_TIME, MIN_UPDATE_DISTANCE, mLocationUpdatePendingIntent);
			
			Log.d(TAG, "Enabled Location updates through provider-"+locationProvider);

			
		} else {
			
			Log.e(TAG, "No location provider found! Location will not be tracked");
			Toast.makeText(this, "No location provider found at the moment! You may need to allow location tracking in the device settings.", Toast.LENGTH_LONG).show();
			
		}
		
		//Listen for events where my current location provider is disabled
		IntentFilter intentFilter = new IntentFilter("com.ems.falldetect.location_provider_disabled");
	  registerReceiver(mLocationProviderDisabledReceiver, intentFilter);
	    
	  // Register a receiver that listens for when a better provider than I'm using becomes available.
	  String bestProvider = mLocationManager.getBestProvider(mCriteria, false);
	  String bestAvailableProvider = mLocationManager.getBestProvider(mCriteria, true);
			
	  if (bestProvider != null && !bestProvider.equals(bestAvailableProvider)) {
	      
	    	//There is a better provider for my criteria, but it is currently
	    	//disabled. Listen for status updates for that provider, so that if
	    	//it is enabled, we re-register using that provider
	    	
	    	mLocationManager.requestLocationUpdates(bestProvider, 
	      		0, 0, bestInactiveLocationProviderListener, getMainLooper());
	    	
	  }
		
		
		
	}
	
	private void disableLocationUpdates() {
		
		Log.d(TAG, "Disabled Location updates");

		if (mLocationProviderDisabledReceiver != null)
			unregisterReceiver(mLocationProviderDisabledReceiver);
		
		mLocationManager.removeUpdates(mLocationUpdatePendingIntent);
		mLocationManager.removeUpdates(bestInactiveLocationProviderListener);
		
	}
	
	
	private void showNotification() {
		
		RemoteViews statusView = 
			new RemoteViews(this.getPackageName(),R.layout.statusbar_main);
		
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
		
		notificationBuilder.setSmallIcon(R.drawable.ic_launcher);
		notificationBuilder.setTicker("Service started..");
		notificationBuilder.setWhen(System.currentTimeMillis());
		notificationBuilder.setOngoing(true);
		
		Intent intent = new 
			Intent("com.ems.falldetect.VIEW_SERVICE_STATUS").addFlags(
					Intent.FLAG_ACTIVITY_NEW_TASK);
    
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
		
		Notification notification = notificationBuilder.build();
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.contentIntent = pendingIntent;
		notification.contentView = statusView;
		
		mNotificationManager.notify(SERVICE_STATUS, notification);
		
	}
	
	private void cancelNotification() {
		
		mNotificationManager.cancel(SERVICE_STATUS);
		
	}
	
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	

}
