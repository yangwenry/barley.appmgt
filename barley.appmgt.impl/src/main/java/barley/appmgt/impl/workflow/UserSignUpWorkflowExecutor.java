/*
*  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package barley.appmgt.impl.workflow;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.user.mgt.stub.UserAdminStub;
import org.wso2.carbon.user.mgt.stub.types.carbon.FlaggedName;

import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.core.utils.BarleyUtils;
import barley.user.api.UserStoreManager;
import barley.user.core.UserRealm;
import barley.user.core.service.RealmService;

public abstract class UserSignUpWorkflowExecutor extends WorkflowExecutor {

    private static final Log log = LogFactory.getLog(UserSignUpWSWorkflowExecutor.class);

    /**
     * Method updates Roles users with subscriber role
     *
     * @param serverURL
     * @param adminUsername
     * @param adminPassword
     * @param userName
     * @param role
     * @throws Exception
     */
    protected static void updateRolesOfUser(String serverURL, String adminUsername,
                                            String adminPassword, String userName, String role)
            throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Adding Subscriber role to " + userName);
        }

        String url = serverURL + "UserAdmin";
        RealmService realmService = ServiceReferenceHolder.getInstance().getRealmService();
        UserRealm realm = realmService.getBootstrapRealm();
        UserStoreManager manager = realm.getUserStoreManager();
        if (!manager.isExistingRole(role)) {
            log.error("Could not find role " + role + " in the user store");
            throw new Exception("Could not find role " + role + " in the user store");
        }

        UserAdminStub userAdminStub = new UserAdminStub(url);
        BarleyUtils.setBasicAccessSecurityHeaders(adminUsername, adminPassword, true, userAdminStub._getServiceClient());
        FlaggedName[] flaggedNames = userAdminStub.getRolesOfUser(userName, "*", -1);
        List<String> roles = new ArrayList<String>();
        if (flaggedNames != null) {
            for (FlaggedName flaggedName : flaggedNames) {
                if (flaggedName.getSelected()) {
                    roles.add(flaggedName.getItemName());
                }
            }
        }
        roles.add(role);
        userAdminStub.updateRolesOfUser(userName, roles.toArray(new String[roles.size()]));
    }

    /**
     * Update the roles with the users
     *
     * @param serverURL
     * @param adminUsername
     * @param adminPassword
     * @param userName
     * @param roleList
     * @param tenantDomain
     * @throws Exception
     */

    protected static void updateRolesOfUser(String serverURL, String adminUsername,
                                            String adminPassword, String userName,
                                            List<String> roleList, String tenantDomain)
            throws Exception {

        if (log.isDebugEnabled()) {
            log.debug("Adding roles to " + userName + "in " + tenantDomain + " Domain");
        }
        String url = serverURL + "UserAdmin";
        RealmService realmService = ServiceReferenceHolder.getInstance().getRealmService();
        int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                .getTenantId(tenantDomain);
        UserRealm realm = (UserRealm) realmService.getTenantUserRealm(tenantId);
        UserStoreManager manager = realm.getUserStoreManager();

        if (manager.isExistingUser(userName)) {
            // check whether given roles exist
            for (String role : roleList) {
                if (!manager.isExistingRole(role)) {
                    log.error("Could not find role " + role + " in the user store");
                    throw new Exception("Could not find role " + role + " in the user store");
                }
            }

            UserAdminStub userAdminStub = new UserAdminStub(url);
            BarleyUtils.setBasicAccessSecurityHeaders(adminUsername, adminPassword, true,
                    userAdminStub._getServiceClient());

            FlaggedName[] flaggedNames = userAdminStub.getRolesOfUser(userName, "*", -1);
            List<String> roles = new ArrayList<String>();
            if (flaggedNames != null) {
                for (FlaggedName flaggedName : flaggedNames) {
                    if (flaggedName.getSelected()) {
                        roles.add(flaggedName.getItemName());
                    }
                }
            }
            for (String role : roleList) {
                roles.add(role);
            }
            userAdminStub.updateRolesOfUser(userName, roles.toArray(new String[roles.size()]));
        } else {
            log.error("User does not exist. Unable to approve user " + userName);
        }

    }
}
