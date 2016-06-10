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
//todo clean up:
// 0) clear state between exec calls from js
// 1) permission impl
// 2) includeCameraView impl
// 3) logic assumes camera is running

public class Snapshot extends CordovaPlugin {
	private static final String TAG = "Snapshot";

	protected final static String[] permissions = {
			Manifest.permission.CAMERA,
			Manifest.permission.WRITE_EXTERNAL_STORAGE };
	public final static int PERMISSION_DENIED_ERROR = 20;
	public final static int CAMERA_SEC = 0;
	public final static int SAVE_TO_ALBUM_SEC = 1;

	public final static int DEFAULT_BACKGROUND_COLOR = Color.WHITE;

	private View webViewView;
	private MediaActionSound mSound;

	private CompressFormat  encoding;
	private int quality;
	private int scale;
	private boolean saveToPhotoAlbum;
	private boolean includeCameraView = true;
	private boolean includeWebView = true;
	private CallbackContext callbackContext;

	private Bitmap snapshotBitmap;
	private String saveToMediaStoreUrl;

	enum ActionContext {
		SNAPSHOT,
		SAVE_TO_GALLERY
	};

	private ActionContext actionContext = null;

	@Override
	public void initialize(final CordovaInterface cordova, final CordovaWebView cvWebView) {
		super.initialize(cordova, cvWebView);

		webViewView = cvWebView.getView();
		mSound = new MediaActionSound();
		mSound.load(MediaActionSound.SHUTTER_CLICK);
	}


    @Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		Log.d(TAG, action + " " + args.length());

		this.callbackContext = callbackContext;
		
		if (action.equals("snapshot")) {
			actionContext = ActionContext.SNAPSHOT;
			int encodingParam = args.getInt(0);  //JPG: 0, PNG: 1
			this.encoding = encodingParam == 0 ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
			this.quality = args.getInt(1);
			this.scale = args.getInt(2);
			this.saveToPhotoAlbum = args.getBoolean(3);
			this.includeCameraView = args.getBoolean(4) && getVOPlugin() != null;
			this.includeWebView = args.getBoolean(5);

			//todo check for includeCameraView & includeWebView == false, which is an error
			this.snapshot(encoding, quality, saveToPhotoAlbum, callbackContext);

			return true;
		} else if (action.equals("saveToPhotoGallery")) {
			actionContext = ActionContext.SAVE_TO_GALLERY;
			String imageData = args.getString(0);
			this.saveToPhotoGallery(imageData);
			return true; 
		}
		return false;
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
				secureSnapshot(this.encoding, this.quality, this.saveToPhotoAlbum, this.callbackContext);
			case SAVE_TO_ALBUM_SEC:
				saveToMediaStore();
				break;
		}
	}

    private void snapshot(final CompressFormat encoding, final int quality, final boolean saveToPhotoAlbum, CallbackContext callbackContext) {
		Log.d(TAG, "snapshot");

		if (includeCameraView) {
			if (PermissionHelper.hasPermission(this, permissions[0])) {
				secureSnapshot(encoding, quality, saveToPhotoAlbum, callbackContext);
			} else {
				PermissionHelper.requestPermission(this, CAMERA_SEC, Manifest.permission.CAMERA);
			}
		} else {
			secureSnapshot(encoding, quality, saveToPhotoAlbum, callbackContext);
		}
	}

	private void saveToPhotoGallery(String imageData) {
		byte[] decodedBytes = Base64.decode(imageData, 0);
		snapshotBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
		if (snapshotBitmap == null) {
			callbackContext.error("Unable to convert image data to image");
			return;
		}
		saveToMediaStore();
	}


	private void secureSnapshot(final CompressFormat encoding, final int quality, final boolean saveToPhotoAlbum, final CallbackContext callbackContext) {
		Log.d(TAG, "secureSnapshot");

//		if (includeCameraView) {
//			Camera camera = getActiveVOCamera();
//			if (camera != null) {
//				//why start preview, I don't give a crap if camera is running or not
//				camera.startPreview();
//			}
//		}

		buildAndSaveSnapshotImage(encoding, saveToPhotoAlbum, true, callbackContext);
	}

	private void buildAndSaveSnapshotImage(final CompressFormat format, final boolean saveToGallery,
										   final boolean playSound,
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

				Log.d(TAG, "WebView width: " + webViewWidth + "  ht: " + webViewHt);

				//create webView imageData
				Bitmap webViewBitmap = null;
				Canvas webViewCanvas = null;
				if (includeWebView) {
					webViewBitmap = Bitmap.createBitmap(webViewWidth, webViewHt, Bitmap.Config.ARGB_8888);
					webViewCanvas = new Canvas(webViewBitmap);
					if (!includeCameraView) {
						webViewCanvas.drawColor(getBackgroundColor());
					}
					webViewView.draw(webViewCanvas);
				}

				int scaledWidth = (int)(webViewWidth * scale / 100.0f);
				int scaledHt = (int)(webViewHt * scale / 100.0f);
				Bitmap resultBitmap = Bitmap.createBitmap(scaledWidth, scaledHt, Bitmap.Config.ARGB_8888);
				Canvas resultCanvas = new Canvas(resultBitmap);
				Rect dstRect = new Rect();
				resultCanvas.getClipBounds(dstRect);

				if (includeCameraView) {

					TextureView cameraView = getVOCameraView();
					Bitmap scaledVideoFrameBitmap = cameraView.getBitmap();

					Log.d(TAG, "scaledVideoFrameBitmap2,  w: " + scaledVideoFrameBitmap.getWidth() + ": " + scaledVideoFrameBitmap.getHeight());

					//create new resultBitmap, set its bounds to clip to webview rect, draw videoFrameBitmap onto it
					resultCanvas.drawBitmap(scaledVideoFrameBitmap, null, dstRect, null);
				}

				if (includeWebView) {
					//draw webviewBitmap on top of videoFrameBitmap, i.e., resultBitmap in the resultCanvas
					try {
						Paint p = new Paint();
						p.setAlpha(includeCameraView ? 255 : 0);
						p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
						resultCanvas.drawBitmap(webViewBitmap, null, dstRect, p);

					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}

				snapshotBitmap = resultBitmap;
				String imageEncoded = encodeImageData(resultBitmap, format, quality);

				if (saveToGallery) {
					saveToMediaStore();
				}

				callbackContext.success(imageEncoded);

				resultBitmap = null;
				resultCanvas = null;
				webViewBitmap = null;
				webViewCanvas = null;
				imageEncoded = null;
				includeCameraView = true;
				includeWebView = true;
			}
		}); //post
	}


	private void saveToMediaStore() {
		if (PermissionHelper.hasPermission(this, permissions[1])) {
			saveToMediaStoreUrl = saveToMediaStore(snapshotBitmap);
			if (actionContext == ActionContext.SAVE_TO_GALLERY) {
				callbackContext.success(saveToMediaStoreUrl);
			}
			snapshotBitmap = null;
		} else {
			PermissionHelper.requestPermission(this, SAVE_TO_ALBUM_SEC, Manifest.permission.WRITE_EXTERNAL_STORAGE);
		}

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

	private String encodeImageData(Bitmap imageData, CompressFormat encoding, int quality) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		imageData.compress(encoding, quality, baos);
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

	private int getBackgroundColor() {
		//reflectively access VideoOverlay plugin to get background property

		int color = DEFAULT_BACKGROUND_COLOR;

		CordovaPlugin voPlugin = getVOPlugin();
		if (voPlugin == null) {
			return color;
		}

		Method method = null;

		try {
			method = voPlugin.getClass().getMethod("getBackgroundColor");
		} catch (SecurityException e) {
			//e.printStackTrace();
		} catch (NoSuchMethodException e) {
			//e.printStackTrace();
		}

		try {
			if (method == null) return color;

			color = (Integer)method.invoke(voPlugin);

		} catch (IllegalArgumentException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		} catch (IllegalAccessException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		} catch (InvocationTargetException e) { // exception handling omitted for brevity
			//e.printStackTrace();
		}

		return color;
	}
}
