package com.ems.falldetect.heartmonitor;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;

import ca.uol.aig.fftpack.RealDoubleFFT;

public class HeartMonitorThread extends Thread {
	
	Handler mHandler;
	
	private static final int SAMPLE_RATE = 44100;
	private static final int CHANNEL_TYPE = AudioFormat.CHANNEL_IN_MONO;
	private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	
	private int bufferSize;
	
	private RealDoubleFFT mTransformer;
	
	private AudioRecord mAudioRecord;
	
	public HeartMonitorThread(Handler dataHandler){
		
		mHandler = dataHandler;
		
		bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_TYPE, ENCODING);
	
		mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, 
				CHANNEL_TYPE, ENCODING, bufferSize);
	
	}
	
	@Override
	public void run(){
		
		
		
	}

}
