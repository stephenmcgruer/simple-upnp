// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR 'CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.stephenmcgruer.simpleupnp.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.stephenmcgruer.simpleupnp.R;

import org.fourthline.cling.support.model.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class FileBrowserAdapter extends ArrayAdapter<FileBrowserAdapter.ListItem>
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "FileBrowserAdapter";

    interface OnItemClickListener {
        void playItems(List<Item> items);
        void addBookmark(String bookmarkName, String containerId);
        void removeBookmark(String containerId);
    }

    private final OnItemClickListener mListener;
    private boolean mProgrammaticallyModifyingCheckboxes = false;

    FileBrowserAdapter(OnItemClickListener listener, Context context, int resource) {
        super(context, resource);
        mListener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View rowView = convertView;
        if (rowView == null) {
            LayoutInflater inflator = (LayoutInflater)
                    getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            rowView = inflator.inflate(R.layout.fragment_file_browser_item, parent, false);
            ViewHolder holder = new ViewHolder();
            holder.text = (TextView) rowView.findViewById(R.id.file_browser_list_item_text);
            holder.checkBox = (CheckBox) rowView.findViewById(R.id.file_browser_list_item_favorite);
            holder.button = (Button) rowView.findViewById(R.id.file_browser_list_item_button);
            rowView.setTag(holder);
        }

        ViewHolder holder = (ViewHolder) rowView.getTag();
        ListItem listItem = getItem(position);
        if (listItem != null) {
            holder.position = position;
            holder.text.setText(listItem.toString());

            setModifyingCheckboxes(true);
            holder.checkBox.setVisibility(
                    !listItem.isPreviousContainerListItem() && listItem.holdsContainer() ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(listItem.isBookmarked());
            holder.checkBox.setOnCheckedChangeListener(this);
            setModifyingCheckboxes(false);

            holder.button.setText(R.string.play_button_text);
            holder.button.setVisibility(listItem.hasMediaItems() ? View.VISIBLE : View.GONE);
            holder.button.setOnClickListener(this);
        }

        return rowView;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() != R.id.file_browser_list_item_button) {
            throw new IllegalStateException("Unhandled view in onClick: " + view.toString());
        }

        RelativeLayout parent = (RelativeLayout) view.getParent();
        ViewHolder holder = (ViewHolder) parent.getTag();
        ListItem listItem = getItem(holder.position);
        if (mListener != null && listItem != null) {
            mListener.playItems(listItem.getMediaItems());
        }
    }

    @Override
    public void onCheckedChanged(final CompoundButton view, boolean isChecked) {
        if (mProgrammaticallyModifyingCheckboxes)
            return;

        RelativeLayout parent = (RelativeLayout) view.getParent();
        final ViewHolder holder = (ViewHolder) parent.getTag();
        final ListItem listItem = getItem(holder.position);
        if (listItem == null || !listItem.holdsContainer()) {
            return;
        }

        if (isChecked && mListener != null) {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext());
            dialogBuilder.setTitle(R.string.bookmark_name_dialog_title);

            final EditText inputEditText = new EditText(getContext());
            inputEditText.setHint(listItem.getContainer().getTitle());
            inputEditText.setSelectAllOnFocus(true);
            dialogBuilder.setView(inputEditText);

            dialogBuilder.setPositiveButton(R.string.bookmark_name_dialog_ok_button_text,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Log.d(TAG, "onClick: OK button pressed");
                    String bookmarkName = inputEditText.getText().toString();
                    if (bookmarkName.isEmpty()) {
                        bookmarkName = listItem.getContainer().getTitle();
                    }
                    mListener.addBookmark(bookmarkName, listItem.getContainer().getId());
                }
            });
            dialogBuilder.setNegativeButton(R.string.bookmark_name_dialog_cancel_button_text,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Log.d(TAG, "onClick: Cancel button pressed");
                    setModifyingCheckboxes(true);
                    view.setChecked(false);
                    setModifyingCheckboxes(false);
                }
            });
            dialogBuilder.show();
        } else {
            mListener.removeBookmark(listItem.getContainer().getId());
        }
    }

    public void setModifyingCheckboxes(boolean modifyingCheckboxes) {
        mProgrammaticallyModifyingCheckboxes = modifyingCheckboxes;
    }

    static class ListItem {
        static final ListItem PREVIOUS_CONTAINER_LIST_ITEM = new ListItem(null, null);

        private final ContainerWrapper mContainer;
        private final Item mItem;
        private List<Item> mMediaItems;
        private boolean mIsBookmarked;

        ListItem(@NonNull ContainerWrapper container) {
            this(container, null);
        }

        ListItem(@NonNull Item item) {
            this(null, item);
        }

        private ListItem(ContainerWrapper container, Item item) {
            mContainer = container;
            mItem = item;
            mMediaItems = new ArrayList<>();
            mIsBookmarked = false;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ListItem) {
                ListItem other = (ListItem) obj;
                return Objects.equals(mContainer, other.mContainer) && Objects.equals(mItem, other.mItem) &&
                        Objects.equals(mMediaItems, other.mMediaItems);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mContainer, mItem, mMediaItems);
        }

        @Override
        public String toString() {
            if (isPreviousContainerListItem()) {
                return "...";
            }

            return holdsContainer() ? mContainer.getTitle() : mItem.getTitle();
        }

        ContainerWrapper getContainer() {
            return mContainer;
        }

        Item getItem() {
            return mItem;
        }

        List<Item> getMediaItems() {
            return mMediaItems;
        }

        void setMediaItems(List<Item> mediaItems) {
            this.mMediaItems = mediaItems;
        }

        boolean holdsContainer() {
            return mContainer != null;
        }

        boolean isPreviousContainerListItem() {
            return mContainer == null && mItem == null;
        }

        boolean hasMediaItems() {
            return mMediaItems.size() > 0;
        }

        public boolean isBookmarked() {
            return mIsBookmarked;
        }

        public void setIsBookmarked(boolean isBookmarked) {
            mIsBookmarked = isBookmarked;
        }
    }

    private static class ViewHolder {
        int position;
        TextView text;
        CheckBox checkBox;
        Button button;
    }
}
