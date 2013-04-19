package com.ems.falldetect;

import com.ems.falldetect.bluetooth.BluetoothStateListenerService;
import android.os.Bundle;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {
	
	private static final String TAG = "FallDetector";
	
	Button mStart, mStop;
	
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
	   
		@Override
	   public void onReceive(Context context, Intent intent) {
	  	 
	      String action = intent.getAction();
	      if(action.equals("com.ems.falldetect.alert_sent")){
	        
	      	Log.d(TAG, "Received com.ems.falldetect.alert_sent");
	      		      	
	      	MainActivity.this.finish();
	      		
	      }
	      
	   }
		
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mStart = (Button) findViewById(R.id.startService);
		mStop = (Button) findViewById(R.id.stopService);
		
		String action = getIntent().getAction();
		if(action.endsWith("com.ems.falldetect.VIEW_SERVICE_STATUS")) {
			
			mStart.setEnabled(false);
			mStop.setEnabled(true);
			
		}
		
		mStart.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				mStart.setEnabled(false);
				Intent serviceIntent = new Intent(MainActivity.this, FallDetectionService.class);
				MainActivity.this.startService(serviceIntent);
				mStop.setEnabled(true);
				
			}
			
		});
		
		mStop.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				mStop.setEnabled(false);
				Intent serviceIntent = new Intent(MainActivity.this, FallDetectionService.class);
				MainActivity.this.stopService(serviceIntent);
				mStart.setEnabled(true);
				
			}
			
		});
		
		//Start the BluetoothStateListenerService, if not started already
		Log.d(TAG, "Trying to start BluetoothStateListenerService");
		Intent svcIntent = new Intent(this, BluetoothStateListenerService.class);
		startService(svcIntent);
		
		IntentFilter filter = new IntentFilter();
		filter.addAction("com.ems.falldetect.alert_sent");
		
		registerReceiver(mReceiver, filter);
		
	}
	
	@Override
	protected void onDestroy() {
		
  	Log.d(TAG, "MainActivity: onDestroy()");
		
		unregisterReceiver(mReceiver);
				
		super.onDestroy();
		
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    
			switch(item.getItemId()) {
	    
	    	case R.id.action_settings:
	        Intent intent = new Intent(this, SettingsActivity.class);
	        this.startActivity(intent);
	        break;
	    	
	    	default:
	        return super.onOptionsItemSelected(item);
	    }

	    return true;
	}

}
