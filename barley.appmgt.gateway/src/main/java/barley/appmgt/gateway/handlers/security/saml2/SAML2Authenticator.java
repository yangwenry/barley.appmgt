/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package barley.appmgt.gateway.handlers.security.saml2;

import javax.cache.Cache;
import javax.cache.Caching;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.rest.RESTConstants;

import barley.appmgt.api.model.AuthenticatedIDP;
import barley.appmgt.gateway.handlers.Utils;
import barley.appmgt.gateway.handlers.security.APISecurityConstants;
import barley.appmgt.gateway.handlers.security.APISecurityException;
import barley.appmgt.gateway.handlers.security.APISecurityUtils;
import barley.appmgt.gateway.handlers.security.AuthenticationContext;
import barley.appmgt.gateway.handlers.security.Authenticator;
import barley.appmgt.gateway.handlers.security.keys.APIKeyDataStore;
import barley.appmgt.gateway.handlers.security.keys.JDBCAPIKeyDataStore;
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.dto.APIKeyValidationInfoDTO;
import barley.core.context.PrivilegedBarleyContext;

public class SAML2Authenticator implements Authenticator{

    private static final Log log = LogFactory.getLog(SAML2Authenticator.class);

    private APIKeyDataStore dataStore;
    private AuthenticationContext authenticationContext;

    public void init(SynapseEnvironment env) {

        try {
            this.dataStore = new JDBCAPIKeyDataStore();
        } catch (APISecurityException e) {
            e.printStackTrace();
        }

        this.getKeyCache();
    }

    protected Cache getKeyCache() {
        return Caching.getCacheManager(AppMConstants.API_MANAGER_CACHE_MANAGER).
                getCache(AppMConstants.KEY_CACHE_NAME);
    }

    public void destroy() {

    }

    public boolean authenticate(MessageContext synCtx) throws APISecurityException {

        String user = (String) synCtx.getProperty(APISecurityConstants.SUBJECT);
        String apiContext = (String) synCtx.getProperty(RESTConstants.REST_API_CONTEXT);
        String apiVersion = (String) synCtx.getProperty(RESTConstants.SYNAPSE_REST_API_VERSION);
        String authCookie = Utils.getAuthenticationCookie(synCtx);

        Object authenticatedIDPObject = synCtx.getProperty(APISecurityConstants.AUTHENTICATED_IDP);

        AuthenticatedIDP[] authenticatedIDPs = null;
        if(authenticatedIDPObject != null){
            authenticatedIDPs = (AuthenticatedIDP[]) authenticatedIDPObject;
        }

        AuthenticationContext authContext = getAuthenticationContext(apiContext, apiVersion, user, authenticatedIDPs, authCookie);
        if (authContext.isAuthenticated()) {
            APISecurityUtils.setAuthenticationContext(synCtx, authContext, "X-JWT-Assertion");
            return true;
        } else {
            log.warn("Access failure for WebApp: " + apiContext + ", version: " + apiVersion + ", samlssoTokenId: " + authCookie);
            return false;
        }
    }




    public String getChallengeString() {
        return null;
    }

    public String getRequestOrigin() {
        return null;
    }

    public String getSecurityContextHeader() {
        return null;  
    }

    private AuthenticationContext getAuthenticationContext(String appContext, String appVersion,
                                                          String subscriber, AuthenticatedIDP[] authenticatedIDPs, String accessToken) throws APISecurityException {

		APIKeyValidationInfoDTO info = null;

        String tenantDomain = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantDomain();

        if(!tenantDomain.equalsIgnoreCase("carbon.super")){
            String tenantedSubscriber = subscriber+'@'+tenantDomain;
            info = dataStore.getAPPData(appContext, appVersion, tenantedSubscriber, authenticatedIDPs);
        }else{
            info = dataStore.getAPPData(appContext, appVersion, subscriber, authenticatedIDPs);
        }

		if (info == null) {
			log.warn("cannot load application data for the provided context and version");
			return null;
		}
		AuthenticationContext authContext = new AuthenticationContext();
		authContext.setAccessToken(accessToken);
		authContext.setApplicationId(info.getApplicationId());
		authContext.setApplicationName(info.getApplicationName());
		authContext.setApplicationTier(info.getApplicationTier());
		authContext.setTier(info.getTier());
		authContext.setConsumerKey(accessToken);
		authContext.setAuthenticated(info.isAuthorized());
        authContext.setValidationStatus(info.getValidationStatus());
        authContext.setContext(info.getContext());
        authContext.setApiVersion(info.getApiVersion());
        authContext.setApiPublisher(info.getApiPublisher());
        authContext.setLogoutURL(info.getLogoutURL());

        if (authContext.getAccessToken() != null) {
            getKeyCache().put(authContext.getAccessToken(), subscriber);
        }

        return authContext;

    }

}
