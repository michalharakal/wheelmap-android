package org.wheelmap.android.ui;

import org.wheelmap.android.R;
import org.wheelmap.android.ui.PoiListFragment.PoiSelectedListener;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;


public class FragmentActivity extends Activity implements PoiSelectedListener {
  private static final String FRAGMENT_NAME_DETAIL = "Detail";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_fragment);
  }

  private void showDetail(final long id) {
    // check if the NoteEditFragment has been added
    FragmentManager fm = getFragmentManager();
    POIDetailFragment detail = (POIDetailFragment) fm.findFragmentByTag(FRAGMENT_NAME_DETAIL);
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

}
