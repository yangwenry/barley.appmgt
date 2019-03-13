/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package barley.appmgt.impl.idp.sso.configurator;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.IdentityApplicationManagementAdapter;
import barley.appmgt.api.IdentityApplicationManagementFactory;

/**
 * WSO2 IS 5.1.0 based Identity Management interface
 */
public class IS510IdentityApplicationManagementFactory implements IdentityApplicationManagementFactory {

    @Override
    public IdentityApplicationManagementAdapter createAdapter(String backendServerURL, String authCookie)
            throws AppManagementException {
        IS510IdentityApplicationManagementAdapter adapter = new IS510IdentityApplicationManagementAdapter();
        adapter.init(backendServerURL);
        adapter.setAuthCookie(authCookie);
        return adapter;
    }
}
