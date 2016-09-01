package com.ezartech.ezar.snapshot;

import android.view.View;

/**
 * Created by Zirk on 8/30/2016.
 */
public interface WebviewBitmapProvider {

    void createBitmap(int width, int ht, View webview, int backgroundColor, Snapshot snapshot);
}
