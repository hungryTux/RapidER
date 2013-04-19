package com.ems.falldetect;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class LocationUpdateReceiver extends BroadcastReceiver {

	private static final String TAG = "FallDetector";
	
	public static final String LOCATION_INFO_FILE = "fall_detect_location_info.txt";
	
	private static Location lastRecordedLocation;
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		String locationKey = LocationManager.KEY_LOCATION_CHANGED;
    String providerEnabledKey = LocationManager.KEY_PROVIDER_ENABLED;
		
    if(intent.hasExtra(providerEnabledKey)) {
    	
    	if (!intent.getBooleanExtra(providerEnabledKey, true)) {
    		
    		Intent providerDisabledIntent = new Intent("com.ems.falldetect.location_provider_disabled");
        context.sendBroadcast(providerDisabledIntent);
    		
    	}
    
    }
    
    if(intent.hasExtra(locationKey)) {
    	
    	Location location = (Location)intent.getExtras().get(locationKey);
    	
    	lastRecordedLocation = location;
    	
    	Log.d(TAG, "Received Location Update");
    
    	String info = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date())+
    			"|"+Double.toString(location.getLatitude())+","+Double.toString(location.getLongitude())+
    			"|"+Double.toString(location.getAccuracy());
    	
    	try {
    		
				FileOutputStream fos = context.getApplicationContext().openFileOutput(LOCATION_INFO_FILE, Context.MODE_PRIVATE | Context.MODE_APPEND);
				fos.write(info.getBytes());
				fos.write(System.getProperty("line.separator").getBytes());
				fos.close();
				
    	} catch (IOException e) {
			
    		Log.e(TAG, "Write to location file failed-"+e.getMessage());
			
    	}
    	
    	
    }
    

	}
	
	public static Location getLastRecordedLocation() {
		
		return lastRecordedLocation;
		
	}

}
