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

import java.util.HashMap;

import org.mapsforge.android.maps.GeoPoint;
import org.mapsforge.android.maps.ItemizedOverlay;
import org.mapsforge.android.maps.MapActivity;
import org.mapsforge.android.maps.MapController;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.OverlayItem;
import org.wheelmap.android.R;
import org.wheelmap.android.app.WheelmapApp;
import org.wheelmap.android.manager.SupportManager;
import org.wheelmap.android.manager.SupportManager.NodeType;
import org.wheelmap.android.model.POIHelper;
import org.wheelmap.android.model.Wheelmap;
import org.wheelmap.android.service.SyncService;
import org.wheelmap.android.ui.mapsforge.ConfigureMapView;
import org.wheelmap.android.ui.mapsforge.POIsMapsforgeActivity;

import wheelmap.org.WheelchairState;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class POIDetailActivity extends MapActivity {
	private final static String TAG = "poidetail";

	// private ImageView iconImage = null;
	private TextView nameText = null;
	private TextView categoryText = null;
	private TextView nodetypeText = null;
	private TextView commentText = null;
	private TextView addressText = null;
	private TextView websiteText = null;
	private TextView phoneText = null;
	private ImageView mStateIcon = null;
	private TextView mWheelchairStateText = null;
	private RelativeLayout mWheelchairStateLayout = null;
	private HashMap<WheelchairState, Integer> mWheelchairStateTextColorMap = new HashMap<WheelchairState, Integer>();
	private HashMap<WheelchairState, Integer> mWheelchairStateTextsMap = new HashMap<WheelchairState, Integer>();

	private MapController mapController;
	private MapView mapView;

	private WheelchairState mWheelChairState;
	private SupportManager mSupportManager;
	private ViewGroup mContentView;

	private Long poiID;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_detail);
		mSupportManager = WheelmapApp.getSupportManager();
		System.gc();

		//nameText = (TextView) findViewById(R.id.title_name);
		//categoryText = (TextView) findViewById(R.id.title_category);
		nodetypeText = (TextView) findViewById(R.id.nodetype);

		phoneText = (TextView) findViewById(R.id.phone);
		addressText = (TextView) findViewById(R.id.addr);
		commentText = (TextView) findViewById(R.id.comment);
		websiteText = (TextView) findViewById(R.id.website);
		mStateIcon = (ImageView) findViewById(R.id.wheelchair_state_icon);
		mWheelchairStateText = (TextView) findViewById(R.id.wheelchair_state_text);
		mWheelchairStateLayout = (RelativeLayout) findViewById( R.id.wheelchair_state_layout );

		mWheelchairStateTextColorMap.put(WheelchairState.YES, new Integer(
				R.color.wheel_enabled));
		mWheelchairStateTextColorMap.put(WheelchairState.NO, new Integer(
				R.color.wheel_disabled));
		mWheelchairStateTextColorMap.put(WheelchairState.LIMITED, new Integer(
				R.color.wheel_limited));
		mWheelchairStateTextColorMap.put(WheelchairState.UNKNOWN, new Integer(
				R.color.wheel_unknown));

		mWheelchairStateTextsMap.put(WheelchairState.YES, new Integer(
				R.string.ws_enabled_title));
		mWheelchairStateTextsMap.put(WheelchairState.NO, new Integer(
				R.string.ws_disabled_title));
		mWheelchairStateTextsMap.put(WheelchairState.LIMITED, new Integer(
				R.string.ws_limited_title));
		mWheelchairStateTextsMap.put(WheelchairState.UNKNOWN, new Integer(
				R.string.ws_unknown_title));

		mWheelchairStateLayout.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				onEditWheelchairState(v);
			}
		});

		mapView = (MapView) findViewById(R.id.map);

		mapView.setClickable(true);
		mapView.setBuiltInZoomControls(true);
		ConfigureMapView.pickAppropriateMap(this, mapView);
		mapController = mapView.getController();
		mapController.setZoom(18);

		poiID = getIntent().getLongExtra(Wheelmap.POIs.EXTRAS_POI_ID, -1);
		Log.d( TAG, "onCreate: poiID = " + poiID );
		
		if (poiID != -1) {
			load();
		}
		
	}

	@Override
	public void onPause() {
		// save();

		super.onPause();
	}

	@Override
	public void onResume() {
		if (poiID != -1) {
			load();
		}
		super.onResume();
		logMemory();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mSupportManager.cleanReferences();
		nullViewDrawablesRecursive( mContentView);
		mapView = null;
		mapController = null;
		System.gc();
		System.gc(); // to be sure ;-)
	}

	public void onItemEdit(View v) {
		// Launch overall conference schedule
		Intent i = new Intent(POIDetailActivity.this,
				POIDetailActivityEditable.class);
		i.putExtra(Wheelmap.POIs.EXTRAS_POI_ID, poiID);
		startActivity(i);
	}
	
	public void onItemShare(View v) {
		
		Uri poiUri = Uri.withAppendedPath(Wheelmap.POIs.CONTENT_URI_POI_ID,
				String.valueOf(poiID));
		
		// Then query for this specific record:
		Cursor cur = managedQuery(poiUri, null, null, null, null);

		if (cur.getCount() < 1) {
			cur.close();
			return;
		}

		cur.moveToFirst();
		WheelchairState state = POIHelper.getWheelchair(cur);
		String name = POIHelper.getName(cur);
		String comment = POIHelper.getComment(cur);
		int lat = (int) (POIHelper.getLatitude(cur) * 1E6);
		int lon = (int) (POIHelper.getLongitude(cur) * 1E6);
		int nodeTypeId = POIHelper.getNodeTypeId(cur);
		int categoryId = POIHelper.getCategoryId(cur);
	
		StringBuilder sb = new StringBuilder(name);
		sb.append(comment);
		sb.append(',');
		sb.append(POIHelper.getAddress(cur));
		sb.append(',');
		sb.append(POIHelper.getWebsite(cur));
		
		Intent sharingIntent = new Intent(Intent.ACTION_SEND);
		sharingIntent.setType("text/plain");
		sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, sb.toString());
		startActivity(Intent.createChooser(sharingIntent,"Share using"));
	}
	
	
	

	public void onEditWheelchairState(View v) {
		// Sometimes, the poiId doesnt exists in the db, as the db got loaded again
		// Actually it would be better to use the wmId in this activity, instead
		// of the poiId, as the wmId is persistent during reload
		// This is only a quick fix to take care of a npe here,
		// as mWheelchairState is null in this case.
		if ( mWheelChairState == null )
			return;
		
		// Start the activity whose result we want to retrieve. The
		// result will come back with request code GET_CODE.
		Intent intent = new Intent(POIDetailActivity.this,
				WheelchairStateActivity.class);
		intent.putExtra(Wheelmap.POIs.WHEELCHAIR,
				(long) mWheelChairState.getId());
		startActivityForResult(intent, SELECT_WHEELCHAIRSTATE);
	}

	private void setWheelchairState(WheelchairState newState) {
		mWheelChairState = newState;
		mStateIcon.setImageDrawable(mSupportManager
				.lookupWheelDrawable(newState.getId()));
		mWheelchairStateText.setTextColor(getResources().getColor(
				mWheelchairStateTextColorMap.get(newState)));
		mWheelchairStateText.setText(mWheelchairStateTextsMap.get(newState));
	}

	private void load() {

		// Use the ContentUris method to produce the base URI for the contact
		// with _ID == 23.
		Uri poiUri = Uri.withAppendedPath(Wheelmap.POIs.CONTENT_URI_POI_ID,
				String.valueOf(poiID));

		// Then query for this specific record:
		Cursor cur = managedQuery(poiUri, null, null, null, null);

		if (cur.getCount() < 1) {
			cur.close();
			return;
		}

		cur.moveToFirst();
		WheelchairState state = POIHelper.getWheelchair(cur);
		String name = POIHelper.getName(cur);
		String comment = POIHelper.getComment(cur);
		int lat = (int) (POIHelper.getLatitude(cur) * 1E6);
		int lon = (int) (POIHelper.getLongitude(cur) * 1E6);
		int nodeTypeId = POIHelper.getNodeTypeId(cur);
		int categoryId = POIHelper.getCategoryId(cur);

		NodeType nodeType = mSupportManager.lookupNodeType(nodeTypeId);
		// iconImage.setImageDrawable(nodeType.iconDrawable);

		setWheelchairState(state);
		nameText.setText(name);
		String category = mSupportManager.lookupCategory(categoryId).localizedName;
		categoryText.setText(category);
		nodetypeText.setText(nodeType.localizedName);
		commentText.setText(comment);
		addressText.setText(POIHelper.getAddress(cur));
		websiteText.setText(POIHelper.getWebsite(cur));
		phoneText.setText(POIHelper.getPhone(cur));

		POIMapsforgeOverlay overlay = new POIMapsforgeOverlay();
		overlay.setItem(name, comment, nodeType, state, lat, lon);
		mapView.getOverlays().clear();
		mapView.getOverlays().add(overlay);
		mapController.setCenter(new GeoPoint(lat, lon));
		cur.close();
	}

	/**
	 * This method is called when the sending activity has finished, with the
	 * result it supplied.
	 * 
	 * @param requestCode
	 *            The original request code as given to startActivity().
	 * @param resultCode
	 *            From sending activity as per setResult().
	 * @param data
	 *            From sending activity as per setResult().
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// You can use the requestCode to select between multiple child
		// activities you may have started. Here there is only one thing
		// we launch.
		if (requestCode == SELECT_WHEELCHAIRSTATE) {
			// This is a standard resultCode that is sent back if the
			// activity doesn't supply an explicit result. It will also
			// be returned if the activity failed to launch.
			if (resultCode == RESULT_OK) {
				// newly selected wheelchair state as action data
				if (data != null) {
					WheelchairState newState = WheelchairState.valueOf(Integer
							.parseInt(data.getAction()));
					Uri poiUri = Uri.withAppendedPath(
							Wheelmap.POIs.CONTENT_URI_POI_ID, String.valueOf(poiID));
					ContentValues values = new ContentValues();
					values.put(Wheelmap.POIs.WHEELCHAIR, newState.getId());
					values.put(Wheelmap.POIs.UPDATE_TAG,
							Wheelmap.UPDATE_WHEELCHAIR_STATE);
					this.getContentResolver().update(poiUri, values, "", null);

					final Intent intent = new Intent(Intent.ACTION_SYNC, null,
							POIDetailActivity.this, SyncService.class);
					intent.putExtra(SyncService.EXTRA_WHAT,
							SyncService.WHAT_UPDATE_SERVER);
					startService(intent);

					load();
				}
			}
		}
	}

	// Definition of the one requestCode we use for receiving resuls.
	static final private int SELECT_WHEELCHAIRSTATE = 0;

	private class POIMapsforgeOverlay extends ItemizedOverlay<OverlayItem> {
		private OverlayItem item;

		private int items;

		public POIMapsforgeOverlay() {
			super(null);
			items = 0;
		}

		public void setItem(String title, String snippet, NodeType nodeType,
				WheelchairState state, int latitude, int longitude) {

			Drawable marker = nodeType.stateDrawables.get(state);
			item = new OverlayItem();
			item.setTitle(title);
			item.setSnippet(snippet);
			item.setMarker(marker);
			item.setPoint(new GeoPoint(latitude, longitude));
			items = 1;

			populate();
		}

		@Override
		public int size() {
			return items;
		}

		@Override
		protected OverlayItem createItem(int index) {
			if (index > 0)
				return null;
			return item;
		}

		@Override
		public boolean onTap(int index) {
			finish();

			int lat = item.getPoint().getLatitudeE6();
			int lon = item.getPoint().getLongitudeE6();

			Intent i = new Intent(POIDetailActivity.this,
					POIsMapsforgeActivity.class);
			i.putExtra(POIsMapsforgeActivity.EXTRA_CENTER_AT_LAT, lat);
			i.putExtra(POIsMapsforgeActivity.EXTRA_CENTER_AT_LON, lon);

			POIDetailActivity.this.startActivity(i);
			return true;
		}

	}

	@Override
	public void setContentView(int layoutResID) {
		ViewGroup mainView = (ViewGroup) LayoutInflater.from(this).inflate(
				layoutResID, null);

		setContentView(mainView);
	}

	@Override
	public void setContentView(View view) {
		super.setContentView(view);

		mContentView = (ViewGroup) view;
	}

	private void nullViewDrawablesRecursive(View view) {
		if (view != null) {
			try {
				ViewGroup viewGroup = (ViewGroup) view;

				int childCount = viewGroup.getChildCount();
				for (int index = 0; index < childCount; index++) {
					View child = viewGroup.getChildAt(index);
					nullViewDrawablesRecursive(child);
				}
			} catch (Exception e) {
			}

			nullViewDrawable(view);
		}
	}

	private void nullViewDrawable(View view) {
		try {
			view.setBackgroundDrawable(null);
		} catch (Exception e) {
		}

		try {
			ImageView imageView = (ImageView) view;
			imageView.setImageDrawable(null);
			imageView.setBackgroundDrawable(null);
		} catch (Exception e) {
		}
	}
	
	private void logMemory() {
		long totalMemory = Runtime.getRuntime().totalMemory();
		long freeMemory = Runtime.getRuntime().freeMemory();
		Log.d( TAG, "memory: totalMemory = " + totalMemory + " freeMemory = " + freeMemory );
		
		
	}

}
