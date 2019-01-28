package com.mackentoch.beaconsandroid;

import android.content.Context;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;


public class BeaconsAndroidLifecycle implements LifecycleEventListener {
  private BeaconsAndroidModule mBeaconsAndroid;
  private ReactApplicationContext mReactContext;
  private Context mApplicationContext;

  public BeaconsAndroidLifecycle(BeaconsAndroidModule beaconsAndroid, ReactApplicationContext reactContext) {
    this.mBeaconsAndroid = beaconsAndroid;
    this.mReactContext = reactContext;
    this.mApplicationContext = reactContext.getApplicationContext();

    reactContext.addLifecycleEventListener(this);
  }

  public void onHostResume() {
      mBeaconsAndroid.whenForeground();
  }

  public void onHostPause() {
      mBeaconsAndroid.whenBackground();
  }

  public void onHostDestroy() {
      mBeaconsAndroid.whenKilled();
  }
}
