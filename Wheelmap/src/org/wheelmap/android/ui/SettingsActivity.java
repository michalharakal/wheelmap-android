package org.wheelmap.android.ui;

import org.wheelmap.android.R;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class SettingsActivity extends FragmentActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tablet_activity_settings);
		getSupportFragmentManager().beginTransaction()
				.add(new SettingsFragment(), "").commit();

	}
}
