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

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.util.Log;

import java.util.Arrays;

public class BookmarksRemoveTask extends AsyncTask<String, Void, Integer> {

    private static final String TAG = "BookmarksRemoveTask";

    public interface ResultsListener {
        void onBookmarkRemoveTaskFinished(int numRemoved);
    }

    private final SQLiteOpenHelper mDbHelper;
    private final ResultsListener mListener;

    public BookmarksRemoveTask(SQLiteOpenHelper dbHelper, ResultsListener listener) {
        mDbHelper = dbHelper;
        mListener = listener;
    }

    @Override
    protected Integer doInBackground(String... params) {
        if (params.length < 2) {
            return 0;
        }

        String whereClause = BookmarksContract.BookmarksEntry.COLUMN_NAME_UDN + " = ? AND " +
                BookmarksContract.BookmarksEntry.COLUMN_NAME_CONTAINER_ID + " LIKE ?";
        String[] whereArgs = {
                params[0], params[1]
        };

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        Log.d(TAG, "doInBackground: removing rows with whereClause '" + whereClause + "' and args " + Arrays.toString(whereArgs));
        return db.delete(BookmarksContract.BookmarksEntry.TABLE_NAME, whereClause, whereArgs);
    }

    @Override
    protected void onPostExecute(Integer numRemoved) {
        super.onPostExecute(numRemoved);
        mListener.onBookmarkRemoveTaskFinished(numRemoved);
    }
}
