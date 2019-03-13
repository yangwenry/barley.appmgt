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

package barley.appmgt.impl.dao.test;


import barley.user.api.RealmConfiguration;
import barley.user.api.TenantMgtConfiguration;
import barley.user.core.UserRealm;
import barley.user.core.UserStoreException;
import barley.user.core.config.multitenancy.MultiTenantRealmConfigBuilder;
import barley.user.core.service.RealmService;
import barley.user.core.tenant.TenantManager;

public class TestRealmService implements RealmService {

    public UserRealm getUserRealm(RealmConfiguration realmConfiguration) throws UserStoreException {
        return null;
    }

    public RealmConfiguration getBootstrapRealmConfiguration() {
        return null;
    }

    public UserRealm getBootstrapRealm() throws UserStoreException {
        return null;
    }

    public void setTenantManager(TenantManager tenantManager) throws UserStoreException {

    }

    public TenantManager getTenantManager() {
        return new TestTenantManager();
    }

    public MultiTenantRealmConfigBuilder getMultiTenantRealmConfigBuilder() throws UserStoreException {
        return null;
    }

    public UserRealm getCachedUserRealm(int i) throws UserStoreException {
        return null;
    }

    public void setTenantManager(barley.user.api.TenantManager tenantManager) throws barley.user.api.UserStoreException {

    }

    public barley.user.api.UserRealm getTenantUserRealm(int i) throws barley.user.api.UserStoreException {
        return null;
    }

    public TenantMgtConfiguration getTenantMgtConfiguration() {
        return null;
    }

    public void setBootstrapRealmConfiguration(RealmConfiguration realmConfiguration) {
        //TODO implement method
    }

	public void clearCachedUserRealm(int arg0) throws UserStoreException {
	
	}

	
}
