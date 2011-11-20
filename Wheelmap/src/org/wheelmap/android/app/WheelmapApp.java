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

package org.wheelmap.android.app;

import org.acra.ACRA;
import org.acra.ErrorReporter;
import org.acra.annotation.ReportsCrashes;
import org.wheelmap.android.manager.MyLocationManager;
import org.wheelmap.android.manager.SupportManager;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * Application class for Wheelmap on Android
 * This class initializes the ARCA framework (for crash reporting), the SupportManager
 * (used for category and nodetype lookup), and the LocationManager.
 * 
 * It implements the Singleton pattern (using the static member "INSTANCE") and allows
 * access to the central SupportManager.
 * @author Michal Harakal, Michael Kroez
 *
 */
// Beta and PRE-RC key: "dGJWQW5PelRXWUFTbDh6VW5UYm94cXc6MQ"
// RC1 - key: @ReportsCrashes(formKey = "dC1VVDdKenJLRUpZTC1MZXBVR3p6ZlE6MQ" )
// RC2 - key: @ReportsCrashes(formKey = "dG1fUDltTlNiM3V4NmRvaVExT3dJclE6MQ" )
@ReportsCrashes( formKey = "dGMzcTRSZjRMRG14c0JmU25ET1JLQmc6MQ")
public class WheelmapApp extends Application {
	// used for debug logging
	private final static String TAG = "wheelmapapp";
	
	private static WheelmapApp INSTANCE;
	private MyLocationManager mLocationManager;
	private SupportManager mSupportManager;
	
	/**
	 * Called from the Android framework when the application is started through the launcher.
	 * Initializes the SupportManager and the MyLocationManager singleton.
	 */
	@Override
	public void onCreate() {
		ACRA.init(this);
		ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        String memoryClass = String.valueOf( am.getMemoryClass());
		ErrorReporter.getInstance().putCustomData("memoryClass", memoryClass);
		super.onCreate();
		Log.d( TAG, "onCreate" );
		mLocationManager = MyLocationManager.initOnce( this );
		mSupportManager = new SupportManager( this );
		INSTANCE = this;
	}

	/**
	 * Called when the applications gets terminated. It detaches the MyLocationManager
	 * from any event callbacks.
	 */
	@Override
	public void onTerminate() {
		super.onTerminate();
		mLocationManager.clear();
		Log.d(TAG,  "onTerminate" );
	}
	
	/**
	 * Called when the application is low on memory. Writes a debug message to ease debugging
	 * issues related with low memory situations.
	 */
	@Override
	public void onLowMemory() {
		super.onLowMemory();
		Log.d( "lowmemory", "wheelmap app - onLowMemory" );
	}
	
	/**
	 * Get the instance of the SupportManager. This has been initialized during
	 * application startup and will _not_ be create on-demand!
	 * @return single instance of SupportManager created during stratup
	 */
	public static SupportManager getSupportManager() {
		if ( INSTANCE == null )
			Log.d( TAG, "INSTANCE == null - how can that be?" );
		if ( INSTANCE != null && INSTANCE.mSupportManager == null )
			Log.d(TAG, "INSTANCE != null - mSupportManager = null - how can that be?" );
		
		return INSTANCE.mSupportManager;
	}
}
