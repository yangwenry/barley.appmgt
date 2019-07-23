/*
 *
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   you may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package barley.appmgt.gateway.handlers.subscription;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.rest.AbstractHandler;
import org.apache.synapse.rest.RESTConstants;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.AuthenticatedIDP;
import barley.appmgt.api.model.Subscription;
import barley.appmgt.gateway.handlers.security.Session;
import barley.appmgt.gateway.handlers.security.authentication.AuthenticationContext;
import barley.appmgt.gateway.internal.ServiceReferenceHolder;
import barley.appmgt.gateway.utils.GatewayUtils;
import barley.appmgt.impl.DefaultAppRepository;
import barley.appmgt.impl.service.TenantConfigurationService;

/**
 * Validates app subscription (individual and enterprise subscriptions)
 */
public class SubscriptionsHandler extends AbstractHandler implements ManagedLifecycle {

    private static final Log log = LogFactory.getLog(SubscriptionsHandler.class);

    private Subscription enterpriseSubscription;

    @Override
    public boolean handleRequest(MessageContext messageContext) {

        GatewayUtils.logRequest(log, messageContext);

        if(!isHandlerApplicable()){
            return true;
        }

        String webAppContext = (String) messageContext.getProperty(RESTConstants.REST_API_CONTEXT);
        String webAppVersion = (String) messageContext.getProperty(RESTConstants.SYNAPSE_REST_API_VERSION);

        // (임시주석) SSO 세션이 동작하지 않기 때문에 처리방식 변경 해야함. 
        //Session session = GatewayUtils.getSession(messageContext);
        //AuthenticationContext authenticationContext = session.getAuthenticationContext();

        if(isEnterpriseSubscriptionEnabled()) {

            if(enterpriseSubscription == null) {
                try {
                    enterpriseSubscription = new DefaultAppRepository(null).getEnterpriseSubscription(webAppContext, webAppVersion);
                } catch (AppManagementException e) {
                    GatewayUtils.logAndThrowException(log, String.format("Can't find enterprise subscription entry for '%s':'%s'", webAppContext, webAppVersion), e);
                }
            }

            // (임시주석)
            /*
            if(hasValidEnterpriseSubscription(authenticationContext)){
                if(log.isDebugEnabled()){

                    StringBuilder authenticatedIDPNames = new StringBuilder();
                    for (AuthenticatedIDP authenticatedIDP : authenticationContext.getAuthenticatedIDPs()){
                        authenticatedIDPNames.append(authenticatedIDP.getIdpName());
                    }

                    if(log.isDebugEnabled()){
                        GatewayUtils.logWithRequestInfo(log, messageContext, String.format("User '%s' has an enterprise subscription (IDP(s) : ['%s']) for '%s':'%s'",
                            authenticationContext.getSubject(), authenticatedIDPNames, webAppContext, webAppVersion));
                    }
                }
                return true;
            }
            */
            return true;
        }

        if(isSelfSubscriptionEnabled()){
            // TODO : Validate self subscriptions
            return true;
        }

        // (임시주석)
        /*
        if(log.isDebugEnabled()){
            GatewayUtils.logWithRequestInfo(log, messageContext, String.format("User '%s' has no subscriptions for '%s':'%s'",
                    authenticationContext.getSubject(), webAppContext, webAppVersion));
        }
        */

        GatewayUtils.send401(messageContext, "You have no subscriptions for this app.");

        return false;
    }

    @Override
    public void init(SynapseEnvironment synapseEnvironment) {

    }

    @Override
    public void destroy() {

    }

    @Override
    public boolean handleResponse(MessageContext messageContext) {
        return true;
    }


    // ----------------------------------------------------------------------------------------------------------

    private boolean hasValidEnterpriseSubscription(AuthenticationContext session) {

        List<AuthenticatedIDP> authenticatedIDPs = session.getAuthenticatedIDPs();

        if(authenticatedIDPs != null){
            AuthenticatedIDP[] authenticatedIDPsArray = new AuthenticatedIDP[authenticatedIDPs.size()];
            authenticatedIDPs.toArray(authenticatedIDPsArray);
            return enterpriseSubscription.isTrustedIdp(authenticatedIDPsArray);
        }else{
            return false;
        }
    }

    private boolean isHandlerApplicable() {
        return isSelfSubscriptionEnabled() || isEnterpriseSubscriptionEnabled();
    }


    private boolean isSelfSubscriptionEnabled() {
    	// (임시주석) 2018.04.16 - 임의로 true를 리턴하자.
//        String propertyValue = readConfiguration("Subscriptions.EnableSelfSubscription");
    	String propertyValue = "true";
        return Boolean.parseBoolean(propertyValue);
    }


    private boolean isEnterpriseSubscriptionEnabled() {
    	// (임시주석) 2018.04.16 - 임의로 true를 리턴하자.
//        String propertyValue = readConfiguration("Subscriptions.EnableEnterpriseSubscription");
        String propertyValue = "true";
        return Boolean.parseBoolean(propertyValue);
    }

    private String readConfiguration(String key) {
        TenantConfigurationService tenantConfigurationService = ServiceReferenceHolder.getInstance().getTenantConfigurationService();
        return tenantConfigurationService.getFirstProperty(key);
    }

}
