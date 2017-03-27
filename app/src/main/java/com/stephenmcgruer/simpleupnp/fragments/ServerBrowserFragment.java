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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.stephenmcgruer.simpleupnp.R;
import com.stephenmcgruer.simpleupnp.cling.DeviceRegistryListener;
import com.stephenmcgruer.simpleupnp.database.BookmarksContract.Bookmark;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.types.UDAServiceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ServerBrowserFragment extends Fragment implements AdapterView.OnItemClickListener,
        DeviceRegistryListener.DeviceChangeHandler {

    private static final String TAG = "ServerBrowserFragment";

    private OnFragmentInteractionListener mListener;

    private ArrayAdapter<DeviceWrapper> mServerListAdapter;
    private TextView mBookmarkTextView;
    private ArrayAdapter<BookmarkWrapper> mBookmarkListAdapter;

    private AndroidUpnpService mUpnpService;
    private final DeviceRegistryListener mRegistryListener = new DeviceRegistryListener(this, this);

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected: " + name.flattenToShortString() + ", " + service.toString());
            mUpnpService = (AndroidUpnpService) service;

            mServerListAdapter.clear();

            mUpnpService.getRegistry().addListener(mRegistryListener);

            // Add any already-cached devices.
            for (Device device : mUpnpService.getRegistry().getDevices()) {
                onDeviceAdded(device);
            }

            // Kick off a search for all devices on the network.
            mUpnpService.getControlPoint().search();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected: " + name.flattenToShortString());
            mUpnpService = null;
        }
    };

    public interface OnFragmentInteractionListener {
        void onServerSelected(Device device);
        void requestBookmarksForDevice(Device device);
        void onBookmarkSelected(Bookmark bookmark);
    }

    public ServerBrowserFragment() {
        // Required empty public constructor.
    }

    public static ServerBrowserFragment newInstance() {
        return new ServerBrowserFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO(smcgruer): Handle failure gracefully.
        Log.d(TAG, "onCreate: binding service");
        if (!getActivity().getApplicationContext().bindService(
                new Intent(getActivity(), AndroidUpnpServiceImpl.class),
                mServiceConnection,
                Context.BIND_AUTO_CREATE)) {
            throw new IllegalStateException("Unable to bind AndroidUpnpServiceImpl");
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        RelativeLayout relativeLayoutView = (RelativeLayout)  inflater.inflate(R.layout.fragment_server_browser, container, false);

        ListView serverListView = (ListView) relativeLayoutView.findViewById(R.id.server_browser_list);
        mServerListAdapter =
                new ArrayAdapter<>(serverListView.getContext(), R.layout.fragment_server_browser_item);
        serverListView.setAdapter(mServerListAdapter);
        serverListView.setOnItemClickListener(this);

        mBookmarkTextView = (TextView) relativeLayoutView.findViewById(R.id.bookmarks_text);

        ListView bookmarkListView = (ListView) relativeLayoutView.findViewById(R.id.bookmarks_list);
        mBookmarkListAdapter =
                new ArrayAdapter<>(bookmarkListView.getContext(), R.layout.fragment_server_browser_item);
        bookmarkListView.setAdapter(mBookmarkListAdapter);
        bookmarkListView.setOnItemClickListener(this);

        return relativeLayoutView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        mListener = null;

        if (mUpnpService != null) {
            mUpnpService.getRegistry().removeListener(mRegistryListener);
        }
        getActivity().getApplicationContext().unbindService(mServiceConnection);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        switch (adapterView.getId()) {
            case R.id.server_browser_list:
                DeviceWrapper deviceWrapper = (DeviceWrapper) adapterView.getItemAtPosition(position);
                mListener.onServerSelected(deviceWrapper.getDevice());
                break;
            case R.id.bookmarks_list:
                BookmarkWrapper bookmarkWrapper = (BookmarkWrapper) adapterView.getItemAtPosition(position);
                mListener.onBookmarkSelected(bookmarkWrapper.getBookmark());
                break;
        }
    }

    @Override
    public void onDeviceAdded(Device device) {
        if (device.findService(new UDAServiceType("ContentDirectory")) == null)
            return;

        DeviceWrapper wrapper = new DeviceWrapper(device);
        int position = mServerListAdapter.getPosition(wrapper);
        if (position >= 0) {
            mServerListAdapter.remove(wrapper);
            mServerListAdapter.insert(wrapper, position);
        } else {
            mServerListAdapter.add(wrapper);
        }
        mServerListAdapter.sort(new DeviceWrapper.Comparator());
        mServerListAdapter.notifyDataSetChanged();

        mListener.requestBookmarksForDevice(device);
    }

    @Override
    public void onDeviceRemoved(Device device) {
        mServerListAdapter.remove(new DeviceWrapper(device));
        mServerListAdapter.sort(new DeviceWrapper.Comparator());
        mServerListAdapter.notifyDataSetChanged();

        // Remove any related bookmarks.
        String udn = device.getIdentity().getUdn().getIdentifierString();
        List<BookmarkWrapper> toRemove = new ArrayList<>();
        for (int i = 0; i < mBookmarkListAdapter.getCount(); i++) {
            BookmarkWrapper wrapper = mBookmarkListAdapter.getItem(i);
            if (wrapper != null && wrapper.getBookmark().getUdn().equals(udn)) {
                toRemove.add(wrapper);
            }
        }
        if (toRemove.isEmpty())
            return;

        for (BookmarkWrapper wrapper : toRemove) {
            mBookmarkListAdapter.remove(wrapper);
        }
        if (mBookmarkListAdapter.isEmpty()) {
            mBookmarkTextView.setVisibility(View.GONE);
        }
        mBookmarkListAdapter.notifyDataSetChanged();
    }

    public void addBookmarks(List<Bookmark> bookmarks) {
        if (bookmarks.isEmpty())
            return;

        for (Bookmark bookmark : bookmarks) {
            BookmarkWrapper wrapper = new BookmarkWrapper(bookmark);
            if (mBookmarkListAdapter.getPosition(wrapper) < 0) {
                mBookmarkListAdapter.add(wrapper);
            }
        }
        mBookmarkTextView.setVisibility(View.VISIBLE);
        mBookmarkListAdapter.notifyDataSetChanged();
    }

    /**
     * Simple wrapper of a @link{Device} for the list @link{ArrayAdapter}.
     */
    private static class DeviceWrapper {
        private final Device mDevice;

        DeviceWrapper(Device device) {
            mDevice = device;
        }

        Device getDevice() {
            return mDevice;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof DeviceWrapper) {
                DeviceWrapper other = (DeviceWrapper) obj;
                return Objects.equals(mDevice, other.mDevice);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDevice);
        }

        @Override
        public String toString() {
            String name = (mDevice.getDetails() != null && mDevice.getDetails().getFriendlyName() != null)
                    ? mDevice.getDetails().getFriendlyName() : mDevice.getDisplayString();
            // Mark not yet fully loaded devices with an asterix.
            return mDevice.isFullyHydrated() ? name : name + "*";
        }

        static class Comparator implements java.util.Comparator<DeviceWrapper> {
            @Override
            public int compare(DeviceWrapper o1, DeviceWrapper o2) {
                return o1.toString().compareTo(o2.toString());
            }
        }
    }

    private static class BookmarkWrapper {
        private final Bookmark mBookmark;

        BookmarkWrapper(Bookmark bookmark) {
            mBookmark = bookmark;
        }

        Bookmark getBookmark() {
            return mBookmark;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof BookmarkWrapper) {
                BookmarkWrapper other = (BookmarkWrapper) obj;
                return Objects.equals(mBookmark, other.mBookmark);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mBookmark);
        }

        @Override
        public String toString() {
            return mBookmark.getContainerName() + " (on " + mBookmark.getDeviceName() + ")";

        }
    }
}