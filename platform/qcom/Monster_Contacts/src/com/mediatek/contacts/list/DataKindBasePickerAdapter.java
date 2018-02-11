/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.contacts.list;

import java.util.ArrayList;
import java.util.TreeSet;

import mst.widget.SliderView;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import mst.provider.ContactsContract.CommonDataKinds.Phone;
import mst.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListItemView;

import com.android.contacts.common.list.PinnedHeaderListView;
import com.android.contacts.common.list.ContactEntryListAdapter.ViewHolderForContacts;
import com.android.contacts.common.list.ContactListAdapter.ContactQuery;
import com.android.contacts.common.list.IndexerListAdapter.Placement;
import com.android.contacts.common.preference.ContactsPreferences;

import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.util.MtkToast;

public abstract class DataKindBasePickerAdapter extends ContactEntryListAdapter {
	private static final String TAG = "DataKindBasePickerAdapter";

	private ListView mListView;
	private ContactListItemView.PhotoPosition mPhotoPosition;
	private Context mContext;

	private int headerPaddingLeft;
	public DataKindBasePickerAdapter(Context context, ListView lv) {
		super(context);
		mListView = lv;
		mContext = context;
		headerPaddingLeft=context.getResources().getDimensionPixelOffset(R.dimen.contact_listview_header_padding_left);
	}

	protected ListView getListView() {
		return mListView;
	}

	@Override
	public final void configureLoader(CursorLoader loader, long directoryId) {
		loader.setUri(configLoaderUri(directoryId));
		loader.setProjection(configProjection());
		configureSelection(loader, directoryId, getFilter());

		// Set the Contacts sort key as sort order
		String sortOrder;
		if (getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY) {
			sortOrder = Contacts.SORT_KEY_PRIMARY;
		} else {
			sortOrder = Contacts.SORT_KEY_ALTERNATIVE;
		}

		loader.setSortOrder(sortOrder);
	}

	protected static Uri buildSectionIndexerUri(Uri uri) {
		return uri.buildUpon()
				.appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true").build();
	}

	@Override
	protected View newView(Context context, int partition, Cursor cursor,
			int position, ViewGroup parent) {
		Log.i(TAG, "[newView11]partition = " + partition + ",position = " + position);
		ViewHolderForContacts viewHolder = new ViewHolderForContacts();
		ContactListItemView view = new ContactListItemView(context, null);
		view.setUnknownNameText(context.getText(android.R.string.unknownName));
		view.setQuickContactEnabled(isQuickContactEnabled());

		// Enable check box
		view.setCheckable(false);
		////Fixed ALPS02292698:The selected contact will change to blue background @{
		//view.setActivatedStateSupported(true);
		view.setActivatedStateSupported(isSelectionVisible());
		///@}

		ViewGroup outer = (ViewGroup)LayoutInflater.from(mContext).inflate(R.layout.mst_contacts_listview_item,null);
		ViewGroup front_view=(ViewGroup)outer.findViewById(R.id.front_view);
		front_view.addView(view, 1, new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.WRAP_CONTENT));
		CheckBox checkBox=(CheckBox)front_view.findViewById(android.R.id.button1);
		checkBox.setVisibility(View.VISIBLE);
		TextView header=(TextView)outer.findViewById(R.id.listview_item_header);		
		header.setPadding(headerPaddingLeft, 0, 0, 0);
		header.setVisibility(View.GONE);
		SliderView sliderView=(SliderView)outer.findViewById(com.mst.R.id.slider_view);
		sliderView.addTextButton(1,context.getString(R.string.mst_remove));

		viewHolder.view=view;
		viewHolder.checkBox=checkBox;
		viewHolder.name=view.getNameTextView();
		viewHolder.header=header;
		viewHolder.header.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Log.d(TAG,"click header");
			}
		});
		viewHolder.sliderLayout=sliderView;
		outer.setTag(viewHolder);

		return outer;
	}

	public void displayPhotoOnLeft() {
		mPhotoPosition = ContactListItemView.PhotoPosition.LEFT;
	}

    public void setExistDataList(long[] ids) {
    	Log.d(TAG,"setExistDataList:"+ids);
    	if(ids==null) return;
    	TreeSet<Long> mSelectedContactIds=new TreeSet<Long>();
        for(long id:ids){
        	mSelectedContactIds.add(id);
        	setSelectedContactIds(mSelectedContactIds);
        }
    }
    
	@Override
	protected void bindView(View itemView, int partition, Cursor cursor, int position) {
		Log.d(TAG, "[bindView]position = " + position + ",partition = " + partition);
		final ViewHolderForContacts viewHolder = (ViewHolderForContacts) itemView.getTag();
		//        final ContactListItemView view = (ContactListItemView) itemView;
		final ContactListItemView view=viewHolder.view;

		// Look at elements before and after this position, checking if contact
		// IDs are same.
		// If they have one same contact ID, it means they can be grouped.
		//
		// In one group, only the first entry will show its photo and names
		// (display name and
		// phonetic name), and the other entries in the group show just their
		// data (e.g. phone
		// number, email address).
		cursor.moveToPosition(position);
		boolean isFirstEntry = true;
		boolean showBottomDivider = true;
		final long currentContactId = cursor.getLong(getContactIDColumnIndex());
		if (cursor.moveToPrevious() && !cursor.isBeforeFirst()) {
			final long previousContactId = cursor.getLong(getContactIDColumnIndex());
			if (currentContactId == previousContactId) {
				isFirstEntry = false;
			}
		}
		cursor.moveToPosition(position);
		if (cursor.moveToNext() && !cursor.isAfterLast()) {
			final long nextContactId = cursor.getLong(getContactIDColumnIndex());
			if (currentContactId == nextContactId) {
				// The following entry should be in the same group, which means
				// we don't want a
				// divider between them.
				// TODO: we want a different divider than the divider between
				// groups. Just hiding
				// this divider won't be enough.
				showBottomDivider = false;
			}
		}
		cursor.moveToPosition(position);

		//		bindSectionHeaderAndDivider(view, position, cursor);
		bindSectionHeaderAndDividerV2(viewHolder.header,position,cursor);
		viewHolder.header.setOnClickListener(null);
		bindName(view, cursor);
		//        if (isFirstEntry) {
		//            bindName(view, cursor);
		//            if (isQuickContactEnabled()) {
		//                bindQuickContact(view, partition, cursor);
		//            } else {
		//                bindPhoto(view, cursor);
		//            }
		//        } else {
		//            unbindName(view);
		//
		//            view.removePhotoView(true, false);
		//        }

		bindData(view, cursor);

		if (!isSearchMode()) {
			view.setSnippet(null);
		}
		// remove phonetic name, ALPS01767166.
		view.hidePhoneticName();
		//view.getCheckBox().setChecked(mListView.isItemChecked(position));
		if(TextUtils.equals(mstFilterString, "oneKeyAlarm")){
			viewHolder.checkBox.setVisibility(View.GONE);
		}else{
			bindCheckBoxForMst(viewHolder.checkBox, cursor, position);
		}

		final SliderView sliderView=viewHolder.sliderLayout;
		sliderView.setLockDrag(true);
		if(sliderView.isOpened()){
			sliderView.close(false);
		}
	}

	protected void bindSectionHeaderAndDivider(ContactListItemView view, int position,
			Cursor cursor) {
		if (isSectionHeaderDisplayEnabled()) {
			Placement placement = getItemPlacementInSection(position);

			view.setSectionHeader(placement.sectionHeader);
		} else {
			view.setSectionHeader(null);
		}
	}

	protected void bindSectionHeaderAndDividerV2(TextView textView, int position,
			Cursor cursor) {
		if (isSectionHeaderDisplayEnabled()) {			
			Placement placement = getItemPlacementInSection(position);
			if (!TextUtils.isEmpty(placement.sectionHeader)) {				
				textView.setText(placement.sectionHeader);				
				textView.setVisibility(View.VISIBLE);
			}
		}else{
			textView.setVisibility(View.GONE);
		}
	}

	protected void bindPhoto(final ContactListItemView view, Cursor cursor) {
		long photoId = 0;
		if (!cursor.isNull(getPhotoIDColumnIndex())) {
			photoId = cursor.getLong(getPhotoIDColumnIndex());
		}

		DefaultImageRequest request = null;
		if (photoId == 0) {
			request = getDefaultImageRequestFromCursor(cursor, getDisplayNameColumnIdex(),
					getLookupKeyColumnIndex());
		}
		getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, false, true, request);
	}

	protected void bindData(ContactListItemView view, Cursor cursor) {
		CharSequence label = null;
		if (!cursor.isNull(getDataTypeColumnIndex())) {
			final int type = cursor.getInt(getDataTypeColumnIndex());
			final String customLabel = cursor.getString(getDataLabelColumnIndex());

			label = Phone.getTypeLabel(mContext.getResources(), type, customLabel);
			label = ExtensionManager.getInstance().getAasExtension()
					.getTypeLabel(type, (CharSequence) customLabel, (String) label,
							cursor.getColumnIndex(Contacts.INDICATE_PHONE_SIM));
		}
		Log.d(TAG, "label: " + label);
		view.setLabel(label);
		view.showData(cursor, getDataColumnIndex());
	}

	protected void unbindName(final ContactListItemView view) {
		view.hideDisplayName();
		view.hidePhoneticName();
	}

	public void configurePinnedHeaders(PinnedHeaderListView listView) {
		super.configurePinnedHeaders(listView);
		listView.setDrawPinnedHeader(false);
	}

	public boolean hasStableIds() {
		return false;
	}

	public long getItemId(int position) {
		return getDataId(position);
	}

	protected abstract String[] configProjection();

	protected abstract Uri configLoaderUri(long directoryId);

	protected abstract void configureSelection(CursorLoader loader, long directoryId,
			ContactListFilter filter);

	public abstract Uri getDataUri(int position);

	public abstract long getDataId(int position);

	public abstract void bindName(final ContactListItemView view, Cursor cursor);

	public abstract void bindQuickContact(final ContactListItemView view, int partitionIndex,
			Cursor cursor);

	public abstract int getPhotoIDColumnIndex();

	public abstract int getDataTypeColumnIndex();

	public abstract int getDataLabelColumnIndex();

	public abstract int getDataColumnIndex();

	public abstract int getDisplayNameColumnIdex();

	public abstract int getContactIDColumnIndex();

	public abstract int getPhoneticNameColumnIndex();

	public abstract int getIndicatePhoneSIMColumnIndex();

	public abstract int getIsSdnContactColumnIndex();

	public abstract int getLookupKeyColumnIndex();

	/** @} */
	/*=================for checkbox=========================*/

	private SelectedContactsListener mSelectedContactsListener;

	public interface SelectedContactsListener {
		void onSelectedContactsChangedViaCheckBox();
	}

	private final OnClickListener mCheckBoxClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Log.d(TAG, "[mCheckBoxClickListener] onClick");
			final CheckBox checkBox = (CheckBox) v;
			final Long contactId = (Long) checkBox.getTag();

			/* fix CR ALPS02350421 intercept more than 3500 selected @{*/
			int multiChoiceLimitCount = AbstractPickerFragment.DEFAULT_MULTI_CHOICE_MAX_COUNT;
			boolean isSelectedLimited = mSelectedContactIds.size() >= multiChoiceLimitCount;
			boolean isChecked = checkBox.isChecked();
			if (isSelectedLimited && isChecked) {
				Log.i(TAG, "[mCheckBoxClickListener] Current selected Contact cnt > 3500,cannot " +
						"select more");
				checkBox.setChecked(false);
				String msg = (mContext.getResources()).getString(
						R.string.multichoice_contacts_limit, multiChoiceLimitCount);
				MtkToast.toast(mContext.getApplicationContext(), msg);
				return;
			}
			//@}

			if (checkBox.isChecked()) {
				mSelectedContactIds.add(contactId);
			} else {
				mSelectedContactIds.remove(contactId);
			}
			if (mSelectedContactsListener != null) {
				mSelectedContactsListener.onSelectedContactsChangedViaCheckBox();
			}
		}
	};

	private void bindCheckBox(ContactListItemView view, Cursor cursor, int position) {
		Log.d(TAG,"[bindCheckBox]position = " + position);
		final CheckBox checkBox = view.getCheckBox();
		//checkBox.setChecked(mSelectedContactIds.contains(contactId));
		// checkBox.setChecked(mListView.isItemChecked(position));
		final long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
		checkBox.setChecked(mSelectedContactIds.contains(contactId));
		checkBox.setTag(contactId);
		//        checkBox.setOnClickListener(mCheckBoxClickListener);
	}

	private void bindCheckBoxForMst(CheckBox checkBox, Cursor cursor, int position) {
		Log.d(TAG,"[bindCheckBox]position = " + position);
		//checkBox.setChecked(mSelectedContactIds.contains(contactId));
		// checkBox.setChecked(mListView.isItemChecked(position));
		final long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
		Log.d(TAG," mSelectedContactIds:"+mSelectedContactIds+" contactId:"+contactId);
		checkBox.setChecked(mSelectedContactIds.contains(contactId));
		checkBox.setTag(contactId);
		//        checkBox.setOnClickListener(mCheckBoxClickListener);
	}

	public void setSelectedContactsListener(SelectedContactsListener listener) {
		mSelectedContactsListener = listener;
	}
}
