/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android.camera;

import java.io.IOException;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.client.android.Size;
import com.google.zxing.client.android.camera.open.OpenCameraManager;

/**
 * This object wraps the Camera service object and expects to be the only one
 * talking to it. The implementation encapsulates the steps needed to take
 * preview-sized images, which are used for both preview and decoding.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager {

	private static final String TAG = CameraManager.class.getSimpleName();

	private static final float RANG_OF_SIZE = (float) 2 / 3;

	private final Context mContext;
	private final CameraConfigurationManager mConfigManager;
	private Camera mCamera;
	private AutoFocusManager mAutoFocusManager;
	private Rect mFramingRect;
	private Rect mFramingRectInPreview;
	private boolean initialized;
	private boolean previewing;
	private int requestedFramingRectWidth;
	private int requestedFramingRectHeight;
	/**
	 * Preview frames are delivered here, which we pass on to the registered
	 * handler. Make sure to clear the handler so it will only receive one
	 * message.
	 */
	private final PreviewCallback previewCallback;

	public CameraManager(Context context) {
		this.mContext = context;
		this.mConfigManager = new CameraConfigurationManager(context);
		previewCallback = new PreviewCallback(mConfigManager);
	}

	/**
	 * Opens the camera driver and initializes the hardware parameters.
	 * 
	 * @param holder The surface object which the camera will draw preview frames into.
	 * @throws IOException Indicates the camera driver failed to open.
	 */
	public synchronized void openDriver(SurfaceHolder holder) throws IOException {
		Camera theCamera = mCamera;
		if (theCamera == null) {
			theCamera = new OpenCameraManager().build().open();
			if (theCamera == null) {
				throw new IOException();
			}
			mCamera = theCamera;
		}
		theCamera.setPreviewDisplay(holder);

		if (!initialized) {
			initialized = true;
			mConfigManager.initFromCameraParameters(theCamera);
			if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
				setManualFramingRect(requestedFramingRectWidth, requestedFramingRectHeight);
				requestedFramingRectWidth = 0;
				requestedFramingRectHeight = 0;
			}
		}

		Camera.Parameters parameters = theCamera.getParameters();
		String parametersFlattened = parameters == null ? null : parameters .flatten(); // Save these, temporarily
		try {
			mConfigManager.setDesiredCameraParameters(theCamera, false);
		} catch (RuntimeException re) {
			// Driver failed
			Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters");
			Log.i(TAG, "Resetting to saved camera params: " + parametersFlattened);
			// Reset:
			if (parametersFlattened != null) {
				parameters = theCamera.getParameters();
				parameters.unflatten(parametersFlattened);
				try {
					theCamera.setParameters(parameters);
					mConfigManager.setDesiredCameraParameters(theCamera, true);
				} catch (RuntimeException re2) {
					// Well, darn. Give up
					Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
				}
			}
		}

	}

	public synchronized boolean isOpen() {
		return mCamera != null;
	}

	/**
	 * Closes the camera driver if still in use.
	 */
	public synchronized void closeDriver() {
		if (mCamera != null) {
			mCamera.release();
			mCamera = null;
			// Make sure to clear these each time we close the camera, so that
			// any scanning rect
			// requested by intent is forgotten.
			mFramingRect = null;
			mFramingRectInPreview = null;
		}
	}

	/**
	 * Asks the camera hardware to begin drawing preview frames to the screen.
	 */
	public synchronized void startPreview() {
		Camera theCamera = mCamera;
		if (theCamera != null && !previewing) {
			theCamera.startPreview();
			previewing = true;
			mAutoFocusManager = new AutoFocusManager(mContext, mCamera);
		}
	}

	/**
	 * Tells the camera to stop drawing preview frames.
	 */
	public synchronized void stopPreview() {
		if (mAutoFocusManager != null) {
			mAutoFocusManager.stop();
			mAutoFocusManager = null;
		}
		if (mCamera != null && previewing) {
			mCamera.stopPreview();
			previewCallback.setHandler(null, 0);
			previewing = false;
		}
	}

	/**
	 * Convenience method for
	 * {@link com.google.zxing.client.android.CaptureActivity}
	 */
	public synchronized void setTorch(boolean newSetting) {
		if (newSetting != mConfigManager.getTorchState(mCamera)) {
			if (mCamera != null) {
				if (mAutoFocusManager != null) {
					mAutoFocusManager.stop();
				}
				mConfigManager.setTorch(mCamera, newSetting);
				if (mAutoFocusManager != null) {
					mAutoFocusManager.start();
				}
			}
		}
	}

	/**
	 * A single preview frame will be returned to the handler supplied. The data
	 * will arrive as byte[] in the message.obj field, with width and height
	 * encoded as message.arg1 and message.arg2, respectively.
	 * 
	 * @param handler The handler to send the message to.
	 * @param message The what field of the message to be sent.
	 */
	public synchronized void requestPreviewFrame(Handler handler, int message) {
		Camera theCamera = mCamera;
		if (theCamera != null && previewing) {
			previewCallback.setHandler(handler, message);
			theCamera.setOneShotPreviewCallback(previewCallback);
		}
	}

	/**
	 * Calculates the framing rect which the UI should draw to show the user
	 * where to place the barcode. This target helps with alignment as well as
	 * forces the user to hold the device far enough away to ensure the image
	 * will be in focus.
	 * 
	 * @return The rectangle to draw on screen in window coordinates.
	 */
	public synchronized Rect getFramingRect() {
		if (mFramingRect == null) {
			if (mCamera == null) {
				return null;
			}
			Size screenSize = mConfigManager.getScreenSize();
			if (screenSize == null) {
				// Called early, before init even finished
				return null;
			}

			Size focusSize = getFocusSize(screenSize, RANG_OF_SIZE);

			//Add support portrait screen.
			if (screenSize.width > screenSize.height) {
				screenSize.exchange();
			}
			//Add support portrait screen end.

			int leftOffset = (screenSize.width - focusSize.width) / 2;
			int topOffset = (screenSize.height - focusSize.height) / 2;
			mFramingRect = new Rect(leftOffset, topOffset, leftOffset + focusSize.width, topOffset + focusSize.height);
			Log.d(TAG, "Calculated framing rect: " + mFramingRect);
		}
		return mFramingRect;
	}
	
	/**
	 * Get focus window size.
	 * @param screenSize
	 * @param range
	 * @return
	 */
	private static Size getFocusSize(Size screenSize, float range) {
		int min = Math.min(screenSize.width, screenSize.height);
		int size = (int) (min * range);
		return new Size(size, size);
	}

	/**
	 * Like {@link #getFramingRect} but coordinates are in terms of the preview
	 * frame, not UI / screen.
	 */
	public synchronized Rect getFramingRectInPreview() {
		if (mFramingRectInPreview == null) {
			Rect framingRect = getFramingRect();
			if (framingRect == null) {
				return null;
			}
			Rect rect = new Rect(framingRect);
			Size cameraSize = mConfigManager.getCameraResolution();
			Size screenSize = mConfigManager.getScreenSize();
			if (cameraSize == null || screenSize == null) {
				// Called early, before init even finished
				return null;
			}

			//Add support portrait screen.
			rect.left = rect.left * cameraSize.height / screenSize.width;
			rect.right = rect.right * cameraSize.height / screenSize.width;
			rect.top = rect.top * cameraSize.width / screenSize.height;
			rect.bottom = rect.bottom * cameraSize.width / screenSize.height;
			//Add support portrait screen end.
			
			mFramingRectInPreview = rect;
		}
		return mFramingRectInPreview;
	}

	/**
	 * Allows third party apps to specify the scanning rectangle dimensions,
	 * rather than determine them automatically based on screen resolution.
	 * 
	 * @param width The width in pixels to scan.
	 * @param height The height in pixels to scan.
	 */
	public synchronized void setManualFramingRect(int width, int height) {
		if (initialized) {
			Size screenSize = mConfigManager.getScreenSize();
			if (width > screenSize.width) {
				width = screenSize.width;
			}
			if (height > screenSize.height) {
				height = screenSize.height;
			}
			int leftOffset = (screenSize.width - width) / 2;
			int topOffset = (screenSize.height - height) / 2;
			mFramingRect = new Rect(leftOffset, topOffset, leftOffset + width,
					topOffset + height);
			Log.d(TAG, "Calculated manual framing rect: " + mFramingRect);
			mFramingRectInPreview = null;
		} else {
			requestedFramingRectWidth = width;
			requestedFramingRectHeight = height;
		}
	}

	/**
	 * A factory method to build the appropriate LuminanceSource object based on
	 * the format of the preview buffers, as described by Camera.Parameters.
	 * 
	 * @param data
	 *            A preview frame.
	 * @param width
	 *            The width of the image.
	 * @param height
	 *            The height of the image.
	 * @return A PlanarYUVLuminanceSource instance.
	 */
	public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data,
			int width, int height) {
		Rect rect = getFramingRectInPreview();
		if (rect == null) {
			return null;
		}
		// Go ahead and assume it's YUV rather than die.
		return new PlanarYUVLuminanceSource(data, width, height, rect.left,
				rect.top, rect.width(), rect.height(), false);
	}

}
