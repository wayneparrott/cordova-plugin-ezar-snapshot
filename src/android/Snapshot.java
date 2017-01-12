package com.ezartech.ezar.snapshot;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import android.net.Uri;
import android.os.Environment;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Zirk on 2/5/2016.
 */
//todo clean up:
// 0) clear state between exec calls from js
// 1) permission impl

public class Snapshot extends CordovaPlugin {
	private static final String TAG = "Snapshot";

	protected final static String[] permissions = {
			Manifest.permission.CAMERA,
			Manifest.permission.WRITE_EXTERNAL_STORAGE };
	public final static int PERMISSION_DENIED_ERROR = 20;
	public final static int CAMERA_SEC = 0;
	public final static int SAVE_TO_ALBUM_SEC = 1;

	public final static int DEFAULT_BACKGROUND_COLOR = Color.WHITE;
	private final static int NO_COLOR = -1;

	private View webViewView;
	private MediaActionSound mSound;

	private CompressFormat  encoding;
	private int quality; //[1,100]
	private int scale;   //[1-100]
	private boolean saveToPhotoGallery;
	private String imageName;
	private boolean includeCameraView = true;
	private boolean includeWebView = true;
	private CallbackContext callbackContext;

	private byte[] snapshotBytes;
	private Bitmap snapshotBitmap;
	private String saveToMediaStoreUrl;
	private File imageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

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
			this.saveToPhotoGallery = args.getBoolean(3);  //redundant for saveToPhotoGallery action
			this.imageName = args.getString(4);
			this.includeCameraView = args.getBoolean(5) && getVOPlugin() != null;
			this.includeWebView = args.getBoolean(6);

			//todo check for includeCameraView & includeWebView == false, which is an error
			this.snapshot();

			return true;
		} else if (action.equals("saveToPhotoGallery")) {
			actionContext = ActionContext.SAVE_TO_GALLERY;
			String imageData = args.getString(0);
			int encodingParam = args.getInt(1);  //JPG: 0, PNG: 1
			this.encoding = encodingParam == 0 ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
			this.quality = args.getInt(2);
			this.scale = args.getInt(3);
			this.saveToPhotoGallery = args.getBoolean(4);  //redundant for saveToPhotoGallery action
			this.imageName = args.getString(5);
		
			snapshotBitmap = base64String2Bitmap(imageData);
			snapshotBytes = bitmap2Bytes(snapshotBitmap, encoding, quality, scale);
			this.saveToPhotoGallery();
			return true; 
		}
		return false;
	}

	public void returnBitmap(byte[] bitmap) {
		callbackContext.success(bytesToBase64String(bitmap));
	}

	public void returnCordovaError(String msg) {
		callbackContext.error(msg);
		//callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, msg));
	}

	public void returnCordovaError(int msg) {
		callbackContext.error(msg);
		//callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, msg));
	}

	//clear state
	private void reset() {
		//clear parameters
		encoding = CompressFormat.JPEG;
		quality = 50;
		scale = 100;
		imageName = "";
		callbackContext = null;
		includeCameraView = true;
		includeWebView = true;

		//clear internal state
		snapshotBytes = null;
		snapshotBitmap = null;
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
				secureSnapshot();
			case SAVE_TO_ALBUM_SEC:
				saveToMediaStore();
				break;
		}
	}

    private void snapshot() {
		Log.d(TAG, "snapshot");

		if (includeCameraView) {
			if (PermissionHelper.hasPermission(this, permissions[0])) {
				secureSnapshot();
			} else {
				PermissionHelper.requestPermission(this, CAMERA_SEC, Manifest.permission.CAMERA);
			}
		} else {
			secureSnapshot();
		}
	}

	private void saveToPhotoGallery() {
//		byte[] decodedBytes = Base64.decode(imageData, 0);
//		snapshotBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
		if (snapshotBitmap == null) {
			returnCordovaError("Unable to convert image data to image");
			return;
		}
		saveToMediaStore();
	}


	private void secureSnapshot() {
		Log.d(TAG, "secureSnapshot");
		buildAndSaveSnapshotImage(true);
	}

	public void buildAndSaveSnapshotImage(final boolean playSound) {
		buildAndSaveSnapshotImage(playSound,null);
	}

	public void buildAndSaveSnapshotImage(final boolean playSound, final Bitmap webviewBitmap) {

		//must resume preview after it automatically stopped during takePicture()

		webViewView.getRootView().post(new Runnable() {
			@Override
			public void run() {

				if (playSound) {
					mSound.play(MediaActionSound.SHUTTER_CLICK);
				}

				int webViewWidth = webViewView.getWidth();
				int webViewHt = webViewView.getHeight();

				Log.d(TAG, "WebView width: " + webViewWidth + "  ht: " + webViewHt);

				if (includeWebView && webviewBitmap == null) {
					WebviewBitmapProvider bitmapProvider = createWebviewBitmapProvider();

					bitmapProvider.createBitmap(webViewWidth, webViewHt, webViewView,
								includeCameraView ? getBackgroundColor() : NO_COLOR, Snapshot.this);

					return; // delegate to provider to callback to this method with webviewBitmap defined
				}

				//move scaling to bitmapToBytes encoding
//				int scaledWidth = (int)(webViewWidth * scale / 100.0f);
//				int scaledHt = (int)(webViewHt * scale / 100.0f);
				int scaledWidth = webViewWidth;
				int scaledHt = webViewHt;
				Bitmap resultBitmap = Bitmap.createBitmap(scaledWidth, scaledHt, Bitmap.Config.ARGB_8888);
				Canvas resultCanvas = new Canvas(resultBitmap);
				Rect dstRect = new Rect();
				resultCanvas.getClipBounds(dstRect);

				if (includeCameraView) {

					TextureView cameraView = getVOCameraView();
					Bitmap scaledVideoFrameBitmap = cameraView.getBitmap();

					//Log.d(TAG, "scaledVideoFrameBitmap2,  w: " + scaledVideoFrameBitmap.getWidth() + ": " + scaledVideoFrameBitmap.getHeight());

					//create new resultBitmap, set its bounds to clip to webview rect, draw videoFrameBitmap onto it
					resultCanvas.drawBitmap(scaledVideoFrameBitmap, null, dstRect, null);
				}

				if (includeWebView) {
					//draw webviewBitmap on top of videoFrameBitmap, i.e., resultBitmap in the resultCanvas
					try {
						Paint p = null;
						if (includeCameraView) { 
							p = new Paint();
							p.setAlpha(includeCameraView ? 255 : 0);
							p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
						}
						resultCanvas.drawBitmap(webviewBitmap, null, dstRect, p);

					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}

				snapshotBitmap = resultBitmap;
				snapshotBytes = bitmap2Bytes(resultBitmap, encoding, quality, scale);

				if (saveToPhotoGallery) {
					saveToPhotoGallery();
				}

				returnBitmap(snapshotBytes);

				//clean up
				//resultBitmap = null;
				//resultCanvas = null;
				//webviewBitmap = null;
				//webViewCanvas = null;
			}
		});
	}

	private WebviewBitmapProvider createWebviewBitmapProvider() {

		WebviewBitmapProvider provider = null;

		if ("org.xwalk.core.XWalkView".equals(webViewView.getClass().getName()) ||
		    "org.crosswalk.engine.XWalkCordovaView".equals(webViewView.getClass().getName())) {

			try {
				Class<?> clazz = Class.forName("com.ezartech.ezar.snapshot.XWalkGetBitmapCallbackImpl");
				provider = (WebviewBitmapProvider) clazz.newInstance();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		if (provider == null) {
			provider = new BasicWebviewBitmapProvider();
		}

		return provider;
	}


	//snapshotBytes must be set before calling this method
	private void saveToMediaStore() {
		if (PermissionHelper.hasPermission(this, permissions[1])) {
			saveToMediaStoreUrl = this.secureSaveToMediaStore();
			if (actionContext == ActionContext.SAVE_TO_GALLERY) {
				callbackContext.success(saveToMediaStoreUrl);
				reset();
			}
			snapshotBitmap = null;
		} else {
			PermissionHelper.requestPermission(this, SAVE_TO_ALBUM_SEC, Manifest.permission.WRITE_EXTERNAL_STORAGE);
		}
	}

	private String bytesToBase64String(byte[]bytes) {
		return Base64.encodeToString(bytes, Base64.DEFAULT);
	}

	private byte[] base64String2Bytes(String data) {
		return Base64.decode(data, 0);
	}

	private Bitmap base64String2Bitmap(String data) {
		byte[] bytes = base64String2Bytes(data);
		Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		return bitmap;
	}

	private byte[] bitmap2Bytes(Bitmap imageData, CompressFormat encoding, int quality, int scale) {
		Bitmap bmap = imageData;
		if (scale > 0 && scale < 100) {
			int scaledWidth = (int)(imageData.getWidth() * scale / 100.0f);
			int scaledHeight = (int)(imageData.getHeight() * scale / 100.0f);
			boolean filter = false;
			bmap = Bitmap.createScaledBitmap(imageData, scaledWidth, scaledHeight, filter);
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bmap.compress(encoding, quality, baos);
		byte[] bytes = baos.toByteArray();

		try {
			baos.close();
		} catch (Exception ex) {
			//do nothing during clean up
		}

		return bytes;
	}

	//based on http://stackoverflow.com/questions/28243330/add-image-to-media-gallery-android
	private String secureSaveToMediaStore() {

		//generate filename
		imageName = createImageName(imageName);
		String filenameExt =
				shouldAddExtension(imageName) ?("." + extensionForEncoding(this.encoding)) : "";
		String filename = imageName + filenameExt;

		//File imageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		File file =  new File(this.imageDir,filename);
		if (file.exists()) {
			imageName = '_' + imageName;
			return secureSaveToMediaStore();
		}

		try {
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(snapshotBytes);
			fos.flush();
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		ContentValues values = new ContentValues();
		values.put(MediaStore.Images.Media.TITLE, imageName);
		values.put(MediaStore.Images.Media.DISPLAY_NAME, imageName);
		values.put(MediaStore.Images.Media.DESCRIPTION, "");
		values.put(MediaStore.Images.Media.MIME_TYPE, "image/" + extensionForEncoding(this.encoding));
		values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
		values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
		values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());

		cordova.getActivity().getContentResolver().
				insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

		return file.getAbsolutePath();
	}

	private String createImageName(String proposedName) {

		String name = proposedName;

		//generate filename if proposedImageFilename is undefined
		if (proposedName == null || proposedName.trim().isEmpty()) {
			name =   "" + System.currentTimeMillis();
		} else {
			name = proposedName.trim();
		}

		//search for existing image with "name" in photo gallery
		Cursor cursor = null;
		try {
			String selection = MediaStore.Images.ImageColumns.TITLE + " = ?";
			String[] selectionArgs = { name };
			cursor = MediaStore.Images.Media.query(
					cordova.getActivity().getContentResolver(),
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					new String[]{MediaStore.MediaColumns.TITLE},
//					new String[]{"*"},
					selection, selectionArgs, null);

			if (cursor == null || cursor.getCount() == 0) {
				return name;
			}

		} finally {
			 if (cursor != null) {
				 cursor.close();
			 }
		}

		Pattern p = Pattern.compile("^(.+?)(\\(\\d+\\))?(\\..{1,5})?$");
		Matcher m = p.matcher(name);

		boolean hit = m.matches();
		if (!hit) {
			return name + "(1)";
		}
		String baseName = m.group(1);
		//System.out.println("basename:" + baseName);

		int newIndex = 1;
		String indexGrp = m.start(2) > 1 ? m.group(2) : null;
		if (indexGrp != null) {
			newIndex = Integer.parseInt( indexGrp.substring(1,indexGrp.length()-1) ) + 1;
		}

		String extGrp = m.start(3) > 1 ? m.group(3) : "";
		String newName = baseName + "(" + newIndex + ")" + extGrp;

		return createImageName(newName);
	}

	private String extensionForEncoding(Bitmap.CompressFormat format) {
		String result = "";
		if (format == CompressFormat.JPEG) {
			result = "jpg";
		} else if (format == CompressFormat.PNG) {
			result ="png";
		}
		return result;
	}

	//assume imageFilename.length() > 4 (the len of extension,e.g., .jpg)
	private boolean shouldAddExtension(String imageFilename) {
		String name = imageFilename.toLowerCase();
		return !name.endsWith(".jpg") && !name.endsWith(".png");
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
