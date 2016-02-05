package com.ezartech.ezar.snapshot;

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
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
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

	private View webViewView;
	private MediaActionSound mSound;



	@Override
	public void initialize(final CordovaInterface cordova, final CordovaWebView cvWebView) {
		super.initialize(cordova, cvWebView);

		webViewView = cvWebView.getView();
		mSound = new MediaActionSound();
		mSound.load(MediaActionSound.SHUTTER_CLICK);

//		cordova.getActivity().runOnUiThread(new Runnable() {
//			@Override
//			public void run() {
//				mSound = new MediaActionSound();
//				mSound.load(MediaActionSound.SHUTTER_CLICK);
//			}
//		});
	}


    @Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		Log.v(TAG, action + " " + args.length());

		if (action.equals("snapshot")) {
			//TODO: process args
			int encodingParam = 0;  //JPG: 0, PNG: 1
			boolean saveToPhotoAlbum = true;
			Bitmap.CompressFormat encoding =
					encodingParam == 0 ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;

			this.snapshot(encoding, saveToPhotoAlbum, callbackContext);

			return true;
		}

		return false;
	}
    
    private void snapshot(final CompressFormat encoding, final boolean saveToPhotoAlbum, final CallbackContext callbackContext) {
		Log.d(TAG, "snapshot");

		Camera voCamera = getActiveVOCamera();
		if (voCamera == null) {
			buildAndSaveSnapshotImage(encoding, saveToPhotoAlbum, true, null, voCamera, callbackContext);
			return;
		}

		//otherwise get image frame from video stream
		voCamera.takePicture(
				new Camera.ShutterCallback() {
					@Override
					public void onShutter() {
						mSound.play(MediaActionSound.SHUTTER_CLICK);
					}
				},
				null, null,
				new Camera.PictureCallback() {
					@Override
					public void onPictureTaken(byte[] data, final Camera camera) {
						buildAndSaveSnapshotImage(encoding, saveToPhotoAlbum,
								false,
								data, camera,
								callbackContext);
					}
				}
		);
	}

	private void buildAndSaveSnapshotImage(CompressFormat format, final boolean saveToGallery,
										   final boolean playSound,
										   final byte[] videoFrameData, final Camera camera,
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

				//create webView imageData
				Bitmap webViewBitmap = Bitmap.createBitmap(webViewView.getWidth(), webViewView.getHeight(), Bitmap.Config.ARGB_8888);
				Canvas webViewCanvas = new Canvas(webViewBitmap);
				webViewView.draw(webViewCanvas);

				Bitmap resultBitmap = null;
				Canvas resultCanvas = null;

				if (includeVideoFrame) {

					Bitmap rawVideoFrameBitmap = BitmapFactory.decodeByteArray(videoFrameData, 0, videoFrameData.length);
					int w = rawVideoFrameBitmap.getWidth();
					int h = rawVideoFrameBitmap.getHeight();
					Log.i(TAG, "build snapshot,  videoframe w: " + w + "  h: " + h);

					//Matrix mtx = computePictureTransform(1200,1824);
					Matrix mtx = new Matrix();
					mtx.setScale(1.5f, 0.5f);

					Bitmap videoFrameBitmap = Bitmap.createBitmap(rawVideoFrameBitmap, 0, 0, w, h, mtx, true);
					rawVideoFrameBitmap = null;

					//create new resultBitmap, set its bounds to cip to webview rect, draw videoFrameBitmap onto it
					resultBitmap = Bitmap.createBitmap(webViewView.getWidth(), webViewView.getHeight(), Bitmap.Config.ARGB_8888);
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

				//save snapshot image to gallery
				String url = saveToMediaStore(resultBitmap);
				Log.i(TAG, "SAVED image: " + url);

				//
				String imageEncoded = encodeImageData(resultBitmap,CompressFormat.JPEG);

				callbackContext.success(imageEncoded);

				resultBitmap = null;
				resultCanvas = null;
				webViewBitmap = null;
				webViewCanvas = null;
				imageEncoded = null;

			}
		}); //post
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

	private boolean isVOPluginInstalled() {
		return getVoPlugin() != null;
	}

	private CordovaPlugin getVoPlugin() {
		String pluginName = "videoOverlay";
		CordovaPlugin voPlugin = webView.getPluginManager().getPlugin(pluginName);
		return voPlugin;
	}

	private Camera getActiveVOCamera() {
		//reflectively access VideoOverlay plugin to get camera in same direction as lightLoc

		Camera camera = null;

		CordovaPlugin voPlugin = getVoPlugin();
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

}
