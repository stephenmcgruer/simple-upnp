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
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.stephenmcgruer.simpleupnp.R;
import com.stephenmcgruer.simpleupnp.database.BookmarksContract.Bookmark;
import com.stephenmcgruer.simpleupnp.database.BookmarksReadTask;
import com.stephenmcgruer.simpleupnp.database.BookmarksRemoveTask;
import com.stephenmcgruer.simpleupnp.database.BookmarksWriteTask;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.support.contentdirectory.callback.Browse;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FileBrowserFragment extends Fragment implements AdapterView.OnItemClickListener, ServiceConnection,
        FileBrowserAdapter.OnItemClickListener, BookmarksReadTask.ResultListener, BookmarksRemoveTask.ResultsListener,
        BookmarksWriteTask.ResultListener {

    private static final String TAG = "FileBrowserFragment";

    private static final String ARGS_UDN = "com.stephenmcgruer.simpleupnp.fragments.ARGS_UDN";
    private static final String ARGS_INITIAL_CONTAINER_ID =
            "com.stephenmcgruer.simpleupnp.fragments.ARGS_INITIAL_CONTAINER_ID";

    private static final String BOOKMARK_PARENT_ID = "-1";

    private String mDeviceUdn;
    private String mDeviceName;

    private FileBrowserAdapter mFileBrowserAdapter;

    private AndroidUpnpService mUpnpService;
    private Service mContentDirectoryService;

    private OnFragmentInteractionListener mListener;

    // Should only be accessed on the main thread.
    private Map<String, ContainerWrapper> mContainerMap;
    private ContainerWrapper mCurrentContainer;

    public FileBrowserFragment() {
        // Required empty public constructor.
    }

    public static FileBrowserFragment newInstance(String deviceUdn, String initialContainerId) {
        FileBrowserFragment fragment = new FileBrowserFragment();
        Bundle args = new Bundle();
        args.putString(ARGS_UDN, deviceUdn);
        Log.d(TAG, "newInstance: putting ARGS_INITIAL_CONTAINER_ID as " + initialContainerId);
        args.putString(ARGS_INITIAL_CONTAINER_ID, initialContainerId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDeviceUdn = getArguments().getString(ARGS_UDN);
        if (mDeviceUdn == null || mDeviceUdn.isEmpty())
            throw new IllegalStateException("FileBrowserFragment requires a Device UDN");

        mContainerMap = new HashMap<>();
        mContainerMap.put(ContainerWrapper.ROOT_CONTAINER_ID, ContainerWrapper.ROOT_CONTAINER);
        mCurrentContainer = ContainerWrapper.ROOT_CONTAINER;

        if (getArguments().getString(ARGS_INITIAL_CONTAINER_ID) != null) {
            ContainerWrapper wrapper = new ContainerWrapper(
                    "Bookmark", getArguments().getString(ARGS_INITIAL_CONTAINER_ID), BOOKMARK_PARENT_ID);
            mContainerMap.put(wrapper.getId(), wrapper);
            mCurrentContainer = wrapper;
        }

        // TODO(smcgruer): Handle failure gracefully.
        if (!getActivity().getApplicationContext().bindService(
                new Intent(getActivity(), AndroidUpnpServiceImpl.class),
                this,
                Context.BIND_AUTO_CREATE)) {
            throw new IllegalStateException("Unable to bind AndroidUpnpServiceImpl");
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ListView view = (ListView) inflater.inflate(R.layout.fragment_file_browser, container, false);
        Context context = view.getContext();

        mFileBrowserAdapter = new FileBrowserAdapter(this, context, R.layout.fragment_file_browser_item);
        mFileBrowserAdapter.add(FileBrowserAdapter.ListItem.PREVIOUS_CONTAINER_LIST_ITEM);
        view.setAdapter(mFileBrowserAdapter);
        view.setOnItemClickListener(this);
        return view;
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
        getActivity().getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "onServiceConnected: " + name.flattenToShortString() + ", " + service.toString());

        mUpnpService = (AndroidUpnpService) service;

        Device device = mUpnpService.getRegistry().getDevice(new UDN(mDeviceUdn), false);
        mContentDirectoryService = device.findService(new UDAServiceType("ContentDirectory"));
        if (mContentDirectoryService == null) {
            throw new IllegalStateException("Unable to find ContentDirectory service for device "
                    + mDeviceUdn);
        }
        mDeviceName = (device.getDetails() != null && device.getDetails().getFriendlyName() != null)
                ? device.getDetails().getFriendlyName() : device.getDisplayString();

        selectContainer(mCurrentContainer);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "onServiceDisconnected: " + name.flattenToShortString());
        mUpnpService = null;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        FileBrowserAdapter.ListItem listItem = (FileBrowserAdapter.ListItem) adapterView.getItemAtPosition(position);
        if (listItem.isPreviousContainerListItem()) {
            onBackPressed();
        } else if (listItem.holdsContainer()) {
            selectContainer(listItem.getContainer());
        } else {
            List<Item> items = new ArrayList<>();
            items.add(listItem.getItem());
            playItems(items);
        }
    }

    @Override
    public void playItems(List<Item> itemsToPlay) {
        if (mListener == null)
            return;

        // Convert to MediaQueueItem for Cast.
        List<MediaQueueItem> mediaItems = new ArrayList<>();
        for (Item item : itemsToPlay) {
            // Assumption: first non-null resource is the URL. No idea if correct.
            Res urlResource = item.getFirstResource();
            if (urlResource != null) {
                MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
                metadata.putString(MediaMetadata.KEY_TITLE, item.getTitle());
                MediaInfo mediaInfo = new MediaInfo.Builder(urlResource.getValue())
                        .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                        .setContentType("audio/mp3")
                        .setMetadata(metadata)
                        .build();
                mediaItems.add(new MediaQueueItem.Builder(mediaInfo).build());
            }
        }
        mListener.playFiles(mediaItems);
    }

    @Override
    public void addBookmark(String bookmarkName, String containerId) {
        new BookmarksWriteTask(mListener.getDbHelper(), this)
                .execute(mDeviceUdn, bookmarkName, containerId, mDeviceName);
    }

    @Override
    public void removeBookmark(String containerId) {
        new BookmarksRemoveTask(mListener.getDbHelper(), this).execute(mDeviceUdn, containerId);
    }

    @Override
    public void onBookmarksReadFromDatabase(List<Bookmark> bookmarks) {
        if (bookmarks.size() != 1) {
            throw new IllegalStateException("Multiple bookmarks returned for single container?");
        }

        Bookmark bookmark = bookmarks.get(0);
        for (int i = 0; i < mFileBrowserAdapter.getCount(); i++) {
            FileBrowserAdapter.ListItem listItem = mFileBrowserAdapter.getItem(i);
            if (listItem != null && listItem.holdsContainer() &&
                    listItem.getContainer().getId().equals(bookmark.getContainerId())) {
                listItem.setIsBookmarked(true);
                mFileBrowserAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onBookmarksWriteTaskFinished(boolean success, String containerId) {
        if (success) {
            for (int i = 0; i < mFileBrowserAdapter.getCount(); i++) {
                FileBrowserAdapter.ListItem listItem = mFileBrowserAdapter.getItem(i);
                if (listItem != null && listItem.holdsContainer() &&
                        listItem.getContainer().getId().equals(containerId)) {
                    listItem.setIsBookmarked(true);
                    mFileBrowserAdapter.notifyDataSetChanged();
                }
            }
        } else {
            Toast.makeText(getContext(), "Unable to save bookmark", Toast.LENGTH_SHORT).show();
            // TODO(smcgruer): Uncheck the bookmark.
        }
    }

    @Override
    public void onBookmarkRemoveTaskFinished(int numRemoved) {
        if (numRemoved == 0) {
            Toast.makeText(getContext(), "Unable to remove bookmark", Toast.LENGTH_SHORT).show();
            // TODO(smcgruer): Re-check the bookmark.
        }
    }

    public void onBackPressed() {
        String parentId = mCurrentContainer.getParentID();
        if (parentId.isEmpty() || parentId.equals(BOOKMARK_PARENT_ID)) {
            // We are at the initial root level.
            if (mListener != null) {
                mListener.onQuitFileBrowsing();
            }
        } else {
            // Go back to the parent container.
            if (!mContainerMap.containsKey(parentId)) {
                throw new IllegalStateException(
                        "Container map does not contain parent container: " + parentId);
            }
            selectContainer(mContainerMap.get(parentId));
        }
    }

    private void selectContainer(ContainerWrapper container) {
        mCurrentContainer = container;
        SelectContainerBrowse containerBrowse = new SelectContainerBrowse(
                mContentDirectoryService, container.getId(), BrowseFlag.DIRECT_CHILDREN);
        mUpnpService.getControlPoint().execute(containerBrowse);
    }

    public interface OnFragmentInteractionListener {
        void onQuitFileBrowsing();
        SQLiteOpenHelper getDbHelper();
        void playFiles(List<MediaQueueItem> mediaItems);
    }

    private class SelectContainerBrowse extends Browse {

        SelectContainerBrowse(Service service, String containerId, BrowseFlag flag) {
            super(service, containerId, flag);
        }

        @Override
        public void received(ActionInvocation actionInvocation, final DIDLContent didl) {
            if (getActivity() == null)
                return;

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFileBrowserAdapter.clear();
                    mFileBrowserAdapter.add(FileBrowserAdapter.ListItem.PREVIOUS_CONTAINER_LIST_ITEM);

                    for (Container container : didl.getContainers()) {
                        FileBrowserAdapter.ListItem listItem = new FileBrowserAdapter.ListItem(
                                new ContainerWrapper(container));
                        mFileBrowserAdapter.add(listItem);
                        new BookmarksReadTask(mListener.getDbHelper(), FileBrowserFragment.this)
                                .execute(mDeviceUdn, container.getId());
                        if (!mContainerMap.containsKey(container.getId())) {
                            mContainerMap.put(container.getId(), listItem.getContainer());
                        }

                        CheckForMediaItemsBrowse mediaItemsBrowse = new CheckForMediaItemsBrowse(
                                mContentDirectoryService, container.getId(), BrowseFlag.DIRECT_CHILDREN);
                        mUpnpService.getControlPoint().execute(mediaItemsBrowse);
                    }

                    for (Item item : didl.getItems()) {
                        mFileBrowserAdapter.add(new FileBrowserAdapter.ListItem(item));
                    }
                }
            });
        }

        @Override
        public void updateStatus(Status status) {
            // Do nothing.
        }

        @Override
        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
            if (getActivity() == null)
                return;

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity(), "Unable to retrieve results", Toast.LENGTH_SHORT)
                            .show();
                }
            });
        }
    }

    private class CheckForMediaItemsBrowse extends Browse {
        private final String mContainerId;

        CheckForMediaItemsBrowse(Service service, String containerId, BrowseFlag flag) {
            super(service, containerId, flag);
            mContainerId = containerId;
        }

        @Override
        public void received(ActionInvocation actionInvocation, final DIDLContent didl) {
            if (getActivity() == null || didl.getItems().isEmpty())
                return;

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < mFileBrowserAdapter.getCount(); i++) {
                        FileBrowserAdapter.ListItem listItem = mFileBrowserAdapter.getItem(i);
                        if (listItem != null && listItem.holdsContainer() &&
                                Objects.equals(listItem.getContainer().getId(), mContainerId)) {
                            listItem.setMediaItems(didl.getItems());
                            mFileBrowserAdapter.notifyDataSetChanged();
                        }
                    }
                }
            });
        }

        @Override
        public void updateStatus(Status status) {
            // Do nothing.
        }

        @Override
        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
            // Do nothing.
        }
    }
}
