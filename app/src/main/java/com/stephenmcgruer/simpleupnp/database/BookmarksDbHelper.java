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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.stephenmcgruer.simpleupnp.database.BookmarksContract.BookmarksEntry;

public class BookmarksDbHelper extends SQLiteOpenHelper {

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + BookmarksEntry.TABLE_NAME + " (" +
                    BookmarksEntry.COLUMN_NAME_UDN + " TEXT, " +
                    BookmarksEntry.COLUMN_NAME_CONTAINER_NAME + " TEXT, " +
                    BookmarksEntry.COLUMN_NAME_CONTAINER_ID + " TEXT, " +
                    " PRIMARY KEY (" +
                        BookmarksEntry.COLUMN_NAME_UDN +
                    ")" +
            ")";

    private static final String DATABASE_NAME = "SimpleUpnpBookmarks.db";
    private static final int DATABASE_VERSION = 1;

    public BookmarksDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new UnsupportedOperationException("Database upgrades not supported");
    }
}
