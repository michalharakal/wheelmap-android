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

package org.wheelmap.android.manager;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.wheelmap.android.R;
import org.wheelmap.android.model.Support;
import org.wheelmap.android.model.Support.CategoriesContent;
import org.wheelmap.android.model.Support.LastUpdateContent;
import org.wheelmap.android.model.Support.LocalesContent;
import org.wheelmap.android.model.Support.NodeTypesContent;
import org.wheelmap.android.service.SyncService;
import org.wheelmap.android.utils.DetachableResultReceiver;

import wheelmap.org.WheelchairState;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Allows lookup of category and nodeTypes from wheelmap.org.
 * It also communicates with wheelmap to get the localized translations of the
 * nodetypes and categories.
 */
public class SupportManager {
	// tag used for debug log messages
	private static final String TAG = "support";

	// the application context
	private Context mContext;
	private Map<Integer, NodeType> mNodeTypeLookup;
	private Map<Integer, Category> mCategoryLookup;
	private Drawable[] mWheelDrawables;

	private DetachableResultReceiver mStatusSender;

	private NodeType mDefaultNodeType;
	private Category mDefaultCategory;
	private boolean mNeedsReloading;
	private boolean mInitialized;

	private final static long MILLISECS_PER_DAY = 1000 * 60 * 60 * 24;
	private final static long DATE_INTERVAL_FOR_UPDATE_IN_DAYS = 90;
	public final static String PREFS_SERVICE_LOCALE = "prefsServiceLocale";
	

	/**
	 * NodeType represents a node type (e.g. "Night Club") from wheelmap.org
	 * Every NodeType belongs to a category (e.g. "Leisure") and has a localized name.
	 * Additionally it has an icon which is drawn on the map.
	 * @author Michael Kroez, Michal Harakal
	 */
	public static class NodeType {
		public NodeType(int id, String identifier, String localizedName,
				int categoryId) {
			this.id = id;
			this.identifier = identifier;
			this.localizedName = localizedName;
			this.categoryId = categoryId;
		}

		public Drawable iconDrawable;
		public Map<WheelchairState, Drawable> stateDrawables;
		public int id;
		public String identifier;
		public String localizedName;
		public int categoryId;
	}

	/**
	 * Category represents the category of a node or node type, e.g. "Leisure".
	 * It has a localized name.
	 * @author Michael Kroez, Michal Harakal
	 */
	public static class Category {
		public Category(int id, String identifier, String localizedName) {
			this.id = id;
			this.identifier = identifier;
			this.localizedName = localizedName;
		}

		public int id;
		public String identifier;
		public String localizedName;
	}

	/**
	 * Create the support manager (should be a singleton?).
	 * It will load data from local database and set the flag "needs reloading"
	 * if the data has never been loaded from REST service or is already stale.
	 * The flag "initialized" will be true if data is loaded (but maybe outdated)
	 * and false if no data is loaded.
	 * @param ctx application context
	 */
	public SupportManager(Context ctx) {
		mContext = ctx;
		mCategoryLookup = new HashMap<Integer, Category>();
		mNodeTypeLookup = new HashMap<Integer, NodeType>();

		mDefaultCategory = new Category(0, "unknown",
				mContext.getString(R.string.support_category_unknown));
		mDefaultNodeType = new NodeType(0, "unknown",
				mContext.getString(R.string.support_nodetype_unknown), 0);
		mDefaultNodeType.stateDrawables = createDefaultDrawables();
		
		Drawable wheelYes = ctx.getResources().getDrawable( R.drawable.wheelchair_state_enabled );
		Drawable wheelLimited = ctx.getResources().getDrawable( R.drawable.wheelchair_state_limited );
		Drawable wheelNo = ctx.getResources().getDrawable( R.drawable.wheelchair_state_disabled );
		Drawable wheelUnknown = ctx.getResources().getDrawable( R.drawable.wheelchair_state_unknown );
		mWheelDrawables = new Drawable[] { wheelUnknown, wheelYes, wheelLimited, wheelNo, null };
			
		mInitialized = false;
		mNeedsReloading = false;
		if (checkForLocales() && checkForCategories() && checkForNodeTypes()) {
			initLookup();
			
			if (checkIfUpdateDurationPassed())
				mNeedsReloading = true;
		} else
			mNeedsReloading = true;
	}
	
	/**
	 * release the receiver which is managing the stages of the reload
	 */
	public void releaseReceiver() {
		if ( mStatusSender != null)
			mStatusSender.clearReceiver();
	}
	
	/**
	 * If true, SupportManager is initialized and no background process is
	 * running. All lookup tables have been filled with data.
	 * @return
	 */
	public boolean isInitialized() {
		return mInitialized;
	}
	
	/**
	 * If true, the data should be reloaded from the REST service using the
	 * "reload" function
	 * @return true if reload is needed, false if not
	 */
	public boolean needsReloading() {
		return mNeedsReloading;
	}
	
	/**
	 * load all lookup tables with data from local database
	 */
	private void initLookup() {
		initCategories();
		initNodeTypes();
		mInitialized = true;
	}
	
	/**
	 * Reload data from REST service. Starts with fetching data from intents
	 * and goes on to the next stages (if the DetachableResultReceiver does what
	 * is expected)
	 * @param receiver will be notified when locales are loaded, and
	 * should then call the next step (reloadStageTwo) and later the other steps.
	 */
	public void reload( DetachableResultReceiver receiver ) {
		mStatusSender = receiver;
		Intent localesIntent = new Intent(Intent.ACTION_SYNC, null, mContext,
				SyncService.class);
		localesIntent.putExtra(SyncService.EXTRA_WHAT,
				SyncService.WHAT_RETRIEVE_LOCALES);
		localesIntent
				.putExtra(SyncService.EXTRA_STATUS_RECEIVER, mStatusSender);
		mContext.startService(localesIntent);
	}
	
	/**
	 * Second stage of reloading: load the locale data from database into preferences
	 * and call the async service to fetch the categories from REST service
	 */
	public void reloadStageTwo() {
		initLocales();
		retrieveCategories();
	}
	
	/**
	 * Third stage of reloading: load the categories from database into lookup map
	 * and call the async service to fetch the node types from REST service
	 */
	public void reloadStageThree() {
		initCategories();
		retrieveNodeTypes();
	}
	
	/**
	 * Fourth stage of reloading: load the node types from database into lookup
	 * map and write the "last update" date,
	 * set SupportManager to be initialized & up-to-date.
	 */
	public void reloadStageFour() {
		initNodeTypes();
		createCurrentTimeTag();
		mInitialized = true;
		mNeedsReloading = false;
	}

	/**
	 * call the sync intent to get up-to-date categories as a background service.
	 * The currently set DetachableResultReceived will be notified when data is synced.
	 * Should probably be private / protected?
	 */
	public void retrieveCategories() {

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		String locale = prefs.getString(PREFS_SERVICE_LOCALE, "");

		Intent categoriesIntent = new Intent(Intent.ACTION_SYNC, null,
				mContext, SyncService.class);
		categoriesIntent.putExtra(SyncService.EXTRA_WHAT,
				SyncService.WHAT_RETRIEVE_CATEGORIES);
		categoriesIntent.putExtra(SyncService.EXTRA_LOCALE, locale);
		categoriesIntent.putExtra(SyncService.EXTRA_STATUS_RECEIVER,
				mStatusSender);
		mContext.startService(categoriesIntent);
	}

	/**
	 * call the sync intent to get up-to-date node types as a background service.
	 * The currently set DetachableResultReceiver will be notified when data is synced.
	 * Should probably be private / protected?
	 */
	public void retrieveNodeTypes() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		String locale = prefs.getString(PREFS_SERVICE_LOCALE, "");

		Intent nodeTypesIntent = new Intent(Intent.ACTION_SYNC, null, mContext,
				SyncService.class);
		nodeTypesIntent.putExtra(SyncService.EXTRA_WHAT,
				SyncService.WHAT_RETRIEVE_NODETYPES);
		nodeTypesIntent.putExtra(SyncService.EXTRA_LOCALE, locale);

		nodeTypesIntent.putExtra(SyncService.EXTRA_STATUS_RECEIVER,
				mStatusSender);
		mContext.startService(nodeTypesIntent);

	}

	/**
	 * Check if the latest update in the local database was older than
	 * DATE_INTERVAL_FOR_UPDATE_IN_DAYS days away
	 * @return true if data is old, false if data is fresh enough
	 */
	private boolean checkIfUpdateDurationPassed() {
		ContentResolver resolver = mContext.getContentResolver();
		Cursor cursor = resolver.query(LastUpdateContent.CONTENT_URI,
				LastUpdateContent.PROJECTION, null, null, null);
		cursor.moveToFirst();
		int count = cursor.getCount();
		
		if (count == 1) {
			Date date;
			try {
				date = Support.LastUpdateContent.parseDate(LastUpdateContent
						.getDate(cursor));
			} catch (ParseException e) {
				cursor.close();
				return true;
			}

			long now = System.currentTimeMillis();

			long days = (now - date.getTime()) / MILLISECS_PER_DAY;
			Log.d(TAG, "checkIfUpdateDurationPassed: days = " + days);

			if (days >= DATE_INTERVAL_FOR_UPDATE_IN_DAYS) {
				cursor.close();
				return true;
			}

			cursor.close();
			return false;
		}

		return true;
	}

	/**
	 * write the current date as "latest update" date into the database.
	 * Should probably be a private function?
	 */
	public void createCurrentTimeTag() {
		
		ContentValues values = new ContentValues();
		String date = Support.LastUpdateContent.formatDate(new Date());
		values.put(LastUpdateContent.DATE, date);
		String whereClause = "( " + LastUpdateContent._ID + " = ? )";
		String[] whereValues = new String[] { String.valueOf(1) };

		insertContentValues(LastUpdateContent.CONTENT_URI,
				LastUpdateContent.PROJECTION, whereClause, whereValues, values);
	}

	/**
	 * Check if locale data is loaded into database, a locale preference is set
	 * in the preferences, and if it is the same as the current application locale.
	 * 
	 * @return true if something is loaded locally,
	 * false if data has to be fetched from REST service
	 */
	private boolean checkForLocales() {
		ContentResolver resolver = mContext.getContentResolver();
		Cursor cursor = resolver.query(LocalesContent.CONTENT_URI,
				LocalesContent.PROJECTION, null, null, null);

		boolean dbEmpty = cursor.getCount() == 0;
		cursor.close();
		
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		String prefsLocale = prefs.getString(PREFS_SERVICE_LOCALE, "");
		boolean prefsEmpty = prefsLocale.equals("");

		String locale = mContext.getResources().getConfiguration().locale
				.getLanguage();

		if (dbEmpty || prefsEmpty || !locale.equals(prefsLocale)) {
			Log.d(TAG, "dbEmpty = " + dbEmpty + " prefsLocale = " + prefsLocale
					+ " locale = " + locale);
			return false;
		} else
			return true;
	}

	/**
	 * Check if category data is loaded locally into the SQLite database.
	 * 
	 * @return true if category data is available,
	 * false if it has to be fetched from REST service.
	 */
	private boolean checkForCategories() {
		ContentResolver resolver = mContext.getContentResolver();
		Cursor cursor = resolver.query(CategoriesContent.CONTENT_URI,
				CategoriesContent.PROJECTION, null, null, null);

		boolean dbFull = cursor.getCount() != 0;
		cursor.close();
		return dbFull;
	}

	/**
	 * Check if the node type data is loaded locally into the SQLite database.
	 * 
	 * @return true if node types are available,
	 * false if they have to be fetched from REST service.
	 */
	private boolean checkForNodeTypes() {
		ContentResolver resolver = mContext.getContentResolver();
		Cursor cursor = resolver.query(NodeTypesContent.CONTENT_URI,
				NodeTypesContent.PROJECTION, null, null, null);

		boolean dbFull = cursor.getCount() != 0;
		cursor.close();
		return dbFull;
	}

	/**
	 * Check if the current system locale is stored in the local database.
	 * If not the locale "en" will be used. The detected locale will be
	 * stored as preference.
	 */
	public void initLocales() {
//		Log.d(TAG, "SupportManager:initLocales");
		String locale = mContext.getResources().getConfiguration().locale
				.getLanguage();
//		Log.d(TAG, "SupportManager: locale = " + locale);
		ContentResolver resolver = mContext.getContentResolver();
		String whereClause = "( " + LocalesContent.LOCALE_ID + " = ? )";
		String[] whereValues = { locale };

		Cursor cursor = resolver.query(LocalesContent.CONTENT_URI,
				LocalesContent.PROJECTION, whereClause, whereValues, null);
		String serviceLocale = null;
		if (cursor.getCount() == 1)
			serviceLocale = locale;
		else
			serviceLocale = "en";
		cursor.close();
		
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(mContext);

		String storedLocale = prefs.getString(PREFS_SERVICE_LOCALE, "");
		if (storedLocale.equals("") || !storedLocale.equals(serviceLocale)) {
			prefs.edit().putString(PREFS_SERVICE_LOCALE, serviceLocale)
					.commit();
		}
//		Log.d(TAG, "SupportManager:initLocales: serviceLocale = "
//				+ serviceLocale);
	}

	/**
	 * Load the category data from the local database into a lookup map.
	 */
	public void initCategories() {
//		Log.d(TAG, "SupportManager:initCategories");
		ContentResolver resolver = mContext.getContentResolver();
		Cursor cursor = resolver.query(CategoriesContent.CONTENT_URI,
				CategoriesContent.PROJECTION, null, null, null);
		cursor.moveToFirst();
		mCategoryLookup.clear();

		while (!cursor.isAfterLast()) {
			int id = CategoriesContent.getCategoryId(cursor);
			String identifier = CategoriesContent.getIdentifier(cursor);
			String localizedName = CategoriesContent.getLocalizedName(cursor);
			mCategoryLookup
					.put(id, new Category(id, identifier, localizedName));

			cursor.moveToNext();
		}
		
		cursor.close();
	}

	/**
	 * Load the node types data from the local database into a lookup map.
	 */
	public void initNodeTypes() {
//		Log.d(TAG, "SupportManager:initNodeTypes");
		ContentResolver resolver = mContext.getContentResolver();
		Cursor cursor = resolver.query(NodeTypesContent.CONTENT_URI,
				NodeTypesContent.PROJECTION, null, null, null);
		cursor.moveToFirst();
		mNodeTypeLookup.clear();

		while (!cursor.isAfterLast()) {
			int id = NodeTypesContent.getNodeTypeId(cursor);
			String identifier = NodeTypesContent.getIdentifier(cursor);
			// Log.d(TAG, "Loading nodetype: identifier = " + identifier);
			String localizedName = CategoriesContent.getLocalizedName(cursor);
			int categoryId = NodeTypesContent.getCategoryId(cursor);
			String iconPath = NodeTypesContent.getIconURL(cursor);

			NodeType nodeType = new NodeType(id, identifier, localizedName,
					categoryId);
			nodeType.iconDrawable = createIconDrawable(iconPath);
			nodeType.stateDrawables = createDrawableLookup(iconPath);
			mNodeTypeLookup.put(id, nodeType);
			cursor.moveToNext();
		}
		
		cursor.close();
	}

	/**
	 * Helper function to create a cropped, scaled icon (cut 15px from left,
     * final size 80x65) from an image file.
	 * @param assetPath
	 * name of the image file in the assets subfolder "icons"
	 * @return bitmap from cropped / scaled image
	 */
	private Drawable createIconDrawable(String assetPath) {
		Bitmap bitmap;
		// Log.d(TAG, "SupportManager:createIconDrawable loading " + assetPath);
		try {
			bitmap = BitmapFactory.decodeStream(mContext.getAssets().open(
					"icons/" + assetPath));

		} catch (IOException e) {
			Log.w(TAG, "Warning in createIconDrawable." + e.getMessage());
			return null;
		}
		Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 15,
				bitmap.getWidth(), bitmap.getHeight() - 15);
		Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, 80, 65,
				true);
		bitmap.recycle();
		croppedBitmap.recycle();
		return new BitmapDrawable(scaledBitmap);

	}

	/**
	 * Load bitmaps for the differrent wheelchair states (unknown, yes, limited, no) from
	 * the folder "marker" in assets. The bounds of the drawables are set to create the correct
	 * shadow when drawn on the map.
	 * @return
	 */
	private Map<WheelchairState, Drawable> createDefaultDrawables() {
		Map<WheelchairState, Drawable> lookupMap = new HashMap<WheelchairState, Drawable>();

		int idx;
		for (idx = 0; idx < WheelchairState.values().length - 1; idx++) {
			String path = String.format("marker/%s.png", WheelchairState
					.valueOf(idx).toString().toLowerCase());
			Drawable drawable = null;
			try {
				drawable = Drawable.createFromStream(
						mContext.getAssets().open(path), null);
			} catch (IOException e) {
				Log.w(TAG, "Error in createDefaultDrawables:" + e.getMessage());
			}
//			drawable.setBounds(-32, -64, 32, 0);
			drawable.setBounds(-24, -48, 24, 0);
			lookupMap.put(WheelchairState.valueOf(idx), drawable);
		}

		return lookupMap;
	}

	/**
	 * Create for a node type path (e.g. "artgallery") a lookup map which gives the
	 * correct icon for the node type depending on the wheelchair state.
	 * @param assetPath
	 * subfolder name / node type identified
	 * @return lookup map from wheelchair state to drawable for provided node type
	 */
	private Map<WheelchairState, Drawable> createDrawableLookup(String assetPath) {
		Map<WheelchairState, Drawable> lookupMap = new HashMap<WheelchairState, Drawable>();
//		Log.d(TAG, "SupportManager:createDrawableLookup loading " + assetPath);

		int idx;
		for (idx = 0; idx < WheelchairState.values().length - 1; idx++) {
			String path = String.format("marker/%s/%s", WheelchairState
					.valueOf(idx).toString().toLowerCase(), assetPath);
			Drawable drawable = null;
			try {
				drawable = Drawable.createFromStream(
						mContext.getAssets().open(path), null);
			} catch (IOException e) {
				Log.w(TAG,
						"Error in createDrawableLookup. Assigning fallback. "
								+ e.getMessage());
				drawable = mDefaultNodeType.stateDrawables.get(WheelchairState
						.valueOf(idx));
			}
//			drawable.setBounds(-32, -64, 32, 0);
			drawable.setBounds(-24, -48, 24, 0);
			lookupMap.put(WheelchairState.valueOf(idx), drawable);
		}

		return lookupMap;
	}

	/**
	 * cleanup function which deletes all lookup maps with icons attached.
	 * Every attached callback is cleared.
	 */
	public void cleanReferences() {
		// Log.d(TAG, "clearing callbacks for mDefaultNodeType ");
		cleanReferences(mDefaultNodeType.stateDrawables);
		
		for (int nodeTypeId : mNodeTypeLookup.keySet()) {
			NodeType nodeType = mNodeTypeLookup.get(nodeTypeId);
			// Log.d(TAG, "clearing callbacks for " + nodeType.identifier);
			cleanReferences(nodeType.stateDrawables);
		}
		
		for( Drawable wheelDrawable: mWheelDrawables) {
			if ( wheelDrawable != null)
				wheelDrawable.setCallback( null );
		}
	}

	/**
	 * cleanup function for a specific lookup map containing icons.
	 * Every attached callback is cleared.
	 * @param lookupMap
	 */
	public void cleanReferences(Map<WheelchairState, Drawable> lookupMap) {
		for (WheelchairState state : lookupMap.keySet()) {
			Drawable drawable = lookupMap.get(state);
			drawable.setCallback(null);
		}
	}

	/**
	 * lookup function for a category. Return matching entry or "unknown category"
	 * if not found.
	 * @param id value for which a category is searched for
	 * @return Category object (or default category if not found)
	 */
	public Category lookupCategory(int id) {
		if (mCategoryLookup.containsKey(id))
			return mCategoryLookup.get(id);
		else
			return mDefaultCategory;

	}

	/**
	 * lookup function for a node type. Return matching entry or "unknown node type"
	 * if not found.
	 * @param id value for which a category is searched for
	 * @return NodeType object (or default node type if not found)
	 */
	public NodeType lookupNodeType(int id) {
		if (mNodeTypeLookup.containsKey(id))
			return mNodeTypeLookup.get(id);
		else
			return mDefaultNodeType;
	}
	
	/**
	 * Return drawable marked for wheelchair state
	 * @param idx identified for wheelchair state
	 * @return corresponding marker
	 */
	public Drawable lookupWheelDrawable( int idx ) {
		return mWheelDrawables[idx];
	}

	/**
	 * Get a list of all categories as a copy.
	 * There *must* be some reason for not using new ArrayList<Category>(mCategoryLookup.values()) ...
	 * @return list of all categories
	 */
	public List<Category> getCategoryList() {
		Set<Integer> keys = mCategoryLookup.keySet();
		List<Category> list = new ArrayList<Category>();
		for (Integer key : keys) {
			list.add(mCategoryLookup.get(key));
		}
		return list;
	}

	/**
	 * Get a list of all node types as a copy.
	 * There *must* be some reason for not using new ArrayList<NodeType>(mNodeTypeLookup.values()) ...
	 * @return list of all node types
	 */
	public List<NodeType> getNodeTypeList() {
		Set<Integer> keys = mNodeTypeLookup.keySet();
		List<NodeType> list = new ArrayList<NodeType>();
		for (Integer key : keys) {
			list.add(mNodeTypeLookup.get(key));
		}

		return list;
	}

	/**
	 * Get a list of all node types for a specific category.
	 * @param categoryId identifier for the category
	 * @return list of all node types with this category
	 */
	public List<NodeType> getNodeTypeListByCategory(int categoryId) {
		Set<Integer> keys = mNodeTypeLookup.keySet();
		List<NodeType> list = new ArrayList<NodeType>();
		for (Integer key : keys) {
			NodeType nodeType = mNodeTypeLookup.get(key);

			if (nodeType.categoryId == categoryId) {
				list.add(nodeType);
			}
		}
		return list;
	}
	
	/**
	 * insert (or update) a value in the local database. The where clause has to be
	 * specific enough to return not more than one value.
	 * @param contentUri URI pointing to the database
	 * @param projection columns to be retrieved / updated
	 * @param whereClause where clause to identify rows
	 * @param whereValues specific values used in whereClause
	 * @param values new values to be stored in database
	 */
	private void insertContentValues(Uri contentUri, String[] projection,
			String whereClause, String[] whereValues, ContentValues values) {
		ContentResolver resolver = mContext.getContentResolver();
		Cursor c = resolver.query(contentUri, projection, whereClause,
				whereValues, null);
		int cursorCount = c.getCount();
		c.close();
		if (cursorCount == 0)
			resolver.insert(contentUri, values);
		else if (cursorCount == 1)
			resolver.update(contentUri, values, whereClause, whereValues);
		else {
			// do nothing, as more than one file would be updated
		}
	}

}
