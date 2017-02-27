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
import android.view.Menu;
import android.widget.Toast;

import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.stephenmcgruer.simpleupnp.fragments.FileBrowserFragment;
import com.stephenmcgruer.simpleupnp.fragments.ServerBrowserFragment;

import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.model.meta.Device;

import java.util.Collections;
import java.util.List;

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

        // Make the Cast button show.
        // TODO(smcgruer): Determine if there is a more correct way to do this.
        CastContext.getSharedInstance(this);

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
    protected void onDestroy() {
        super.onDestroy();
        getApplicationContext().unbindService(mServiceConnection);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mFileBrowserFragment != null) {
            mFileBrowserFragment.onBackPressed();
        } else {
            super.onBackPressed();
        }
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

    @Override
    public void playFiles(List<MediaQueueItem> mediaItems) {
        CastSession castSession =
                CastContext.getSharedInstance(this).getSessionManager().getCurrentCastSession();
        if (castSession == null) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_LONG).show();
            return;
        }

        // For variety, shuffle the list.
        Collections.shuffle(mediaItems);

        RemoteMediaClient mediaClient = castSession.getRemoteMediaClient();
        int startIndex = 0;
        mediaClient.queueLoad(mediaItems.toArray(new MediaQueueItem[0]), startIndex,
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE, null);
    }
}
