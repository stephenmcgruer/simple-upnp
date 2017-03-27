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

package com.stephenmcgruer.simpleupnp.database;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.util.Log;

import com.stephenmcgruer.simpleupnp.database.BookmarksContract.BookmarksEntry;

public class BookmarksWriteTask extends AsyncTask<String, Void, Boolean> {
    private static final String TAG = "BookmarksWriteTask";

    public interface ResultListener {
        void onBookmarksWriteTaskFailure();
    }

    private final SQLiteOpenHelper mDbHelper;
    private final ResultListener mListener;

    public BookmarksWriteTask(SQLiteOpenHelper dbHelper, ResultListener listener) {
        mDbHelper = dbHelper;
        mListener = listener;
    }
    @Override
    protected Boolean doInBackground(String... params) {
        if (params.length < 3) {
            return false;
        }

        String udn = params[0];
        String containerName = params[1];
        String containerId = params[2];

        ContentValues values = new ContentValues();
        values.put(BookmarksEntry.COLUMN_NAME_UDN, udn);
        values.put(BookmarksEntry.COLUMN_NAME_CONTAINER_NAME, containerName);
        values.put(BookmarksEntry.COLUMN_NAME_CONTAINER_ID, containerId);

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        Log.d(TAG, "doInBackground: writing values " + values.toString() + " to " + BookmarksEntry.TABLE_NAME);
        return db.replace(BookmarksEntry.TABLE_NAME, null, values) != -1;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        super.onPostExecute(success);
        if (!success) {
            mListener.onBookmarksWriteTaskFailure();
        }
    }
}
