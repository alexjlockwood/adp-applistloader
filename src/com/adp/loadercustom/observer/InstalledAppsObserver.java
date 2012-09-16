package com.adp.loadercustom.observer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.adp.loadercustom.loader.AppListLoader;

/**
 * Used by the {@link AppListLoader}. An observer that listens for
 * application installs, removals, and updates (and notifies the loader when
 * these changes are detected).
 */
public class InstalledAppsObserver extends BroadcastReceiver {
  private static final String TAG = "ADP_InstalledAppsObserver";
  private static final boolean DEBUG = true;

  private AppListLoader mLoader;

  public InstalledAppsObserver(AppListLoader loader) {
    mLoader = loader;

    // Register for events related to application installs/removals/updates.
    IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
    filter.addDataScheme("package");
    mLoader.getContext().registerReceiver(this, filter);

    // Register for events related to sdcard installation.
    IntentFilter sdFilter = new IntentFilter();
    sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
    sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
    mLoader.getContext().registerReceiver(this, sdFilter);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (DEBUG) Log.i(TAG, "+++ The observer has detected an application change!" +
    		" Notifying Loader... +++");

    // Tell the loader about the change.
    mLoader.onContentChanged();
  }
}