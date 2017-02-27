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
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.stephenmcgruer.simpleupnp.R;

import org.fourthline.cling.support.model.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class FileBrowserAdapter extends ArrayAdapter<FileBrowserAdapter.ListItem> implements View.OnClickListener {

    private final OnItemClickListener mListener;

    interface OnItemClickListener {
        void playItems(List<Item> items);
    }

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
            holder.button = (Button) rowView.findViewById(R.id.file_browser_list_item_button);
            rowView.setTag(holder);
        }

        ViewHolder holder = (ViewHolder) rowView.getTag();
        ListItem listItem = getItem(position);
        if (listItem != null) {
            holder.position = position;
            holder.text.setText(listItem.toString());
            holder.button.setText(R.string.play_button_text);
            holder.button.setVisibility(listItem.hasMediaItems() ? View.VISIBLE : View.GONE);
            holder.button.setOnClickListener(this);
        }

        return rowView;
    }

    @Override
    public void onClick(View view) {
        if (mListener == null)
            return;

        RelativeLayout parent = (RelativeLayout) view.getParent();
        ViewHolder holder = (ViewHolder) parent.getTag();
        ListItem listItem = getItem(holder.position);
        if (listItem != null) {
            mListener.playItems(listItem.getMediaItems());
        }
    }

    static class ListItem {
        private final ContainerWrapper mContainer;
        private final Item mItem;
        private List<Item> mMediaItems;

        ListItem(ContainerWrapper container, Item item) {
            mContainer = container;
            mItem = item;
            mMediaItems = new ArrayList<>();
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
            if (isPreviousActionWrapper()) {
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

        boolean isPreviousActionWrapper() {
            return mContainer == null && mItem == null;
        }

        boolean hasMediaItems() {
            return mMediaItems.size() > 0;
        }
    }

    private static class ViewHolder {
        int position;
        TextView text;
        Button button;
    }
}
