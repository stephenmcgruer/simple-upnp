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

import android.support.annotation.NonNull;

import org.fourthline.cling.support.model.container.Container;

class ContainerWrapper {
    static final String ROOT_CONTAINER_ID = "0";
    static final ContainerWrapper ROOT_CONTAINER = new ContainerWrapper(null, ROOT_CONTAINER_ID, "", true);

    private final String mTitle;
    private final String mId;
    private final String mParentId;
    private final boolean mIsRootContainer;

    ContainerWrapper(@NonNull Container container) {
        this(container.getTitle(), container.getId(), container.getParentID(), false);
    }

    ContainerWrapper(String title, String id, String parentId) {
        this(title, id, parentId, false);
    }

    private ContainerWrapper(String title, String id, String parentId, boolean isRootContainer) {
        mTitle = title;
        mId = id;
        mParentId = parentId;
        mIsRootContainer = isRootContainer;
    }

    String getId() {
        return mId;
    }

    String getParentID() {
        return mParentId;
    }

    String getTitle() {
        if (mIsRootContainer)
            throw new UnsupportedOperationException("The root container does not have a title");
        return mTitle;
    }
}
