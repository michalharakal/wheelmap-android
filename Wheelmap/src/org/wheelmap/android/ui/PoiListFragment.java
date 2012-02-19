package org.wheelmap.android.ui;

import org.wheelmap.android.R;
import org.wheelmap.android.manager.MyLocationManager;
import org.wheelmap.android.model.POIHelper;
import org.wheelmap.android.model.POIsCursorWrapper;
import org.wheelmap.android.model.POIsListCursorAdapter;
import org.wheelmap.android.model.QueriesBuilderHelper;
import org.wheelmap.android.model.Wheelmap;
import org.wheelmap.android.service.SyncService;
import org.wheelmap.android.utils.DetachableResultReceiver;
import org.wheelmap.android.utils.DetachableResultReceiver.Receiver;

import wheelmap.org.BoundingBox.Wgs84GeoCoordinates;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.app.SupportActivity;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.markupartist.android.widget.PullToRefreshListView;
import com.markupartist.android.widget.PullToRefreshListView.OnRefreshListener;

public class PoiListFragment extends ListFragment implements OnRefreshListener,
        Receiver, LoaderCallbacks<Cursor> {

    public interface PoiSelectedListener {

        void onPoiSelected(long id);
    }

    public static final String TAG = "PoiListFragment";
    private static final double QUERY_DISTANCE_DEFAULT = 0.8;
    private static final String PREF_KEY_LIST_DISTANCE = "listDistance";

    public static final String EXTRA_IS_RECREATED = "org.wheelmap.android.ORIENTATION_CHANGE";
    public static final String EXTRA_FIRST_VISIBLE_POSITION = "org.wheelmap.android.FIRST_VISIBLE_POSITION";

    private MyLocationManager mLocationManager;
    private Location mLocation;
    private DetachableResultReceiver mReceiver;

    private float mDistance;
    private int mFirstVisiblePosition = 0;
    private boolean mIsRecreated;
    private POIsListCursorAdapter mListAdapter;
    private PoiSelectedListener listener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mReceiver = new DetachableResultReceiver(new Handler());
        mReceiver.setReceiver(this);
        mLocationManager = MyLocationManager.get(mReceiver, true);
        mLocation = mLocationManager.getLastLocation();
        mDistance = getDistanceFromPreferences();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.list_fragment, container, false);
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.Fragment#onAttach(android.support.v4.app.
     * SupportActivity)
     */
    @Override
    public void onAttach(SupportActivity activity) {
        // TODO Auto-generated method stub
        super.onAttach(activity);
        listener = (PoiSelectedListener) activity;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getListView().setTextFilterEnabled(true);

        PullToRefreshListView listView = (PullToRefreshListView) getListView();
        listView.setOnRefreshListener(this);

        mListAdapter = new POIsListCursorAdapter(getActivity(), null);
        setListAdapter(mListAdapter);

        getLoaderManager().initLoader(0, null, this);

        if (savedInstanceState == null) {
            mIsRecreated = false;
        } else {
            mIsRecreated = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        runQueryOnCreation();
        mLocationManager.register(mReceiver, true);
    }

    @Override
    public void onPause() {
        super.onPause();

        mLocationManager.release(mReceiver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_FIRST_VISIBLE_POSITION, mFirstVisiblePosition);
    }

    @Override
    public void onRefresh() {
        runQuery(true);
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        switch (resultCode) {
        case MyLocationManager.WHAT_LOCATION_MANAGER_UPDATE: {
            mLocation = (Location) resultData
                    .getParcelable(MyLocationManager.EXTRA_LOCATION_MANAGER_LOCATION);
            break;
        }
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        saveListPosition();

        Cursor cursor = (Cursor) l.getAdapter().getItem(position);
        if (cursor == null) {
            return;
        }

        long poiId = POIHelper.getId(cursor);

        // Intent i = new Intent(getActivity(), POIDetailActivity.class);
        // i.putExtra(Wheelmap.POIs.EXTRAS_POI_ID, poiId);
        // startActivity(i);
        listener.onPoiSelected(poiId);
    }

    private float getDistanceFromPreferences() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getActivity()
                        .getApplicationContext());

        String prefDist = prefs.getString(PREF_KEY_LIST_DISTANCE,
                String.valueOf(QUERY_DISTANCE_DEFAULT));
        return Float.valueOf(prefDist);
    }

    public void runQueryOnCreation() {
        Log.d(TAG, "runQueryOnCreation: mIsRecreated = " + mIsRecreated);
        if (!mIsRecreated) {
            mFirstVisiblePosition = 0;
            getListView().setSelection(mFirstVisiblePosition);
        }
        runQuery(!mIsRecreated);
    }

    public void runQuery(boolean forceReload) {
        Log.d(TAG, "runQuery: forceReload = " + forceReload);
        if (forceReload) {
            mFirstVisiblePosition = 0;
            requestData();
        }
        Log.d(TAG, "runQuery: mFirstVisible = " + mFirstVisiblePosition);
        getListView().setSelection(mFirstVisiblePosition);
    }

    public String[] createWhereValues() {
        String[] lonlat = new String[] {
                String.valueOf(mLocation.getLongitude()),
                String.valueOf(mLocation.getLatitude()) };
        return lonlat;
    }

    public Cursor createCursorWrapper(Cursor cursor) {
        Wgs84GeoCoordinates wgsLocation = new Wgs84GeoCoordinates(
                mLocation.getLongitude(), mLocation.getLatitude());
        return new POIsCursorWrapper(cursor, wgsLocation);
    }

    private void requestData() {
        final Intent intent = new Intent(Intent.ACTION_SYNC, null,
                getActivity(), SyncService.class);
        intent.putExtra(SyncService.EXTRA_WHAT, SyncService.WHAT_RETRIEVE_NODES);
        intent.putExtra(SyncService.EXTRA_STATUS_RECEIVER, mReceiver);
        intent.putExtra(SyncService.EXTRA_LOCATION, mLocation);
        intent.putExtra(SyncService.EXTRA_DISTANCE_LIMIT, mDistance);
        getActivity().startService(intent);
    }

    private void saveListPosition() {
        mFirstVisiblePosition = getListView().getFirstVisiblePosition();
    }

    public void updateRefreshStatus(boolean syncing) {
        Log.d(TAG, "refreshSTatus: " + syncing);
        if (syncing) {
            ((PullToRefreshListView) getListView()).prepareForRefresh();
        } else {
            ((PullToRefreshListView) getListView()).onRefreshComplete();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri = Wheelmap.POIs.CONTENT_URI_POI_SORTED;
        return new CursorLoader(getActivity(), uri, Wheelmap.POIs.PROJECTION,
                QueriesBuilderHelper.userSettingsFilter(getActivity()
                        .getApplicationContext()), createWhereValues(), "");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Cursor wrappingCursor = createCursorWrapper(data);
        mListAdapter.swapCursor(wrappingCursor);
        updateRefreshStatus(false);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mListAdapter.swapCursor(null);
    }

}
