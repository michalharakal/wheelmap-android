package org.wheelmap.android.ui.mapsforge;

import java.util.ArrayList;
import java.util.List;

import org.mapsforge.android.maps.GeoPoint;
import org.mapsforge.android.maps.MapContext;
import org.mapsforge.android.maps.MapController;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.MapView.OnZoomListener;
import org.wheelmap.android.manager.MyLocationManager;
import org.wheelmap.android.model.QueriesBuilderHelper;
import org.wheelmap.android.model.Wheelmap;
import org.wheelmap.android.ui.mapsforge.MyMapView.MapViewTouchMove;
import org.wheelmap.android.utils.DetachableResultReceiver;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.SupportActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

public class MapsforgeFragment extends Fragment implements
        DetachableResultReceiver.Receiver, MapViewTouchMove, OnZoomListener {

    private final static String TAG = "mapsforge";

    private State mState;

    private MapFragmentContext mMapFragmentContext;
    private MapController mMapController;
    private MyMapView mMapView;
    private POIsCursorMapsforgeOverlay mPoisItemizedOverlay;
    private MyLocationOverlay mCurrLocationOverlay;
    private MyLocationManager mLocationManager;
    private GeoPoint mLastGeoPointE6;

    private boolean isCentered;

    // Begin startup lifecylce

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.Fragment#onAttach(android.support.v4.app.
     * SupportActivity)
     */
    @Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        mState = new State();
        mState.mReceiver.setReceiver(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMapFragmentContext = getMapContext();

        mMapView = new MyMapView(mMapFragmentContext);
        mMapView.setClickable(true);
        mMapView.setBuiltInZoomControls(true);
        mMapView.setScaleBar(true);

        ConfigureMapView.pickAppropriateMap(getActivity(), mMapView);

        mMapController = mMapView.getController();

        // overlays
        mPoisItemizedOverlay = new POIsCursorMapsforgeOverlay(getActivity());
        runQuery();

        mMapView.getOverlays().add(mPoisItemizedOverlay);

        mCurrLocationOverlay = new MyLocationOverlay();
        mMapView.getOverlays().add(mCurrLocationOverlay);
        mMapView.registerListener(this);
        mMapView.registerZoomListener(this);
        mMapController.setZoom(18); // Zoom 1 is world view

        isCentered = false;

        mLocationManager = MyLocationManager.get(mState.mReceiver, true);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        LinearLayout.LayoutParams llParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        LinearLayout ll = new LinearLayout(getActivity());
        ll.addView(mMapView, llParams);
        return ll;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapFragmentContext.onResume();
    }

    // end startup lifecycle

    // begin stop lifecycle

    @Override
    public void onPause() {
        super.onPause();
        mMapFragmentContext.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapFragmentContext.destroyMapViews();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    // end startup cycle

    public void setCenter(GeoPoint location) {
        mMapController.setCenter(location);
        isCentered = true;
    }

    public void repopulateOverlay() {
        runQuery();
    }

    public MapFragmentContext getMapContext() {
        return new MapFragmentContext(getActivity().getApplicationContext());
    }

    private class MapFragmentContext extends ContextWrapper implements
            MapContext {

        /**
         * Counter to store the last ID given to a MapView.
         */
        private int lastMapViewId;
        private List<MapView> mapViews = new ArrayList<MapView>(2);

        public MapFragmentContext(Context context) {
            super(context);
        }

        @Override
        public int getMapViewId() {
            return ++this.lastMapViewId;
        }

        @Override
        public void registerMapView(MapView mapView) {
            if (this.mapViews != null) {
                this.mapViews.add(mapView);
            }
        }

        @Override
        public void unregisterMapView(MapView mapView) {
            if (this.mapViews != null) {
                this.mapViews.remove(mapView);
            }
        }

        private void destroyMapViews() {
            if (this.mapViews != null) {
                MapView currentMapView;
                while (!this.mapViews.isEmpty()) {
                    currentMapView = this.mapViews.get(0);
                    currentMapView.destroy();
                }
                currentMapView = null;
                this.mapViews.clear();
                this.mapViews = null;
            }
        }

        private void onResume() {
            for (int i = 0, n = this.mapViews.size(); i < n; ++i) {
                this.mapViews.get(i).onResume();
            }
        }

        private void onPause() {
            for (int i = 0, n = this.mapViews.size(); i < n; ++i) {
                MapView currentMapView = this.mapViews.get(i);
                currentMapView.onPause();
            }
        }
    }

    @Override
    public void onZoom(byte zoomLevel) {

    }

    @Override
    public void onMapViewTouchMoveEnough() {

    }

    private void runQuery() {
        // Run query
        Uri uri = Wheelmap.POIs.CONTENT_URI;
        Cursor cursor = getActivity().getContentResolver().query(
                uri,
                Wheelmap.POIs.PROJECTION,
                QueriesBuilderHelper.userSettingsFilter(getActivity()
                        .getApplicationContext()), null,
                Wheelmap.POIs.DEFAULT_SORT_ORDER);

        mPoisItemizedOverlay.setCursor(cursor);
    }

    /** {@inheritDoc} */
    public void onReceiveResult(int resultCode, Bundle resultData) {
        Log.d(TAG, "onReceiveResult in mapsforge fragment resultCode = "
                + resultCode);
        switch (resultCode) {
        case MyLocationManager.WHAT_LOCATION_MANAGER_UPDATE: {
            Location location = (Location) resultData
                    .getParcelable(MyLocationManager.EXTRA_LOCATION_MANAGER_LOCATION);
            GeoPoint geoPoint = calcGeoPoint(location);
            if (!isCentered) {
                mMapController.setCenter(geoPoint);
                isCentered = true;
            }

            // we got the first time current position so center map on it
            if (mLastGeoPointE6 == null && !isCentered) {
                // findViewById(R.id.btn_title_gps).setVisibility(View.VISIBLE);
                mMapController.setCenter(geoPoint);
            }
            mLastGeoPointE6 = geoPoint;
            mCurrLocationOverlay.setLocation(mLastGeoPointE6,
                    location.getAccuracy());
            break;
        }

        }
    }

    private GeoPoint calcGeoPoint(Location location) {
        int lat = (int) (location.getLatitude() * 1E6);
        int lng = (int) (location.getLongitude() * 1E6);
        return new GeoPoint(lat, lng);
    }

    private static class State {

        public DetachableResultReceiver mReceiver;
        public boolean mSyncing = false;

        private State() {
            mReceiver = new DetachableResultReceiver(new Handler());
        }
    }

}
