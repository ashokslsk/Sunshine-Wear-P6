/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with weather forecast. In ambient mode, the bitmap is not displayed.
 * On devices with low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "SunshineWatchFace";
    private static final String WEARABLE_MSG_PATH = "/wearable/data/sunshine/path";
    private static final String WEARABLE_RDY_MSG = "ready";
    private static final String TEMPERATURE_SPACING = " ";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    /**
     * EngineHandler is responsible for scheduling updates to the watch when in interactive mode
     * based on the <code>INTERACTIVE_UPDATE_RATE_MS</code>
     */
    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    /**
     * The engine is the true power behind the watch face. It basically controls everything,
     * including drawing the watch face and managing ambient mode.
     */
    private class Engine extends CanvasWatchFaceService.Engine
            implements MessageApi.MessageListener, NodeApi.NodeListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
            }
        };

        static final String COLON_STRING = ":";
        static final String HOUR_STRING = "00";
        static final String TIME_STRING = "00:00";

        /**
         * This boolean(mSwitchedToThisWatchFace) determines if the onCreate was called
         */
        boolean mSwitchedToThisWatchFace;
        boolean mLowBitAmbient;
        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTimePaint;
        Paint mMaxTempPaint;
        Paint mMinTempPaint;



        Calendar mCalendar;
        float mCenterXTimeOffset;
        float mCenterYTimeOffset;
        float mHourWidth;
        float mColonWidth;
        float mCenterXForecastOffset;
        float mCenterYForecastOffset;
        float mMaxTempWidth;
        float mTempTextHalfHeight;
        float mForecastBitmapHalfHeight;
        int mForecastBitmapWidth;

        GoogleApiClient mGoogleApiClient;
        String mMinTemp;
        String mMaxTemp;
        Bitmap mForecastBitmap;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            // Set up configuration for thw watch face
            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL)
                    .build());

            Resources resources = SunshineWatchFace.this.getResources();

            // Retrieve specified offsets used to position time and forecast correctly
            mCenterYTimeOffset = resources.getDimension(R.dimen.digital_y_time_offset);
            mCenterYForecastOffset = resources.getDimension(R.dimen.digital_y_forecast_offset);

            // Initialize background paint
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(
                    SunshineWatchFace.this, R.color.digital_background));

            // Initialize time text paint for hour, colon, and minutes
            mTimePaint = createTextPaint(ContextCompat.getColor(
                    SunshineWatchFace.this, R.color.digital_text));

            // Initialize temperature text paint for max and min temps
            mMaxTempPaint = createTextPaint(ContextCompat.getColor(
                    SunshineWatchFace.this, R.color.digital_text));
            mMaxTempPaint.setTextSize(resources.getDimension(R.dimen.digital_min_temp_text_size));

            mMinTempPaint = createTextPaint(ContextCompat.getColor(
                    SunshineWatchFace.this, R.color.digital_text_transparent));
            mMinTempPaint.setTextSize(resources.getDimension(R.dimen.digital_min_temp_text_size));

            mCalendar = Calendar.getInstance();

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();

            mSwitchedToThisWatchFace = true;
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);

            mTimePaint.setTextSize(textSize);
            //Figure out how much to offset time from the center point to make it look center
            mCenterXTimeOffset = mTimePaint.measureText(TIME_STRING)/2;
            mHourWidth = mTimePaint.measureText(HOUR_STRING);
            mColonWidth = mTimePaint.measureText(COLON_STRING);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // Determine if colon needs to be drawn
            boolean drawColon;
            if(isInAmbientMode()){
                drawColon = true;
            } else {
                //If even second draw colon. If odd second don't draw colon.
                drawColon = mCalendar.get(Calendar.SECOND) % 2 == 0;
            }

            float centerX = bounds.width()/2f;
            float centerY = bounds.height()/2f;
            float hourXOffset = centerX - mCenterXTimeOffset;
            float hourYOffset = centerY - mCenterYTimeOffset;

            // Draw hour
            canvas.drawText(
                    Integer.toString(mCalendar.get(Calendar.HOUR_OF_DAY)),
                    hourXOffset,
                    hourYOffset,
                    mTimePaint);

            // Draw colon
            if(drawColon) {
                canvas.drawText(
                        COLON_STRING,
                        hourXOffset + mHourWidth,
                        hourYOffset,
                        mTimePaint);
            }

            // Draw minutes
            canvas.drawText(
                    Integer.toString(mCalendar.get(Calendar.MINUTE)),
                    hourXOffset + mHourWidth + mColonWidth,
                    hourYOffset,
                    mTimePaint);

            // Draw forecast bitmap
            float forecastBitmapXOffset = centerX - mCenterXForecastOffset;
            float forecastBitmapYOffset = centerY + mCenterYForecastOffset;
            if(mForecastBitmap != null && !isInAmbientMode()){
                canvas.drawBitmap(
                        mForecastBitmap,
                        forecastBitmapXOffset,
                        forecastBitmapYOffset,
                        null);
            }

            // Draw max temp
            if(mMaxTemp != null){
                canvas.drawText(
                        mMaxTemp,
                        forecastBitmapXOffset + mForecastBitmapWidth,
                        forecastBitmapYOffset + mForecastBitmapHalfHeight + mTempTextHalfHeight,
                        mMaxTempPaint);
            }

            // Draw min temp
            if(mMinTemp != null){
                canvas.drawText(
                        mMinTemp,
                        forecastBitmapXOffset + mForecastBitmapWidth + mMaxTempWidth,
                        forecastBitmapYOffset + mForecastBitmapHalfHeight + mTempTextHalfHeight,
                        mMinTempPaint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                mGoogleApiClient.connect();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.NodeApi.removeListener(mGoogleApiClient, this);
                    Wearable.MessageApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // START HERE! if you are tracing the code! everything starts with updateTimer()!
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        //This is called every minute to update watch-face in ambient mode
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if(mLowBitAmbient) {
                mTimePaint.setAntiAlias(!inAmbientMode);
                mMaxTempPaint.setAntiAlias(!inAmbientMode);
                mMinTempPaint.setAntiAlias(!inAmbientMode);
            }

            mBackgroundPaint.setColor(inAmbientMode ? Color.BLACK :
                    ContextCompat.getColor(SunshineWatchFace.this,
                            R.color.digital_background));

            invalidate();

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            if(messageEvent.getPath().equals(WEARABLE_MSG_PATH)){
                try {
                    byte[][] byteArrayMsgHolder = (byte[][]) deserialize(messageEvent.getData());

                    // Un-package the data the same way we packaged it
                    mForecastBitmap = BitmapFactory.decodeByteArray(
                            byteArrayMsgHolder[0],
                            0,
                            byteArrayMsgHolder[0].length);

                    mMinTemp = new String(byteArrayMsgHolder[1]);
                    mMaxTemp = new String(byteArrayMsgHolder[2]);

                    // Add spacing so it doesn't look scrunched up together
                    mMinTemp = TEMPERATURE_SPACING + mMinTemp;
                    mMaxTemp = TEMPERATURE_SPACING + mMaxTemp;

                    float minTempTextWidth = mMinTempPaint.measureText(mMinTemp);
                    mMaxTempWidth = mMaxTempPaint.measureText(mMaxTemp);
                    float totalTempTextWidth = minTempTextWidth + mMaxTempWidth;

                    mForecastBitmapWidth = mForecastBitmap.getWidth();

                    // Divide total width by 2 to calculate bitmap position
                    mCenterXForecastOffset = (mForecastBitmapWidth + totalTempTextWidth)/2;

                    // Find center height of bitmap.
                    // It is used to center the temperature to the bitmap.
                    mForecastBitmapHalfHeight = mForecastBitmap.getHeight()/2f;

                    // Find the center height of temperature text.
                    // It is also used to center the temperature to the bitmap.
                    Rect bounds = new Rect();
                    mMaxTempPaint.getTextBounds(mMaxTemp, 0, mMaxTemp.length(), bounds);
                    mTempTextHalfHeight = bounds.height()/2f;

                    invalidate();
                }catch (IOException | ClassNotFoundException e){
                    Log.e(TAG, Log.getStackTraceString(e));
                }
            }
        }

        /**
         * Sends a msg using the <code>MessageApi</code> to tell that we are ready to receive
         * forecast
         */
        private void sendReadyMessageToPhone(){
            if(mGoogleApiClient.isConnected()) {
                new Thread(){
                    @Override
                    public void run() {
                        NodeApi.GetConnectedNodesResult nodesList =
                                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

                        for(Node node : nodesList.getNodes()){
                            Wearable.MessageApi.sendMessage(
                                    mGoogleApiClient,
                                    node.getId(),
                                    WEARABLE_MSG_PATH,
                                    WEARABLE_RDY_MSG.getBytes()).await();
                        }
                    }
                }.start();
            }
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle bundle) {
            Wearable.NodeApi.addListener(mGoogleApiClient, this);
            Wearable.MessageApi.addListener(mGoogleApiClient, this);

            if(mSwitchedToThisWatchFace) {
                sendReadyMessageToPhone();
                mSwitchedToThisWatchFace = false;
            }
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);

        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        }

        @Override
        public void onPeerConnected(Node node) {
            // request data once a peer is connected
            sendReadyMessageToPhone();
        }

        @Override
        public void onPeerDisconnected(Node node) {
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        public Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
            ByteArrayInputStream b = new ByteArrayInputStream(bytes);
            ObjectInputStream o = new ObjectInputStream(b);
            return o.readObject();
        }
    }
}
