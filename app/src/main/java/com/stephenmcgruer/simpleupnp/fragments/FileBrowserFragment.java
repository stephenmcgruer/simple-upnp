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
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.stephenmcgruer.simpleupnp.R;

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

public class FileBrowserFragment extends Fragment implements AdapterView.OnItemClickListener,
        FileBrowserAdapter.OnItemClickListener, ServiceConnection {

    private static final String TAG = "FileBrowserFragment";

    private static final String ARGS_UDN = "com.stephenmcgruer.simpleupnp.fragments.ARGS_UDN";

    private String mDeviceUdn;

    private FileBrowserAdapter mFileBrowserAdapter;

    private AndroidUpnpService mUpnpService;
    private Service mContentDirectoryService;

    private OnFragmentInteractionListener mListener;

    // Should only be accessed on the main thread.
    private Map<String, ContainerWrapper> mContainerMap;
    private ContainerWrapper mCurrentContainer;

    public interface OnFragmentInteractionListener {
        void onQuitFileBrowsing();
        void playFiles(List<MediaQueueItem> mediaItems);
    }

    public FileBrowserFragment() {
        // Required empty public constructor.
    }

    public static FileBrowserFragment newInstance(String deviceUdn) {
        FileBrowserFragment fragment = new FileBrowserFragment();
        Bundle args = new Bundle();
        args.putString(ARGS_UDN, deviceUdn);
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
        mFileBrowserAdapter.add(new FileBrowserAdapter.ListItem(null, null));
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

        selectContainer(ContainerWrapper.ROOT_CONTAINER);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "onServiceDisconnected: " + name.flattenToShortString());
        mUpnpService = null;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        FileBrowserAdapter.ListItem listItem = (FileBrowserAdapter.ListItem) adapterView.getItemAtPosition(position);
        if (listItem.isPreviousActionWrapper()) {
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
            mListener.playFiles(mediaItems);
        }
    }

    public void onBackPressed() {
        String parentId = mCurrentContainer.getParentID();
        if (parentId.isEmpty()) {
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
                    mFileBrowserAdapter.add(new FileBrowserAdapter.ListItem(null, null));

                    for (Container container : didl.getContainers()) {
                        FileBrowserAdapter.ListItem listItem = new FileBrowserAdapter.ListItem(
                                new ContainerWrapper(container), null);
                        mFileBrowserAdapter.add(listItem);
                        if (!mContainerMap.containsKey(container.getId())) {
                            mContainerMap.put(container.getId(), listItem.getContainer());
                        }

                        CheckForMediaItemsBrowse mediaItemsBrowse = new CheckForMediaItemsBrowse(
                                mContentDirectoryService, container.getId(), BrowseFlag.DIRECT_CHILDREN);
                        mUpnpService.getControlPoint().execute(mediaItemsBrowse);
                    }

                    for (Item item : didl.getItems()) {
                        mFileBrowserAdapter.add(new FileBrowserAdapter.ListItem(null, item));
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
