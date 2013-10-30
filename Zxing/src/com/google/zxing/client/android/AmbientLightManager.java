/*
 * Copyright (C) 2012 ZXing authors
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

package com.google.zxing.client.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.client.android.camera.FrontLightMode;

/**
 * Detects ambient light and switches on the front light when very dark, and off
 * again when sufficiently light.
 * 
 * @author Sean Owen
 * @author Nikolaus Huber
 */
public final class AmbientLightManager implements SensorEventListener {

	private static final float TOO_DARK_LUX = 45.0f;
	private static final float BRIGHT_ENOUGH_LUX = 450.0f;

	private final Context mContext;
	private CameraManager mCameraManager;
	private Sensor mLightSensor;

	public AmbientLightManager(Context context) {
		this.mContext = context;
	}

	public void start(CameraManager cameraManager) {
		this.mCameraManager = cameraManager;
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		if (FrontLightMode.readPref(sharedPrefs) == FrontLightMode.AUTO) {
			SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
			mLightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
			if (mLightSensor != null) {
				sensorManager.registerListener(this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
			}
		}
	}

	public void stop() {
		if (mLightSensor != null) {
			SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
			sensorManager.unregisterListener(this);
			mCameraManager = null;
			mLightSensor = null;
		}
	}

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		float ambientLightLux = sensorEvent.values[0];
		if (mCameraManager != null) {
			if (ambientLightLux <= TOO_DARK_LUX) {
				mCameraManager.setTorch(true);
			} else if (ambientLightLux >= BRIGHT_ENOUGH_LUX) {
				mCameraManager.setTorch(false);
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// do nothing
	}

}
