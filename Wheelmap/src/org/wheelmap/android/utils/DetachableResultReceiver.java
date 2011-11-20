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

package org.wheelmap.android.utils;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;

/**
 * Proxy {@link ResultReceiver} that offers a listener interface that can be
 * detached. Useful for when sending callbacks to a {@link Service} where a
 * listening {@link Activity} can be swapped out during configuration changes.
 * @author Michal Harakal, Michael Kroez
 */
public class DetachableResultReceiver extends ResultReceiver {
	private static final String TAG = "DetachableResultReceiver";

	private Receiver mReceiver;
	private int resultCode;
	private Bundle resultData;

	public DetachableResultReceiver(Handler handler) {
		super(handler);
	}

	/**
	 * detach the inner receiver
	 */
	public void clearReceiver() {
		mReceiver = null;
	}

	/**
	 * attach an inner receiver, without resending the latest received data.
	 * @param receiver new receiver to be attached
	 */
	public void setReceiver(Receiver receiver) {
		mReceiver = receiver;
	}
	
	/**
	 * attach an inner receiver and - if needed - resend the latest received data.
	 * @param receiver new receiver to be attached
	 * @param resendLast true if the last received data shall be resent
	 */
	public void setReceiver(Receiver receiver, boolean resendLast ) {
		mReceiver = receiver;
		if ( resendLast )
			mReceiver.onReceiveResult( resultCode, resultData);
	}

	/**
	 * The 'real' receiver object will be called when results have been
	 * received from the DetachableResultReceiver
	 * @author Michal Harakal, Michael Kroez
	 */
	public interface Receiver {
		/**
		 * Called when the DetachableResultReceiver receives a result.
		 * All data will be passed through
		 * @param resultCode data received from DetachableResultReceiver
		 * @param resultData data received from DetachableResultReceiver
		 */
		public void onReceiveResult(int resultCode, Bundle resultData);
	}

	/**
	 * Called when an event shall be dispatched to the DetachableResultReceiver.
	 * The data will be passed through the inner Receiver if attached, or just
	 * dropped if no inner receiver is attached. The data is in any case
	 * stored in local variables to allow resending if a newly attached receiver
	 * wants to get them.
	 * @param resultCode code received
	 * @param resultData data received
	 */
	@Override
	protected void onReceiveResult(int resultCode, Bundle resultData) {
		this.resultCode = resultCode;
		this.resultData = resultData;
		if (mReceiver != null) {
			mReceiver.onReceiveResult(resultCode, resultData);
		} else {
			Log.w(TAG, "Dropping result on floor for code " + resultCode + ": "
					+ resultData.toString());
		}
	}
}
