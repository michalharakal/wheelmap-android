/*
Copyright (C) 2011 Michal Harakal and Michael Kroez

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS-IS" BASIS
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
        
*/

package org.wheelmap.android.ui;

import org.wheelmap.android.R;
import org.wheelmap.android.service.SyncService;
import org.wheelmap.android.service.SyncServiceException;
import org.wheelmap.android.utils.DetachableResultReceiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

public class POIsListActivity extends Activity implements
		DetachableResultReceiver.Receiver {

	private final static String TAG = "poislist";

	private State mState;
	private boolean isInForeground;
	private boolean isShowingDialog;

	GoogleAnalyticsTracker tracker;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// GA
		tracker = GoogleAnalyticsTracker.getInstance();
		tracker.startNewSession("UA-25843648-1", 20, this);
		tracker.setAnonymizeIp(true);
		tracker.trackPageView("/ListActivity");

		mState = (State) getLastNonConfigurationInstance();
		final boolean previousState = mState != null;

		if (previousState) {
			// Start listening for SyncService updates again
			mState.mReceiver.setReceiver(this);
		} else {
			mState = new State();
			mState.mReceiver.setReceiver(this);
		}
		
		if (savedInstanceState == null) {
		    getFragmentManager().beginTransaction().replace(android.R.id.content, new PoiListFragment(), PoiListFragment.TAG).commit();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		isInForeground = true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		isInForeground = false;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Stop the tracker when it is no longer needed.
		tracker.stopSession();
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		// Clear any strong references to this Activity, we'll reattach to
		// handle events on the other side.
		mState.mReceiver.clearReceiver();
		return mState;
	}

	public void onInfoClick(View v) {
		Intent intent = new Intent(this, InfoActivity.class);
		startActivity(intent);
	}

//	public void onNewPOIClick(View v) {
//		saveListPosition();
//
//		// create new POI and start editing
//		ContentValues cv = new ContentValues();
//		cv.put(Wheelmap.POIs.NAME, getString(R.string.new_default_name));
//		cv.put(Wheelmap.POIs.COORD_LAT,
//				Math.ceil(mLocation.getLatitude() * 1E6));
//		cv.put(Wheelmap.POIs.COORD_LON,
//				Math.ceil(mLocation.getLongitude() * 1E6));
//		cv.put(Wheelmap.POIs.CATEGORY_ID, 1);
//		cv.put(Wheelmap.POIs.NODETYPE_ID, 1);
//
//		Uri new_pois = getContentResolver().insert(Wheelmap.POIs.CONTENT_URI,
//				cv);
//
//		// edit activity
//		Log.i(TAG, new_pois.toString());
//		long poiId = Long.parseLong(new_pois.getLastPathSegment());
//		Intent i = new Intent(POIsListActivity.this,
//				POIDetailActivityEditable.class);
//		i.putExtra(Wheelmap.POIs.EXTRAS_POI_ID, poiId);
//		startActivity(i);
//
//	}

	private void updateRefreshStatus() {
	    Log.d(TAG, "updateRefreshStatus");
	    PoiListFragment listFragment = (PoiListFragment) getFragmentManager().findFragmentByTag(PoiListFragment.TAG);
	    if (listFragment != null) {
	        listFragment.updateRefreshStatus(mState.mSyncing);
	    }
	}

	public void onReceiveResult(int resultCode, Bundle resultData) {
		Log.d(TAG, "onReceiveResult in list resultCode = " + resultCode);
		switch (resultCode) {
		case SyncService.STATUS_RUNNING: {
			mState.mSyncing = true;
			updateRefreshStatus();
			break;
		}
		case SyncService.STATUS_FINISHED: {
			mState.mSyncing = false;
			updateRefreshStatus();
			break;
		}
		case SyncService.STATUS_ERROR: {
			// Error happened down in SyncService, show as toast.
			mState.mSyncing = false;
			updateRefreshStatus();
			final SyncServiceException e = resultData
					.getParcelable(SyncService.EXTRA_ERROR);
			showErrorDialog(e);
			break;
		}
		}
	}

	private void showErrorDialog(SyncServiceException e) {
		if (!isInForeground)
			return;
		if (isShowingDialog)
			return;
		
		isShowingDialog = true;

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		Log.d(TAG, "showErrorDialog: e.getCode = " + e.getErrorCode());
		if (e.getErrorCode() == SyncServiceException.ERROR_NETWORK_FAILURE)
			builder.setTitle(R.string.error_network_title);
		else
			builder.setTitle(R.string.error_occurred);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setMessage(e.getRessourceString());
		builder.setNeutralButton(R.string.okay,
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						isShowingDialog = false;
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}
	
    /**
     * State specific to {@link HomeActivity} that is held between configuration
     * changes. Any strong {@link Activity} references <strong>must</strong> be
     * cleared before {@link #onRetainNonConfigurationInstance()}, and this
     * class should remain {@code static class}.
     */
    private static class State {

        public DetachableResultReceiver mReceiver;
        public boolean mSyncing = false;

        private State() {
            mReceiver = new DetachableResultReceiver(new Handler());
        }
    }
}
