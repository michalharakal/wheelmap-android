package org.wheelmap.android.ui.mapsforge;


import org.mapsforge.android.maps.CircleOverlay;
import org.mapsforge.android.maps.GeoPoint;
import org.mapsforge.android.maps.MapActivity;
import org.mapsforge.android.maps.MapController;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.OverlayCircle;
import org.wheelmap.android.R;
import org.wheelmap.android.manager.MyLocationManager;
import org.wheelmap.android.model.Wheelmap;
import org.wheelmap.android.service.SyncService;

import org.wheelmap.android.ui.WheelmapHomeActivity;
import org.wheelmap.android.utils.DetachableResultReceiver;
import org.wheelmap.android.utils.ParceableBoundingBox;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Toast;

public class POIsMapsforgeActivity extends MapActivity implements
		DetachableResultReceiver.Receiver {

	public static final String EXTRA_NO_RETRIEVAL = "org.wheelmap.android.ui.Mapsforge.NO_RETRIEVAL";

	/** State held between configuration changes. */
	private State mState;

	private Cursor mCursor;

	private MapController mMapController;
	private MapView mMapView;
	// private POIsPaintedMapsforgeOverlay poisItemizedOverlay;
	private POIsCursorMapsforgeOverlay mPoisItemizedOverlay;
	private MyLocationOverlay mCurrLocationOverlay;

	private MyLocationManager mLocationManager;
	private GeoPoint mLastGeoPointE6;
	private boolean isCentered;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mapsforge);
		mMapView = (MapView) findViewById(R.id.map);

		mMapView.setClickable(true);
		mMapView.setBuiltInZoomControls(true);

		ConfigureMapView.pickAppropriateMap( this, mMapView );

		mMapController = mMapView.getController();
		mMapController.setZoom(16); // Zoon 1 is world view

		// Run query
		Uri uri = Wheelmap.POIs.CONTENT_URI;
		mCursor = getContentResolver().query(uri, Wheelmap.POIs.PROJECTION,
				null, null, Wheelmap.POIs.DEFAULT_SORT_ORDER);

		// overlays
		// poisItemizedOverlay = new POIsPaintedMapsforgeOverlay(this, mCursor);
		mPoisItemizedOverlay = new POIsCursorMapsforgeOverlay(this, mCursor);
		mMapView.getOverlays().add(mPoisItemizedOverlay);

		mCurrLocationOverlay = new MyLocationOverlay();
		mMapView.getOverlays().add(mCurrLocationOverlay);
		isCentered = false;
				
		if (getIntent() != null && !getIntent().getBooleanExtra(EXTRA_NO_RETRIEVAL, false)) {
			mMapView.getViewTreeObserver().addOnGlobalLayoutListener(
					new OnGlobalLayoutListener() {

						@Override
						public void onGlobalLayout() {
							requestUpdate();
							mMapView.getViewTreeObserver()
									.removeGlobalOnLayoutListener(this);
						}
					});
		}

		mState = (State) getLastNonConfigurationInstance();
		final boolean previousState = mState != null;

		if (previousState) {
			// Start listening for SyncService updates again
			mState.mReceiver.setReceiver(this);
			updateRefreshStatus();

		} else {
			mState = new State();
			mState.mReceiver.setReceiver(this);
		}
		
		mLocationManager = MyLocationManager.get( mState.mReceiver, true );
		findViewById(R.id.btn_title_gps).setVisibility(View.GONE);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		mCursor.deactivate();
		mLocationManager.release( mState.mReceiver );
	}


	@Override
	protected void onResume() {
		super.onResume();
		mCursor.requery();
		mLocationManager.register( mState.mReceiver, true );
	}

	/** {@inheritDoc} */
	public void onReceiveResult(int resultCode, Bundle resultData) {
		switch (resultCode) {
		case SyncService.STATUS_RUNNING: {
			mState.mSyncing = true;
			updateRefreshStatus();
			break;
		}
		case SyncService.STATUS_FINISHED: {
			mState.mSyncing = false;
			updateRefreshStatus();
			mMapView.invalidate();
			break;
		}
		case SyncService.STATUS_ERROR: {
			// Error happened down in SyncService, show as toast.
			mState.mSyncing = false;
			updateRefreshStatus();
			final String errorText = getString(R.string.toast_sync_error,
					resultData.getString(Intent.EXTRA_TEXT));
			Toast.makeText(POIsMapsforgeActivity.this, errorText,
					Toast.LENGTH_LONG).show();
			break;
		}
		case MyLocationManager.WHAT_LOCATION_MANAGER_UPDATE: {
			Location location = (Location)resultData.getParcelable( MyLocationManager.EXTRA_LOCATION_MANAGER_LOCATION );
			GeoPoint geoPoint = calcGeoPoint(location);
			if ( !isCentered ) {
				mMapController.setCenter(geoPoint);
				isCentered = true;
			}
			
			// we got the first time current position so center map on it
			if (mLastGeoPointE6 == null) {
				findViewById(R.id.btn_title_gps).setVisibility(View.VISIBLE);
				mMapController.setCenter(geoPoint);
			}
			mLastGeoPointE6 = geoPoint;
			mCurrLocationOverlay.setLocation(mLastGeoPointE6,
					location.getAccuracy());
			break;
		}
		
		}
	}

	private void updateRefreshStatus() {
		findViewById(R.id.btn_title_refresh).setVisibility(
				mState.mSyncing ? View.GONE : View.VISIBLE);
		findViewById(R.id.title_refresh_progress).setVisibility(
				mState.mSyncing ? View.VISIBLE : View.GONE);
	}

	public void onCenterOnCurrentLocationClick(View v) {
		if (mLastGeoPointE6 != null) {
			mMapController.setCenter(mLastGeoPointE6);
			requestUpdate();
		}
	}

	public void onHomeClick(View v) {
		final Intent intent = new Intent(this, WheelmapHomeActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		this.startActivity(intent);
	}

	private void fillExtrasWithBoundingRect(Bundle bundle) {
		int latSpan = mMapView.getLatitudeSpan();
		int lonSpan = mMapView.getLongitudeSpan();
		GeoPoint center = mMapView.getMapCenter();
		ParceableBoundingBox boundingBox = new ParceableBoundingBox(
				center.getLatitudeE6() + (latSpan / 2), center.getLongitudeE6()
						+ (lonSpan / 2),
				center.getLatitudeE6() - (latSpan / 2), center.getLongitudeE6()
						- (lonSpan / 2));
		bundle.putSerializable(SyncService.EXTRA_BOUNDING_BOX,
				boundingBox);
	}

	private void requestUpdate() {
		// get bounding box from current view
		Bundle extras = new Bundle();
		//
		fillExtrasWithBoundingRect(extras);

		// trigger off background sync
		final Intent intent = new Intent(Intent.ACTION_SYNC, null, this,
				SyncService.class);
		intent.putExtras(extras);
		intent.putExtra(SyncService.EXTRA_WHAT, SyncService.WHAT_RETRIEVE_NODES );
		intent.putExtra(SyncService.EXTRA_STATUS_RECEIVER, mState.mReceiver);
		startService(intent);
	}

	public void onRefreshClick(View v) {
		requestUpdate();
	}

	public void onSearchClick(View v) {
		Toast.makeText(this, "Searching..", Toast.LENGTH_SHORT).show();
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

	private GeoPoint calcGeoPoint(Location location) {
		int lat = (int) (location.getLatitude() * 1E6);
		int lng = (int) (location.getLongitude() * 1E6);
		return new GeoPoint(lat, lng);
	}

	private static class MyLocationOverlay extends CircleOverlay<OverlayCircle> {
		OverlayCircle mCircleLarge, mCircleSmall;
		private final static float RADIUS_SMALL_CIRCLE = 2.0f;
		private final static int NUMBER_OF_CIRCLES = 2;

		public MyLocationOverlay() {
			super(null, null);

			Paint fillPaintDark = new Paint(Paint.ANTI_ALIAS_FLAG);
			fillPaintDark.setARGB(60, 127, 159, 239);

			Paint outlinePaintDark = new Paint(Paint.ANTI_ALIAS_FLAG);
			outlinePaintDark.setARGB(255, 79, 92, 140);
			outlinePaintDark.setStrokeWidth(4);
			outlinePaintDark.setStyle(Style.STROKE);

			Paint fillPaintLight = new Paint(Paint.ANTI_ALIAS_FLAG);
			fillPaintLight.setARGB(255, 47, 111, 223);

			Paint outlinePaintLight = new Paint(Paint.ANTI_ALIAS_FLAG);
			outlinePaintLight.setARGB(255, 132, 132, 132);
			outlinePaintLight.setStrokeWidth(10);
			outlinePaintLight.setStyle(Style.STROKE);

			mCircleLarge = new OverlayCircle(fillPaintDark, outlinePaintDark);
			mCircleSmall = new OverlayCircle(fillPaintLight, outlinePaintLight);
		}

		public void setLocation(GeoPoint center, float radius) {
			mCircleLarge.setCircleData(center, radius);
			mCircleSmall.setCircleData(center, RADIUS_SMALL_CIRCLE);
			populate();
		}

		@Override
		public int size() {
			return NUMBER_OF_CIRCLES;
		}

		@Override
		protected OverlayCircle createCircle(int i) {
			if (i == 1)
				return mCircleLarge;
			else
				return mCircleSmall;
		}
	}

}