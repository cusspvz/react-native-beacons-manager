package com.mackentoch.beaconsandroid;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.service.ArmaRssiFilter;
import org.altbeacon.beacon.service.RunningAverageRssiFilter;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class BeaconsAndroidModule extends ReactContextBaseJavaModule implements BeaconConsumer {
    private static final String LOG_TAG = "BeaconsAndroidModule";
    private static final int RUNNING_AVG_RSSI_FILTER = 0;
    private static final int ARMA_RSSI_FILTER = 1;
    private NotificationCompat.Builder notificationBuilder = null;
    private Notification pendingNotification = null;
    private BeaconManager mBeaconManager;
    private Context mApplicationContext;
    private ReactApplicationContext mReactContext;
    private BeaconsAndroidLifecycle mLifecycle;
    private final Object bindLock = new Object();
    private volatile boolean binding = false;

    private static final String NOTIFICATION_CHANNEL_ID = "BeaconsAndroidModule";
    private static boolean channelCreated = false;
    private static boolean isActivityActivated = true;

    public BeaconsAndroidModule(ReactApplicationContext reactContext) {
        super(reactContext);
        Log.d(LOG_TAG, "started");
        this.mReactContext = reactContext;
        this.mApplicationContext = reactContext.getApplicationContext();

        // // Setup Beacon Manager
        this.mBeaconManager = BeaconManager.getInstanceForApplication(mApplicationContext);

        // IBeacon
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        // AltBeacon
        // mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24"));

        // Setup Lifecycle Event Listener
        // this.mLifecycle = new BeaconsAndroidLifecycle(this, reactContext);

        if (mBeaconManager.getForegroundServiceNotification() == null) {
            Integer requestCode = 123;

            if (notificationBuilder == null) {
                notificationBuilder = createNotificationBuilder("test", "testing", requestCode);
            }

            if (pendingNotification == null) {
                NotificationManager notificationManager = (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
                checkOrCreateChannel(notificationManager);

                pendingNotification = notificationBuilder.build();
                pendingNotification.defaults |= Notification.DEFAULT_LIGHTS;

                notificationManager.notify(requestCode, pendingNotification);
                mBeaconManager.enableForegroundServiceScanning(pendingNotification, requestCode);
            }
        }

        bindManager();
    }

    @Override
    public String getName() {
        return LOG_TAG;
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("SUPPORTED", BeaconTransmitter.SUPPORTED);
        constants.put("NOT_SUPPORTED_MIN_SDK", BeaconTransmitter.NOT_SUPPORTED_MIN_SDK);
        constants.put("NOT_SUPPORTED_BLE", BeaconTransmitter.NOT_SUPPORTED_BLE);
        constants.put("NOT_SUPPORTED_CANNOT_GET_ADVERTISER_MULTIPLE_ADVERTISEMENTS", BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER_MULTIPLE_ADVERTISEMENTS);
        constants.put("NOT_SUPPORTED_CANNOT_GET_ADVERTISER", BeaconTransmitter.NOT_SUPPORTED_CANNOT_GET_ADVERTISER);
        constants.put("RUNNING_AVG_RSSI_FILTER",RUNNING_AVG_RSSI_FILTER);
        constants.put("ARMA_RSSI_FILTER",ARMA_RSSI_FILTER);
        return constants;
    }

    public void whenForeground () {
        Log.d(LOG_TAG, "whenForeground");
    }

    public void whenBackground () {
        Log.d(LOG_TAG, "whenBackground");
    }

    public void whenKilled () {
        Log.d(LOG_TAG, "whenKilled");

        synchronized(bindLock) {
            unbindManager();

            // TODO
            // pendingNotification.cancel();
            this.pendingNotification = null;
            mBeaconManager.disableForegroundServiceScanning();
            mBeaconManager.setEnableScheduledScanJobs(true);

            bindManager();
        }
    }

    @ReactMethod
    public void setHardwareEqualityEnforced(Boolean e) {
        Beacon.setHardwareEqualityEnforced(e.booleanValue());
    }

    public void bindManager() {
        synchronized(bindLock) {

            if (!mBeaconManager.isBound(this)) {
                Log.d(LOG_TAG, "bindManager: ");
                mBeaconManager.bind(this);
            }
        }
    }

    public void unbindManager() {
        synchronized(bindLock) {
            if (mBeaconManager.isBound(this)) {
                if (binding) {
                    try {
                        bindLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                mBeaconManager.removeMonitorNotifier(mMonitorNotifier);
                mBeaconManager.removeRangeNotifier(mRangeNotifier);

                Log.d(LOG_TAG, "unbindManager: ");
                mBeaconManager.unbind(this);
            }
        }
    }

    @ReactMethod
    public void addParser(String parser, Callback resolve, Callback reject) {
        try {
            Log.d(LOG_TAG, "addParser: " + parser);
            unbindManager();
            mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(parser));
            bindManager();
            resolve.invoke();
        } catch (Exception e) {
            reject.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void removeParser(String parser, Callback resolve, Callback reject) {
        try {
            Log.d(LOG_TAG, "removeParser: " + parser);
            unbindManager();
            mBeaconManager.getBeaconParsers().remove(new BeaconParser().setBeaconLayout(parser));
            bindManager();
            resolve.invoke();
        } catch (Exception e) {
            reject.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void addParsersListToDetection(ReadableArray parsers, Callback resolve, Callback reject) {
        try {
            unbindManager();
            for (int i = 0; i < parsers.size(); i++) {
                String parser = parsers.getString(i);
                Log.d(LOG_TAG, "addParsersListToDetection - add parser: " + parser);
                mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(parser));
            }
            bindManager();
            resolve.invoke(parsers);
        } catch (Exception e) {
            reject.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void removeParsersListToDetection(ReadableArray parsers, Callback resolve, Callback reject) {
        try {
            unbindManager();
            for (int i = 0; i < parsers.size(); i++) {
                String parser = parsers.getString(i);
                Log.d(LOG_TAG, "removeParsersListToDetection - remove parser: " + parser);
                mBeaconManager.getBeaconParsers().remove(new BeaconParser().setBeaconLayout(parser));
            }
            bindManager();
            resolve.invoke(parsers);
        } catch (Exception e) {
            reject.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void setEnableScheduledScanJobs(boolean enabled) {
        mBeaconManager.setEnableScheduledScanJobs(enabled);
    }

    @ReactMethod
    public void setBackgroundScanPeriod(int period) {
        mBeaconManager.setBackgroundScanPeriod((long) period);
    }

    @ReactMethod
    public void setBackgroundBetweenScanPeriod(int period) {
        mBeaconManager.setBackgroundBetweenScanPeriod((long) period);
    }

    @ReactMethod
    public void setForegroundScanPeriod(int period) {
        mBeaconManager.setForegroundScanPeriod((long) period);
    }

    @ReactMethod
    public void setForegroundBetweenScanPeriod(int period) {
        mBeaconManager.setForegroundBetweenScanPeriod((long) period);
    }

    @ReactMethod
    public void setRssiFilter(int filterType, double avgModifier) {
        String logMsg = "Could not set the rssi filter.";
        if (filterType==RUNNING_AVG_RSSI_FILTER){
            logMsg="Setting filter RUNNING_AVG";
            BeaconManager.setRssiFilterImplClass(RunningAverageRssiFilter.class);

            if (avgModifier>0){
                RunningAverageRssiFilter.setSampleExpirationMilliseconds((long) avgModifier);
                logMsg+=" with custom avg modifier";
            }
        } else if (filterType==ARMA_RSSI_FILTER){
            logMsg="Setting filter ARMA";
            BeaconManager.setRssiFilterImplClass(ArmaRssiFilter.class);

            if (avgModifier>0){
                ArmaRssiFilter.setDEFAULT_ARMA_SPEED(avgModifier);
                logMsg+=" with custom avg modifier";
            }
        }
        Log.d(LOG_TAG, logMsg);
    }

    @ReactMethod
    public void checkTransmissionSupported(Callback callback) {
        int result = BeaconTransmitter.checkTransmissionSupported(mReactContext);
        callback.invoke(result);
    }

    @ReactMethod
    public void getMonitoredRegions(Callback callback) {
        WritableArray array = new WritableNativeArray();
        for (Region region: mBeaconManager.getMonitoredRegions()) {
            WritableMap map = new WritableNativeMap();
            map.putString("identifier", region.getUniqueId());
            map.putString("uuid", region.getId1().toString());
            map.putInt("major", region.getId2() != null ? region.getId2().toInt() : 0);
            map.putInt("minor", region.getId3() != null ? region.getId3().toInt() : 0);
            array.pushMap(map);
        }
        callback.invoke(array);
    }

    @ReactMethod
    public void getRangedRegions(Callback callback) {
        WritableArray array = new WritableNativeArray();
        for (Region region: mBeaconManager.getRangedRegions()) {
            WritableMap map = new WritableNativeMap();
            map.putString("region", region.getUniqueId());
            map.putString("uuid", region.getId1().toString());
            array.pushMap(map);
        }
        callback.invoke(array);
    }

    /***********************************************************************************************
    * BeaconConsumer
    **********************************************************************************************/
    @Override
    public void onBeaconServiceConnect() {
        Log.v(LOG_TAG, "onBeaconServiceConnect");

        synchronized(bindLock) {
            // deprecated since v2.9 (see github: https://github.com/AltBeacon/android-beacon-library/releases/tag/2.9)
            // mBeaconManager.setMonitorNotifier(mMonitorNotifier);
            // mBeaconManager.setRangeNotifier(mRangeNotifier);
            mBeaconManager.addMonitorNotifier(mMonitorNotifier);
            mBeaconManager.addRangeNotifier(mRangeNotifier);

            bindLock.notify();
            sendEvent(mReactContext, "beaconServiceConnected", null);
        }
    }

    @Override
    public Context getApplicationContext() {
        return mApplicationContext;
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {
        mApplicationContext.unbindService(serviceConnection);
    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
        return mApplicationContext.bindService(intent, serviceConnection, i);
    }

    /***********************************************************************************************
    * Monitoring
    **********************************************************************************************/
    @ReactMethod
    public void startMonitoring(String regionId, String beaconUuid, int minor, int major, Callback resolve, Callback reject) {
        Log.d(LOG_TAG, "startMonitoring, monitoringRegionId: " + regionId + ", monitoringBeaconUuid: " + beaconUuid + ", minor: " + minor + ", major: " + major);
        try {
            Region region = createRegion(
                regionId,
                beaconUuid,
                String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
                String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
            );
            mBeaconManager.startMonitoringBeaconsInRegion(region);

            resolve.invoke();
        } catch (Exception e) {
            Log.e(LOG_TAG, "startMonitoring, error: ", e);
            reject.invoke(e.getMessage());
        }
    }

    private MonitorNotifier mMonitorNotifier = new MonitorNotifier() {
        @Override
        public void didEnterRegion(Region region) {
            Log.i(LOG_TAG, "didEnterRegion");
            sendEvent(mReactContext, "regionDidEnter", createMonitoringResponse(region));
            wakeUpAppIfNotRunning();
        }

        @Override
        public void didExitRegion(Region region) {
            Log.i(LOG_TAG, "regionDidExit");
            sendEvent(mReactContext, "regionDidExit", createMonitoringResponse(region));

            // NOTE: Support the option to stop monitoring the region
        }

        @Override
        public void didDetermineStateForRegion(int i, Region region) {
            Log.i(LOG_TAG, "didDetermineStateForRegion");
        }
    };

    private WritableMap createMonitoringResponse(Region region) {
        WritableMap map = new WritableNativeMap();
        map.putString("identifier", region.getUniqueId());
        map.putString("uuid", region.getId1() != null ? region.getId1().toString() : "");
        map.putInt("major", region.getId2() != null ? region.getId2().toInt() : 0);
        map.putInt("minor", region.getId3() != null ? region.getId3().toInt() : 0);
        return map;
    }

    @ReactMethod
    public void stopMonitoring(String regionId, String beaconUuid, int minor, int major, Callback resolve, Callback reject) {
        Region region = createRegion(
            regionId,
            beaconUuid,
            String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
            String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
            // minor,
            // major
        );

        try {
            mBeaconManager.stopMonitoringBeaconsInRegion(region);
            resolve.invoke();
        } catch (Exception e) {
            Log.e(LOG_TAG, "stopMonitoring, error: ", e);
            reject.invoke(e.getMessage());
        }
    }

    /***********************************************************************************************
    * Ranging
    **********************************************************************************************/
    @ReactMethod
    public void startRanging(String regionId, String beaconUuid, Callback resolve, Callback reject) {
        Log.d(LOG_TAG, "startRanging, rangingRegionId: " + regionId + ", rangingBeaconUuid: " + beaconUuid);
        try {
            Region region = createRegion(regionId, beaconUuid);
            mBeaconManager.startRangingBeaconsInRegion(region);
            resolve.invoke();
        } catch (Exception e) {
            Log.e(LOG_TAG, "startRanging, error: ", e);
            reject.invoke(e.getMessage());
        }
    }

    private RangeNotifier mRangeNotifier = new RangeNotifier() {
        @Override
        public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
            Log.d(LOG_TAG, "rangingConsumer didRangeBeaconsInRegion, beacons: " + beacons.toString());
            Log.d(LOG_TAG, "rangingConsumer didRangeBeaconsInRegion, region: " + region.toString());
            sendEvent(mReactContext, "beaconsDidRange", createRangingResponse(beacons, region));

            if (!beacons.isEmpty()) {
                wakeUpAppIfNotRunning();
            }
        }
    };

    private WritableMap createRangingResponse(Collection<Beacon> beacons, Region region) {
        WritableMap map = new WritableNativeMap();
        map.putString("identifier", region.getUniqueId());
        map.putString("uuid", region.getId1() != null ? region.getId1().toString() : "");
        WritableArray a = new WritableNativeArray();
        for (Beacon beacon : beacons) {
            WritableMap b = new WritableNativeMap();

            b.putString("uuid", beacon.getId1().toString());

            if (beacon.getIdentifiers().size() > 2) {
                b.putInt("major", beacon.getId2().toInt());
                b.putInt("minor", beacon.getId3().toInt());
            }

            b.putInt("rssi", beacon.getRssi());

            if(
                beacon.getDistance() == Double.POSITIVE_INFINITY
                || Double.isNaN(beacon.getDistance())
                || beacon.getDistance() == Double.NaN
                || beacon.getDistance() == Double.NEGATIVE_INFINITY
            ){
                b.putDouble("distance", 999.0);
                b.putString("proximity", "far");
            }else {
                b.putDouble("distance", beacon.getDistance());
                b.putString("proximity", getProximity(beacon.getDistance()));
            }

            a.pushMap(b);
        }
        map.putArray("beacons", a);
        return map;
    }

    private String getProximity(double distance) {
        if (distance == -1.0) {
            return "unknown";
        } else if (distance < 1) {
            return "immediate";
        } else if (distance < 3) {
            return "near";
        } else {
            return "far";
        }
    }

    @ReactMethod
    public void stopRanging(String regionId, String beaconUuid, Callback resolve, Callback reject) {
        Region region = createRegion(regionId, beaconUuid);

        try {
            mBeaconManager.stopRangingBeaconsInRegion(region);
            resolve.invoke();
        } catch (Exception e) {
            Log.e(LOG_TAG, "stopRanging, error: ", e);
            reject.invoke(e.getMessage());
        }
    }


    /***********************************************************************************************
    * Utils
    **********************************************************************************************/
    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        }
    }

    private Region createRegion(String regionId, String beaconUuid) {
        Identifier id1 = (beaconUuid == null) ? null : Identifier.parse(beaconUuid);
        return new Region(regionId, id1, null, null);
    }

    private Region createRegion(String regionId, String beaconUuid, String minor, String major) {
        Identifier id1 = (beaconUuid == null) ? null : Identifier.parse(beaconUuid);
        return new Region(
            regionId,
            id1,
            major.length() > 0 ? Identifier.parse(major) : null,
            minor.length() > 0 ? Identifier.parse(minor) : null
        );
    }

    private Class getMainActivityClass() {
        String packageName = mApplicationContext.getPackageName();
        Intent launchIntent = mApplicationContext.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Intent getNewIntentFromMainActivity() {
      Class intentClass = getMainActivityClass();
      return new Intent(mApplicationContext, intentClass);
    }

    private void checkOrCreateChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        if (channelCreated)
            return;
        if (manager == null)
            return;

        @SuppressLint("WrongConstant") NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Smart_Space_Pro_Channel", android.app.NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Smart_Space_Pro_Channel_Description");
        channel.enableLights(true);
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
        channelCreated = true;
    }

    // private void createPendingIntentNotification () {

    //     // Setup Notification Builder
    //     Notification.Builder builder = new Notification.Builder(mApplicationContext);
    //     builder.setSmallIcon(android.R.drawable.ic_dialog_info);
    //     builder.setContentTitle("Scanning for Beacons");

    //     Intent intent = getNewIntentFromMainActivity();
    //     PendingIntent pendingIntent = PendingIntent.getActivity(mApplicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    //     builder.setContentIntent(pendingIntent);

    //     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    //         NotificationChannel channel = new NotificationChannel("My Notification Channel ID", "My Notification Name", NotificationManager.IMPORTANCE_DEFAULT);
    //         channel.setDescription("My Notification Channel Description");
    //         NotificationManager notificationManager = (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
    //         notificationManager.createNotificationChannel(channel);
    //         builder.setChannelId(channel.getId());
    //     }

    // }

    private NotificationCompat.Builder createNotificationBuilder (String title, String message, Integer requestCode) {
        Intent notificationIntent = getNewIntentFromMainActivity();
        // Integer requestCode = 123124

        // if(! (boolean) requestCode) {
        //     requestCode = new Random().nextInt(10000);
        // }

        PendingIntent contentIntent = PendingIntent.getActivity(mApplicationContext, requestCode, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mApplicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
            .setContentIntent(contentIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_CALL);
        }

        return notificationBuilder;
    }

    private Boolean isActivityRunning(Class activityClass) {
        ActivityManager activityManager = (ActivityManager) mApplicationContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);

        for (ActivityManager.RunningTaskInfo task : tasks) {
            if (activityClass.getCanonicalName().equalsIgnoreCase(task.baseActivity.getClassName()))
                return true;
        }

        return false;
    }

    private void wakeUpAppIfNotRunning() {
        Class intentClass = getMainActivityClass();
        Boolean isRunning = isActivityRunning(intentClass);

        if (!isRunning) {
            Intent intent = new Intent(mApplicationContext, intentClass);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Important:  make sure to add android:launchMode="singleInstance" in the manifest
            // to keep multiple copies of this activity from getting created if the user has
            // already manually launched the app.
            mApplicationContext.startActivity(intent);
        }
    }
}
