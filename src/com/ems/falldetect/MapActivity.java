package com.ems.falldetect;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.Window;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager.LayoutParams;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;

public class MapActivity extends FragmentActivity {

	private static final String TAG = "FallDetector";
	
	private static final int REQUEST_DIALOG = 1;
	
	private Vibrator mVibrator;
	
	private Window mWindow;
	
	private boolean outOfFocus = false;
	
	//These will be passed in the SOS message
	private double mLatitude;
	private double mLongitude;
	private double mAccuracy;
	
	private LatLng mLastSeenPosition;
	
	private GoogleMap mGoogleMap;
	
	@Override
  protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_testmap);
      
      Intent intent = getIntent();
      mLongitude = intent.getDoubleExtra("longitude", 0.0);
      mLatitude = intent.getDoubleExtra("latitude", 0.0);
      mAccuracy = intent.getDoubleExtra("accuracy", 0.0);
      
      mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      
      // Getting status
      int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getBaseContext());

      // Showing status
      if(status==ConnectionResult.SUCCESS)
      {
          SupportMapFragment supportMapFragment = (SupportMapFragment) 
                  getSupportFragmentManager().findFragmentById(R.id.map);

          // Getting a reference to the map
          mGoogleMap = supportMapFragment.getMap();
          mGoogleMap.setMyLocationEnabled(true);
          
          
          mLastSeenPosition = new LatLng(mLatitude,mLongitude);
          
          //Add marker
          mGoogleMap.addMarker(new MarkerOptions().position(mLastSeenPosition).title("Last seen here"));
          
          
      } else {

          GooglePlayServicesUtil.getErrorDialog(status, this, REQUEST_DIALOG).show();
      }
  }
	
	@Override
	protected void onResume() {
		
		super.onResume();
		
		mWindow = this.getWindow();
		mWindow.addFlags(LayoutParams.FLAG_DISMISS_KEYGUARD);
		mWindow.addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		mWindow.addFlags(LayoutParams.FLAG_TURN_SCREEN_ON);
		
		final LatLngBounds.Builder bc = new LatLngBounds.Builder();
		
		bc.include(mLastSeenPosition);
		
		try {
			
	    mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bc.build(),50));
		
		} catch (IllegalStateException e) {
			
			final View mapView = getSupportFragmentManager().findFragmentById(R.id.map).getView();
		
			if (mapView.getViewTreeObserver().isAlive()) {
				
				mapView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener(){

					@SuppressWarnings("deprecation")
					@Override
					public void onGlobalLayout() {
						
						if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
              mapView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
						} else {
              mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
						}
          
						MapActivity.this.mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bc.build(), 50));
					
					}
					
				});
				
				
			}
			
		}
		
		AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
    dlgAlert.setMessage("An emergency seems to have been detected near you! Please try and provide assistance to the victim.");
    dlgAlert.setTitle("RapidER");
    dlgAlert.setPositiveButton("OK", null);
    dlgAlert.setCancelable(true);
    dlgAlert.create().show();
		
    outOfFocus = false;
	}
	
	@Override
	public void onStart() {
		
		super.onStart();
		
		if(!outOfFocus) {
			
			long[] pattern = { 100, 200, 100, 200, 100, 200, 100, 200, 100, 200 };
			int repeat = -1;
			mVibrator.vibrate(pattern, repeat);
			
		}
		
	}
	
	@Override
	public void onPause() {
		
		outOfFocus = true;
		
		mVibrator.cancel();
		
		super.onPause();
		
	}
	
	
	
}
