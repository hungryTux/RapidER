package com.ems.falldetect.message;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.ems.falldetect.LocationUpdateReceiver;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import android.content.Context;
import android.location.Location;
import android.util.Log;

public class MessageUtil {
	
	private static final String TAG = "FallDetector";
	
	public static final String END_OF_MSG = "END";
	
	private Context mContext;
	
	public MessageUtil(Context ctx) {
		mContext = ctx;
	}
	
	public String buildMessage(Location locationOfEmergency, long msgId){
				
		JSONArray locationArr = new JSONArray();
		
		Double curr_latitude = -1.0;
		Double curr_longitude = -1.0;
		
		try {
		
			if(locationOfEmergency != null) {
		
				JSONObject locationObj = new JSONObject();
			
				curr_latitude = locationOfEmergency.getLatitude();
				curr_longitude = locationOfEmergency.getLongitude();
		
				locationObj.put("timestamp", "null");
				locationObj.put("latitude", curr_latitude);
				locationObj.put("longitude", curr_longitude);
				locationObj.put("accuracy", locationOfEmergency.getAccuracy());
				locationObj.put("id", msgId);
			
				locationArr.put(locationObj);
			
			}
		
			//Read the location info file and dump locations
			try {
			
				FileInputStream fis = mContext.openFileInput(LocationUpdateReceiver.LOCATION_INFO_FILE);
				String line = null;
				
				BufferedReader br = new BufferedReader(new InputStreamReader(fis));
				
				int numLocations = 0;
				
				while((line = br.readLine()) != null && numLocations < 5) {
					
					String[] tokens = line.split("|");
					
					String timestamp = tokens[0];
					String[] location_coordinates = tokens[1].split(",");
					double latitude = Double.parseDouble(location_coordinates[0]);
					double longitude = Double.parseDouble(location_coordinates[1]);
					double accuracy = Double.parseDouble(tokens[2]);

					if((curr_latitude != latitude) && (curr_longitude != longitude)) {
							
							//Only if the location is different from location of emergency
							JSONObject old_locationObj = new JSONObject();
							
							old_locationObj.put("timestamp", timestamp);
							old_locationObj.put("latitude", latitude);
							old_locationObj.put("longitude", longitude);
							old_locationObj.put("accuracy", accuracy);
							old_locationObj.put("id", msgId);
						
							locationArr.put(old_locationObj);
							
							
							//Put at most 5 previous locations
							numLocations++;
					}
					
				}
				
				fis.close();
				br.close();
			
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
						
		} catch (JSONException e) {
			
			Log.e(TAG, e.getMessage());
		
		}
		
		Log.d(TAG, "Built Message - "+locationArr.toString());
		return locationArr.toString();
		
	}
	
	public List<UserLocation> parseMessage(String jsonMsg){
		
		Gson gson = new Gson();
		Type listType = new TypeToken<List<UserLocation>>(){}.getType();
		
		
		List<UserLocation> list = gson.fromJson(jsonMsg, listType);
		
		return list;
		
	}
	

}
