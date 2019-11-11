package com.example.mediasoupandroidsample.media;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.voiceengine.WebRtcAudioUtils;

/**
 * Class to handle local media capturing
 */
public class MediaCapturer {
	private static final String TAG = "MediaCapturer";

	private static final String MEDIA_STREAM_ID = "ARDAMS";
	private static final String VIDEO_TRACK_ID = "ARDAMSv0";
	private static final String AUDIO_TRACK_ID = "ARDAMSa0";

	private CameraVideoCapturer mCameraVideoCapturer;
	private final PeerConnectionFactory mPeerConnectionFactory;
	private final MediaStream mMediaStream;

	public MediaCapturer() {
		mPeerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();
		mMediaStream = mPeerConnectionFactory.createLocalMediaStream(MEDIA_STREAM_ID);
	}

	/**
	 * Initialize local camera
	 * @param context Context
	 * @throws Exception Failed to get camera device
	 */
	public void initCamera(Context context)
	throws Exception {
		boolean isCamera2Supported = Camera2Enumerator.isSupported(context);

		CameraEnumerator cameraEnumerator;

		if (isCamera2Supported) {
			cameraEnumerator = new Camera2Enumerator(context);
		} else {
			cameraEnumerator = new Camera1Enumerator();
		}

		final String[] deviceNames = cameraEnumerator.getDeviceNames();

		for(String deviceName : deviceNames) {
			// Get the front camera for now
			if(cameraEnumerator.isFrontFacing(deviceName)) {
				mCameraVideoCapturer = cameraEnumerator.createCapturer(deviceName, new MediaCapturerEventHandler());

				Log.d(TAG, "created camera video capturer deviceName=" + deviceName);
			}
		}

		if (mCameraVideoCapturer == null) {
			throw new Exception("Failed to get Camera Device");
		}
	}

	/**
	 * Create local video track from camera capturer
	 * @param context Context
	 * @param localVideoView Local Video View
	 * @param eglBaseContext EGL Context
	 * @return VideoTrack
	 */
	public VideoTrack createVideoTrack(Context context, SurfaceViewRenderer localVideoView, EglBase.Context eglBaseContext) {
		if (mCameraVideoCapturer == null) {
			throw new IllegalStateException("Camera must be initialized");
		}

		SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext);
		VideoSource videoSource = mPeerConnectionFactory.createVideoSource(false);

		mCameraVideoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());

		// Capture 640x480 @ 30fps
		mCameraVideoCapturer.startCapture(640, 480, 30);

		VideoTrack videoTrack = mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
		videoTrack.setEnabled(true);
		localVideoView.setMirror(true);
		localVideoView.setEnableHardwareScaler(true);
		mMediaStream.addTrack(videoTrack);

		videoTrack.addSink(localVideoView);

		return videoTrack;
	}

	/**
	 * Create local audio track
	 * @return AudioTrack
	 */
	public AudioTrack createAudioTrack() {
		AudioSource audioSource = mPeerConnectionFactory.createAudioSource(new MediaConstraints());
		WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
		WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true);

		AudioTrack audioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
		audioTrack.setEnabled(true);
		mMediaStream.addTrack(audioTrack);

		return audioTrack;
	}

	private class MediaCapturerEventHandler implements CameraVideoCapturer.CameraEventsHandler {
		@Override
		public void onCameraOpening(String s) {
			Log.d(TAG, "onCameraOpening s=" + s);
		}

		@Override
		public void onFirstFrameAvailable() {
			Log.d(TAG, "onFirstFrameAvailable");
		}

		@Override
		public void onCameraFreezed(String s) {
			Log.d(TAG, "onCameraFreezed s=" + s);
		}

		@Override
		public void onCameraError(String s) {
			Log.e(TAG, "onCameraError s=" + s);
		}

		@Override
		public void onCameraDisconnected() {
			Log.d(TAG, "onCameraDisconnected");
		}

		@Override
		public void onCameraClosed() {
			Log.d(TAG, "onCameraClosed");
		}
	}
}
