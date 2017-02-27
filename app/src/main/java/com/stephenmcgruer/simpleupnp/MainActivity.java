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

package com.stephenmcgruer.simpleupnp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.stephenmcgruer.simpleupnp.fragments.FileBrowserFragment;
import com.stephenmcgruer.simpleupnp.fragments.ServerBrowserFragment;

import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.model.meta.Device;

public class MainActivity extends AppCompatActivity implements
        ServerBrowserFragment.OnFragmentInteractionListener,
        FileBrowserFragment.OnFragmentInteractionListener {

    private static final String TAG = "MainActivity";

    private ServerBrowserFragment mServerBrowserFragment = null;
    private FileBrowserFragment mFileBrowserFragment = null;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected: " + name.flattenToShortString() + ", " + service.toString());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected: " + name.flattenToShortString());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        // Bind the UPnP service. This is not actually used in MainActivity, but is used by multiple child fragments.
        // Binding it here means that it will not be destroyed and recreated during fragment transitions.
        // TODO(smcgruer): Handle failure gracefully.
        if (!getApplicationContext().bindService(
                new Intent(this, AndroidUpnpServiceImpl.class),
                mServiceConnection,
                Context.BIND_AUTO_CREATE)) {
            throw new IllegalStateException("Unable to bind AndroidUpnpServiceImpl");
        }

        if (mServerBrowserFragment != null)
            throw new IllegalStateException("mServerBrowserFragment should be null in onCreate");

        mServerBrowserFragment = ServerBrowserFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, mServerBrowserFragment)
                .commit();
    }

    @Override
    public void onServerSelected(Device device) {
        if (mServerBrowserFragment == null) {
            throw new IllegalStateException(
                    "mServerBrowserFragment should be non-null in onServerSelected");
        }

        if (mFileBrowserFragment != null) {
            throw new IllegalStateException(
                    "mFileBrowserFragment should be null in onServerSelected");
        }

        mFileBrowserFragment = FileBrowserFragment.newInstance(
                device.getIdentity().getUdn().getIdentifierString());

        getSupportFragmentManager().beginTransaction()
                .remove(mServerBrowserFragment)
                .add(R.id.fragment_container, mFileBrowserFragment)
                .commit();

        mServerBrowserFragment = null;
    }

    @Override
    public void onQuitFileBrowsing() {
        if (mFileBrowserFragment == null) {
            throw new IllegalStateException(
                    "mFileBrowserFragment should be non-null in onQuitFileBrowsing");
        }

        if (mServerBrowserFragment != null) {
            throw new IllegalStateException(
                    "mServerBrowserFragment should be null in onQuitFileBrowsing");
        }

        mServerBrowserFragment = ServerBrowserFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .remove(mFileBrowserFragment)
                .add(R.id.fragment_container, mServerBrowserFragment)
                .commit();

        mFileBrowserFragment = null;
    }
}
