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

import com.stephenmcgruer.simpleupnp.R;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.model.types.UDN;

public class FileBrowserFragment extends Fragment implements ServiceConnection {

    private static final String TAG = "FileBrowserFragment";

    private static final String ARGS_UDN = "com.stephenmcgruer.simpleupnp.fragments.ARGS_UDN";

    private String mDeviceUdn;
    private OnFragmentInteractionListener mListener;
    private AndroidUpnpService mUpnpService;
    private Service mContentDirectoryService;

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
        return inflater.inflate(R.layout.fragment_file_browser, container, false);
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

        // TODO(smcgruer): Browse directory + return contents.
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "onServiceDisconnected: " + name.flattenToShortString());
        mUpnpService = null;
    }

    public interface OnFragmentInteractionListener {
        // TODO(smcgruer): Add methods.
    }
}
