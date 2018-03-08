package com.example.android.sunshine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Created by Hassan on 3/2/2017.
 */

public class WatchFaceUtility {
    // The Paths and Keys used to send data items to the wearable device
    public static final String PATH_WEATHER_INFO = "/weather-info";
    public static final String PATH_WEATHER_IMAGE = "/weather-image";
    public static final String KEY_HIGH = "high";
    public static final String KEY_LOW = "low";
    public static final String KEY_IMAGE = "image";

    // Connection time out for GoogleApiClient
    static final long TIMEOUT_MS = 10000;  // 10 seconds

    public static Bitmap loadBitmapFromAsset(Asset asset, Context context) {
        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .build();

        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        ConnectionResult result =
                mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w("wearableservice", "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }
}
