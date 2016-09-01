package com.ezartech.ezar.snapshot;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

/**
 * Created by Zirk on 8/30/2016.
 */
public class BasicWebviewBitmapProvider implements WebviewBitmapProvider {

    @Override
    public void createBitmap(int width, int ht, View webview, int backgroundColor, Snapshot snapshot) {

        Bitmap webviewBitmap = Bitmap.createBitmap(width, ht, Bitmap.Config.ARGB_8888);
        Canvas webviewCanvas = webviewCanvas = new Canvas(webviewBitmap);

        if (backgroundColor >= 0) {
            webviewCanvas.drawColor(backgroundColor);
        }
        webview.draw(webviewCanvas);

        snapshot.buildAndSaveSnapshotImage(false,webviewBitmap);
    }
}
