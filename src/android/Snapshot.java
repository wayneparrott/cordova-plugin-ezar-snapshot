package com.ezartech.ezar.snapshot;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.MediaActionSound;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by Zirk on 2/5/2016.
 */
public class Snapshot extends CordovaPlugin {
	private static final String TAG = "Snapshot";

	protected final static String[] permissions = {
			Manifest.permission.CAMERA,
			Manifest.permission.WRITE_EXTERNAL_STORAGE };
	public final static int PERMISSION_DENIED_ERROR = 20;
	public final static int CAMERA_SEC = 0;
	public final static int SAVE_TO_ALBUM_SEC = 1;

	private View webViewView;
	private MediaActionSound mSound;

	private CompressFormat  encoding;
	private boolean saveToPhotoAlbum;
	private CallbackContext callbackContext;

	private Bitmap snapshotBitmap;


	@Override
	public void initialize(final CordovaInterface cordova, final CordovaWebView cvWebView) {
		super.initialize(cordova, cvWebView);

		webViewView = cvWebView.getView();
		mSound = new MediaActionSound();
		mSound.load(MediaActionSound.SHUTTER_CLICK);
	}


    @Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		Log.v(TAG, action + " " + args.length());

		if (action.equals("snapshot")) {
			this.callbackContext = callbackContext;
			int encodingParam = args.getInt(0);  //JPG: 0, PNG: 1
			this.encoding = encodingParam == 0 ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
			this.saveToPhotoAlbum = args.getBoolean(1);

			this.snapshot(encoding, saveToPhotoAlbum, callbackContext);

			return true;
		}

		return false;
	}
    
    private void snapshot(final CompressFormat encoding, final boolean saveToPhotoAlbum, CallbackContext callbackContext) {
		Log.d(TAG, "snapshot");

		if (getActiveVOCamera() != null) {
			if (PermissionHelper.hasPermission(this, permissions[0])) {
				secureSnapshot(encoding, saveToPhotoAlbum, callbackContext);
			} else {
				PermissionHelper.requestPermission(this, CAMERA_SEC, Manifest.permission.CAMERA);
			}
		}
	}

	//copied from Apache Cordova plugin
	public void onRequestPermissionResult(int requestCode, String[] permissions,
										  int[] grantResults) throws JSONException {
		for(int r:grantResults) {
			if(r == PackageManager.PERMISSION_DENIED) {
				this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
				return;
			}
		}
		switch(requestCode) {
			case CAMERA_SEC:
				secureSnapshot(this.encoding, this.saveToPhotoAlbum, this.callbackContext);
			case SAVE_TO_ALBUM_SEC:
				saveToMediaStore();
				break;
		}
	}

	private void secureSnapshot(final CompressFormat encoding, final boolean saveToPhotoAlbum, final CallbackContext callbackContext) {
		Log.d(TAG, "secureSnapshot");

		Camera camera = getActiveVOCamera();
		if (camera != null) camera.startPreview();

		buildAndSaveSnapshotImageXXX(encoding, saveToPhotoAlbum, true, isVOPluginInstalled(), callbackContext);

//		Camera voCamera = getActiveVOCamera();
//		if (voCamera == null) {
//			buildAndSaveSnapshotImage(encoding, saveToPhotoAlbum, true,  null, voCamera, callbackContext);
//			return;
//		}
//
//		Camera.Parameters cameraParameters = voCamera.getParameters();
//		Camera.Size sz = cameraParameters.getPictureSize();
//		Log.v(TAG, "snapshot picture size:  " + sz.width + ":" + sz.height);
//
//		if (updateCameraPicOrientation(cameraParameters)) {
//			voCamera.setParameters(cameraParameters);
//		}
//
//		try {
//			//otherwise get image frame from video stream
//			voCamera.takePicture(
//					new Camera.ShutterCallback() {
//						@Override
//						public void onShutter() {
//							mSound.play(MediaActionSound.SHUTTER_CLICK);
//						}
//					},
//					null, null,
//					new Camera.PictureCallback() {
//						@Override
//						public void onPictureTaken(byte[] data, final Camera camera) {
//							buildAndSaveSnapshotImageXXX(encoding,
//									saveToPhotoAlbum,
//									false,
//									data,
//									camera,
//									callbackContext);
//						}
//					}
//			);
//		} catch( Exception ex) {
//			ex.printStackTrace();
//		}
	}

	private void buildAndSaveSnapshotImageXXX(final CompressFormat format, final boolean saveToGallery,
										   final boolean playSound,
										   final boolean includeVideoFrame,
										   final CallbackContext callbackContext) {


		final CordovaPlugin plugin = this;

		//resume preview after it automatically stopped during takePicture()

		webViewView.getRootView().post(new Runnable() {
			@Override
			public void run() {

				if (playSound) {
					mSound.play(MediaActionSound.SHUTTER_CLICK);
				}

				int webViewWidth = webViewView.getWidth();
				int webViewHt = webViewView.getHeight();

				//webViewWidth = 1920;
				Log.d(TAG, "WebView width: " + webViewWidth + "  ht: " + webViewHt);

				//create webView imageData
				Bitmap webViewBitmap = Bitmap.createBitmap(webViewWidth, webViewHt, Bitmap.Config.ARGB_8888);
				Canvas webViewCanvas = new Canvas(webViewBitmap);
				webViewView.draw(webViewCanvas);

				Bitmap resultBitmap = null;
				Canvas resultCanvas = null;

				if (includeVideoFrame) {

					TextureView cameraView = getVOCameraView();
					Bitmap scaledVideoFrameBitmap = cameraView.getBitmap();

					Log.d(TAG, "scaledVideoFrameBitmap2,  w: " + scaledVideoFrameBitmap.getWidth() + ": " + scaledVideoFrameBitmap.getHeight());

//					resultBitmap = scaledVideoFrameBitmap;

					//create new resultBitmap, set its bounds to cip to webview rect, draw videoFrameBitmap onto it
					resultBitmap = Bitmap.createBitmap(webViewWidth, webViewHt, Bitmap.Config.ARGB_8888);
					resultCanvas = new Canvas(resultBitmap);
					Rect dstRect = new Rect();
					resultCanvas.getClipBounds(dstRect);
					resultCanvas.drawBitmap(scaledVideoFrameBitmap, dstRect, dstRect, null);
					//scaledVideoFrameBitmap = null;

					//draw webviewBitmap on top of videoFrameBitmap, i.e., resultBitmap in the resultCanvas
					try {
						Paint p = new Paint();
						p.setAlpha(255);
						p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
						resultCanvas.drawBitmap(webViewBitmap, null, dstRect, p);

					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} else {
					//no videoFrameset webviewBitmap as the result
					resultBitmap = webViewBitmap;
				}

				snapshotBitmap = resultBitmap;
				String imageEncoded = encodeImageData(resultBitmap, format);

				if (saveToGallery) {
					if (PermissionHelper.hasPermission(plugin, permissions[1])) {
						saveToMediaStore();
					} else {
						PermissionHelper.requestPermission(plugin, SAVE_TO_ALBUM_SEC, Manifest.permission.WRITE_EXTERNAL_STORAGE);
					}
				}

				callbackContext.success(imageEncoded);

				resultBitmap = null;
				resultCanvas = null;
				webViewBitmap = null;
				webViewCanvas = null;
				imageEncoded = null;

			}
		}); //post
	}


	private void buildAndSaveSnapshotImageXXXxxx(final CompressFormat format, final boolean saveToGallery,
											  final boolean playSound,
											  final byte[] videoFrameData,
											  final Camera camera,
											  final CallbackContext callbackContext) {

		final boolean includeVideoFrame = !(videoFrameData == null || camera == null);

		//resume preview after it automatically stopped during takePicture()
		if (includeVideoFrame) {
			camera.startPreview();
		}

		webViewView.getRootView().post(new Runnable() {
			@Override
			public void run() {

				if (playSound) {
					mSound.play(MediaActionSound.SHUTTER_CLICK);
				}

				int webViewWidth = webViewView.getWidth();
				int webViewHt = webViewView.getHeight();

				//webViewWidth = 1920;
				Log.d(TAG, "WebView width: " + webViewWidth + "  ht: " + webViewHt);

				//create webView imageData
				Bitmap webViewBitmap = Bitmap.createBitmap(webViewWidth, webViewHt, Bitmap.Config.ARGB_8888);
				Canvas webViewCanvas = new Canvas(webViewBitmap);
				webViewView.draw(webViewCanvas);

				Bitmap resultBitmap = null;
				Canvas resultCanvas = null;

				Bitmap crap = null;
				if (includeVideoFrame) {
					Bitmap rawVideoFrameBitmap = BitmapFactory.decodeByteArray(videoFrameData, 0, videoFrameData.length);
					int videoFrameWidth = rawVideoFrameBitmap.getWidth();
					int videoFrameHt = rawVideoFrameBitmap.getHeight();

//					if (isPortraitOrientation()) {
//						videoFrameWidth = rawVideoFrameBitmap.getHeight();
//						videoFrameHt = rawVideoFrameBitmap.getWidth();
//					}

					Log.d(TAG, "build snapshot,  videoframe w: " + videoFrameWidth + "  h: " + videoFrameHt);

					//fit videoFrame bitmap into webView rect
					//try horizontal fit
					float scaleX = (float) webViewWidth / (float) videoFrameWidth;
					int scaledVideoFrameHt = (int) (scaleX * (float) videoFrameHt);
					int scaledVideoFrameHtDelta = scaledVideoFrameHt - webViewHt;

					//try vertical fit
					float scaleY = (float) webViewHt / (float) videoFrameHt;
					int scaledVideoFrameWidth = (int) (scaleY * (float) videoFrameWidth);
					int scaledVideoFrameWidthDelta = scaledVideoFrameWidth - webViewWidth;

					//select the smallest scaled dimension > than the corresponding webView dimension
					float scale = 1.0f;
					if (0 < scaledVideoFrameHtDelta && scaledVideoFrameWidthDelta < scaledVideoFrameHtDelta) {
						scale = scaleX;
						scaledVideoFrameWidth = webViewWidth;
					} else {
						scale = scaleY;
						scaledVideoFrameHt = webViewHt;
					}

					crap = rawVideoFrameBitmap;

//					float scaleX = 1.0f;
//					float scaleY = 1.3214285f;
////					if (isPortraitOrientation()) {
////						scaleX = (float) webViewHt / (float) previewHeight * (float) previewWidth / (float) webViewWidth;
////					} else {
////						scaleY = (float) webViewWidth / (float) previewWidth * (float) previewHeight / (float) webViewHt;
////					}

					Bitmap scaledVideoFrameBitmap =
							Bitmap.createScaledBitmap(
									rawVideoFrameBitmap,
									scaledVideoFrameWidth,
									scaledVideoFrameHt,
									true);

					Log.d(TAG, "scale: " + scale);
					Log.d(TAG, "scaledVideoFrameBitmap,  w: " + scaledVideoFrameBitmap.getWidth() + ": " + scaledVideoFrameBitmap.getHeight());

					int startX = (scaledVideoFrameWidth - webViewWidth) / 2;
					int startY = (scaledVideoFrameHt - webViewHt) / 2;
					scaledVideoFrameBitmap =
							Bitmap.createBitmap(
									scaledVideoFrameBitmap,
									startX, startY,
									webViewWidth, webViewHt,
									new Matrix(),
									false);

					Log.d(TAG, "FinalScaledVideoFrameBitmap,  w: " + scaledVideoFrameBitmap.getWidth() + ": " + scaledVideoFrameBitmap.getHeight());

//					scaledVideoFrameBitmap =
//						Bitmap.createScaledBitmap(
//								rawVideoFrameBitmap,
//								scaledVideoFrameWidth,
//								webViewHt,
//								true);

//					Log.d(TAG, "scaledVideoFrameBitmap2,  w: " + scaledVideoFrameBitmap.getWidth() + ": " + scaledVideoFrameBitmap.getHeight());

					//create new resultBitmap, set its bounds to cip to webview rect, draw videoFrameBitmap onto it
					resultBitmap = Bitmap.createBitmap(webViewWidth, webViewHt, Bitmap.Config.ARGB_8888);
					resultCanvas = new Canvas(resultBitmap);
					Rect dstRect = new Rect();
					resultCanvas.getClipBounds(dstRect);
					resultCanvas.drawBitmap(scaledVideoFrameBitmap, dstRect, dstRect, null);
					scaledVideoFrameBitmap = null;

					//draw webviewBitmap on top of videoFrameBitmap, i.e., resultBitmap in the resultCanvas
					try {
						Paint p = new Paint();
						p.setAlpha(255);
						p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
						resultCanvas.drawBitmap(webViewBitmap, null, dstRect, p);

					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} else {
					//no videoFrameset webviewBitmap as the result
					resultBitmap = webViewBitmap;
				}

				if (saveToGallery) {
					//save snapshot image to gallery
//					String url = saveToMediaStore(resultBitmap);
					String url = saveToMediaStore(crap);
					Log.i(TAG, "SAVED image: " + url);
				}

				//
				String imageEncoded = encodeImageData(resultBitmap, format);

				callbackContext.success(imageEncoded);

				resultBitmap = null;
				resultCanvas = null;
				webViewBitmap = null;
				webViewCanvas = null;
				imageEncoded = null;

			}
		}); //post
	}

	private void buildAndSaveSnapshotImage(final CompressFormat format, final boolean saveToGallery,
										   final boolean playSound,
										   final byte[] videoFrameData,
										   final Camera camera,
										   final CallbackContext callbackContext) {

		final boolean includeVideoFrame = !(videoFrameData == null || camera == null);

		//resume preview after it automatically stopped during takePicture()
		if (includeVideoFrame) {
			camera.startPreview();
		}

		webViewView.getRootView().post(new Runnable() {
			@Override
			public void run() {

				if (playSound) {
					mSound.play(MediaActionSound.SHUTTER_CLICK);
				}

				int webViewWidth = webViewView.getWidth();
				int webViewHt = webViewView.getHeight();
				Log.d(TAG, "WebView width: " + webViewWidth + "  ht: " + webViewHt);

				//create webView imageData
				Bitmap webViewBitmap = Bitmap.createBitmap(webViewWidth, webViewHt, Bitmap.Config.ARGB_8888);
				Canvas webViewCanvas = new Canvas(webViewBitmap);
				webViewView.draw(webViewCanvas);

				Bitmap resultBitmap = null;
				Canvas resultCanvas = null;

				if (includeVideoFrame) {

					Bitmap rawVideoFrameBitmap = BitmapFactory.decodeByteArray(videoFrameData, 0, videoFrameData.length);
					int videoFrameWidth = rawVideoFrameBitmap.getWidth();
					int videoFrameHt = rawVideoFrameBitmap.getHeight();
					Log.d(TAG, "build snapshot,  videoframe w: " + videoFrameWidth + "  h: " + videoFrameHt);

					//fit videoFrame bitmap into webView rect


//					Bitmap videoFrameBitmap = Bitmap.createBitmap(rawVideoFrameBitmap, 0, 0, w, h, mtx, true);
					Bitmap videoFrameBitmap = Bitmap.createBitmap(rawVideoFrameBitmap, 0, 0, videoFrameWidth, videoFrameHt);
					rawVideoFrameBitmap = null;

					//create new resultBitmap, set its bounds to cip to webview rect, draw videoFrameBitmap onto it
					resultBitmap = Bitmap.createBitmap(webViewWidth, webViewHt, Bitmap.Config.ARGB_8888);
					resultCanvas = new Canvas(resultBitmap);
					Rect dstRect = new Rect();
					resultCanvas.getClipBounds(dstRect);
					resultCanvas.drawBitmap(videoFrameBitmap, null, dstRect, null);
					videoFrameBitmap = null;

					//draw webviewBitmap on top of videoFrameBitmap, i.e., resultBitmap in the resultCanvas
					try {
						Paint p = new Paint();
						p.setAlpha(255);
						p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
						resultCanvas.drawBitmap(webViewBitmap, null, dstRect, p);

					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} else {
					//no videoFrameset webviewBitmap as the result
					resultBitmap = webViewBitmap;
				}

				if (saveToGallery) {
					//save snapshot image to gallery
					String url = saveToMediaStore(resultBitmap);
					Log.i(TAG, "SAVED image: " + url);
				}

				//
				String imageEncoded = encodeImageData(resultBitmap, format);

				callbackContext.success(imageEncoded);

				resultBitmap = null;
				resultCanvas = null;
				webViewBitmap = null;
				webViewCanvas = null;
				imageEncoded = null;

			}
		}); //post
	}

	private void saveToMediaStore() {
		saveToMediaStore(snapshotBitmap);
		snapshotBitmap = null;
	}

	private String saveToMediaStore(Bitmap imageData) {
		//save snapshot image to gallery
		String title = "" + System.currentTimeMillis();
		String url =
				MediaStore.Images.Media.insertImage(
						cordova.getActivity().getContentResolver(),
						imageData, title, "");

		Log.i(TAG, "SAVED image: " + url);
		return url;
	}

	private String encodeImageData(Bitmap imageData, CompressFormat encoding) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		imageData.compress(encoding, 100, baos);
		byte[] bytes = baos.toByteArray();
		String imageEncoded = Base64.encodeToString(bytes, Base64.DEFAULT);

		try {
			baos.close();
		} catch (Exception ex) {
			//do nothing during clean up
		}

		return imageEncoded;
	}

	private boolean isPortraitOrientation() {
		Display display = cordova.getActivity().getWindowManager().getDefaultDisplay();
		boolean isPortrait = display.getRotation() == Surface.ROTATION_0 || display.getRotation() == Surface.ROTATION_180;
		return isPortrait;
	}

	private boolean updateCameraPicOrientation(Camera.Parameters parameters) {
		int cameraId = getActiveVOCameraId();
		if (cameraId < 0) return false; //totally jacked

		int orientation = cordova.getActivity().getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (orientation) {
			case Surface.ROTATION_0:
				degrees = 0;
				break;
			case Surface.ROTATION_90:
				degrees = 270; //compensate by 180
				break;
			case Surface.ROTATION_180:
				degrees = 180;
				break;
			case Surface.ROTATION_270:
				degrees = 90; //compensate by 180
				break;
		}

		Camera.CameraInfo info = new Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, info);
		//orientation = (orientation + 45) / 90 * 90;

		int rotation = 0;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			rotation = (info.orientation - degrees + 360) % 360;
		} else {  // back-facing camera
			rotation = (info.orientation + degrees) % 360;
		}

		parameters.setRotation(rotation);

		return true;
	}

//	private Matrix computePictureTransform(int width, int height) {
//		Log.d(TAG, "computePICTURETransform, width: " + width + " ht: " + height);
//
//		Camera.Parameters cameraParameters = camera.getParameters();
//		Camera.Size sz = cameraParameters.getPictureSize();
//		Log.d(TAG, "computePICTURETransform, pic width: " + sz.width + " pic ht: " + sz.height);
//
//		boolean isPortrait = false;
//
//		Display display = activity.getWindowManager().getDefaultDisplay();
//		if (display.getRotation() == Surface.ROTATION_0 || display.getRotation() == Surface.ROTATION_180)
//			isPortrait = true;
//		else if (display.getRotation() == Surface.ROTATION_90 || display.getRotation() == Surface.ROTATION_270)
//			isPortrait = false;
//
//		int previewWidth = previewSizePair.pictureSize.width;
//		int previewHeight = previewSizePair.pictureSize.height;
//
//		if (isPortrait) {
//			previewWidth = previewSizePair.pictureSize.height;
//			previewHeight = previewSizePair.pictureSize.width;
//		}
//		float scaleX = 1;
//		float scaleY = 1;
//
//		if (isPortrait) {
//			scaleX = (float) height / (float) previewHeight * (float) previewWidth / (float) width;
//		} else {
//			scaleY = (float) width / (float) previewWidth * (float) previewHeight / (float) height;
//		}
//
//		scaleX = 1.0f;
//
//		Log.d(TAG, "computeMatrix, scaledX: " + scaleX + " scaleY: " + scaleY);
//
//		Matrix matrix = new Matrix();
//		matrix.setScale(scaleX, scaleY);
//
//		return matrix;
//	}

	//----------------------------------------------------------------------------
	private boolean isVOPluginInstalled() {
		return getVOPlugin() != null;
	}

	private CordovaPlugin getVOPlugin() {
		String pluginName = "videoOverlay";
		CordovaPlugin voPlugin = webView.getPluginManager().getPlugin(pluginName);
		return voPlugin;
	}

	private Camera getActiveVOCamera() {
		//reflectively access VideoOverlay plugin to get camera in same direction as lightLoc

		Camera camera = null;

		CordovaPlugin voPlugin = getVOPlugin();
		if (voPlugin == null) {
			return camera;
		}

		Method method = null;

		try {
			method = voPlugin.getClass().getMethod("getActiveCamera");
		} catch (SecurityException e) {
			//e.printStackTrace();
		} catch (NoSuchMethodException e) {
			//e.printStackTrace();
		}

		try {
			if (method == null) return camera;

			camera = (Camera)method.invoke(voPlugin);

		} catch (IllegalArgumentException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		} catch (IllegalAccessException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		} catch (InvocationTargetException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		}

		return camera;
	}

	private int getActiveVOCameraId() {
		//reflectively access VideoOverlay plugin to get camera id of running camera

		int cameraId = -1;

		CordovaPlugin voPlugin = getVOPlugin();
		if (voPlugin == null) {
			return cameraId;
		}

		Method method = null;

		try {
			method = voPlugin.getClass().getMethod("getActiveCameraId");
		} catch (SecurityException e) {
			//e.printStackTrace();
		} catch (NoSuchMethodException e) {
			//e.printStackTrace();
		}

		try {
			if (method == null) return cameraId;

			cameraId = (Integer)method.invoke(voPlugin);

		} catch (IllegalArgumentException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		} catch (IllegalAccessException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		} catch (InvocationTargetException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		}

		return cameraId;
	}

	private TextureView getVOCameraView() {
		//reflectively access VideoOverlay plugin for the scale of pictureSize to previewSize setting

		CordovaPlugin voPlugin = getVOPlugin();
		if (voPlugin == null) {
			return null;
		}

		Method method = null;

		try {
			method = voPlugin.getClass().getMethod("getCameraView");
		} catch (SecurityException e) {
			//e.printStackTrace();
		} catch (NoSuchMethodException e) {
			//e.printStackTrace();
		}

		TextureView view = null;
		try {
			if (method == null) return null;

			view = (TextureView)method.invoke(voPlugin);

		} catch (IllegalArgumentException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		} catch (IllegalAccessException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		} catch (InvocationTargetException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		}

		return view;
	}

}
