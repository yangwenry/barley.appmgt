/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package barley.appmgt.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.APIIdentifier;
import barley.appmgt.api.model.APIStatus;
import barley.appmgt.api.model.App;
import barley.appmgt.api.model.AppDefaultVersion;
import barley.appmgt.api.model.Tier;
import barley.appmgt.api.model.WebApp;
import barley.appmgt.impl.dao.AppMDAO;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.core.multitenancy.MultitenantUtils;
import barley.governance.api.exception.GovernanceException;
import barley.governance.api.generic.dataobjects.GenericArtifact;
import barley.governance.api.util.GovernanceUtils;
import barley.registry.core.Registry;
import barley.registry.core.RegistryConstants;
import barley.registry.core.exceptions.RegistryException;
import barley.user.api.UserStoreException;

/**
 * Factory class to create web apps.
 */
public class WebAppFactory extends AppFactory {

    private static final Log log = LogFactory.getLog(WebAppFactory.class);

    @Override
    public App doCreateApp(GenericArtifact artifact, Registry registry) throws AppManagementException {

        WebApp webApp;

        try {

            String providerName = artifact.getAttribute(AppMConstants.API_OVERVIEW_PROVIDER);
            String apiName = artifact.getAttribute(AppMConstants.API_OVERVIEW_NAME);
            String apiVersion = artifact.getAttribute(AppMConstants.API_OVERVIEW_VERSION);
            APIIdentifier apiId = new APIIdentifier(AppManagerUtil.replaceEmailDomainBack(providerName), apiName, apiVersion);
            webApp = new WebApp(apiId);

            webApp.setType(AppMConstants.WEBAPP_ASSET_TYPE);
            webApp.setUUID(artifact.getId());
            webApp.setApiName(apiName);
            webApp.setDescription(artifact.getAttribute(AppMConstants.API_OVERVIEW_DESCRIPTION));


            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
            webApp.setLastUpdated(registry.get(artifactPath).getLastModified());

            webApp.setUrl(artifact.getAttribute(AppMConstants.API_OVERVIEW_ENDPOINT_URL));
            webApp.setLogoutURL(artifact.getAttribute(AppMConstants.API_OVERVIEW_LOGOUT_URL));
            webApp.setDisplayName(artifact.getAttribute(AppMConstants.API_OVERVIEW_DISPLAY_NAME));
            webApp.setSandboxUrl(artifact.getAttribute(AppMConstants.API_OVERVIEW_SANDBOX_URL));
            if (artifact.getLifecycleState() != null) {
                webApp.setStatus(AppManagerUtil.getApiStatus(artifact.getLifecycleState().toUpperCase()));
            }
            webApp.setWsdlUrl(artifact.getAttribute(AppMConstants.API_OVERVIEW_WSDL));
            webApp.setWadlUrl(artifact.getAttribute(AppMConstants.API_OVERVIEW_WADL));
            webApp.setTechnicalOwner(artifact.getAttribute(AppMConstants.API_OVERVIEW_TEC_OWNER));
            webApp.setTechnicalOwnerEmail(artifact.getAttribute(AppMConstants.API_OVERVIEW_TEC_OWNER_EMAIL));
            webApp.setBusinessOwner(artifact.getAttribute(AppMConstants.API_OVERVIEW_BUSS_OWNER));
            webApp.setBusinessOwnerEmail(artifact.getAttribute(AppMConstants.API_OVERVIEW_BUSS_OWNER_EMAIL));
            webApp.setVisibility(artifact.getAttribute(AppMConstants.API_OVERVIEW_VISIBILITY));
            webApp.setVisibleRoles(artifact.getAttribute(AppMConstants.API_OVERVIEW_VISIBLE_ROLES));
            webApp.setVisibleTenants(artifact.getAttribute(AppMConstants.API_OVERVIEW_VISIBLE_TENANTS));
            webApp.setEndpointSecured(Boolean.parseBoolean(artifact.getAttribute(AppMConstants.API_OVERVIEW_ENDPOINT_SECURED)));
            webApp.setEndpointUTUsername(artifact.getAttribute(AppMConstants.API_OVERVIEW_ENDPOINT_USERNAME));
            webApp.setEndpointUTPassword(artifact.getAttribute(AppMConstants.API_OVERVIEW_ENDPOINT_PASSWORD));
            webApp.setTransports(artifact.getAttribute(AppMConstants.API_OVERVIEW_TRANSPORTS));
            webApp.setInSequence(artifact.getAttribute(AppMConstants.API_OVERVIEW_INSEQUENCE));
            webApp.setOutSequence(artifact.getAttribute(AppMConstants.API_OVERVIEW_OUTSEQUENCE));
            webApp.setResponseCache(artifact.getAttribute(AppMConstants.API_OVERVIEW_RESPONSE_CACHING));
            webApp.setSsoEnabled(artifact.getAttribute("sso_singleSignOn"));
            webApp.setThumbnailUrl(artifact.getAttribute(AppMConstants.APP_IMAGES_THUMBNAIL));
            webApp.setBanner(artifact.getAttribute(AppMConstants.APP_IMAGES_BANNER));
            webApp.setTrackingCode(artifact.getAttribute(AppMConstants.APP_TRACKING_CODE));
            webApp.setSkipGateway(Boolean.parseBoolean(artifact.getAttribute(AppMConstants.API_OVERVIEW_SKIP_GATEWAY)));
            webApp.setTreatAsASite(artifact.getAttribute(AppMConstants.APP_OVERVIEW_TREAT_AS_A_SITE));
            webApp.setAllowAnonymous(Boolean.parseBoolean(artifact.getAttribute(AppMConstants.API_OVERVIEW_ALLOW_ANONYMOUS)));

            int cacheTimeout = AppMConstants.API_RESPONSE_CACHE_TIMEOUT;
            if(artifact.getAttribute(AppMConstants.API_OVERVIEW_CACHE_TIMEOUT) != null){
                try {
                    cacheTimeout = Integer.parseInt(artifact.getAttribute(AppMConstants.API_OVERVIEW_CACHE_TIMEOUT));
                } catch (NumberFormatException e) {
                    log.warn(String.format("Error while parsing cache timeout for the web app '%s'. Setting the default value ''", webApp.getUUID(), cacheTimeout));
                }
            }
            webApp.setCacheTimeout(cacheTimeout);webApp.setCacheTimeout(cacheTimeout);

            webApp.setEndpointConfig(artifact.getAttribute(AppMConstants.API_OVERVIEW_ENDPOINT_CONFIG));
            webApp.setRedirectURL(artifact.getAttribute(AppMConstants.API_OVERVIEW_REDIRECT_URL));
            webApp.setAppOwner(artifact.getAttribute(AppMConstants.API_OVERVIEW_OWNER));
            webApp.setAdvertiseOnly(Boolean.parseBoolean(artifact.getAttribute(AppMConstants.API_OVERVIEW_ADVERTISE_ONLY)));
            webApp.setAppTenant(artifact.getAttribute(AppMConstants.API_OVERVIEW_TENANT));
            webApp.setDisplayName(artifact.getAttribute(AppMConstants.API_OVERVIEW_DISPLAY_NAME));
            webApp.setSubscriptionAvailability(artifact.getAttribute(AppMConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABILITY));
            webApp.setSubscriptionAvailableTenants(artifact.getAttribute(AppMConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABLE_TENANTS));

            String tenantDomainName = MultitenantUtils.getTenantDomain(AppManagerUtil.replaceEmailDomainBack(providerName));
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(tenantDomainName);

            Set<Tier> availableTier = new HashSet<Tier>();
            String tiers = artifact.getAttribute(AppMConstants.API_OVERVIEW_TIER);

            Map<String, Tier> definedTiers = AppManagerUtil.getTiers(tenantId);
            if (tiers != null && !"".equals(tiers)) {
                String[] tierNames = tiers.split(",");
                for (String tierName : tierNames) {
                    Tier definedTier = definedTiers.get(tierName);
                    if (definedTier != null) {
                        availableTier.add(definedTier);
                    } else {

                        log.warn("Unknown tier: " + tierName + " found on WebApp: " + apiName);
                    }
                }
            }
            webApp.addAvailableTiers(availableTier);

            if (tenantId == -1234) {
                webApp.setContext(artifact.getAttribute(AppMConstants.API_OVERVIEW_CONTEXT));
            } else {
                String context = artifact.getAttribute(AppMConstants.API_OVERVIEW_CONTEXT);
                if (!context.startsWith(RegistryConstants.PATH_SEPARATOR)) {
                    context = RegistryConstants.PATH_SEPARATOR + context;
                }
                webApp.setContext(RegistryConstants.PATH_SEPARATOR + "t" + RegistryConstants.PATH_SEPARATOR + tenantDomainName + context);
            }

            webApp.setLatest(Boolean.valueOf(artifact.getAttribute(AppMConstants.API_OVERVIEW_IS_LATEST)));

            Set<String> tags = new HashSet<String>();
            barley.registry.core.Tag[] tag = registry.getTags(artifactPath);
            for (barley.registry.core.Tag tag1 : tag) {
                tags.add(tag1.getTagName());
            }
            webApp.addTags(tags);

            webApp.setLastUpdated(registry.get(artifactPath).getLastModified());
            String defaultVersion = AppMDAO.getDefaultVersion(apiName, providerName,
                    AppDefaultVersion.APP_IS_ANY_LIFECYCLE_STATE);
            webApp.setDefaultVersion(apiVersion.equals(defaultVersion));

            //Set Lifecycle status
            if (artifact.getLifecycleState() != null && artifact.getLifecycleState() != "") {
                if (artifact.getLifecycleState().toUpperCase().equalsIgnoreCase(APIStatus.INREVIEW.getStatus())) {
                    webApp.setLifeCycleStatus(APIStatus.INREVIEW);
                } else {
                    webApp.setLifeCycleStatus(APIStatus.valueOf(artifact.getLifecycleState().toUpperCase()));
                }
            }
            webApp.setLifeCycleName(artifact.getLifecycleName());

            return webApp;
        } catch (GovernanceException e) {
            String errorMessage = "Error while creating the web app object from the registry artifact.";
            throw new AppManagementException(errorMessage, e);
        } catch (RegistryException e) {
            String errorMessage = "Error while creating the web app object from the registry artifact.";
            throw new AppManagementException(errorMessage, e);
        } catch (AppManagementException e) {
            String errorMessage = "Error while creating the web app object from the registry artifact.";
            throw new AppManagementException(errorMessage, e);
        } catch (UserStoreException e) {
            String errorMessage = "Error while creating the web app object from the registry artifact.";
            throw new AppManagementException(errorMessage, e);
        }
    }
}
