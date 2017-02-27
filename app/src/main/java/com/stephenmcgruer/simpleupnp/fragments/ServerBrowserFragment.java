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

import com.stephenmcgruer.simpleupnp.R;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;

import java.util.Objects;

public class ServerBrowserFragment extends Fragment implements AdapterView.OnItemClickListener {

    private static final String TAG = "ServerBrowserFragment";

    private OnFragmentInteractionListener mListener;

    private ArrayAdapter<DeviceWrapper> mListAdapter;

    private AndroidUpnpService mUpnpService;
    private DeviceRegistryListener mRegistryListener = new DeviceRegistryListener();

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected: " + name.flattenToShortString() + ", " + service.toString());
            mUpnpService = (AndroidUpnpService) service;

            mListAdapter.clear();

            mUpnpService.getRegistry().addListener(mRegistryListener);

            // Add any already-cached devices.
            for (Device device : mUpnpService.getRegistry().getDevices()) {
                mRegistryListener.deviceAdded(device);
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
        ListView listView = (ListView) inflater.inflate(R.layout.fragment_server_browser, container, false);

        mListAdapter = new ArrayAdapter<>(listView.getContext(), R.layout.fragment_server_browser_item);
        listView.setAdapter(mListAdapter);
        listView.setOnItemClickListener(this);

        return listView;
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
        DeviceWrapper deviceWrapper = (DeviceWrapper) adapterView.getItemAtPosition(position);
        mListener.onServerSelected(deviceWrapper.getDevice());
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

    private class DeviceRegistryListener extends DefaultRegistryListener {

        /**
         * Handle adding a new device, either local or remote.
         *
         * @param device The device to be added
         */
        void deviceAdded(final Device device) {
            if (getActivity() == null)
                return;

            // We only care about devices that provide a ContentDirectory.
            if (device.findService(new UDAServiceType("ContentDirectory")) == null)
                return;

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DeviceWrapper wrapper = new DeviceWrapper(device);
                    int position = mListAdapter.getPosition(wrapper);
                    if (position >= 0) {
                        mListAdapter.remove(wrapper);
                        mListAdapter.insert(wrapper, position);
                    } else {
                        mListAdapter.add(wrapper);
                    }
                    mListAdapter.sort(new DeviceWrapper.Comparator());
                    mListAdapter.notifyDataSetChanged();
                }
            });
        }

        /**
         * Handle removing a device, either local or remote.
         *
         * @param device The device to be removed.
         */
        private void deviceRemoved(final Device device) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mListAdapter.remove(new DeviceWrapper(device));
                        mListAdapter.sort(new DeviceWrapper.Comparator());
                        mListAdapter.notifyDataSetChanged();
                    }
                });
            }
        }

        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
            deviceRemoved(device);
        }

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            deviceRemoved(device);
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            deviceAdded(device);
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            deviceRemoved(device);
        }
    }
}