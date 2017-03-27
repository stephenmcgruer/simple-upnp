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

import java.util.Objects;

public class BookmarksContract {
    private BookmarksContract() {}

    public static class BookmarksEntry {
        public static final String TABLE_NAME = "bookmarks";
        public static final String COLUMN_NAME_UDN = "udn";
        public static final String COLUMN_NAME_CONTAINER_NAME = "container_name";
        public static final String COLUMN_NAME_CONTAINER_ID = "container_id";
    }

    public static class Bookmark {
        private final String mUdn;
        private final String mContainerName;
        private final String mContainerId;

        public Bookmark(String udn, String containerName, String containerId) {
            mUdn = udn;
            mContainerName = containerName;
            mContainerId = containerId;
        }

        public String getUdn() {
            return mUdn;
        }

        public String getContainerName() {
            return mContainerName;
        }

        public String getContainerId() {
            return mContainerId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Bookmark) {
                Bookmark other = (Bookmark) obj;
                return Objects.equals(mUdn, other.mUdn) &&
                        Objects.equals(mContainerName, other.mContainerName) &&
                        Objects.equals(mContainerId, other.mContainerId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mUdn, mContainerName, mContainerId);
        }
    }
}
