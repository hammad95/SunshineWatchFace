/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.example.android.sunshine.sync;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.MainActivity;
import com.example.android.sunshine.R;
import com.example.android.sunshine.data.WeatherContract;
import com.example.android.sunshine.utilities.SunshineWeatherUtils;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.firebase.jobdispatcher.RetryStrategy;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;


public class SunshineFirebaseJobService extends JobService
        implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

    final String LOG_TAG = SunshineFirebaseJobService.class.getSimpleName();

    private Context context;

    // The client used to communicate with the wearable
    GoogleApiClient mGoogleApiClient;

    // The Paths and Keys used to send data items to the wearable device
    public static final String PATH_WEATHER_INFO = "/weather-info";
    public static final String PATH_WEATHER_IMAGE = "/weather-image";
    public static final String KEY_HIGH = "high";
    public static final String KEY_LOW = "low";
    public static final String KEY_IMAGE = "image";

    private AsyncTask<Void, Void, Void> mFetchWeatherTask;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(LOG_TAG, "on create job called");

        context = getApplicationContext();

        // Create the GoogleApiCLient object
        mGoogleApiClient = new GoogleApiClient.Builder(SunshineFirebaseJobService.this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // Connect the GoogleApiClient to Google Play Services
        mGoogleApiClient.connect();
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "on destroy job called");
        // Disconnect the GoogleApiClient
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected())
            mGoogleApiClient.disconnect();

        super.onDestroy();
    }

    /**
     * The entry point to your Job. Implementations should offload work to another thread of
     * execution as soon as possible.
     *
     * This is called by the Job Dispatcher to tell us we should start our job. Keep in mind this
     * method is run on the application's main thread, so we need to offload work to a background
     * thread.
     *
     * @return whether there is more work remaining.
     */
    @Override
    public boolean onStartJob(final JobParameters jobParameters) {

        Log.d(LOG_TAG, "on start job called");

        mFetchWeatherTask = new AsyncTask<Void, Void, Void>(){
            @Override
            protected Void doInBackground(Void... voids) {
                Cursor dataCursor;

                // high and low temp before syncing new data
                double highInCelsius = 0;
                double lowInCelsius = 0;
                int weatherId = -1;

                // high and low temp after syncing new data
                double newHigh = 0;
                double newLow = 0;
                int newWeatherId = -1;

                // Get the current data before syncing new data
                dataCursor = getWeatherInfo();

                if(dataCursor.moveToFirst()) {
                    highInCelsius = dataCursor.getDouble(MainActivity.INDEX_WEATHER_MAX_TEMP);
                    lowInCelsius = dataCursor.getDouble(MainActivity.INDEX_WEATHER_MIN_TEMP);
                    weatherId = dataCursor.getInt(MainActivity.INDEX_WEATHER_CONDITION_ID);
                }

                // Sync new weather data
                SunshineSyncTask.syncWeather(context);
                jobFinished(jobParameters, false);

                // Get the new data after syncing
                dataCursor = getWeatherInfo();

                if(dataCursor.moveToFirst()) {
                    newHigh = dataCursor.getDouble(MainActivity.INDEX_WEATHER_MAX_TEMP);
                    newLow = dataCursor.getDouble(MainActivity.INDEX_WEATHER_MIN_TEMP);
                    newWeatherId = dataCursor.getInt(MainActivity.INDEX_WEATHER_CONDITION_ID);
                }

                dataCursor.close();

                // Compare the low and high values before syncing data with
                // the values after syncing data to determine if the wearable should be
                // sent the new data
                if(highInCelsius != newHigh || lowInCelsius != newLow) {
                    // Send the high and low temp
                    if(mGoogleApiClient.isConnected())
                        sendWeatherInfoToWearable(newHigh, newLow);
                }

                // Compare the weather id before syncing data with
                // the weather id after syncing data to determine if the wearable should be
                // sent the new data
                if(weatherId != newWeatherId) {
                    // Send the weather image
                    if(mGoogleApiClient.isConnected())
                        sendWeatherImageToWearable(newWeatherId);
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                Log.d(LOG_TAG, "task finished");

                if(mGoogleApiClient.isConnected()) {
                    Log.d(LOG_TAG, "google client connected after task finished");
                    sendWeatherInfoToWearable(80, 75);
                }

                jobFinished(jobParameters, false);
            }

            // Query the database and get the high and low temp and the weather id
            private Cursor getWeatherInfo() {
                return getContentResolver().query(
                        WeatherContract.WeatherEntry.CONTENT_URI,
                        new String[]{
                                WeatherContract.WeatherEntry._ID,
                                WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                                WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
                                WeatherContract.WeatherEntry.COLUMN_WEATHER_ID
                            },
                        null,
                        null,
                        null
                );
            }

            // Sends the high and low temperatures to the wearable as data items
            private void sendWeatherInfoToWearable(double high, double low ) {
                // Format the temp to get a string in celsius or farenheit according
                // to the user's preference
                String highString = SunshineWeatherUtils.formatTemperature(context, high);
                String lowString = SunshineWeatherUtils.formatTemperature(context, low);

                PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WEATHER_INFO);
                putDataMapRequest.getDataMap().putString(KEY_HIGH, highString);
                putDataMapRequest.getDataMap().putString(KEY_LOW, lowString);

                PutDataRequest request = putDataMapRequest.asPutDataRequest();
                request.setUrgent();

                Wearable.DataApi.putDataItem(mGoogleApiClient, request).setResultCallback(
                        new ResultCallback<DataApi.DataItemResult>() {
                            @Override
                            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                                if(!dataItemResult.getStatus().isSuccess())
                                    Log.d(LOG_TAG, "Could not send weather info");
                                else
                                    Log.d(LOG_TAG, "Weather info sent");
                            }
                        }
                );
            }

            // Sends the weather art image to the wearable as asset
            private void sendWeatherImageToWearable(int weatherId) {
                // Get the res id of the drawable we want
                int resId = SunshineWeatherUtils.getSmallArtResourceIdForWeatherCondition(weatherId);

                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId);
                Asset asset = createAssetFromBitmap(bitmap);

                PutDataRequest request = PutDataRequest.create(PATH_WEATHER_IMAGE);
                request.putAsset(KEY_IMAGE, asset);
                request.setUrgent();

                Wearable.DataApi.putDataItem(mGoogleApiClient, request).
                        setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                            @Override
                            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                                if(!dataItemResult.getStatus().isSuccess())
                                    Log.d(LOG_TAG, "Could not send weather image");
                                else
                                    Log.d(LOG_TAG, "Weather image sent");
                            }
                        }
                );
            }

            // Creates an asset from a BitMap
            private Asset createAssetFromBitmap(Bitmap bitmap) {
                final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
                return Asset.createFromBytes(byteStream.toByteArray());
            }
        };

        mFetchWeatherTask.execute();
        return true;
    }

    /**
     * Called when the scheduling engine has decided to interrupt the execution of a running job,
     * most likely because the runtime constraints associated with the job are no longer satisfied.
     *
     * @return whether the job should be retried
     * @see Job.Builder#setRetryStrategy(RetryStrategy)
     * @see RetryStrategy
     */
    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        if (mFetchWeatherTask != null) {
            mFetchWeatherTask.cancel(true);
        }
        return true;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(LOG_TAG, "Connection successful");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(LOG_TAG, "Connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(LOG_TAG, "Connection failed");
    }
}