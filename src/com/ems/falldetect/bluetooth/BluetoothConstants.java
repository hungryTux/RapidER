package com.ems.falldetect.bluetooth;

public interface BluetoothConstants {
	
	//This contains constants to indicate state of connection
	//with a remote device
	public static final int STATUS_CONNECTION_ERROR = -100;
	public static final int STATUS_CONNECTION_LOST_ERROR = -200;

	public static final int STATUS_CONNECTED = 100;
	public static final int STATUS_DATA_AVAILABLE = 200;

	public static final String MSG_SUCCESS_STR = "SOS will be sent immediately";
	public static final String MSG_FAILURE_STR = "No Connectivity. SOS will be sent ASAP";
	
}
