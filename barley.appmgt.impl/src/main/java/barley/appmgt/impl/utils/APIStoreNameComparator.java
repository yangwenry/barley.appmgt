/*
 *  Copyright WSO2 Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package barley.appmgt.impl.utils;

import java.io.Serializable;
import java.util.Comparator;

import barley.appmgt.api.model.AppStore;

/**
 * <p>Compares APIStores by their names.
 */

public class APIStoreNameComparator implements Comparator<AppStore>, Serializable {
    public int compare(AppStore store1, AppStore store2) {
        if (store1.getDisplayName().equals(store2.getDisplayName())) {

            return store1.getName().compareToIgnoreCase(store2.getName());
        } else {

            return store1.getDisplayName().compareToIgnoreCase(store2.getDisplayName());
        }


    }
}
