package org.wheelmap.android.ui;

import java.util.HashMap;

import org.mapsforge.android.maps.GeoPoint;
import org.mapsforge.android.maps.MapController;
import org.mapsforge.android.maps.MapView;
import org.wheelmap.android.R;
import org.wheelmap.android.app.WheelmapApp;
import org.wheelmap.android.manager.SupportManager;
import org.wheelmap.android.manager.SupportManager.NodeType;
import org.wheelmap.android.model.POIHelper;
import org.wheelmap.android.model.Wheelmap;

import wheelmap.org.WheelchairState;
import android.app.Fragment;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class POIDetailFragment extends Fragment {

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
    }
	
	
    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	            Bundle savedInstanceState) {
    	View rootView = (View) inflater.inflate(R.layout.activity_detail, container, true);
     	mSupportManager = WheelmapApp.getSupportManager();
		System.gc();

		nameText = (TextView) rootView.findViewById(R.id.title_name);
		categoryText = (TextView) rootView.findViewById(R.id.title_category);
		nodetypeText = (TextView) rootView.findViewById(R.id.nodetype);

		phoneText = (TextView) rootView.findViewById(R.id.phone);
		addressText = (TextView) rootView.findViewById(R.id.addr);
		commentText = (TextView) rootView.findViewById(R.id.comment);
		websiteText = (TextView) rootView.findViewById(R.id.website);
		mStateIcon = (ImageView) rootView.findViewById(R.id.wheelchair_state_icon);
		mWheelchairStateText = (TextView) rootView.findViewById(R.id.wheelchair_state_text);
		mWheelchairStateLayout = (RelativeLayout) rootView.findViewById( R.id.wheelchair_state_layout );

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

		/*
		mWheelchairStateLayout.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				//onEditWheelchairState(v);
			}
		});
		*/

		//mapView = (MapView) rootView findViewById(R.id.map);

		//mapView.setClickable(true);
		//mapView.setBuiltInZoomControls(true);
		//ConfigureMapView.pickAppropriateMap(this, mapView);
		//mapController = mapView.getController();
		//mapController.setZoom(18);

		/*
		poiID = getIntent().getLongExtra(Wheelmap.POIs.EXTRAS_POI_ID, -1);
		Log.d( TAG, "onCreate: poiID = " + poiID );

		if (poiID != -1) {
			load();
		}
		 */
		return rootView;
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
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
		Cursor cur =  getActivity().managedQuery(poiUri, null, null, null, null);

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

		/*
		POIMapsforgeOverlay overlay = new POIMapsforgeOverlay();
		overlay.setItem(name, comment, nodeType, state, lat, lon);
		mapView.getOverlays().clear();
		mapView.getOverlays().add(overlay);
		mapController.setCenter(new GeoPoint(lat, lon));
		*/
		cur.close();
	}
}

