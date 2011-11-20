package org.wheelmap.android.ui;

import org.wheelmap.android.R;
import org.wheelmap.android.ui.POIDetailFragment.POIDetailFragmentUpdater;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.net.Uri;
import android.os.Bundle;


public class FragmentActivity extends Activity implements POIDetailFragmentUpdater {
  private static final String FRAGMENT_NAME_DETAIL = "Detail";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_fragment);
  }

  private void showNote(final Uri noteUri) {
    // check if the NoteEditFragment has been added
    FragmentManager fm = getFragmentManager();
    POIDetailFragment detail = (POIDetailFragment) fm.findFragmentByTag(FRAGMENT_NAME_DETAIL);
    if (detail == null) {
      // add the NoteEditFragment to the container
      FragmentTransaction ft = fm.beginTransaction();
      detail = new POIDetailFragment();
      ft.add(R.id.detail, detail, FRAGMENT_NAME_DETAIL);
      ft.commit();
    } else if (noteUri == null) {
      //      detail.clear();
    }

    if (noteUri != null) {
      //      detail.loadNote(noteUri);
    }
  }

  public void onNoteSelected(Uri noteUri) {
    showNote(noteUri);
  }

  public void onNoteDeleted() {
    // remove the NoteEditFragment after a deletion
    FragmentManager fm = getFragmentManager();
    POIDetailFragment edit = (POIDetailFragment) fm.findFragmentByTag(FRAGMENT_NAME_DETAIL);
    if (edit != null) {
      FragmentTransaction ft = fm.beginTransaction();
      ft.remove(edit);
      ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
      ft.commit();
    }
  }

  @Override
  public void Update() {
    // TODO Auto-generated method stub

  }

}
