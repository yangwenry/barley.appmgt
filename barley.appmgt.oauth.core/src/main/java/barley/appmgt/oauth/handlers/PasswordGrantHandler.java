/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package barley.appmgt.oauth.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.core.internal.OSGiDataHolder;
import barley.core.multitenancy.MultitenantUtils;
import barley.identity.application.authentication.framework.model.AuthenticatedUser;
import barley.identity.oauth2.IdentityOAuth2Exception;
import barley.identity.oauth2.token.OAuthTokenReqMessageContext;
import barley.user.api.UserRealmService;
import barley.user.api.UserStoreException;
import barley.user.api.UserStoreManager;
import barley.user.core.util.UserCoreUtil;

/**
 * An extension to the existing password grant handler, to validate the scopes.
 */
public class PasswordGrantHandler extends barley.identity.oauth2.token.handlers.grant.PasswordGrantHandler{


    private static final Log log = LogFactory.getLog(PasswordGrantHandler.class);

    @Override
    public boolean validateScope(OAuthTokenReqMessageContext tokReqMsgCtx) throws IdentityOAuth2Exception {


        Map<String, String> scopeRoleMapping = ScopesRetriever.getScopeRoleMapping(tokReqMsgCtx.getAuthorizedUser().getTenantDomain());

        String[] authorizedScopes = getAuthorizedScopes(tokReqMsgCtx.getAuthorizedUser(), tokReqMsgCtx.getScope(), scopeRoleMapping);

        tokReqMsgCtx.setScope(authorizedScopes);

        return super.validateScope(tokReqMsgCtx);
    }

    private String[] getAuthorizedScopes(AuthenticatedUser authorizedUser, String[] requestedScopes, Map<String, String> scopeRoleMapping) {

        String[] authorizedScopes = new String[0];

        String[] userRoles = getUserRoles(authorizedUser);
        Map<String, Set<String>> roleScopeMapping = getRoleScopeMapping(scopeRoleMapping);

        Set<String> allowedScopes = new HashSet<String>();

        Set<String> scopesForRole = null;
        for(String role : userRoles){
            scopesForRole = roleScopeMapping.get(role);
            if(scopesForRole != null){
                allowedScopes.addAll(scopesForRole);
            }
        }

        if(requestedScopes.length > 0 && allowedScopes.size() > 0){
            List<String> intersection = ListUtils.intersection(Arrays.asList(requestedScopes), new ArrayList<String>(allowedScopes));
            authorizedScopes = intersection.toArray(new String[0]);
        }

        return authorizedScopes;
    }

    private Map<String, Set<String>> getRoleScopeMapping(Map<String, String> scopeRoleMapping) {

        Map<String, Set<String>> roleScopeMapping = new HashMap<>();

        for(Map.Entry<String,String> entry : scopeRoleMapping.entrySet()){

            String rolesString = entry.getValue();

            String[] roles = rolesString.split(",");

            for(String role : roles){

                Set<String> scopesForRole = roleScopeMapping.get(role);

                if(scopesForRole == null){
                    scopesForRole = new HashSet<String>();
                    roleScopeMapping.put(role, scopesForRole);
                }
                scopesForRole.add(entry.getKey());
            }
        }

        return roleScopeMapping;
    }

    private String[] getUserRoles(AuthenticatedUser authorizedUser) {

        String[] userRoles = new String[0];

        String userNameWithUserStoreDomain = UserCoreUtil.addDomainToName(authorizedUser.getUserName(),
                                                                            authorizedUser.getUserStoreDomain());

        // (수정) 아래코드로 변경함. 어차피 osgi에서 서비스를 가져오는 것이라면 DataHolder에서 가져오는 것이 낫다. 
        //PrivilegedBarleyContext carbonContext = PrivilegedBarleyContext.getThreadLocalCarbonContext();
        //RealmService realmService = (RealmService) carbonContext.getOSGiService(RealmService.class, null);
        UserRealmService realmService = OSGiDataHolder.getInstance().getUserRealmService();
        
        try {
            int tenantId = realmService.getTenantManager().getTenantId(authorizedUser.getTenantDomain());
            UserStoreManager userStoreManager = realmService.getTenantUserRealm(tenantId).getUserStoreManager();

            userRoles = userStoreManager.getRoleListOfUser(MultitenantUtils.getTenantAwareUsername(userNameWithUserStoreDomain));

        } catch (UserStoreException e) {
            log.error(String.format("Can't get the roles list for the user '%s'", MultitenantUtils.getTenantAwareUsername(userNameWithUserStoreDomain)));
        }

        return userRoles;
    }
}
