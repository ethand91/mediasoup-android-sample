package com.example.mediasoupandroidsample.permission;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Objects;

/**
 * Headless Fragment that handles Camera/Mic Permissions
 */
public class PermissionFragment extends Fragment {
	public static final String TAG = "PermissionFragment";

	private static final int CAMERA_PERMISSION = 0;
	private static final int MIC_PERMISSION = 1;
	private static final int CAMERA_MIC_PERMISSION = 2;

	private PermissionCallback mPermissionCallback;
	private Context mContext;

	public static PermissionFragment newInstance() {
		return new PermissionFragment();
	}

	public void setPermissionCallback(PermissionCallback permissionCallback) {
		mPermissionCallback = permissionCallback;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		mContext = context;
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mPermissionCallback = null;
	}

	/**
	 * Checks the User's Camera permissions status if not granted requests it
	 */
	public void checkCameraPermission() {
		if (isCameraPermissionGranted()) {
			mPermissionCallback.onPermissionGranted();
		} else {
			requestPermissions(new String[] { Manifest.permission.CAMERA }, CAMERA_PERMISSION);
		}
	}

	/**
	 * Checks the User's Mic permissions status if not granted request it
	 */
	public void checkMicPermission() {
		if (isMicPermissionGranted()) {
			mPermissionCallback.onPermissionGranted();
		} else {
			requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, MIC_PERMISSION);
		}
	}

	/**
	 * Checks the User's Camera and Mic permissions status if not granted request it
	 */
	public void checkCameraMicPermission() {
		if (isMicPermissionGranted() && isCameraPermissionGranted()) {
			mPermissionCallback.onPermissionGranted();
		} else {
			requestPermissions(new String[] { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO }, CAMERA_MIC_PERMISSION);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch(requestCode) {
			case CAMERA_PERMISSION:
				if (isCameraPermissionGranted()) {
					mPermissionCallback.onPermissionGranted();
				} else {
					mPermissionCallback.onPermissionDenied();
				}
				break;
			case MIC_PERMISSION:
				if (isMicPermissionGranted()) {
					mPermissionCallback.onPermissionGranted();
				} else {
					mPermissionCallback.onPermissionDenied();
				}
				break;
			case CAMERA_MIC_PERMISSION:
				if(isMicPermissionGranted() && isCameraPermissionGranted()) {
					mPermissionCallback.onPermissionGranted();
				} else {
					mPermissionCallback.onPermissionDenied();
				}
				break;
			default: super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	/**
	 * @return Whether the user has granted Camera permission or not
	 */
	private boolean isCameraPermissionGranted() {
		return ContextCompat.checkSelfPermission(Objects.requireNonNull(mContext), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
	}

	/**
	 * @return Whether the user has granted Mic Permission or not
	 */
	private boolean isMicPermissionGranted() {
		return ContextCompat.checkSelfPermission(Objects.requireNonNull(getActivity()), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
	}

	public interface PermissionCallback {
		/**
		 * Permission/s have been granted
		 */
		void onPermissionGranted();

		/**
		 * Permission/s have been denied
		 */
		void onPermissionDenied();
	}
}
