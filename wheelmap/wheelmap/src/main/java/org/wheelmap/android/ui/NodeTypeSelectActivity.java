/*
 * #%L
 * Wheelmap - App
 * %%
 * Copyright (C) 2011 - 2012 Michal Harakal - Michael Kroez - Sozialhelden e.V.
 * %%
 * Wheelmap App based on the Wheelmap Service by Sozialhelden e.V.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *         http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wheelmap.android.ui;

import java.util.ArrayList;

import org.wheelmap.android.online.R;
import org.wheelmap.android.model.CategoryNodeTypesAdapter;
import org.wheelmap.android.model.CategoryOrNodeType;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.ListView;

public class NodeTypeSelectActivity extends ListActivity {

	public static final String EXTRA_NODETYPE = "org.wheelmap.android.EXTRA_NODETYPE";
	private int mNodeTypeSelected = -1;
	
	private CheckedTextView oldCheckedView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView( R.layout.activity_nodetype_select);

		ArrayList<CategoryOrNodeType> types = CategoryOrNodeType
				.createTypesList(this, false);
		setListAdapter(new PickOnlyNodeTypesAdapter(this, types));
		
		// Dont know how to set a checkbox to selected
		int nodeType;
		if ( getIntent().getExtras() != null)
			nodeType = getIntent().getExtras().getInt( EXTRA_NODETYPE );
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		CategoryOrNodeType item = (CategoryOrNodeType) l.getAdapter().getItem(
				position);
		switch (item.type) {
		case NODETYPE:
			mNodeTypeSelected = item.id;
			if ( oldCheckedView != null )
				oldCheckedView.setChecked( false );
			CheckedTextView view = (CheckedTextView) v.findViewById( R.id.search_type );
			view.setChecked( true );
			oldCheckedView = view;
			
			Intent intent = new Intent();
			intent.putExtra(EXTRA_NODETYPE, mNodeTypeSelected);
			setResult(RESULT_OK, intent);
			finish();
			break;
		default:
			//
		}
	}

	private static class PickOnlyNodeTypesAdapter extends
			CategoryNodeTypesAdapter {
		public PickOnlyNodeTypesAdapter(Context context,
				ArrayList<CategoryOrNodeType> items) {
			super(context, items, CategoryNodeTypesAdapter.SELECT_MODE);
		}

		@Override
		public boolean isEnabled(int position) {
			CategoryOrNodeType item = (CategoryOrNodeType) getItem(position);
			switch (item.type) {
			case CATEGORY:
				return false;
			case NODETYPE:
				return true;
			default:
				return false;
			}
		}
	}
}
