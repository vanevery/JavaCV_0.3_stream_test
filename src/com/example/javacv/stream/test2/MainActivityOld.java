package com.example.javacv.stream.test2;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.app.NavUtils;

import static com.googlecode.javacv.cpp.avcodec.*;
import static com.googlecode.javacv.cpp.avutil.*;
import static com.googlecode.javacv.cpp.opencv_core.*;

import com.googlecode.javacv.cpp.avcodec;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.FFmpegFrameRecorder;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.Context;
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

public class MainActivityOld extends Activity {

	private final static String LOG_TAG = "RecordActivity";
	private final static String WAKELOCK_TAG = "StreamTest";
	
	private PowerManager.WakeLock mWakeLock;
	
	private boolean isAudioRecording = false;

	private String ffmpeg_link = "rtmp://live:live@128.122.151.108:1935/live/test.flv";
	//private String ffmpeg_link = "rtmp://username:password@xxxxxx:1935/live/test.flv";

	public volatile boolean isRecorderStart = false;
	public volatile boolean isVideoStart = false;
	
    private volatile FFmpegFrameRecorder recorder;

    /* the parameter of ffmpeg setting */
    private final static int sampleAudioRateInHz = 11025;
    private final static int sampleAudioBitRate = 32000;
    private final static int imageWidth = 176;
    private final static int imageHeight = 144;
    private final static int frameRate = 12;
    private final static int sampleVideoBitRate = 200000;


    /* audio data getting thread */
	private AudioRecord audioRecord;
	private Thread audioView;

	/* video data getting thread */
	private Camera cameraDevice;
	private CameraView cameraView;
    private boolean isPreviewOn = false;
	private IplImage yuvIplimage = null;

	private int screenWidth, screenHeight;
	private Button btnRecorderControl;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
		/* add control button: start and stop */
		btnRecorderControl = (Button) findViewById(R.id.recorder_control);
		btnRecorderControl.setOnClickListener(mControlAction);
        
        /* manager - keep phone waking up */
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE); 
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, WAKELOCK_TAG); 
        mWakeLock.acquire(); 

        initLayout();

        initRecorder();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

	@Override
	protected void onResume() {
		super.onResume();
		
		if (mWakeLock == null) {
		   PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		   mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, WAKELOCK_TAG);
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
		
		//isAudioRecording = false;
		
		if (cameraView != null) {
			cameraView.stopPreview();		
			cameraDevice.release();
			cameraDevice = null;
		}
		
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}
		isRecorderStart = false;
	}
	
	
	//---------------------------------------
	// initialize layout   
	//---------------------------------------
	private void initLayout() {
		
		/* get size of screen */
		Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		screenWidth = display.getWidth();
		screenHeight = display.getHeight();

		/*
		RelativeLayout.LayoutParams layoutParam = null; 
		LayoutInflater myInflate = null; 

		myInflate = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		RelativeLayout topLayout = new RelativeLayout(this);
		setContentView(topLayout);
		RelativeLayout preViewLayout = (RelativeLayout) myInflate.inflate(R.layout.activity_main, null);
		layoutParam = new RelativeLayout.LayoutParams(screenWidth, screenHeight);
		topLayout.addView(preViewLayout, layoutParam);
		*/
		

		//add camera view
		/* 
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
		*/
		
		cameraDevice = Camera.open();
		Log.i(LOG_TAG, "cameara open");
		
    	cameraView = new CameraView(this, cameraDevice);
    	LinearLayout topLayout = (LinearLayout) this.findViewById(R.id.record_layout);
    	
    	LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(imageWidth, imageHeight);
    	cameraView.setLayoutParams(layoutParams);
    	
		topLayout.addView(cameraView);
        Log.i(LOG_TAG, "cameara preview start: OK");
        
	}
	
	//---------------------------------------
	// recorder control button 
	//---------------------------------------
	private OnClickListener mControlAction = new OnClickListener() {
		@Override
		public void onClick(View v) {
			
    		if (!isRecorderStart) {
    	    	try {
					recorder.start();
	    	    	isRecorderStart = true;
	    	    	btnRecorderControl.setText("Stop");
				} catch (com.googlecode.javacv.FrameRecorder.Exception e) {
					e.printStackTrace();
				}
    	    	//btnRecorderControl.setBackgroundResource(R.drawable.btn_record_stop);
    		} else if (isRecorderStart) {
    			isRecorderStart = false;
    			btnRecorderControl.setText("Start");
    			//btnRecorderControl.setBackgroundResource(R.drawable.btn_record_start);
    			
    			if (recorder != null) {
					try {
						recorder.stop();
						recorder.release();
					} catch (Exception e) {
						e.printStackTrace();
					}
					recorder = null;
				}    			
    		}
		}
	};
	
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (isRecorderStart) {
				Toast.makeText(MainActivityOld.this, "isRecorderStart", 1000).show();
			} else {
				MainActivityOld.this.finish();
			}
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
	//---------------------------------------
	// initialize ffmpeg_recorder
	//---------------------------------------
    private void initRecorder() {
    	    	
		if (yuvIplimage == null) {
			yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 2);
			Log.i(LOG_TAG, "create yuvIplimage");
		}
    	
    	
		Log.i(LOG_TAG, "ffmpeg_url: " + ffmpeg_link);
    	recorder = new FFmpegFrameRecorder(ffmpeg_link, imageWidth, imageHeight);

    	try {
	    	//recorder.setCodecAudioID(CODEC_ID_AAC);
	    	recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
	    	recorder.setSampleRate(sampleAudioRateInHz);
	    	//recorder.setAudioSampleRate(sampleAudioRateInHz);
	    	recorder.setAudioBitrate(sampleAudioBitRate);
	    	recorder.setAudioChannels(1);
	    	
	    	//recorder.setCodecVideoID(CODEC_ID_FLV1);
    		//recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
    		recorder.setVideoCodec(avcodec.AV_CODEC_ID_FLV1);
	    	recorder.setFrameRate(frameRate);
	    	//recorder.setVideoBitrate(sampleVideoBitRate);
	    	
	    	recorder.setPixelFormat(PIX_FMT_YUV420P);
	    	//recorder.setFormat("mp4");
	    	recorder.setFormat("flv");

	    	Log.i(LOG_TAG, "recorder initialize success");
		} catch (Exception e) {
			try {
				recorder.stop();
				recorder.release();
				recorder = null;
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			Log.e(LOG_TAG, "record initialize failed");
			System.exit(-1);
		}
    	
		audioView = new Thread(new AudioRecordThread());
		audioView.start();
	}
    
    //---------------------------------------------
	// audio thread, gets and encodes audio data
	//---------------------------------------------

	class AudioRecordThread implements Runnable {
		@Override
		public void run() {
			int bufferLength = 0;
			int bufferSize;
			short[] audioData;
			int bufferReadResult;

			try {
				bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz, 
						AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
				
				if (bufferSize <= 2048) {
					bufferLength = 2048;
				} else if (bufferSize <= 4096) {
					bufferLength = 4096;
				}
				
				// set audio recorder parameters, and start recording 
				audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz, 
						AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferLength);
				audioData = new short[bufferLength];
				audioRecord.startRecording();
				Log.d(LOG_TAG, "audioRecord.startRecording()");
				
				isAudioRecording = true;
				
				// ffmpeg_audio encoding loop 
				while (isAudioRecording) {
					bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
					
					if (bufferReadResult == 1024 && isRecorderStart) {
						short[] realAudioData1024 = new short[bufferReadResult];
						System.arraycopy(audioData, 0, realAudioData1024, 0, bufferReadResult);
						
				    	//recorder.record(realAudioData1024);
				    	Log.d(LOG_TAG, "recorder.record(): bufferReadResult == 1024");
					    	
					} else if (bufferReadResult == 2048 && isRecorderStart) {
						short[] realAudioData2048_1 = new short[1024];
						short[] realAudioData2048_2 = new short[1024];
						System.arraycopy(audioData, 0, realAudioData2048_1, 0, 1024);
						System.arraycopy(audioData, 1024, realAudioData2048_2, 0, 1024);

						for (int i = 0; i < 2; i++) {
							if (i == 0) {
								//recorder.record(realAudioData2048_1);
							} else if (i == 1) {
								//recorder.record(realAudioData2048_2);
							}
						}
				    	Log.d(LOG_TAG, "recorder.record(): bufferReadResult == 2048");
					}
				}
				
				// encoding finish, release recorder 
				if (audioRecord != null) {
					try {
						audioRecord.stop();
						audioRecord.release();
					} catch (Exception e) {
						e.printStackTrace();
					}
					audioRecord = null;
				}
				
				if (recorder != null && isRecorderStart) {
					try {
						recorder.stop();
						recorder.release();
					} catch (Exception e) {
						e.printStackTrace();
					}
					recorder = null;
				}
			} catch (Exception e) {
				Log.e(LOG_TAG, "get audio data failed");
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

    	@Override
    	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters camParams = mCamera.getParameters();
        	camParams.setPreviewSize(imageWidth, imageHeight);
        	camParams.setPreviewFrameRate(frameRate);
            mCamera.setParameters(camParams);
            startPreview();
    	}

    	@Override
    	public void surfaceDestroyed(SurfaceHolder holder) {
    		mHolder.addCallback(null);
    		mCamera.setPreviewCallback(null);
    	}
    	
        public synchronized void startPreview() {
            if (!isPreviewOn && mCamera != null) {
        		isPreviewOn = true;
    	    	mCamera.startPreview();
        	}
        }
        
        public synchronized void stopPreview() {
            if (isPreviewOn && mCamera != null) {
    	    	isPreviewOn = false;
    	    	mCamera.stopPreview();
        	}
        }
    	
    	@Override
    	public synchronized void onPreviewFrame(byte[] data, Camera camera) {
    		try {
    			/* get video data */
    			if (yuvIplimage != null && isRecorderStart) {
            		yuvIplimage.getByteBuffer().put(data);
    	    		Log.i(LOG_TAG, "yuvIplimage put data");
    	    		
    	    		recorder.record(yuvIplimage);
    	    		
    			}
    			Log.i(LOG_TAG, "onPreviewFrame - wrote bytes: " + data.length + "; " + 
    					camera.getParameters().getPreviewSize().width +" x " + 
    					camera.getParameters().getPreviewSize().height + "; frameRate: " +
    					camera.getParameters().getPreviewFrameRate());
    		} catch (Exception e) { 
    		}
    	}
    }
}
