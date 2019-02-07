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

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.Promise;
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
    // private BeaconsAndroidModule self;

    private static final String E_LAYOUT_ERROR = "E_LAYOUT_ERROR";

    private static final String LOG_TAG = "BeaconsAndroidModule";
    private static final int RUNNING_AVG_RSSI_FILTER = 0;
    private static final int ARMA_RSSI_FILTER = 1;
    private NotificationCompat.Builder notificationBuilder = null;
    private Notification pendingNotification = null;
    private NotificationManager notificationManager = null;
    private BeaconManager mBeaconManager;
    private Context mApplicationContext;
    private ReactApplicationContext mReactContext;
    private BeaconsAndroidLifecycle mLifecycle;
    private final Object bindingLock = new Object();
    private volatile boolean binding = false;
    private volatile boolean binded = false;
    private Integer requestCode = 123;

    private static final String NOTIFICATION_CHANNEL_ID = "BeaconsAndroidModule";
    private static boolean channelCreated = false;
    private static boolean isActivityActivated = true;

    public BeaconsAndroidModule(ReactApplicationContext reactContext) {
        super(reactContext);
        // this.self = this;

        Log.d(LOG_TAG, "started");
        this.mReactContext = reactContext;
        this.mApplicationContext = reactContext.getApplicationContext();

        // // Setup Beacon Manager
        this.mBeaconManager = BeaconManager.getInstanceForApplication(mApplicationContext);

        // IBeacon
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        // AltBeacon
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24"));

        // Setup Lifecycle Event Listener
        this.mLifecycle = new BeaconsAndroidLifecycle(this, reactContext);

        if (!mBeaconManager.isAnyConsumerBound()) {
            if (notificationBuilder == null) {
                notificationBuilder = createNotificationBuilder("test", "testing", requestCode);
            }

            if (mBeaconManager.getForegroundServiceNotification() == null) {
                notificationManager = (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
                checkOrCreateChannel(notificationManager);

                pendingNotification = notificationBuilder.build();
                pendingNotification.defaults |= Notification.DEFAULT_LIGHTS;
            } else if (pendingNotification == null) {
                pendingNotification = mBeaconManager.getForegroundServiceNotification();
            }
        }
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

        boolean scanJobsEnabled = mBeaconManager.getScheduledScanJobsEnabled();
        if (scanJobsEnabled == true) {
            unbindManager();
            mBeaconManager.setEnableScheduledScanJobs(false);
        }

        mBeaconManager.setForegroundScanPeriod(2500);
        mBeaconManager.setForegroundBetweenScanPeriod(500);
        mBeaconManager.setBackgroundMode(false);

        bindManager();
    }

    public void whenBackground () {
        Log.d(LOG_TAG, "whenBackground");

        boolean scanJobsEnabled = mBeaconManager.getScheduledScanJobsEnabled();
        if (scanJobsEnabled == true) {
            unbindManager();
            mBeaconManager.setEnableScheduledScanJobs(false);
        }

        mBeaconManager.setBackgroundScanPeriod(2000);
        mBeaconManager.setBackgroundBetweenScanPeriod(10000);
        mBeaconManager.setBackgroundMode(true);

        bindManager();
    }

    public void whenKilled () {
        Log.d(LOG_TAG, "whenKilled");

        unbindManager();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.cancel(requestCode);
            pendingNotification = null;
        }

        boolean scanJobsEnabled = mBeaconManager.getScheduledScanJobsEnabled();
        if (scanJobsEnabled == true) {
            mBeaconManager.setEnableScheduledScanJobs(false);
        }

        mBeaconManager.disableForegroundServiceScanning();

        mBeaconManager.setBackgroundScanPeriod(2000);
        mBeaconManager.setBackgroundBetweenScanPeriod(10000);
        mBeaconManager.setBackgroundMode(true);

        bindManager();
    }

    @ReactMethod
    public void setHardwareEqualityEnforced(Boolean e) {
        Beacon.setHardwareEqualityEnforced(e.booleanValue());
    }

    public void bindManager() {
        if (binded) return;

        try {
            if (binding) return;
            Log.d(LOG_TAG, "bindManager: Starting to bind");
            binding = true;

            if (!mBeaconManager.isBound(this)) {
                mBeaconManager.bind(this);
            }

            synchronized(bindingLock) {
                bindingLock.wait(500);
                binded = true;
            }

            Log.d(LOG_TAG, "bindManager: binded");
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "bindManager, error: ", e);
        }
    }

    public void unbindManager() {
        Log.d(LOG_TAG, "unbindManager: called");
        if(!binded) return;

        try {

            synchronized(bindingLock) {
                if (mBeaconManager.isBound(this)) {
                    if (binding) {
                        Log.d(LOG_TAG, "unbindManager: got stuck on bindind");
                        bindingLock.wait(500);
                    }

                    mBeaconManager.removeMonitorNotifier(mMonitorNotifier);
                    mBeaconManager.removeRangeNotifier(mRangeNotifier);

                    Log.d(LOG_TAG, "unbindManager: unbinding");
                    mBeaconManager.unbind(this);
                    binded = false;
                    Log.d(LOG_TAG, "unbindManager: unbinded");
                }
            }

        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "unbindManager, error: ", e);
        }
    }

    @ReactMethod
    public void addParser(String parser, Promise promise) {
        try {
            Log.d(LOG_TAG, "addParser: " + parser);
            unbindManager();
            mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(parser));
            bindManager();

            promise.resolve(null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "addParser, error: ", e);
            promise.reject(E_LAYOUT_ERROR, e);
        }
    }

    @ReactMethod
    public void removeParser(String parser, Promise promise) {
        try {
            Log.d(LOG_TAG, "removeParser: " + parser);
            unbindManager();
            mBeaconManager.getBeaconParsers().remove(new BeaconParser().setBeaconLayout(parser));
            bindManager();

            promise.resolve(null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "removeParser, error: ", e);
            promise.reject(E_LAYOUT_ERROR, e);
        }
    }

    @ReactMethod
    public void addParsersListToDetection(ReadableArray parsers, Promise promise) {
        unbindManager();

        try {
            for (int i = 0; i < parsers.size(); i++) {
                String parser = parsers.getString(i);
                Log.d(LOG_TAG, "addParsersListToDetection - parser: " + parser);
                mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(parser));
            }

            promise.resolve(null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "addParsersListToDetection, error: ", e);
            promise.reject(E_LAYOUT_ERROR, e);
        }

        bindManager();
    }

    @ReactMethod
    public void removeParsersListToDetection(ReadableArray parsers, Promise promise) {
        try {
            unbindManager();
            for (int i = 0; i < parsers.size(); i++) {
                String parser = parsers.getString(i);
                Log.d(LOG_TAG, "removeParsersListToDetection - parser: " + parser);
                mBeaconManager.getBeaconParsers().remove(new BeaconParser().setBeaconLayout(parser));
            }
            bindManager();

            promise.resolve(null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "removeParsersListToDetection, error: ", e);
            promise.reject(E_LAYOUT_ERROR, e);
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
    public void checkTransmissionSupported(Promise promise) {
        int result = BeaconTransmitter.checkTransmissionSupported(mReactContext);
        promise.resolve(result);
    }

    @ReactMethod
    public void getMonitoredRegions(Promise promise) {
        WritableArray array = new WritableNativeArray();
        for (Region region: mBeaconManager.getMonitoredRegions()) {
            WritableMap map = new WritableNativeMap();
            map.putString("identifier", region.getUniqueId());
            map.putString("uuid", region.getId1().toString());
            map.putInt("major", region.getId2() != null ? region.getId2().toInt() : 0);
            map.putInt("minor", region.getId3() != null ? region.getId3().toInt() : 0);
            array.pushMap(map);
        }
        promise.resolve(array);
    }

    @ReactMethod
    public void getRangedRegions(Promise promise) {
        WritableArray array = new WritableNativeArray();
        for (Region region: mBeaconManager.getRangedRegions()) {
            WritableMap map = new WritableNativeMap();
            map.putString("identifier", region.getUniqueId());
            map.putString("uuid", region.getId1().toString());
            array.pushMap(map);
        }
        promise.resolve(array);
    }

    /***********************************************************************************************
    * BeaconConsumer
    **********************************************************************************************/
    @Override
    public void onBeaconServiceConnect() {
        Log.v(LOG_TAG, "onBeaconServiceConnect");

        try {
            mBeaconManager.addMonitorNotifier(mMonitorNotifier);
            mBeaconManager.addRangeNotifier(mRangeNotifier);

            synchronized (bindingLock) {
                binding = false;
                bindingLock.notifyAll();
            }

            sendEvent("beaconServiceConnected", null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "onBeaconServiceConnect, error: ", e);
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
    public void startMonitoring(String regionId, String beaconUuid, int minor, int major, Promise promise) {
        Log.d(LOG_TAG, "startMonitoring, monitoringRegionId: " + regionId + ", monitoringBeaconUuid: " + beaconUuid + ", minor: " + minor + ", major: " + major);
        try {
            Region region = createRegion(
                regionId,
                beaconUuid,
                String.valueOf(minor).equals("-1") ? "" : String.valueOf(minor),
                String.valueOf(major).equals("-1") ? "" : String.valueOf(major)
            );
            mBeaconManager.startMonitoringBeaconsInRegion(region);

            promise.resolve(null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "startMonitoring, error: ", e);
            promise.reject(E_LAYOUT_ERROR, e);
        }
    }

    private MonitorNotifier mMonitorNotifier = new MonitorNotifier() {

        @Override
        public void didEnterRegion(Region region) {
            Log.i(LOG_TAG, "didEnterRegion: " + region.toString());

            wakeUpAppIfNotRunning();

            if (!reactIsActive()) {
                mBeaconManager.removeMonitorNotifier(mMonitorNotifier);
                return;
            }

            sendEvent("regionDidEnter", createMonitoringResponse(region));
        }

        @Override
        public void didExitRegion(Region region) {
            Log.i(LOG_TAG, "regionDidExit: " + region.toString());

            if (!reactIsActive()) {
                mBeaconManager.removeMonitorNotifier(mMonitorNotifier);
                return;

            }

            sendEvent("regionDidExit", createMonitoringResponse(region));
            // NOTE: Support the option to stop monitoring the region
        }


        @Override
        public void didDetermineStateForRegion(int i, Region region) {
            Log.i(LOG_TAG, "didDetermineStateForRegion: " + i + ", " + region.toString());

            WritableMap map = new WritableNativeMap();
            map.putString("identifier", region.getUniqueId());
            map.putString("state", i == 1 ? "inside" : "outside");

            sendEvent("didDetermineState", map);

            if (!reactIsActive()) {
                mBeaconManager.removeMonitorNotifier(mMonitorNotifier);
                return;
            }

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
    public void stopMonitoring(String regionId, String beaconUuid, int minor, int major, Promise promise) {
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
            promise.resolve(null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "stopMonitoring, error: ", e);
            promise.reject(E_LAYOUT_ERROR, e);
        }
    }

    /***********************************************************************************************
    * Ranging
    **********************************************************************************************/
    @ReactMethod
    public void startRanging(String regionId, String beaconUuid, Promise promise) {
        Log.d(LOG_TAG, "startRanging, rangingRegionId: " + regionId + ", rangingBeaconUuid: " + beaconUuid);
        try {
            Region addingRegion = createRegion(regionId, beaconUuid);

            // check first if we're already ranging this region
            // befor adding a new one to the ranging beacons list
            for (Region region: mBeaconManager.getRangedRegions()) {
                if(addingRegion.getUniqueId() == region.getUniqueId()) {
                    return;
                }
            }

            mBeaconManager.startRangingBeaconsInRegion(addingRegion);
            promise.resolve(null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "startRanging, error: ", e);
            promise.reject(E_LAYOUT_ERROR, e);
        }
    }

    private RangeNotifier mRangeNotifier = new RangeNotifier() {
        @Override
        public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
            Log.d(LOG_TAG, "didRangeBeaconsInRegion, region: " + region.toString() + " | beacons: " + beacons.toString());

            if (!beacons.isEmpty()) {
                wakeUpAppIfNotRunning();


                if (!reactIsActive()) {
                    mBeaconManager.removeRangeNotifier(mRangeNotifier);
                    return;
                }

                sendEvent("beaconsDidRange", createRangingResponse(beacons, region));
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
    public void stopRanging(String regionId, String beaconUuid, Promise promise) {
        Region region = createRegion(regionId, beaconUuid);

        try {
            mBeaconManager.stopRangingBeaconsInRegion(region);
            promise.resolve(null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "stopRanging, error: ", e);
            promise.reject(E_LAYOUT_ERROR, e);
        }
    }


    /***********************************************************************************************
    * Utils
    **********************************************************************************************/
    private void sendEvent(String eventName, @Nullable WritableMap params) {
        Log.v(LOG_TAG, "sendEvent: " + eventName);
        if (reactIsActive()) {
            mReactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        } else {
            // TODO: should we fail here?
            Log.w(LOG_TAG, "sendEvent: " + eventName, new Error("It was not sent because there was no active catalyst instance on react context"));
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
            Log.e(LOG_TAG, "getMainActivityClass, error: ", e);
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

    private NotificationCompat.Builder createNotificationBuilder (String title, String message, Integer requestCode) {
        Intent notificationIntent = getNewIntentFromMainActivity();

        PendingIntent contentIntent = PendingIntent.getActivity(mApplicationContext, requestCode, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mApplicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
            .setContentIntent(contentIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_CALL);
        }

        return notificationBuilder;
    }

    @ReactMethod
    public void setNotificationTitle(String title, Promise promise) {
        try {
            notificationBuilder.setContentTitle(title);
            pendingNotification = notificationBuilder.build();
            pendingNotification.defaults |= Notification.DEFAULT_LIGHTS;
            notificationManager.notify(requestCode, pendingNotification);
        } catch (Exception e) {
            promise.reject(E_LAYOUT_ERROR, e);
        }
    }

    @ReactMethod
    public void setNotificationMessage(String message, Promise promise) {
        try {
            notificationBuilder.setContentText(message);
            pendingNotification = notificationBuilder.build();
            pendingNotification.defaults |= Notification.DEFAULT_LIGHTS;
            notificationManager.notify(requestCode, pendingNotification);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject(E_LAYOUT_ERROR, e);
        }
    }

    @ReactMethod
    public void setScanNotificationContent(String title, String message, Promise promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationBuilder.setContentTitle(title);
                notificationBuilder.setContentText(message);
                pendingNotification = notificationBuilder.build();
                pendingNotification.defaults |= Notification.DEFAULT_LIGHTS;
                notificationManager.notify(requestCode, pendingNotification);
                mBeaconManager.enableForegroundServiceScanning(pendingNotification, requestCode);
                promise.resolve(null);
            }
        } catch (Exception e) {
            promise.reject(E_LAYOUT_ERROR, e);
        }
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

    private Boolean isAppRunning() {
        Class intentClass = getMainActivityClass();
        return isActivityRunning(intentClass);
    }

    private void wakeUpAppIfNotRunning() {
        if (!isAppRunning()) {
            Intent intent = new Intent(mApplicationContext, getMainActivityClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Important:  make sure to add android:launchMode="singleInstance" in the manifest
            // to keep multiple copies of this activity from getting created if the user has
            // already manually launched the app.
            mApplicationContext.startActivity(intent);
        }
    }

    private Boolean reactIsActive() {
        if (mReactContext == null) {
            return false;
        }
        return mReactContext.hasActiveCatalystInstance();
    }
}
