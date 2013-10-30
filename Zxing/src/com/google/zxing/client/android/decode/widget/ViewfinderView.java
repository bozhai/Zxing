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

package com.google.zxing.client.android.decode.widget;

import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.R;
import com.google.zxing.client.android.R.color;
import com.google.zxing.client.android.camera.CameraManager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

	private static final int[] SCANNER_ALPHA = { 0, 64, 128, 192, 255, 192, 128, 64 };
	private static final long ANIMATION_DELAY = 80L;
	private static final int CURRENT_POINT_OPACITY = 0xA0;
	private static final int MAX_RESULT_POINTS = 20;
	private static final int POINT_SIZE = 6;

	private CameraManager mCameraManager;
	private final Paint mPaint;
	private Bitmap mFocusFrameBitmap;	//聚焦框图片
	private final int mShadeColor;	//阴影区域颜色
	private final int mFocusFrameColor;	//聚焦框颜色
	private Bitmap mCursorBitmap;	//扫描线
	private final int mLaserColor;	//聚集线条颜色
	private final int mResultPointColor;	//结果点颜色
	private int mScannerAlpha;
	private List<ResultPoint> mPossibleResultPoints;
	private List<ResultPoint> lastPossibleResultPoints;
	private Rect mCursorRect = null;
	private boolean mIsDown = false;
	

	// This constructor is used when the class is built from an XML resource.
	public ViewfinderView(Context context, AttributeSet attrs) {
		super(context, attrs);

		// Initialize these once for performance rather than calling them every
		// time in onDraw().
		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		Resources resources = getResources();
		mShadeColor = resources.getColor(R.color.viewfinder_shade);
		mFocusFrameColor = resources.getColor(R.color.result_view);
		mLaserColor = resources.getColor(R.color.viewfinder_laser);
		mResultPointColor = resources.getColor(R.color.possible_result_points);
		mFocusFrameBitmap = BitmapFactory.decodeResource(resources, R.drawable.barcode_scan_frame);
		mCursorBitmap = BitmapFactory.decodeResource(resources, R.drawable.barcode_scan_cursor);
		mScannerAlpha = 0;
		mPossibleResultPoints = new ArrayList<ResultPoint>(5);
		lastPossibleResultPoints = null;
	}

	public void setCameraManager(CameraManager cameraManager) {
		this.mCameraManager = cameraManager;
	}

	@Override
	public void onDraw(Canvas canvas) {
		if (mCameraManager == null) {
			return; // not ready yet, early draw before done configuring
		}
		
		Rect frame = mCameraManager.getFramingRect();
		if (frame == null) {
			return;
		}
		
		drawShade(canvas, mPaint, frame); //绘制阴影区域

		if (mFocusFrameBitmap != null) {
			// Draw the opaque result bitmap over the scanning rectangle
			mPaint.setAlpha(CURRENT_POINT_OPACITY);
			canvas.drawBitmap(mFocusFrameBitmap, null, frame, mPaint);
		} 
//		else {
			// Draw a red "laser scanner" line through the middle to show
			// decoding is active
			mScannerAlpha = (mScannerAlpha + 1) % SCANNER_ALPHA.length;
			int middle = frame.height() / 2 + frame.top;
//			canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, mPaint);
			if(mCursorBitmap != null) {
				int height = mCursorBitmap.getHeight();
				if(mCursorRect == null) {
					mCursorRect = new Rect(frame.left, middle - height /2 ,frame.right, middle + height / 2);
				}
				canvas.drawBitmap(mCursorBitmap, null, mCursorRect, mPaint);
				if(mIsDown) {
					mCursorRect.top += 10;
					mCursorRect.bottom += 10;
//					middle += 2;
					if(mCursorRect.bottom > frame.bottom) {
						mIsDown = false;
					}
				} else {
					mCursorRect.top -= 10;
					mCursorRect.bottom -= 10;
//					middle -= 2;
					if(mCursorRect.top < frame.top) {
						mIsDown = true;
					}
				}
			}

			mPaint.setColor(mLaserColor);
			mPaint.setAlpha(SCANNER_ALPHA[mScannerAlpha]);
			Rect previewFrame = mCameraManager.getFramingRectInPreview();
			float scaleX = frame.width() / (float) previewFrame.width();
			float scaleY = frame.height() / (float) previewFrame.height();

			List<ResultPoint> currentPossible = mPossibleResultPoints;
			List<ResultPoint> currentLast = lastPossibleResultPoints;
			int frameLeft = frame.left;
			int frameTop = frame.top;
			if (currentPossible.isEmpty()) {
				lastPossibleResultPoints = null;
			} else {
				mPossibleResultPoints = new ArrayList<ResultPoint>(5);
				lastPossibleResultPoints = currentPossible;
				mPaint.setAlpha(CURRENT_POINT_OPACITY);
				mPaint.setColor(mResultPointColor);
				synchronized (currentPossible) {
					for (ResultPoint point : currentPossible) {
						canvas.drawCircle(frameLeft
								+ (int) (point.getX() * scaleX), frameTop
								+ (int) (point.getY() * scaleY), POINT_SIZE,
								mPaint);
					}
				}
			}
			if (currentLast != null) {
				mPaint.setAlpha(CURRENT_POINT_OPACITY / 2);
				mPaint.setColor(mResultPointColor);
				synchronized (currentLast) {
					float radius = POINT_SIZE / 2.0f;
					for (ResultPoint point : currentLast) {
						canvas.drawCircle(frameLeft
								+ (int) (point.getX() * scaleX), frameTop
								+ (int) (point.getY() * scaleY), radius, mPaint);
					}
				}
			}

			// Request another update at the animation interval, but only
			// repaint the laser line,
			// not the entire viewfinder mask.
			postInvalidateDelayed(ANIMATION_DELAY, frame.left + POINT_SIZE,
					frame.top + POINT_SIZE, frame.right - POINT_SIZE,
					frame.bottom - POINT_SIZE);
//		}
	}

	public void drawViewfinder() {
//		Bitmap resultBitmap = this.mResultBitmap;
//		this.mResultBitmap = null;
//		if (resultBitmap != null) {
//			resultBitmap.recycle();
//		}
		invalidate();
	}

	/**
	 * Draw a bitmap with the result points highlighted instead of the live
	 * scanning display.
	 * 
	 * @param barcode An image of the decoded barcode.
	 */
	public void drawResultBitmap(Bitmap barcode) {
		mFocusFrameBitmap = barcode;
		invalidate();
	}

	public void addPossibleResultPoint(ResultPoint point) {
		List<ResultPoint> points = mPossibleResultPoints;
		synchronized (points) {
			points.add(point);
			int size = points.size();
			if (size > MAX_RESULT_POINTS) {
				// trim it
				points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
			}
		}
	}
	
	/**
	 * 绘制阴影区域
	 * @param canvas
	 * @param paint
	 * @param frame
	 */
	private void drawShade(Canvas canvas, Paint paint, Rect frame) {
		int width = canvas.getWidth();
		int height = canvas.getHeight();
		// Draw the exterior (i.e. outside the framing rect) darkened
		mPaint.setColor(mShadeColor);
		canvas.drawRect(0, 0, width, frame.top, paint);
		canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
		canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
		canvas.drawRect(0, frame.bottom + 1, width, height, paint);
	}

}
