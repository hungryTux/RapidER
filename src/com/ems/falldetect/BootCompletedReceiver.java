package com.ems.falldetect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {

	private static final String TAG = "FallDetector";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			
			Log.d(TAG, "Boot Completed. Starting BluetoothStateListenerService");
			
			Intent i = new Intent();
			i.setAction("com.ems.falldetect.bluetooth.BluetoothStateListenerService");
			context.startService(i);
			
		}

	}

}
