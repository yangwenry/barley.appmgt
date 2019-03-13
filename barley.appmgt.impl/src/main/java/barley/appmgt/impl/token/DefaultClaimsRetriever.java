/*
*Copyright (c) 2005-2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/
package barley.appmgt.impl.token;


import java.util.SortedMap;
import java.util.TreeMap;

import javax.cache.Cache;
import javax.cache.Caching;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.appmgt.impl.utils.ClaimCacheKey;
import barley.appmgt.impl.utils.UserClaims;
import barley.core.MultitenantConstants;
import barley.core.multitenancy.MultitenantUtils;
import barley.user.api.ClaimManager;
import barley.user.api.ClaimMapping;
import barley.user.api.UserStoreException;
import barley.user.api.UserStoreManager;

/**
 * This class is the default implementation of ClaimsRetriever.
 * It reads user claim values from the default carbon user store.
 * The user claims are encoded to the JWT in the natural order of the claimURIs.
 * To engage this class its fully qualified class name should be mentioned under
 * app-manager.xml -> AppConsumerAuthConfiguration -> ClaimsRetrieverImplClass
 */
public class DefaultClaimsRetriever implements ClaimsRetriever {
    //TODO refactor caching implementation

    private String dialectURI = ClaimsRetriever.DEFAULT_DIALECT_URI;

    /**
     * Reads the DialectURI of the ClaimURIs to be retrieved from app-manager.xml ->
     * AppConsumerAuthConfiguration -> ConsumerDialectURI.
     * If not configured it uses http://wso2.org/claims as default
     */
    public void init() {
        dialectURI = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().
                getAPIManagerConfiguration().getFirstProperty(CONSUMER_DIALECT_URI);
        if (dialectURI == null) {
            dialectURI = ClaimsRetriever.DEFAULT_DIALECT_URI;
        }
    }

    protected Cache getClaimsLocalCache() {
        return Caching.getCacheManager("API_MANAGER_CACHE").getCache("claimsLocalCache");
    }

    public SortedMap<String, String> getClaims(String endUserName) throws AppManagementException {

        SortedMap<String, String> claimValues = null;

        try {
            int tenantId = AppManagerUtil.getTenantId(endUserName);
            //check in local cache
            String key = endUserName + ":" + tenantId;
            ClaimCacheKey cacheKey = new ClaimCacheKey(key);
            //Object result = claimsLocalCache.getValueFromCache(cacheKey);
            Object result = getClaimsLocalCache().get(cacheKey);
            if (result != null) {
                claimValues = ((UserClaims) result).getClaimValues();
            } else {
                ClaimManager claimManager = ServiceReferenceHolder.getInstance().getRealmService().
                        getTenantUserRealm(tenantId).getClaimManager();
                //Claim[] claims = claimManager.getAllClaims(dialectURI);
                ClaimMapping[] claims = claimManager.getAllClaimMappings(dialectURI);
                String[] claimURIs = claimMappingtoClaimURIString(claims);
                UserStoreManager userStoreManager = ServiceReferenceHolder.getInstance().getRealmService().
                        getTenantUserRealm(tenantId).getUserStoreManager();

                String tenantAwareUserName = endUserName;
                if(MultitenantConstants.SUPER_TENANT_ID != tenantId){
                    tenantAwareUserName = MultitenantUtils.getTenantAwareUsername(endUserName);
                }

                if(userStoreManager.isExistingUser(tenantAwareUserName)){
                    claimValues = new TreeMap(userStoreManager.getUserClaimValues(tenantAwareUserName, claimURIs, null));
                    UserClaims userClaims = new UserClaims(claimValues);
                    //add to cache
                    getClaimsLocalCache().put(cacheKey, userClaims);
                }


            }
        } catch (UserStoreException e) {
            throw new AppManagementException("Error while retrieving user claim values from "
                    + "user store");
        }
        return claimValues;
    }

    /**
     * Always returns the ConsumerDialectURI configured in app-manager.xml
     */
    public String getDialectURI(String endUserName) {
        return dialectURI;
    }

    /**
     * Helper method to convert array of <code>Claim</code> object to
     * array of <code>String</code> objects corresponding to the ClaimURI values.
     */
    private String[] claimMappingtoClaimURIString(ClaimMapping[] claims) {
        String[] temp = new String[claims.length];
        for (int i = 0; i < claims.length; i++) {
            temp[i] = claims[i].getClaim().getClaimUri().toString();
       
        }
        return temp;
    }
}
