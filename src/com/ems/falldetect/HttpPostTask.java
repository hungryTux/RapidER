package com.ems.falldetect;

import java.net.URI;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import android.os.AsyncTask;
import android.util.Log;

public class HttpPostTask extends AsyncTask<String, Integer, String> {

	private URI uri;
	
	@Override
	protected String doInBackground(String... data) {
		
		try {
			
			uri = new URI("http://192.168.1.111:8080/rapidER/webservice");
			
			DefaultHttpClient httpclient = new DefaultHttpClient();
			HttpPost httpPostRequest = new HttpPost(uri);
			
			Log.d("FallDetector", "Sending HTTP Post to "+httpPostRequest.getURI().toString());

			StringEntity se = new StringEntity(data[0], "UTF-8");
			
			//Set HTTP parameters
			httpPostRequest.setEntity(se);
			httpPostRequest.setHeader("Content-type", "application/json");
			
			httpclient.execute(httpPostRequest);
			
		} catch(Exception e) {
			
			
		}
		
		return null;
		
	}
	
	

}
