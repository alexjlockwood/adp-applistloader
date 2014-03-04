package com.adp.loadercustom.loader;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import com.adp.loadercustom.observer.InstalledAppsObserver;
import com.adp.loadercustom.observer.SystemLocaleObserver;

/**
 * An implementation of AsyncTaskLoader which loads a {@code List<AppEntry>}
 * containing all installed applications on the device.
 */
public class AppListLoader extends AsyncTaskLoader<List<AppEntry>> {
  private static final String TAG = "ADP_AppListLoader";
  private static final boolean DEBUG = true;

  final PackageManager mPm;

  // We hold a reference to the Loader's data here.
  private List<AppEntry> mApps;

  public AppListLoader(Context ctx) {
    // Loaders may be used across multiple Activitys (assuming they aren't
    // bound to the LoaderManager), so NEVER hold a reference to the context
    // directly. Doing so will cause you to leak an entire Activity's context.
    // The superclass constructor will store a reference to the Application
    // Context instead, and can be retrieved with a call to getContext().
    super(ctx);
    mPm = getContext().getPackageManager();
  }

  /****************************************************/
  /** (1) A task that performs the asynchronous load **/
  /****************************************************/

  /**
   * This method is called on a background thread and generates a List of
   * {@link AppEntry} objects. Each entry corresponds to a single installed
   * application on the device.
   */
  @Override
  public List<AppEntry> loadInBackground() {
    if (DEBUG) Log.i(TAG, "+++ loadInBackground() called! +++");

    // Retrieve all installed applications.
    List<ApplicationInfo> apps = mPm.getInstalledApplications(0);

    if (apps == null) {
      apps = new ArrayList<ApplicationInfo>();
    }

    // Create corresponding array of entries and load their labels.
    List<AppEntry> entries = new ArrayList<AppEntry>(apps.size());
    for (int i = 0; i < apps.size(); i++) {
      AppEntry entry = new AppEntry(this, apps.get(i));
      entry.loadLabel(getContext());
      entries.add(entry);
    }

    // Sort the list.
    Collections.sort(entries, ALPHA_COMPARATOR);

    return entries;
  }

  /*******************************************/
  /** (2) Deliver the results to the client **/
  /*******************************************/

  /**
   * Called when there is new data to deliver to the client. The superclass will
   * deliver it to the registered listener (i.e. the LoaderManager), which will
   * forward the results to the client through a call to onLoadFinished.
   */
  @Override
  public void deliverResult(List<AppEntry> apps) {
    if (isReset()) {
      if (DEBUG) Log.w(TAG, "+++ Warning! An async query came in while the Loader was reset! +++");
      // The Loader has been reset; ignore the result and invalidate the data.
      // This can happen when the Loader is reset while an asynchronous query
      // is working in the background. That is, when the background thread
      // finishes its work and attempts to deliver the results to the client,
      // it will see here that the Loader has been reset and discard any
      // resources associated with the new data as necessary.
      if (apps != null) {
        releaseResources(apps);
        return;
      }
    }

    // Hold a reference to the old data so it doesn't get garbage collected.
    // We must protect it until the new data has been delivered.
    List<AppEntry> oldApps = mApps;
    mApps = apps;

    if (isStarted()) {
      if (DEBUG) Log.i(TAG, "+++ Delivering results to the LoaderManager for" +
      		" the ListFragment to display! +++");
      // If the Loader is in a started state, have the superclass deliver the
      // results to the client.
      super.deliverResult(apps);
    }

    // Invalidate the old data as we don't need it any more.
    if (oldApps != null && oldApps != apps) {
      if (DEBUG) Log.i(TAG, "+++ Releasing any old data associated with this Loader. +++");
      releaseResources(oldApps);
    }
  }

  /*********************************************************/
  /** (3) Implement the Loader�s state-dependent behavior **/
  /*********************************************************/

  @Override
  protected void onStartLoading() {
    if (DEBUG) Log.i(TAG, "+++ onStartLoading() called! +++");

    if (mApps != null) {
      // Deliver any previously loaded data immediately.
      if (DEBUG) Log.i(TAG, "+++ Delivering previously loaded data to the client...");
      deliverResult(mApps);
    }

    // Register the observers that will notify the Loader when changes are made.
    if (mAppsObserver == null) {
      mAppsObserver = new InstalledAppsObserver(this);
    }

    if (mLocaleObserver == null) {
      mLocaleObserver = new SystemLocaleObserver(this);
    }

    if (takeContentChanged()) {
      // When the observer detects a new installed application, it will call
      // onContentChanged() on the Loader, which will cause the next call to
      // takeContentChanged() to return true. If this is ever the case (or if
      // the current data is null), we force a new load.
      if (DEBUG) Log.i(TAG, "+++ A content change has been detected... so force load! +++");
      forceLoad();
    } else if (mApps == null) {
      // If the current data is null... then we should make it non-null! :)
      if (DEBUG) Log.i(TAG, "+++ The current data is data is null... so force load! +++");
      forceLoad();
    }
  }

  @Override
  protected void onStopLoading() {
    if (DEBUG) Log.i(TAG, "+++ onStopLoading() called! +++");

    // The Loader has been put in a stopped state, so we should attempt to
    // cancel the current load (if there is one).
    cancelLoad();

    // Note that we leave the observer as is; Loaders in a stopped state
    // should still monitor the data source for changes so that the Loader
    // will know to force a new load if it is ever started again.
  }

  @Override
  protected void onReset() {
    if (DEBUG) Log.i(TAG, "+++ onReset() called! +++");

    // Ensure the loader is stopped.
    onStopLoading();

    // At this point we can release the resources associated with 'apps'.
    if (mApps != null) {
      releaseResources(mApps);
      mApps = null;
    }

    // The Loader is being reset, so we should stop monitoring for changes.
    if (mAppsObserver != null) {
      getContext().unregisterReceiver(mAppsObserver);
      mAppsObserver = null;
    }

    if (mLocaleObserver != null) {
      getContext().unregisterReceiver(mLocaleObserver);
      mLocaleObserver = null;
    }
  }

  @Override
  public void onCanceled(List<AppEntry> apps) {
    if (DEBUG) Log.i(TAG, "+++ onCanceled() called! +++");

    // Attempt to cancel the current asynchronous load.
    super.onCanceled(apps);

    // The load has been canceled, so we should release the resources
    // associated with 'mApps'.
    releaseResources(apps);
  }

  @Override
  public void forceLoad() {
    if (DEBUG) Log.i(TAG, "+++ forceLoad() called! +++");
    super.forceLoad();
  }

  /**
   * Helper method to take care of releasing resources associated with an
   * actively loaded data set.
   */
  private void releaseResources(List<AppEntry> apps) {
    // For a simple List, there is nothing to do. For something like a Cursor,
    // we would close it in this method. All resources associated with the
    // Loader should be released here.
  }

  /*********************************************************************/
  /** (4) Observer which receives notifications when the data changes **/
  /*********************************************************************/

  // An observer to notify the Loader when new apps are installed/updated.
  private InstalledAppsObserver mAppsObserver;

  // The observer to notify the Loader when the system Locale has been changed.
  private SystemLocaleObserver mLocaleObserver;

  /**************************/
  /** (5) Everything else! **/
  /**************************/

  /**
   * Performs alphabetical comparison of {@link AppEntry} objects. This is
   * used to sort queried data in {@link loadInBackground}.
   */
  private static final Comparator<AppEntry> ALPHA_COMPARATOR = new Comparator<AppEntry>() {
    Collator sCollator = Collator.getInstance();

    @Override
    public int compare(AppEntry object1, AppEntry object2) {
      return sCollator.compare(object1.getLabel(), object2.getLabel());
    }
  };
}
