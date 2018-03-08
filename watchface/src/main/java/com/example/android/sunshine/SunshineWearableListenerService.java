package com.example.android.sunshine;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class SunshineWearableListenerService extends WearableListenerService {
    // The Paths and Keys used to send data items to the wearable device
    public static final String PATH_WEATHER_INFO = "/weather-info";
    public static final String PATH_WEATHER_IMAGE = "/weather-image";
    public static final String KEY_HIGH = "high";
    public static final String KEY_LOW = "low";
    public static final String KEY_IMAGE = "image";

    // Connection time out for GoogleApiClient
    final long TIMEOUT_MS = 10000;  // 10 seconds

    // Create the GoogleApiCLient object
    GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
            .addApi(Wearable.API)
            .build();

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for(DataEvent dataEvent : dataEventBuffer) {
            if(dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                String path = dataEvent.getDataItem().getUri().getPath();
                if(path.equals(PATH_WEATHER_INFO)) {
                    String high = dataMap.getString(KEY_HIGH);
                    String low = dataMap.getString(KEY_LOW);
                } else if(path.equals(PATH_WEATHER_IMAGE)) {
                    Asset imageResId = dataMap.getAsset(KEY_IMAGE);
                }
            }
        }
    }

    public Bitmap loadBitmapFromAsset(Asset asset) {
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
