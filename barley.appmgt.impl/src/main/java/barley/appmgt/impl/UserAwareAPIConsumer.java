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

package barley.appmgt.impl;

import java.util.List;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.APIIdentifier;
import barley.appmgt.api.model.Application;
import barley.appmgt.api.model.Subscription;
import barley.appmgt.api.model.WebAppSearchOption;
import barley.appmgt.api.model.WebAppSortOption;
import barley.appmgt.impl.utils.AppManagerUtil;

/**
 * User aware APIConsumer implementation which ensures that the invoking user has the
 * necessary privileges to execute the operations. Users can use this class as an
 * entry point to accessing the core WebApp provider functionality. In order to ensure
 * proper initialization and cleanup of these objects, the constructors of the class
 * has been hidden. Users should use the APIManagerFactory class to obtain an instance
 * of this class. This implementation also allows anonymous access to some of the
 * available operations. However if the user attempts to execute a privileged operation
 * when the object had been created in the anonymous mode, an exception will be thrown.
 */
public class UserAwareAPIConsumer extends APIConsumerImpl {

    private String username;

    UserAwareAPIConsumer() throws AppManagementException {
        super();
    }

    UserAwareAPIConsumer(String username) throws AppManagementException {
        super(username);
        this.username = username;
    }

    @Override
    public String addSubscription(APIIdentifier identifier, String subscriptionType,
                                String userId, int applicationId, String trustedIdps) throws
                                                                                      AppManagementException {
        checkSubscribePermission();
        return super.addSubscription(identifier, subscriptionType, userId, applicationId, trustedIdps);
    }

    @Override
    public Subscription getSubscription(APIIdentifier apiIdentifier, int applicationId, String subscriptionType) throws
                                                                                                                 AppManagementException {
        checkSubscribePermission();
        return super.getSubscription(apiIdentifier, applicationId, subscriptionType);
    }

    @Override
    public void removeSubscription(APIIdentifier identifier, String userId,
                                   int applicationId) throws AppManagementException {
        checkSubscribePermission();
        super.removeSubscription(identifier, userId, applicationId);
    }

    @Override
    public String addApplication(Application application, String userId) throws
                                                                         AppManagementException {
        checkSubscribePermission();
        return super.addApplication(application, userId);
    }

    @Override
    public void updateApplication(Application application) throws AppManagementException {
        checkSubscribePermission();
        super.updateApplication(application);
    }

    @Override
    public void addToFavouriteApps(APIIdentifier identifier, String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        checkSubscribePermission();
        super.addToFavouriteApps(identifier, username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public void removeFromFavouriteApps(APIIdentifier identifier, String username, int tenantIdOfUser,
                                        int tenantIdOfStore) throws AppManagementException {
        checkSubscribePermission();
        super.removeFromFavouriteApps(identifier, username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public boolean isFavouriteApp(APIIdentifier identifier, String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        checkSubscribePermission();
        return super.isFavouriteApp(identifier, username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public List<APIIdentifier> getFavouriteApps(String username, int tenantIdOfUser, int tenantIdOfStore,
                                                WebAppSortOption sortOption)
            throws AppManagementException {
        checkSubscribePermission();
        return super.getFavouriteApps(username, tenantIdOfUser, tenantIdOfStore, sortOption);
    }

    @Override
    public List<APIIdentifier> searchFavouriteApps(String username, int tenantIdOfUser, int tenantIdOfStore,
                                                   WebAppSearchOption searchOption, String searchValue)
            throws AppManagementException {
        checkSubscribePermission();
        return super.searchFavouriteApps(username, tenantIdOfUser, tenantIdOfStore, searchOption, searchValue);
    }

    @Override
    public void setFavouritePage(String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        checkSubscribePermission();
        super.setFavouritePage(username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public void removeFavouritePage(String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        checkSubscribePermission();
        super.removeFavouritePage(username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public boolean hasFavouritePage(String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        checkSubscribePermission();
        return super.hasFavouritePage(username, tenantIdOfUser, tenantIdOfStore);

    }
    public void checkSubscribePermission() throws AppManagementException {
        AppManagerUtil.checkPermission(username, AppMConstants.Permissions.WEB_APP_SUBSCRIBE);
    }
}
