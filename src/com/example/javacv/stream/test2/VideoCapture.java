package com.example.javacv.stream.test2;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import com.googlecode.javacv.FFmpegFrameGrabber;
import com.googlecode.javacv.Frame;
import com.googlecode.javacv.FrameGrabber;
import com.googlecode.javacv.FrameGrabber.Exception;
import com.googlecode.javacv.cpp.avcodec;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class VideoCapture extends Activity implements OnClickListener, SurfaceHolder.Callback {

	public static final String LOGTAG = "VIDEOCAPTURE";

	private String ffmpeg_link = "rtmp://live:live@192.168.1.2:1935/live/test.flv";
	
	public static final int LOW_QUALITY = 0;
	private int recordingQuality = LOW_QUALITY;
	
	public static final int TARGET_WIDTH = 720;
	public static final int TARGET_HEIGHT = 480;
	public static final int LOW_TARGET_BITRATE = 700000;
	public static final int TARGET_FRAMERATE = 30;
		
	int videoWidth = 0;
	int videoHeight = 0;
	int videoFramerate = 0;
	int videoBitrate = LOW_TARGET_BITRATE;
	int videoEncoder = MediaRecorder.VideoEncoder.H263;
	int videoSource = MediaRecorder.VideoSource.DEFAULT;
	int videoFormat = MediaRecorder.OutputFormat.MPEG_4;	
	
	CamcorderProfile highQualityProfile;
	
	private MediaRecorder recorder;
	private SurfaceHolder holder;
	//private CamcorderProfile camcorderProfile;
	private Camera camera;	
	private Button startButton;
	private Button cancelButton;
	
	private SurfaceView cameraView;
	    
	boolean recording = false;
	boolean usecamera = true;
	boolean previewRunning = false;
	
	String filePath = "";
	
    Thread sendingThread;      

	    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	    recordingQuality = LOW_QUALITY;

		setContentView(R.layout.activity_video_capture);

		cameraView = (SurfaceView) findViewById(R.id.CameraView);
		
		holder = cameraView.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		cameraView.setClickable(true);
		cameraView.setOnClickListener(this);
		
		startButton = (Button) this.findViewById(R.id.StartButton);
		startButton.setOnClickListener(this);
		
		cancelButton = (Button) this.findViewById(R.id.CancelButton);
		cancelButton.setOnClickListener(this);
		
	}
	
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
      super.onConfigurationChanged(newConfig);
    }

	private void prepareRecorder() {
        recorder = new MediaRecorder();
		recorder.setPreviewDisplay(holder.getSurface());
		
		if (usecamera) {
			camera.unlock();
			recorder.setCamera(camera);
		}
		
		recorder.setVideoSource(videoSource);		
		recorder.setOutputFormat(videoFormat);
		
		//Looping through settings in SurfaceChanged
		Log.v(LOGTAG,"Size: " + videoWidth + " " + videoHeight);
		if (videoWidth > 0) {
			recorder.setVideoSize(videoWidth, videoHeight);

		} else {
			// This shouldn't ever happen
			Log.v(LOGTAG,"Please report an error with setVideoSize");
			recorder.setVideoSize(TARGET_WIDTH, TARGET_HEIGHT);
		}

		//Looping through settings in SurfaceChanged
		Log.v(LOGTAG,"Framerate: " + videoFramerate);
		if (videoFramerate > 0) {
			recorder.setVideoFrameRate(videoFramerate);
		} else {
			recorder.setVideoFrameRate(TARGET_FRAMERATE);
		}
		
		recorder.setVideoEncoder(videoEncoder);

		videoBitrate = LOW_TARGET_BITRATE;
		recorder.setVideoEncodingBitRate(videoBitrate);
		
		try {
			File newFile = File.createTempFile("localreport", ".mp4", Environment.getExternalStorageDirectory());
			filePath = newFile.getAbsolutePath();
			recorder.setOutputFile(filePath);
			recorder.prepare();			
		} catch (IllegalStateException e) {
			e.printStackTrace();
			finish();
		} catch (IOException e) {
			e.printStackTrace();
			finish();
		}
	}

	public void stopRecording() {
		if (recording) {
			recorder.stop();
			recorder.release();
			recording = false;
			Log.v(LOGTAG, "Recording Stopped");
			// Let's prepareRecorder so we can record again - NOPE
			//prepareRecorder();
		}
	}
	
	public void onClick(View v) {
		if (v == startButton || v == cameraView) {
			if (!recording) {
				recording = true;
				recorder.start();
				Log.v(LOGTAG, "Recording Started");
				startButton.setEnabled(false);

				
				
			}
		} else if (v == cancelButton) {
			sendingThread = new Thread(new SendingRunnable());
	        sendingThread.start();      
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceCreated");
		
		if (usecamera) {
			camera = Camera.open();
			
			Camera.Parameters cParameters = camera.getParameters();
			
			List<Size> supportedSizes = cParameters.getSupportedPreviewSizes();
			Iterator<Size> supportedSizesI = supportedSizes.iterator();
			int currentDiff = Integer.MAX_VALUE;
			while (supportedSizesI.hasNext()) {
				Size cSize = supportedSizesI.next();
				System.out.println("Supports: " + cSize.width + " " + cSize.height);
				//if (cSize.width <= TARGET_WIDTH && TARGET_WIDTH - cSize.width < TARGET_WIDTH - videoWidth)
				int testDiff = Math.abs(TARGET_WIDTH - cSize.width) + Math.abs(TARGET_HEIGHT - cSize.height);
				if (testDiff < currentDiff) 
				{
					currentDiff = testDiff;
					videoWidth = cSize.width;
					videoHeight = cSize.height;
					System.out.println("Using");
				}
			}
		    if (videoWidth > 0) {
		    	cParameters.setPreviewSize(videoWidth, videoHeight);
				Log.v(LOGTAG,"Preview Size: " + videoWidth + " " + videoHeight);
		    }
			
			List<Integer> supportedFramerates = cParameters.getSupportedPreviewFrameRates();
			Iterator<Integer> supportedFrameratesI = supportedFramerates.iterator();
			while (supportedFrameratesI.hasNext()) {
				Integer cFramerate = supportedFrameratesI.next();
				System.out.println("Supports: " + cFramerate);
				if (cFramerate <= TARGET_FRAMERATE && TARGET_FRAMERATE - cFramerate < TARGET_FRAMERATE - videoFramerate) {
					videoFramerate = cFramerate;
					System.out.println("Using");
				}
			}
			if (videoFramerate > 0) {
		    	cParameters.setPreviewFrameRate(videoFramerate);
		    	Log.v(LOGTAG,"Framerate: " + videoFramerate);
			}

		    camera.setParameters(cParameters);
		    
		    cameraView.setLayoutParams(new LinearLayout.LayoutParams(videoWidth,videoHeight));
			
			try {
				camera.setPreviewDisplay(holder);
				camera.startPreview();
				previewRunning = true;
				
				
				prepareRecorder();	

			}
			catch (IOException e) {
				Log.e(LOGTAG,e.getMessage());
				e.printStackTrace();
			}	
		}		
		
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.v(LOGTAG, "surfaceChanged");
	}
	
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceDestroyed");
		if (recording) {
			recorder.stop();
			recording = false;
		}
		recorder.release();
		if (usecamera) {
			previewRunning = false;
			camera.lock();
			camera.release();
		}
	}
	
	public void onDestroy() {
		super.onDestroy();
	}
	
    class SendingRunnable implements Runnable
    {
		public void run() 
		{
			try {
	        	FrameGrabber grabber = new FFmpegFrameGrabber(filePath);			
				grabber.start();
				
				Log.i(LOGTAG, "ffmpeg_url: " + ffmpeg_link);
		    	MyFrameRecorder recorder = new MyFFmpegFrameRecorder(ffmpeg_link, grabber.getImageWidth(), grabber.getImageHeight(), 1);
		    	//recorder.interleaved = false;
		    	
				recorder.setFormat("flv");			
		    	recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);		
				recorder.setVideoCodec(avcodec.AV_CODEC_ID_FLV1);
				recorder.setFrameRate(grabber.getFrameRate());
				recorder.setSampleRate(grabber.getSampleRate());
	        	recorder.start();
	        	
				Frame frame;
	        	while ((frame = grabber.grabFrame()) != null) {
	        		Log.v(LOGTAG,"Frame #: " + grabber.getFrameNumber());
	            	recorder.record(frame);
	        	}
	        	recorder.stop();
	        	grabber.stop();
			} catch (MyFrameRecorder.Exception e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
    }	
}
