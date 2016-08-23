package com.example.android.sunshine.app.wearable;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.R;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * This is a Service that listens and send messages to the wearable device
 */
public class MyWearableListenerService extends WearableListenerService
        implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks{
    private static final String TAG = "SunshineWatchFace";
    public static final String WEARABLE_MSG_PATH = "/wearable/data/sunshine/1726356709";
    public static final String WEARABLE_RDY_MSG = "ready";

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };
    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(messageEvent.getPath().equals(WEARABLE_MSG_PATH)){
            if(new String(messageEvent.getData()).equals(WEARABLE_RDY_MSG)){
                new Thread(){
                    @Override
                    public void run() {
                        try {
                            byte[][] byteArrayMsgHolder = getTodaysForecastData();
                            byte[] serializedByteArrayMsgHolder = serialize(byteArrayMsgHolder);
                            sendDataToWearable(serializedByteArrayMsgHolder);
                        }catch (IOException e){
                            Log.e(TAG, Log.getStackTraceString(e));
                        }
                    }
                }.start();
            }
        }else{
            super.onMessageReceived(messageEvent);
        }
    }

    @Override  // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle bundle) {
    }

    @Override  // GoogleApiClient.ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
    }

    @Override  // GoogleApiClient.OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
    }

    /**
     * Retrieves the today's forecast data from the database
     * @return an array of byte arrays with 3 indexes in which contains:
     * <ul>
        <li>index 0: Serialized <code>Bitmap</code>: forecast image</li>
        <li>index 1: Serialized <code>String</code>: min temp</li>
        <li>index 2: Serialized <code>String</code>: max temp</li>
        </ul>
     *  Please cast to the correct object after deserialization of these byte arrays.
     */
    private byte[][] getTodaysForecastData(){
        // Get today's data from the ContentProvider
        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return null;
        }
        if (!data.moveToFirst()) {
            data.close();
            return null;
        }

        // Extract the weather data from the Cursor
        int weatherId = data.getInt(INDEX_WEATHER_ID);
        int weatherArtResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);
        String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp);
        String formattedMinTemperature = Utility.formatTemperature(this, minTemp);
        data.close();

        Bitmap forecastBitmap = BitmapFactory.decodeResource(getResources(), weatherArtResourceId);

        // scale the bitmap on phone before sending to wearable
        forecastBitmap = Bitmap.createScaledBitmap(
                forecastBitmap,
                (int) getResources().getDimension(R.dimen.wearable_today_icon),
                (int) getResources().getDimension(R.dimen.wearable_today_icon),
                false);

        // Serialize the data to be sent to be sent wearable
        byte[] imageByteArray = convertBitmapToByteArray(forecastBitmap);
        byte[] minTempByteArray = formattedMinTemperature.getBytes();
        byte[] maxTempByteArray = formattedMaxTemperature.getBytes();

        return new byte[][]{imageByteArray, minTempByteArray, maxTempByteArray};
    }

    /**
     * Sends data to wearable using the <code>MessageApi</code>
     * @param message
     */
    private void sendDataToWearable(final byte[] message){
        if(mGoogleApiClient.isConnected()) {
            NodeApi.GetConnectedNodesResult nodesList =
                    Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

            for(Node node : nodesList.getNodes()){
                    Wearable.MessageApi.sendMessage(
                            mGoogleApiClient,
                            node.getId(),
                            WEARABLE_MSG_PATH,
                            message).await();
            }
        }
    }

    /**
     * Convert <code>Bitmap</code> to byte array.
     * @param bitmap to be converted into a byteArray
     * @return <code>byte[]</code> that was converted from <code>Bitmap</code>
     */
    private byte[] convertBitmapToByteArray(Bitmap bitmap){
        ByteArrayOutputStream byteStream = null;

        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return byteStream.toByteArray();
        }finally {
            try {
                if(byteStream != null) {
                    byteStream.close();
                }
            }catch (IOException e){
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }

    /**
     * Serializes an <code>Object</code>
     * @param obj to be serialized
     * @return <code>byte[]</code> <code>Object</code> that was serailized.
     * @throws IOException
     */
    private byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ObjectOutputStream o = new ObjectOutputStream(b);
        o.writeObject(obj);
        return b.toByteArray();
    }
}
