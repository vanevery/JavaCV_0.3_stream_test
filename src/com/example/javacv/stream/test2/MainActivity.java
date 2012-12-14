package com.example.javacv.stream.test2;

import static com.googlecode.javacv.cpp.avutil.*;
import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_core.IplImage;

//import com.googlecode.javacv.FFmpegFrameRecorder;
import com.googlecode.javacv.FrameRecorder.Exception;
import com.googlecode.javacv.cpp.avcodec;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {
	private final static String LOG_TAG = "RecordActivity";
	private PowerManager.WakeLock mWakeLock;
    
	//private String ffmpeg_link = "/mnt/sdcard/stream.flv";

	//private String ffmpeg_link = "rtmp://live:live@128.122.151.108:1935/live/test.flv";
	//private String ffmpeg_link = "rtmp://live:live@192.168.1.8:1935/live/test.flv";
	private String ffmpeg_link = "rtmp://live:live@192.168.43.176:1935/live/test.flv";
	
	public boolean recording = false;
	
	private volatile MyFFmpegFrameRecorder recorder;
	
    private boolean isPreviewOn = false;
    
    private int sampleAudioRateInHz = 11025;
    private int imageWidth = 176;
    private int imageHeight = 144;
    private int frameRate = 10;
        	
    /* audio data getting thread */
	private AudioRecord audioRecord;
	private AudioRecordRunnable audioRecordRunnable;
	private Thread audioThread;
	
	private VideoRecordRunnable videoRecordRunnable;
	private Thread videoThread;

	/* video data getting thread */
	private Camera cameraDevice;
	private CameraView cameraView;
	
	private IplImage yuvIplimage = null;
	
	/* layout setting */
	private final int bg_screen_bx = 232;
	private final int bg_screen_by = 128;
	private final int bg_screen_width = 700;
	private final int bg_screen_height = 500;
	private final int bg_width = 1123;
	private final int bg_height = 715;
	private final int live_width = 640;
	private final int live_height = 480;
	private int screenWidth, screenHeight;
	private Button btnRecorderControl;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE); 
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "XYTEST"); 
        mWakeLock.acquire(); 
        
        initLayout();
        initRecorder();
    }
    

	@Override
	protected void onResume() {
		super.onResume();
		
		if (mWakeLock == null) {
		   PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		   mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "XYTEST");
		   mWakeLock.acquire();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}
	}
    
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		recording = false;
				
		if (cameraView != null) {
			cameraView.stopPreview();		
			cameraDevice.release();
			cameraDevice = null;
		}
		
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}
	}
	
	
	private void initLayout() {
		
		/* get size of screen */
		Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		screenWidth = display.getWidth();
		screenHeight = display.getHeight();
		RelativeLayout.LayoutParams layoutParam = null; 
		LayoutInflater myInflate = null; 
		myInflate = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		RelativeLayout topLayout = new RelativeLayout(this);
		setContentView(topLayout);
		LinearLayout preViewLayout = (LinearLayout) myInflate.inflate(R.layout.activity_main, null);
		layoutParam = new RelativeLayout.LayoutParams(screenWidth, screenHeight);
		topLayout.addView(preViewLayout, layoutParam);
		
		/* add control button: start and stop */
		btnRecorderControl = (Button) findViewById(R.id.recorder_control);
		btnRecorderControl.setOnClickListener(this);
		
		/* add camera view */
		int display_width_d = (int) (1.0 * bg_screen_width * screenWidth / bg_width);
		int display_height_d = (int) (1.0 * bg_screen_height * screenHeight / bg_height);
		int prev_rw, prev_rh;
		if (1.0 * display_width_d / display_height_d > 1.0 * live_width / live_height) {
			prev_rh = display_height_d;
			prev_rw = (int) (1.0 * display_height_d * live_width / live_height);
		} else {
			prev_rw = display_width_d;
			prev_rh = (int) (1.0 * display_width_d * live_height / live_width);
		}
		layoutParam = new RelativeLayout.LayoutParams(prev_rw, prev_rh);
		layoutParam.topMargin = (int) (1.0 * bg_screen_by * screenHeight / bg_height);
		layoutParam.leftMargin = (int) (1.0 * bg_screen_bx * screenWidth / bg_width);
		
		cameraDevice = Camera.open();
		Log.i(LOG_TAG, "cameara open");
    	cameraView = new CameraView(this, cameraDevice);
		topLayout.addView(cameraView, layoutParam);
        Log.i(LOG_TAG, "cameara preview start: OK");
	}
	
	public void startRecording() {
		
		audioThread.start();
		videoThread.start();
		
		try {
			recorder.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
    	
		recording = true;
	}
	
	public void stopRecording() {
		
		audioRecordRunnable.runAudioThread = false;
		videoRecordRunnable.runVideoThread = false;
		

		if (recorder != null && recording) {
			recording = false;
			Log.v(LOG_TAG,"Finishing recording, calling stop and release on recorder");
			try {
				recorder.stop();
				recorder.release();
			} catch (Exception e) {
				e.printStackTrace();
			}
			recorder = null;
		}
		
	}
		
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (recording) {
				Toast.makeText(MainActivity.this, "Can't Finish, isRecorderStart", 1000).show();
				stopRecording();
			} else {
				MainActivity.this.finish();
			}
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
	
	//---------------------------------------
	// initialize ffmpeg_recorder
	//---------------------------------------
    private void initRecorder() {
    	
    	Log.w(LOG_TAG,"init recorder");

		if (yuvIplimage == null) {
			yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 2);
			Log.i(LOG_TAG, "create yuvIplimage");
		}
    	
		Log.i(LOG_TAG, "ffmpeg_url: " + ffmpeg_link);
    	recorder = new MyFFmpegFrameRecorder(ffmpeg_link, imageWidth, imageHeight, 1);
    	
		recorder.setFormat("flv");
    	recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);		
		recorder.setSampleRate(sampleAudioRateInHz);
		recorder.setVideoCodec(avcodec.AV_CODEC_ID_FLV1);
		// Set in the surface changed method
		recorder.setFrameRate(frameRate);
		recorder.setPixelFormat(PIX_FMT_YUV420P);

		Log.i(LOG_TAG, "recorder initialize success");
    	
		audioRecordRunnable = new AudioRecordRunnable();
		audioThread = new Thread(audioRecordRunnable);
		
		videoRecordRunnable = new VideoRecordRunnable();
		videoThread = new Thread(videoRecordRunnable);
	}
    
    class VideoRecordRunnable implements Runnable {
    	
    	boolean runVideoThread = true;
    	long lastTime = System.currentTimeMillis();
    	long currentTime = System.currentTimeMillis();
    	long minTime = 1000/frameRate;
    	
    	public void run() {
    		while (runVideoThread) {
    			if (recording) {
	    			currentTime = System.currentTimeMillis();
	    			if (currentTime - lastTime >= minTime) {
	    				Log.v(LOG_TAG,"Writing Frame");
	    				try {
	    					recorder.record(yuvIplimage);
	    				} catch (Exception e) {
	    					Log.v(LOG_TAG,e.getMessage());
	    					e.printStackTrace();
	    				}
	    				lastTime = currentTime;
					}
    			}
    		}
    	}
    }
    
    
    //---------------------------------------------
	// audio thread, gets and encodes audio data
	//---------------------------------------------
	class AudioRecordRunnable implements Runnable {
		boolean runAudioThread = true;

		@Override
		public void run() {
			int bufferSize;
			short[] audioData;
			int bufferReadResult;
			
			int bufferLength = 0;

			bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz, 
					AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
							
			audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz, 
					AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

			// What is this for??								
			if (bufferSize <= 2048) {
				bufferLength = 2048;
			} else if (bufferSize <= 4096) {
				bufferLength = 4096;
			}
			// End What is this for??
				
			audioData = new short[bufferLength];
			audioRecord.startRecording();
			Log.d(LOG_TAG, "audioRecord.startRecording()");
				
			/* ffmpeg_audio encoding loop */
			while (runAudioThread) {
				
				bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
				Log.v(LOG_TAG,"bufferReadResult: " + bufferReadResult);
				
				if (bufferReadResult < 1024 && recording) {
					Buffer realAudioData512 = ShortBuffer.wrap(audioData,0,bufferReadResult);
					try {
						recorder.record(realAudioData512);
					} catch (Exception e) {
						Log.v(LOG_TAG,e.getMessage());
						e.printStackTrace();
					}
					
				} else if (bufferReadResult == 1024 && recording) {
					Buffer realAudioData1024 = ShortBuffer.wrap(audioData,0,1024);
					try {
						recorder.record(realAudioData1024);
					} catch (Exception e) {
						Log.v(LOG_TAG,e.getMessage());
						e.printStackTrace();
					}
				} else if (bufferReadResult == 2048 && recording) {
					Buffer realAudioData2048_1=ShortBuffer.wrap(audioData, 0, 1024);
					Buffer realAudioData2048_2=ShortBuffer.wrap(audioData, 1024, 1024);
					for (int i = 0; i < 2; i++) {
						if (i == 0) {
							try {
								recorder.record(realAudioData2048_1);
							} catch (Exception e) {
								Log.v(LOG_TAG,e.getMessage());
								e.printStackTrace();
							}
						} else if (i == 1) {
							try {
								recorder.record(realAudioData2048_2);
							} catch (Exception e) {
								Log.v(LOG_TAG,e.getMessage());
								e.printStackTrace();
							}
						}
					}
				}
				//recorder.record(yuvIplimage);
				
				
				/*					
				if (recording) {						
			    	recorder.record(ShortBuffer.wrap(audioData, 0, audioData.length));
				}
				*/
			}
			Log.v(LOG_TAG,"AudioThread Finished, release audioRecord");
				
			/* encoding finish, release recorder */
			if (audioRecord != null) {
				audioRecord.stop();
				audioRecord.release();
				audioRecord = null;
				Log.v(LOG_TAG,"audioRecord released");
			}
		}
	}
	
    //---------------------------------------------
	// camera thread, gets and encodes video data
	//---------------------------------------------
    class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {
    	
    	private SurfaceHolder mHolder;
    	private Camera mCamera;
		
    	public CameraView(Context context, Camera camera) {
    		super(context);
    		Log.w("camera","camera view");
    		mCamera = camera;
            mHolder = getHolder();
            mHolder.addCallback(CameraView.this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        	mCamera.setPreviewCallback(CameraView.this);
    	}

    	@Override
    	public void surfaceCreated(SurfaceHolder holder) {
            try {
            	stopPreview();
            	mCamera.setPreviewDisplay(holder);
            } catch (IOException exception) {
                mCamera.release();
                mCamera = null;
            }
    	}

    	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    		Log.v(LOG_TAG,"Setting imageWidth: " + imageWidth + " imageHeight: " + imageHeight + " frameRate: " + frameRate);
            Camera.Parameters camParams = mCamera.getParameters();
        	camParams.setPreviewSize(imageWidth, imageHeight);
        	//camParams.setPreviewFrameRate(frameRate);
            mCamera.setParameters(camParams);
            startPreview();

        	//frameRate = camParams.getPreviewFrameRate();
        	//recorder.setFrameRate(frameRate);
    	
    	}

    	@Override
    	public void surfaceDestroyed(SurfaceHolder holder) {
    		mHolder.addCallback(null);
    		mCamera.setPreviewCallback(null);
    	}
    	
        public void startPreview() {
            if (!isPreviewOn && mCamera != null) {
        		isPreviewOn = true;
    	    	mCamera.startPreview();
        	}
        }
        
        public void stopPreview() {
            if (isPreviewOn && mCamera != null) {
    	    	isPreviewOn = false;
    	    	mCamera.stopPreview();
        	}
        }
    	
    	@Override
    	public void onPreviewFrame(byte[] data, Camera camera) {
    		/* get video data */
			if (yuvIplimage != null && recording) {
				yuvIplimage.getByteBuffer().put(data);
				/*
		    	try {
		    		//Log.v(LOG_TAG,"Recording Frame " + System.currentTimeMillis());
					recorder.record(yuvIplimage);
				} catch (Exception e) {
					e.printStackTrace();
				}
				*/
				//Log.i(LOG_TAG, "yuvIplimage put data");
			}
			
			/*
			Log.i(LOG_TAG, "onPreviewFrame - wrote bytes: " + data.length + "; " + 
					camera.getParameters().getPreviewSize().width +" x " + 
					camera.getParameters().getPreviewSize().height + "; frameRate: " +
					camera.getParameters().getPreviewFrameRate());
			*/
    	}
    }

	@Override
	public void onClick(View v) {
		if (!recording) {
			startRecording();
			Log.w(LOG_TAG, "Start Button Pushed");
	    	btnRecorderControl.setText("stop");
		} else {
			// This will trigger the audio recording loop to stop and then set isRecorderStart = false;
			stopRecording();
			Log.w(LOG_TAG, "Stop Button Pushed");
			btnRecorderControl.setText("start");
		}		
	}
}