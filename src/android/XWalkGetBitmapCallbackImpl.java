package com.ezartech.ezar.snapshot;

import android.graphics.Bitmap;
import android.view.View;

import org.xwalk.core.XWalkGetBitmapCallback;
import org.xwalk.core.XWalkView;


/**
 * Created by Zirk on 8/30/2016.
 */
public class XWalkGetBitmapCallbackImpl extends XWalkGetBitmapCallback implements WebviewBitmapProvider {

    private Snapshot snapshot;

    public XWalkGetBitmapCallbackImpl() {
        super();
    }

    //All params are ignored as xwalk webview will return a bitmap according to its own properties
    public void createBitmap(int width, int ht, View webview, int backgroundColor, Snapshot snapshot) {
        this.snapshot = snapshot;
        ((XWalkView)webview).captureBitmapAsync(this);
    }

//    XWalkGetBitmapCallback
    //Note: onFinishGetBitmap happens on the same thread as captureBitmapAsync, usually the UI thread.
    public void onFinishGetBitmap(Bitmap bitmap, int response) {
        //if response == 0, save this bitmap into a jpg file //otherwise errors.

        System.out.println("onFinishGetBitMap: " + response);

        if (response != 0) {
            //signal cordova error to user & discontinue snapshot action

            snapshot.returnCordovaError("Unable to capture Webview bitmap");
            return;
        }

        snapshot.buildAndSaveSnapshotImage(false, bitmap);
    }


//    private void captureContent() {
//        if ( xWalkView == null) return;
//        mXWalkGetBitmapCallback = new XWalkGetBitmapCallbackImpl();
//        xWalkView.captureBitmapAsync(mXWalkGetBitmapCallback);
//    }
}
