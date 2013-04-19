package com.ems.falldetect;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class BluetoothAlertActivity extends Activity {
	
	private static final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	private static final int REQUEST_CODE = 1234;
	private static final int REQUESTED_TIMEOUT = 0;
	
	private static final String TAG = "FallDetector";
	
	@Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_dummy);
  }
	
	@Override
	public void onResume(){
		super.onResume();
		
		if(mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
			
			Log.d(TAG, "Asking for discoverability permission");
			
			Intent discoverIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, REQUESTED_TIMEOUT);
			startActivityForResult(discoverIntent, REQUEST_CODE);
		
		} else {
			
			//Nothing to be done
			finish();
			
		} 
		
	}
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		
	  if (requestCode == REQUEST_CODE) {

	     if(resultCode == 1){      
	       
	 			Log.d(TAG, "Got discoverability permission");
	    	 
	    	 Intent startBTServerIntent = new Intent("com.ems.falldetect.start_bluetooth_server");
	   		 sendBroadcast(startBTServerIntent);
	    	 
	     } else if (resultCode == RESULT_CANCELED) {    
	       
	    	 //The user didn't give us the permission
	    	 
	     }
	     
	     finish();
	     
	  }
	  
	}
	
}
