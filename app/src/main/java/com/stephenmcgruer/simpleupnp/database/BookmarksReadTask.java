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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;

import com.stephenmcgruer.simpleupnp.database.BookmarksContract.Bookmark;

public class BookmarksReadTask extends AsyncTask<String, Void, List<Bookmark>> {
    public interface ResultListener {
        void onBookmarksReadFromDatabase(List<Bookmark> bookmarks);
    }

    private final SQLiteOpenHelper mDbHelper;
    private final ResultListener mListener;

    public BookmarksReadTask(SQLiteOpenHelper dbHelper, ResultListener listener) {
        mDbHelper = dbHelper;
        mListener = listener;
    }

    @Override
    protected List<BookmarksContract.Bookmark> doInBackground(String... udns) {
        if (udns.length < 1) {
            return null;
        }

        String udn = udns[0];
        String[] projection = {
                BookmarksContract.BookmarksEntry.COLUMN_NAME_CONTAINER_NAME,
                BookmarksContract.BookmarksEntry.COLUMN_NAME_CONTAINER_ID,
        };
        String selection = BookmarksContract.BookmarksEntry.COLUMN_NAME_UDN + " = ?";
        String[] selectionArgs = { udn };

        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                BookmarksContract.BookmarksEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null);

        List<BookmarksContract.Bookmark> bookmarks = new ArrayList<>();
        while (cursor.moveToNext()) {
            String containerName = cursor.getString(
                    cursor.getColumnIndexOrThrow(BookmarksContract.BookmarksEntry.COLUMN_NAME_CONTAINER_NAME));
            String containerId = cursor.getString(
                    cursor.getColumnIndexOrThrow(BookmarksContract.BookmarksEntry.COLUMN_NAME_CONTAINER_ID));
            bookmarks.add(new BookmarksContract.Bookmark(udn, containerName, containerId));
        }

        cursor.close();

        return bookmarks;
    }

    @Override
    protected void onPostExecute(List<BookmarksContract.Bookmark> bookmarks) {
        super.onPostExecute(bookmarks);
        if (bookmarks != null && !bookmarks.isEmpty()) {
            mListener.onBookmarksReadFromDatabase(bookmarks);
        }
    }
}
