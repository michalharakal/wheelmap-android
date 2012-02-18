package org.wheelmap.android.ui;

import java.util.HashMap;

import org.wheelmap.android.R;
import org.wheelmap.android.ui.PoiListFragment.PoiSelectedListener;
import org.wheelmap.android.ui.mapsforge.MapsforgeFragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.widget.TabHost;

public class TabletActivity extends FragmentActivity implements
		PoiSelectedListener {

	private static final String FRAGMENT_NAME_DETAIL = "Detail";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tablet_activity_tabfragment);

		TabHost mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		mTabHost.setup();

		TabManager mTabManager = new TabManager(this, mTabHost,
				R.id.realtabcontent);

		// TODO replace hardcoded String with String values from resource file
		mTabManager.addTab(mTabHost.newTabSpec("liste").setIndicator("Liste"),
				PoiListFragment.class, null);
		mTabManager.addTab(mTabHost.newTabSpec("map").setIndicator("Karte"),
				MapsforgeFragment.class, null);

		if (savedInstanceState != null) {
			mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO add drawable Icon
		MenuInflater inflater =  getMenuInflater();
		inflater.inflate(R.menu.menu_tablet_fragment, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		
		switch (item.getItemId()) {
		case R.id.menu_filter:
			Intent intent = new Intent(this, NewSettingsActivity.class);
			startActivityForResult(intent, 100);
			break;
		case R.id.menu_search:
			// TODO
			break;

		default:
			break;
		}
		
		
		return true;
	}

	private void showDetail(final long id) {
		// check if the NoteEditFragment has been added
		FragmentManager fm = getSupportFragmentManager();
		POIDetailFragment detail = (POIDetailFragment) fm
				.findFragmentByTag(FRAGMENT_NAME_DETAIL);
		if (detail == null) {
			// add the NoteEditFragment to the container
			FragmentTransaction ft = fm.beginTransaction();
			detail = new POIDetailFragment();
			ft.add(R.id.detail, detail, FRAGMENT_NAME_DETAIL);
			ft.commit();
		}
		detail.upDatePOI(id);
	}

	@Override
	public void onPoiSelected(long id) {
		showDetail(id);
	}

	public static class TabManager implements TabHost.OnTabChangeListener {
		private final FragmentActivity mActivity;
		private final TabHost mTabHost;
		private final int mContainerId;
		private final HashMap<String, TabInfo> mTabs = new HashMap<String, TabInfo>();
		TabInfo mLastTab;

		static final class TabInfo {
			private final String tag;
			private final Class<?> clss;
			private final Bundle args;
			private Fragment fragment;

			TabInfo(String _tag, Class<?> _class, Bundle _args) {
				tag = _tag;
				clss = _class;
				args = _args;
			}
		}

		static class DummyTabFactory implements TabHost.TabContentFactory {
			private final Context mContext;

			public DummyTabFactory(Context context) {
				mContext = context;
			}

			@Override
			public View createTabContent(String tag) {
				View v = new View(mContext);
				v.setMinimumWidth(0);
				v.setMinimumHeight(0);
				return v;
			}
		}

		public TabManager(FragmentActivity activity, TabHost tabHost,
				int containerId) {
			mActivity = activity;
			mTabHost = tabHost;
			mContainerId = containerId;
			mTabHost.setOnTabChangedListener(this);
		}

		public void addTab(TabHost.TabSpec tabSpec, Class<?> clss, Bundle args) {
			tabSpec.setContent(new DummyTabFactory(mActivity));
			String tag = tabSpec.getTag();

			TabInfo info = new TabInfo(tag, clss, args);

			// Check to see if we already have a fragment for this tab, probably
			// from a previously saved state. If so, deactivate it, because our
			// initial state is that a tab isn't shown.
			info.fragment = mActivity.getSupportFragmentManager()
					.findFragmentByTag(tag);
			if (info.fragment != null && !info.fragment.isDetached()) {
				FragmentTransaction ft = mActivity.getSupportFragmentManager()
						.beginTransaction();
				ft.detach(info.fragment);
				ft.commit();
			}

			mTabs.put(tag, info);
			mTabHost.addTab(tabSpec);
		}

		@Override
		public void onTabChanged(String tabId) {
			TabInfo newTab = mTabs.get(tabId);
			if (newTab == null || !newTab.equals(mLastTab)) {
				FragmentTransaction ft = mActivity.getSupportFragmentManager()
						.beginTransaction();
				if (mLastTab != null) {
					if (mLastTab.fragment != null) {
						Log.d("TabletActivity", "ft.detach mLastTab");
						ft.detach(mLastTab.fragment);
					}
				}
				if (newTab != null) {
					if (newTab.fragment == null) {
						Log.d("TabletActivity", "ft.add");
						newTab.fragment = Fragment.instantiate(mActivity,
								newTab.clss.getName(), newTab.args);
						ft.add(mContainerId, newTab.fragment, newTab.tag);
					} else {
						Log.d("TabletActivity", "ft.attach");
						ft.attach(newTab.fragment);
					}
				}

				mLastTab = newTab;
				ft.commit();
				mActivity.getSupportFragmentManager()
						.executePendingTransactions();
			}
		}
	}

}
