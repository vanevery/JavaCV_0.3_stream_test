package com.example.javacv.stream.test2;

//import com.googlecode.javacv.FFmpegFrameRecorder;
import com.googlecode.javacv.FrameGrabber;
import com.googlecode.javacv.Frame;
import com.googlecode.javacv.FFmpegFrameGrabber;
import com.googlecode.javacv.FrameGrabber.Exception;
//import com.googlecode.javacv.FrameRecorder;
import com.googlecode.javacv.cpp.avcodec;

import android.app.Activity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class PoorMansTranscoder extends Activity {
	private final static String LOG_TAG = "PoorMansTranscoder";
	Button btnRecorderControl;
	OnClickListener mControlAction;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    	mControlAction = new OnClickListener() {
    		@Override
    		public void onClick(View v) {
    	        try {

    	        	FrameGrabber grabber = new FFmpegFrameGrabber("/mnt/sdcard/input.mp4");			
    				grabber.start();
    				
    				MyFrameRecorder recorder = new MyFFmpegFrameRecorder("/mnt/sdcard/output.mp4", grabber.getImageWidth(), grabber.getImageHeight(), 1);
    				recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
    				recorder.setFrameRate(grabber.getFrameRate());
    				//recorder.setSampleFormat(grabber.getSampleFormat());
    				recorder.setSampleRate(grabber.getSampleRate());
    	        	recorder.start();
    	        	
    				Frame frame;
    	        	while ((frame = grabber.grabFrame()) != null) {
    	        		Log.v(LOG_TAG,"Frame #: " + grabber.getFrameNumber());
    	            	recorder.record(frame);
    	        	}
    	        	recorder.stop();
    	        	grabber.stop();
    	        } catch (MyFrameRecorder.Exception e) {
    	        //} catch (com.googlecode.javacv.FrameRecorder.Exception e) {
    				e.printStackTrace();
    			} catch (Exception e) {
    				e.printStackTrace();
    			}
    	        
    		}
    	};        
        
		/* add control button: start and stop */
		btnRecorderControl = (Button) findViewById(R.id.recorder_control);
		btnRecorderControl.setOnClickListener(mControlAction);
    }	
}