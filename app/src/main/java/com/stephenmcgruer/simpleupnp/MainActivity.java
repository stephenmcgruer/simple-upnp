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

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.stephenmcgruer.simpleupnp.fragments.ServerBrowserFragment;

import org.fourthline.cling.model.meta.Device;

public class MainActivity extends AppCompatActivity implements ServerBrowserFragment.OnFragmentInteractionListener {

    private static final String TAG = "MainActivity";

    private ServerBrowserFragment mServerBrowserFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (mServerBrowserFragment != null)
            throw new IllegalStateException("mServerBrowserFragment should be null in onCreate");

        mServerBrowserFragment = ServerBrowserFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, mServerBrowserFragment)
                .commit();
    }

    @Override
    public void onServerSelected(Device device) {
        // TODO(smcgruer): Implement.
        Log.d(TAG, "onServerSelected: " + device.toString());
    }
}
