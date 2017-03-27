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

package com.stephenmcgruer.simpleupnp.cling;

import android.support.v4.app.Fragment;

import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;

public class DeviceRegistryListener extends DefaultRegistryListener {

    private final Fragment mFragment;
    private final DeviceChangeHandler mHandler;

    public interface DeviceChangeHandler {
        void onDeviceAdded(Device device);
        void onDeviceRemoved(Device device);
    }

    public DeviceRegistryListener(Fragment fragment, DeviceChangeHandler handler) {
        mFragment = fragment;
        mHandler = handler;
    }

    /**
     * Handle adding a new device, either local or remote.
     *
     * @param device The device to be added
     */
    private void deviceAdded(final Device device) {
        if (mFragment.getActivity() != null) {
            mFragment.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mHandler.onDeviceAdded(device);
                }
            });
        }
    }

    /**
     * Handle removing a device, either local or remote.
     *
     * @param device The device to be removed.
     */
    private void deviceRemoved(final Device device) {
        if (mFragment.getActivity() != null) {
            mFragment.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mHandler.onDeviceRemoved(device);
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
