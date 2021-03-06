/*
*  Copyright (c) 2005-2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package barley.appmgt.impl;

import barley.appmgt.api.APIProvider;
import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.EntitlementService;
import barley.appmgt.api.FaultGatewaysException;
import barley.appmgt.api.dto.UserApplicationAPIUsage;
import barley.appmgt.api.model.*;
import barley.appmgt.api.model.Documentation.DocumentSourceType;
import barley.appmgt.api.model.Tag;
import barley.appmgt.api.model.entitlement.EntitlementPolicy;
import barley.appmgt.api.model.entitlement.EntitlementPolicyPartial;
import barley.appmgt.api.model.entitlement.EntitlementPolicyValidationResult;
import barley.appmgt.api.model.entitlement.XACMLPolicyTemplateContext;
import barley.appmgt.impl.dao.AppMDAO;
import barley.appmgt.impl.dto.Environment;
import barley.appmgt.impl.dto.TierPermissionDTO;
import barley.appmgt.impl.entitlement.EntitlementServiceFactory;
import barley.appmgt.impl.idp.sso.SSOConfiguratorUtil;
import barley.appmgt.impl.observers.APIStatusObserverList;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.template.APITemplateBuilder;
import barley.appmgt.impl.template.APITemplateBuilderImpl;
import barley.appmgt.impl.utils.APINameComparator;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.core.BarleyConstants;
import barley.core.MultitenantConstants;
import barley.core.context.PrivilegedBarleyContext;
import barley.core.multitenancy.MultitenantUtils;
import barley.governance.api.common.dataobjects.GovernanceArtifact;
import barley.governance.api.exception.GovernanceException;
import barley.governance.api.generic.GenericArtifactFilter;
import barley.governance.api.generic.GenericArtifactManager;
import barley.governance.api.generic.dataobjects.GenericArtifact;
import barley.governance.api.util.GovernanceUtils;
import barley.registry.common.CommonConstants;
import barley.registry.core.*;
import barley.registry.core.config.RegistryContext;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.jdbc.realm.RegistryAuthorizationManager;
import barley.registry.core.pagination.PaginationContext;
import barley.registry.core.session.UserRegistry;
import barley.registry.core.utils.RegistryUtils;
import barley.user.api.AuthorizationManager;
import barley.user.api.UserStoreException;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axis2.Constants;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;

import javax.cache.Cache;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.*;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class provides the core WebApp provider functionality. It is implemented in a very
 * self-contained and 'pure' manner, without taking requirements like security into account,
 * which are subject to frequent change. Due to this 'pure' nature and the significance of
 * the class to the overall WebApp management functionality, the visibility of the class has
 * been reduced to package level. This means we can still use it for internal purposes and
 * possibly even extend it, but it's totally off the limits of the users. Users wishing to
 * programmatically access this functionality should use one of the extensions of this
 * class which is visible to them. These extensions may add additional features like
 * security to this class.
 */
class APIProviderImpl extends AbstractAPIManager implements APIProvider {

    public APIProviderImpl(String username) throws AppManagementException {
        super(username);
    }

    /**
     * Delete business owner.
     * @param businessOwnerId ID of the owner.
     * @return
     * @throws AppManagementException
     */
    @Override
    public boolean deleteBusinessOwner(String businessOwnerId) throws AppManagementException{
        boolean isBusinessOwnerAssociatedWithApps = appMDAO.isBusinessOwnerAssociatedWithApps(businessOwnerId,
                                                                                              registry, tenantDomain);
        if (!isBusinessOwnerAssociatedWithApps) {
            appMDAO.deleteBusinessOwner(businessOwnerId);
            // return true if business owner is successfully deleted.
            return true;
        }
        // return false if business owner is associated with one or more web apps.
        return false;
    }
    /**
     *Update a business owner.
     * @param businessOwner
     * @throws AppManagementException
     */
    @Override
    public boolean updateBusinessOwner(BusinessOwner businessOwner) throws AppManagementException {
        boolean isUpdated = false;
        if (appMDAO.getBusinessOwner(businessOwner.getBusinessOwnerId(), tenantId) != null) {
            appMDAO.updateBusinessOwner(businessOwner);
            isUpdated =true;
        }
        return isUpdated;
    }


    /**
     * Get all business Owners.
     * @return
     * @throws AppManagementException
     */
    @Override
    public List<BusinessOwner> getBusinessOwners() throws AppManagementException {
        return appMDAO.getBusinessOwners(tenantId);
    }

    /**
     * Get business owners.
     * @param businessOwnerId Business owner Id.
     * @return
     * @throws AppManagementException
     */
    @Override
    public BusinessOwner getBusinessOwner(int businessOwnerId) throws AppManagementException {
        return appMDAO.getBusinessOwner(businessOwnerId, tenantId);
    }

    /**
     * Search business owners with pagination.
     * @param startIndex
     * @param pageSize
     * @param searchValue
     * @return
     * @throws AppManagementException
     */
    @Override
    public  List<BusinessOwner> searchBusinessOwners(int startIndex, int pageSize, String searchValue) throws
                                                                                          AppManagementException {
        return appMDAO.searchBusinessOwners(startIndex, pageSize, searchValue, tenantId);
    }

    @Override
    public  int getBusinessOwnersCount() throws AppManagementException {
        return appMDAO.getBusinessOwnersCount(tenantId);
    }

    /**
     *Save business owner.
     * @param businessOwner
     * @throws AppManagementException
     */
    @Override
    public int saveBusinessOwner(BusinessOwner businessOwner) throws AppManagementException {
        return appMDAO.saveBusinessOwner(businessOwner, tenantId);
    }

    /**
     * Get Business owner Id by owner name and email.
     * @param businessOwnerName
     * @param businessOwnerEmail
     * @return
     * @throws AppManagementException
     */
    @Override
    public int getBusinessOwnerId(String businessOwnerName, String businessOwnerEmail) throws AppManagementException {
        return appMDAO.getBusinessOwnerId(businessOwnerName, businessOwnerEmail, tenantId);
    }

    /**
     * Returns a list of all #{@link barley.apimgt.api.model.Provider} available on the system.
     *
     * @return Set<Provider>
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to get Providers
     */
    public Set<Provider> getAllProviders() throws AppManagementException {
        Set<Provider> providerSet = new HashSet<Provider>();
        GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                            AppMConstants.PROVIDER_KEY);
        try {
            GenericArtifact[] genericArtifact = artifactManager.getAllGenericArtifacts();
            if (genericArtifact == null || genericArtifact.length == 0) {
                return providerSet;
            }
            for (GenericArtifact artifact : genericArtifact) {
                Provider provider =
                        new Provider(artifact.getAttribute(AppMConstants.PROVIDER_OVERVIEW_NAME));
                provider.setDescription(AppMConstants.PROVIDER_OVERVIEW_DESCRIPTION);
                provider.setEmail(AppMConstants.PROVIDER_OVERVIEW_EMAIL);
                providerSet.add(provider);
            }
        } catch (GovernanceException e) {
            handleException("Failed to get all providers", e);
        }
        return providerSet;
    }

    /**
     * Get a list of APIs published by the given provider. If a given WebApp has multiple APIs,
     * only the latest version will
     * be included in this list.
     *
     * @param providerId , provider id
     * @return set of WebApp
     * @throws barley.appmgt.api.AppManagementException
     *          if failed to get set of WebApp
     */
    public List<WebApp> getAPIsByProvider(String providerId) throws AppManagementException {

        List<WebApp> apiSortedList = new ArrayList<WebApp>();

        /*
        //??????????????? ??????
        try {
            providerId = AppManagerUtil.replaceEmailDomain(providerId);
            String providerPath = AppMConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
                                  providerId;
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                                AppMConstants.API_KEY);
            Association[] associations = registry.getAssociations(providerPath,
                                                                  AppMConstants.PROVIDER_ASSOCIATION);
            for (Association association : associations) {
                String apiPath = association.getDestinationPath();
                Resource resource = registry.get(apiPath);
                String apiArtifactId = resource.getUUID();
                if (apiArtifactId != null) {
                    GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiArtifactId);
                    apiSortedList.add(AppManagerUtil.getAPI(apiArtifact, registry));
                } else {
                    throw new GovernanceException("artifact id is null of " + apiPath);
                }
            }

        } catch (RegistryException e) {
            handleException("Failed to get APIs for provider : " + providerId, e);
        }
        */
        
        //DAO ??????
        apiSortedList = appMDAO.getAPPsByProvider(providerId);
        Collections.sort(apiSortedList, new APINameComparator());

        return apiSortedList;

    }



    /**
     * Get a list of all the consumers for all APIs
     *
     * @param providerId if of the provider
     * @return Set<Subscriber>
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to get subscribed APIs of given provider
     */
    public Set<Subscriber> getSubscribersOfProvider(String providerId)
            throws AppManagementException {

        Set<Subscriber> subscriberSet = null;
        try {
            subscriberSet = appMDAO.getSubscribersOfProvider(providerId);
        } catch (AppManagementException e) {
            handleException("Failed to get Subscribers for : " + providerId, e);
        }
        return subscriberSet;
    }

    /**
     * get details of provider
     *
     * @param providerName name of the provider
     * @return Provider
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to get Provider
     */
    public Provider getProvider(String providerName) throws AppManagementException {
        Provider provider = null;
        // (??????) 2018.02.26 - ????????? ???????????? ?????? ??????
        /*String providerPath = RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH +
                              AppMConstants.PROVIDERS_PATH + RegistryConstants.PATH_SEPARATOR + providerName;*/
        String providerPath = AppMConstants.PROVIDERS_PATH + RegistryConstants.PATH_SEPARATOR 
        							+ AppManagerUtil.replaceEmailDomain(providerName);
        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                                AppMConstants.PROVIDER_KEY);
            Resource providerResource = registry.get(providerPath);
            String artifactId =
                    providerResource.getUUID();
            if (artifactId == null) {
                throw new AppManagementException("artifact it is null");
            }
            GenericArtifact providerArtifact = artifactManager.getGenericArtifact(artifactId);
            provider = AppManagerUtil.getProvider(providerArtifact);

        } catch (RegistryException e) {
            handleException("Failed to get Provider form : " + providerName, e);
        }
        return provider;
    }

    /**
     * Return Usage of given APIIdentifier
     *
     * @param apiIdentifier APIIdentifier
     * @return Usage
     */
    public Usage getUsageByAPI(APIIdentifier apiIdentifier) {
        return null;
    }

    /**
     * Return Usage of given provider and WebApp
     *
     * @param providerId if of the provider
     * @param apiName    name of the WebApp
     * @return Usage
     */
    public Usage getAPIUsageByUsers(String providerId, String apiName) {
        return null;
    }

    /**
     * Returns usage details of all APIs published by a provider
     *
     * @param providerName Provider Id
     * @return UserApplicationAPIUsages for given provider
     * @throws barley.apimgt.api.APIManagementException
     *          If failed to get UserApplicationAPIUsage
     */
    public UserApplicationAPIUsage[] getAllAPIUsageByProvider(
            String providerName) throws AppManagementException {
        return appMDAO.getAllAPIUsageByProvider(providerName);
    }

    /**
     * Shows how a given consumer uses the given WebApp.
     *
     * @param apiIdentifier APIIdentifier
     * @param consumerEmail E-mal Address of consumer
     * @return Usage
     */
    public Usage getAPIUsageBySubscriber(APIIdentifier apiIdentifier, String consumerEmail) {
        return null;
    }

    /**
     * Returns full list of Subscribers of an WebApp
     *
     * @param identifier APIIdentifier
     * @return Set<Subscriber>
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to get Subscribers
     */
    public Set<Subscriber> getSubscribersOfAPI(APIIdentifier identifier)
            throws AppManagementException {

        Set<Subscriber> subscriberSet = null;
        try {
            subscriberSet = appMDAO.getSubscribersOfAPI(identifier);
        } catch (AppManagementException e) {
            handleException("Failed to get subscribers for WebApp : " + identifier.getApiName(), e);
        }
        return subscriberSet;
    }

    /**
     * this method returns the Set<APISubscriptionCount> for given provider and api
     *
     * @param identifier APIIdentifier
     * @return Set<APISubscriptionCount>
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to get APISubscriptionCountByAPI
     */
    public long getAPISubscriptionCountByAPI(APIIdentifier identifier)
            throws AppManagementException {
        long count = 0L;
        try {
            count = appMDAO.getAPISubscriptionCountByAPI(identifier);
        } catch (AppManagementException e) {
            handleException("Failed to get APISubscriptionCount for: " + identifier.getApiName(), e);
        }
        return count;
    }

    public Map<String, List> getSubscribedAPPsByUsers(String fromDate, String toDate)
            throws AppManagementException {
        Map<String, List> users = new HashMap<String, List>();
        try {
            users = appMDAO.getSubscribedAPPsByUsers(fromDate, toDate, tenantId);
        } catch (AppManagementException e) {
            handleException("Failed to get subscribed apps by users for the period " + fromDate + "to " +
                    toDate, e);
        }
        return users;
    }


    public void addTier(Tier tier) throws AppManagementException {
        addOrUpdateTier(tier, false);
    }

    public void updateTier(Tier tier) throws AppManagementException {
        addOrUpdateTier(tier, true);
    }

    private void addOrUpdateTier(Tier tier, boolean update) throws AppManagementException {
        if (AppMConstants.UNLIMITED_TIER.equals(tier.getName())) {
            throw new AppManagementException("Changes on the '" + AppMConstants.UNLIMITED_TIER + "' " +
                                             "tier are not allowed");
        }

        Set<Tier> tiers = getTiers();
        if (update && !tiers.contains(tier)) {
            throw new AppManagementException("No tier exists by the name: " + tier.getName());
        }

        Set<Tier> finalTiers = new HashSet<Tier>();
        for (Tier tet : tiers) {
            if (!tet.getName().equals(tier.getName())) {
                finalTiers.add(tet);
            }
        }
        finalTiers.add(tier);
        saveTiers(finalTiers);
    }

    private void saveTiers(Collection<Tier> tiers) throws AppManagementException {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        OMElement root = fac.createOMElement(AppMConstants.POLICY_ELEMENT);
        OMElement assertion = fac.createOMElement(AppMConstants.ASSERTION_ELEMENT);
        try {
            Resource resource = registry.newResource();
            for (Tier tier : tiers) {
                String policy = new String(tier.getPolicyContent());
                assertion.addChild(AXIOMUtil.stringToOM(policy));
                // if (tier.getDescription() != null && !"".equals(tier.getDescription())) {
                //     resource.setProperty(AppMConstants.TIER_DESCRIPTION_PREFIX + tier.getName(),
                //              tier.getDescription());
                //  }
            }
            //resource.setProperty(AppMConstants.TIER_DESCRIPTION_PREFIX + AppMConstants.UNLIMITED_TIER,
            //        AppMConstants.UNLIMITED_TIER_DESC);
            root.addChild(assertion);
            resource.setContent(root.toString());
            registry.put(AppMConstants.API_TIER_LOCATION, resource);
        } catch (XMLStreamException e) {
            handleException("Error while constructing tier policy file", e);
        } catch (RegistryException e) {
            handleException("Error while saving tier configurations to the registry", e);
        }
    }

    public void removeTier(Tier tier) throws AppManagementException {
        if (AppMConstants.UNLIMITED_TIER.equals(tier.getName())) {
            throw new AppManagementException("Changes on the '" + AppMConstants.UNLIMITED_TIER + "' " +
                                             "tier are not allowed");
        }

        Set<Tier> tiers = getTiers();
        if (tiers.remove(tier)) {
            saveTiers(tiers);
        } else {
            throw new AppManagementException("No tier exists by the name: " + tier.getName());
        }
    }

    /**
     * Adds a new WebApp to the Store
     *
     * @param app WebApp
     * @throws barley.appmgt.api.AppManagementException
     *          if failed to add WebApp
     */
    public void addWebApp(WebApp app) throws AppManagementException {
        try {
        	// ??????????????? dao??? ?????? ?????? ???????????? ????????????. artifact??? ????????? id??? ????????? uuid??? ???????????? ????????? ??????. 
            createAPI(app);
            appMDAO.addWebApp(app);
            //?????? ??????
            addTags(app.getId(), app.getTags());
            
            if (AppManagerUtil.isAPIManagementEnabled()) {
            	Cache contextCache = AppManagerUtil.getAPIContextCache();
            	Boolean apiContext = null;
            	if (contextCache.get(app.getContext()) != null) {
            		apiContext = Boolean.parseBoolean(contextCache.get(app.getContext()).toString());
            	}
            	if (apiContext == null) {
                    contextCache.put(app.getContext(), true);
                }
            }
        } catch (AppManagementException e) {
            throw new AppManagementException("Error in adding WebApp :"+app.getId().getApiName(),e);
        }
    }

    /**
     * Create a new mobile applcation artifact
     *
     * @param mobileApp Mobile App
     * @throws barley.appmgt.api.AppManagementException
     */
    public String createMobileApp(MobileApp mobileApp) throws AppManagementException {
        String artifactId = null;
        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                    AppMConstants.MOBILE_ASSET_TYPE);
            final String appName = mobileApp.getAppName();

            Map<String, List<String>> attributeListMap = new HashMap<String, List<String>>();
            attributeListMap.put(AppMConstants.API_OVERVIEW_NAME, new ArrayList<String>() {{
                add(appName);
            }});
            GenericArtifact[] existingArtifacts = artifactManager.findGenericArtifacts(attributeListMap);
            if (existingArtifacts != null && existingArtifacts.length > 0) {
                handleResourceAlreadyExistsException("A duplicate mobile application already exists for name : "+
                        mobileApp.getAppName());
            }
            registry.beginTransaction();
            GenericArtifact genericArtifact =
                    artifactManager.newGovernanceArtifact(new QName(mobileApp.getAppName()));
            GenericArtifact artifact = AppManagerUtil.createMobileAppArtifactContent(genericArtifact, mobileApp);
            artifactManager.addGenericArtifact(artifact);
            artifactId = artifact.getId();
            changeLifeCycleStatus(AppMConstants.MOBILE_ASSET_TYPE, artifactId, APPLifecycleActions.CREATE.getStatus());
            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
            Set<String> tagSet = mobileApp.getTags();
            if (tagSet != null) {
                for (String tag : tagSet) {
                    registry.applyTag(artifactPath, tag);
                }
            }

            if(mobileApp.getAppVisibility() != null) {
                AppManagerUtil.setResourcePermissions(mobileApp.getAppProvider(),
                        AppMConstants.API_RESTRICTED_VISIBILITY, mobileApp.getAppVisibility(), artifactPath);
            }
            registry.commitTransaction();
        } catch (RegistryException e) {
            try {
                registry.rollbackTransaction();
            } catch (RegistryException re) {
                handleException(
                        "Error while rolling back the transaction for mobile application: "
                                + mobileApp.getAppName(), re);
            }
            handleException("Error occurred while creating the mobile application : " + mobileApp.getAppName(), e);
        }
        return artifactId;
    }

    @Deprecated
    @Override
    public String createWebApp(WebApp webApp) throws AppManagementException {

        final String appName = webApp.getId().getApiName();
        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                    AppMConstants.API_KEY);
            // (??????) 2018.02.19 ??????????????? ???????????? ???????????? attribute????????? ?????? filter ???????????? ???????????? ??????. 
            /*
            Map<String, List<String>> attributeListMap = new HashMap<String, List<String>>();
            attributeListMap.put(AppMConstants.API_OVERVIEW_NAME, new ArrayList<String>() {{
                add(appName);
            }});
            GenericArtifact[] existingArtifacts = artifactManager.findGenericArtifacts(attributeListMap);
            */
            GenericArtifact[] existingArtifacts = artifactManager.findGenericArtifacts(new GenericArtifactFilter() {
				public boolean matches(GenericArtifact apiArtifact) throws GovernanceException {
					String attributeVal = apiArtifact.getAttribute(AppMConstants.API_OVERVIEW_NAME);
					return (attributeVal != null && attributeVal.equals(appName));
				}
			});	

            if (existingArtifacts != null && existingArtifacts.length > 0) {
                handleResourceAlreadyExistsException("A duplicate webapp already exists with name : " +
                        appName);
            }
        } catch (GovernanceException e) {
            handleException("Error occurred while checking existence for webapp with name '" + appName);
        }
        // registry -> APPMGT ????????????????????? ??????????????? ????????????. 
        AppRepository appRepository = new DefaultAppRepository(registry);
        String appId = appRepository.saveApp(webApp);
        return appId;

    }

    /**
     * Create new version of the application
     * @param app applictaion
     * @return app UUID
     * @throws AppManagementException
     */
    @Deprecated
    @Override
    public String createNewVersion(App app) throws AppManagementException {
        AppRepository appRepository = new DefaultAppRepository(registry);
        String uuid = appRepository.createNewVersion(app);
        return uuid;
    }
    
    // (??????)
    public void createNewVersion(WebApp api, String newVersion) throws DuplicateAPIException, AppManagementException {
    	String apiSourcePath = AppManagerUtil.getAPIPath(api.getId());

        // target ?????? ????????? 
        String targetPath = AppMConstants.API_LOCATION + RegistryConstants.PATH_SEPARATOR +
        		AppManagerUtil.replaceEmailDomain(api.getId().getProviderName()) +
                            RegistryConstants.PATH_SEPARATOR + api.getId().getApiName() +
                            RegistryConstants.PATH_SEPARATOR + newVersion +
                            AppMConstants.API_RESOURCE_NAME;

        boolean transactionCommitted = false;
        try {
            if (registry.resourceExists(targetPath)) {
                throw new DuplicateAPIException("API already exists with version: " + newVersion);
            }
            registry.beginTransaction();
            Resource apiSourceArtifact = registry.get(apiSourcePath);
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, AppMConstants.API_KEY);
            GenericArtifact artifact = artifactManager.getGenericArtifact(apiSourceArtifact.getUUID());

            //Create new API version
            artifact.setId(UUID.randomUUID().toString());
            artifact.setAttribute(AppMConstants.API_OVERVIEW_VERSION, newVersion);

            //Check the status of the existing api,if its not in 'CREATED' status set
            //the new api status as "CREATED"
            String status = artifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);
            if (!AppMConstants.CREATED.equals(status)) {
                artifact.setAttribute(AppMConstants.API_OVERVIEW_STATUS, AppMConstants.CREATED);
            }

            // default version ?????? 
            if(api.isDefaultVersion())  {
                artifact.setAttribute(AppMConstants.APP_OVERVIEW_MAKE_AS_DEFAULT_VERSION, "true");
                //Check whether an existing API is set as default version.
                //String defaultVersion = getDefaultVersion(api.getId());
                String defaultVersion = getDefaultVersion(api.getId().getApiName(), api.getId().getProviderName(), 
                				AppDefaultVersion.APP_IS_ANY_LIFECYCLE_STATE);

                //if so, change its DefaultAPIVersion attribute to false
                if(defaultVersion != null)    {
                	APIIdentifier defaultAPIId = new APIIdentifier(api.getId().getProviderName(), api.getId().getApiName(),
                                                                   defaultVersion);
                	updateDefaultAPIInRegistry(defaultAPIId, false);
                }
            } else  {
                artifact.setAttribute(AppMConstants.APP_OVERVIEW_MAKE_AS_DEFAULT_VERSION, "false");
            }
            
            // ????????? ?????? 
            String thumbUrl = AppMConstants.API_IMAGE_LOCATION + RegistryConstants.PATH_SEPARATOR +
            		AppManagerUtil.replaceEmailDomain(api.getId().getProviderName()) + RegistryConstants.PATH_SEPARATOR +
                              api.getId().getApiName() + RegistryConstants.PATH_SEPARATOR +
                              api.getId().getVersion() + RegistryConstants.PATH_SEPARATOR + AppMConstants.API_ICON_IMAGE;
            if (registry.resourceExists(thumbUrl)) {
                Resource oldImage = registry.get(thumbUrl);
                apiSourceArtifact.getContentStream();
                APIIdentifier newApiId = new APIIdentifier(api.getId().getProviderName(),
                                                           api.getId().getApiName(), newVersion);
                FileContent icon = new FileContent();
                icon.setContent(oldImage.getContentStream());
                icon.setContentType(oldImage.getMediaType());
                artifact.setAttribute(AppMConstants.API_OVERVIEW_THUMBNAIL_URL,
                                      addResourceFile(AppManagerUtil.getIconPath(newApiId), icon));
            }
            
            //String oldContext =  artifact.getAttribute(AppMConstants.API_OVERVIEW_CONTEXT);
            
            // We need to change the context by setting the new version
            // This is a change that is coming with the context version strategy
            String contextTemplate = artifact.getAttribute(AppMConstants.API_OVERVIEW_CONTEXT);
            artifact.setAttribute(AppMConstants.API_OVERVIEW_CONTEXT, contextTemplate.replace("{version}", newVersion));

            artifactManager.addGenericArtifact(artifact);
            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
            
            // ?????????????????? ?????? 
            artifact.attachLifecycle(AppMConstants.WEBAPP_LIFE_CYCLE);
            registry.addAssociation(AppManagerUtil.getAPIProviderPath(api.getId()), targetPath,
            		AppMConstants.PROVIDER_ASSOCIATION);
            // ?????? ?????? 
            String roles=artifact.getAttribute(AppMConstants.API_OVERVIEW_VISIBLE_ROLES);
            String[] rolesSet = new String[0];
            if (roles != null) {
                rolesSet = roles.split(",");
            }
            AppManagerUtil.setResourcePermissions(api.getId().getProviderName(),
            		artifact.getAttribute(AppMConstants.API_OVERVIEW_VISIBILITY), rolesSet, artifactPath);
            //Here we have to set permission specifically to image icon we added
            String iconPath = artifact.getAttribute(AppMConstants.API_OVERVIEW_THUMBNAIL_URL);
            if (iconPath != null && iconPath.lastIndexOf("/appmgt") != -1) {
                iconPath = iconPath.substring(iconPath.lastIndexOf("/appmgt"));
                AppManagerUtil.copyResourcePermissions(api.getId().getProviderName(), thumbUrl, iconPath);
            }
            
            // tag ?????? 
            barley.registry.core.Tag[] tags = registry.getTags(apiSourcePath);
            if (tags != null) {
                for (barley.registry.core.Tag tag : tags) {
                    registry.applyTag(targetPath, tag.getTagName());
                }
            }
            
            // ????????? app ???????????? 
            APIIdentifier newId = new APIIdentifier(api.getId().getProviderName(),
                                                    api.getId().getApiName(), newVersion);
            WebApp newAPI = getAPI(newId, api.getId());

            if(api.isDefaultVersion()){
                newAPI.setDefaultVersion(true);
            }else{
                newAPI.setDefaultVersion(false);
            }
            
            // old artifact ???????????? ?????? 
            GenericArtifact oldArtifact = artifactManager.getGenericArtifact(
                    apiSourceArtifact.getUUID());
            oldArtifact.setAttribute(AppMConstants.API_OVERVIEW_IS_LATEST, "false");
            artifactManager.updateGenericArtifact(oldArtifact);

            String tenantDomain = MultitenantUtils.getTenantDomain(AppManagerUtil.replaceEmailDomainBack(api.getId().getProviderName()));
            try {
                tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(tenantDomain);
            } catch (UserStoreException e) {
                throw new AppManagementException("Error in retrieving Tenant Information while adding api :"
                        +api.getId().getApiName(),e);
            }

            // dao ?????? 
            appMDAO.addWebApp(newAPI);
            
            // ?????? ??????
            copyWebappDocumentations(api, newVersion);
            
            registry.commitTransaction();
            transactionCommitted = true;

            if(log.isDebugEnabled()) {
                String logMessage = "Successfully created new version : " + newVersion + " of : " + api.getId().getApiName();
                log.debug(logMessage);
            }
        } catch (Exception e) {
            try {
                registry.rollbackTransaction();
            } catch (RegistryException re) {
                handleException("Error while rolling back the transaction for API: " + api.getId(), re);
            }
            String msg = "Failed to create new version : " + newVersion + " of : " + api.getId().getApiName();
            handleException(msg, e);
        } finally {
            try {
                if (!transactionCommitted) {
                    registry.rollbackTransaction();
                }
            } catch (RegistryException ex) {
                handleException("Error while rolling back the transaction for API: " + api.getId(), ex);
            }
        }
        
    }
    
    public void updateDefaultAPIInRegistry(APIIdentifier apiIdentifier,boolean value) throws AppManagementException {
        try {

            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                    AppMConstants.API_KEY);
            String defaultAPIPath = AppMConstants.API_LOCATION + RegistryConstants.PATH_SEPARATOR +
            		AppManagerUtil.replaceEmailDomain(apiIdentifier.getProviderName()) +
                    RegistryConstants.PATH_SEPARATOR + apiIdentifier.getApiName() +
                    RegistryConstants.PATH_SEPARATOR + apiIdentifier.getVersion() +
                    AppMConstants.API_RESOURCE_NAME;

            Resource defaultAPISourceArtifact = registry.get(defaultAPIPath);
            GenericArtifact defaultAPIArtifact = artifactManager.getGenericArtifact(
                    defaultAPISourceArtifact.getUUID());
            defaultAPIArtifact.setAttribute(AppMConstants.APP_OVERVIEW_MAKE_AS_DEFAULT_VERSION, String.valueOf(value));
            artifactManager.updateGenericArtifact(defaultAPIArtifact);

        } catch (RegistryException e) {
            String msg = "Failed to update default API version : " + apiIdentifier.getVersion() + " of : "
                    + apiIdentifier.getApiName();
            handleException(msg, e);
        }
    }

    /**
     * Retrieve webapp for the given uuid
     * @param uuid uuid of the Application
     * @return Webapp
     * @throws AppManagementException
     */
    @Override
    public WebApp getWebApp(String uuid) throws AppManagementException {
        GenericArtifact artifact = null;
        WebApp webApp = null;

        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, AppMConstants.API_KEY);
            artifact = artifactManager.getGenericArtifact(uuid);
            if (artifact == null) {
                handleResourceNotFoundException("Webapp does not exist with app id :" + uuid);
            }
            webApp = AppManagerUtil.getAPI(artifact, registry);

        } catch (GovernanceException e) {
            handleException("Error occurred while retrieving webapp registry artifact with uuid " + uuid);
        }
        return webApp;
    }

    /**
     * Retrieve webapp for the given uuid
     * @param uuid uuid of the Application
     * @return Webapp
     * @throws AppManagementException
     */
    @Override
    public MobileApp getMobileApp(String uuid) throws AppManagementException {
        GenericArtifact artifact = null;
        MobileApp mobileApp = null;

        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, AppMConstants.MOBILE_ASSET_TYPE);
            artifact = artifactManager.getGenericArtifact(uuid);
            if (artifact != null) {
                mobileApp = AppManagerUtil.getMobileApp(artifact);
            }

        } catch (GovernanceException e) {
            handleException("Error occurred while retrieving webapp registry artifact with uuid " + uuid);
        }
        return mobileApp;
    }


    private String createWebAppArtifact(WebApp webApp) throws AppManagementException {
        String artifactId = null;

        GenericArtifactManager artifactManager = null;
        try {
            final String webAppName = webApp.getId().getApiName();
            Map<String, List<String>> attributeListMap = new HashMap<String, List<String>>();

            artifactManager = AppManagerUtil.getArtifactManager(registry,
                    AppMConstants.API_KEY);

            attributeListMap.put(AppMConstants.API_OVERVIEW_NAME, new ArrayList<String>() {{
                add(webAppName);
            }});
            GenericArtifact[] existingArtifacts = artifactManager.findGenericArtifacts(attributeListMap);
            if (existingArtifacts != null && existingArtifacts.length > 0) {
                handleResourceAlreadyExistsException("A duplicate web application already exists for name : " +
                        webAppName);
            }
            registry.beginTransaction();
            GenericArtifact genericArtifact =
                    artifactManager.newGovernanceArtifact(new QName(webApp.getId().getApiName()));
            GenericArtifact artifact = AppManagerUtil.createWebAppArtifactContent(genericArtifact, webApp);
            artifactManager.addGenericArtifact(artifact);
            artifactId = artifact.getId();
            changeLifeCycleStatus(AppMConstants.WEBAPP_LIFE_CYCLE, artifactId, APPLifecycleActions.CREATE.getStatus());
            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());

            Set<String> tagSet = webApp.getTags();
            if (tagSet != null) {
                for (String tag : tagSet) {
                    registry.applyTag(artifactPath, tag);
                }
            }
            if (webApp.getAppVisibility() != null) {
                AppManagerUtil.setResourcePermissions(webApp.getId().getProviderName(),
                        AppMConstants.API_RESTRICTED_VISIBILITY, webApp.getAppVisibility(), artifactPath);
            }
            String providerPath = AppManagerUtil.getAPIProviderPath(webApp.getId());
            //provider ------provides----> WebApp
            registry.addAssociation(providerPath, artifactPath, AppMConstants.PROVIDER_ASSOCIATION);
            registry.commitTransaction();
        } catch (RegistryException e) {
            try {
                registry.rollbackTransaction();
            } catch (RegistryException re) {
                handleException(
                        "Error while rolling back the transaction for web application: "
                                + webApp.getId().getApiName(), re);
            }
            handleException("Error occurred while creating the web application : " + webApp.getId().getApiName(), e);
        }
        return artifactId;
    }

    /**
     * Generates entitlement policies for the given app.
     *
     * @param apiIdentifier@throws AppManagementException
     * @param authorizedAdminCookie      Authorized cookie to access IDP admin services
     */
    @Override
    public void generateEntitlementPolicies(APIIdentifier apiIdentifier, String authorizedAdminCookie) throws
                                                                                                 AppManagementException {

        AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();

        List<XACMLPolicyTemplateContext> xacmlPolicyTemplateContexts =
                appMDAO.getEntitlementPolicyTemplateContexts(apiIdentifier);

        if (xacmlPolicyTemplateContexts != null && !xacmlPolicyTemplateContexts.isEmpty()) {
            EntitlementService entitlementService = EntitlementServiceFactory.getEntitlementService(config,
                                                                                                    authorizedAdminCookie);
            
            //  XacmlEntitlementServiceImpl ????????? ?????? 
            entitlementService.generateAndSaveEntitlementPolicies(xacmlPolicyTemplateContexts);

            // Update URL mapping => XACML partial mapping with the generated policy IDs.
            appMDAO.updateURLEntitlementPolicyPartialMappings(xacmlPolicyTemplateContexts);
        }
    }

    /**
     * Updates given entitlement policies.
     *
     * @param policies        Entitlement policies to be updated.
     * @param authorizedAdminCookie Authorized cookie to access IDP admin services
     * @throws barley.appmgt.api.AppManagementException
     */
    @Override
    public void updateEntitlementPolicies(List<EntitlementPolicy> policies,String authorizedAdminCookie) throws
                                                                            AppManagementException {

        if (policies == null || policies.isEmpty()) {
            return;
        }

        AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();
        EntitlementService entitlementService = EntitlementServiceFactory.getEntitlementService(config, authorizedAdminCookie);

        for (EntitlementPolicy policy : policies) {
            entitlementService.updatePolicy(policy);
        }
    }

    /**
     * Get entitlement policy content from policy id
     *
     * @param policyId        Entitlement policy id
     * @param authorizedAdminCookie Authorized cookie to access IDP admin services
     * @return entitlement policy content
     * @throws AppManagementException
     */
    @Override
    public String getEntitlementPolicy(String policyId, String authorizedAdminCookie) throws AppManagementException {
        if (policyId == null) {
            return null;
        }
        AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();

        EntitlementService entitlementService = EntitlementServiceFactory.getEntitlementService(config, authorizedAdminCookie);
        return entitlementService.getPolicyContent(policyId);
    }

    @Override
    public int getWebAppId(String uuid) throws AppManagementException {
        return appMDAO.getWebAppId(uuid);
    }

    @Override
    public int saveEntitlementPolicyPartial(String policyPartialName, String policyPartial, boolean isSharedPartial,
                                            String policyAuthor,String policyPartialDesc) throws AppManagementException {
        return appMDAO.saveEntitlementPolicyPartial(policyPartialName, policyPartial, isSharedPartial, policyAuthor,
                policyPartialDesc, tenantId);
    }

    @Override
    public boolean updateEntitlementPolicyPartial(int policyPartialId, String policyPartial,
                                                  String author, boolean isShared, String policyPartialDesc,
                                                  String authorizedAdminCookie) throws AppManagementException {
        appMDAO.updateEntitlementPolicyPartial(policyPartialId, policyPartial, author, isShared, policyPartialDesc);

        // Regenerate XACML policies of the apps which are using the updated policy partial.
        List<APIIdentifier> associatedApps = getAssociatedApps(policyPartialId);

        for(APIIdentifier associatedApp : associatedApps){
        	generateEntitlementPolicies(associatedApp, authorizedAdminCookie);
        }

        return true;
    }

    @Override
    public EntitlementPolicyPartial getPolicyPartial(int policyPartialId) throws
                                                                          AppManagementException {
        return appMDAO.getPolicyPartial(policyPartialId);
    }

    @Override
    public List<APIIdentifier> getAssociatedApps(int policyPartialId) throws AppManagementException {
        return appMDAO.getAssociatedApps(policyPartialId);
    }

    @Override
    public boolean deleteEntitlementPolicyPartial(int policyPartialId, String author) throws
                                                                                      AppManagementException {
        return appMDAO.deletePolicyPartial(policyPartialId, author);
    }

    @Override
    public List<EntitlementPolicyPartial> getSharedPolicyPartialsList() throws
                                                                        AppManagementException {
        return appMDAO.getSharedEntitlementPolicyPartialsList(tenantId);
    }


    /**
     * Get Policy Groups Application wise
     *
     * @param appId Application Id
     * @return List of policy groups
     * @throws AppManagementException
     */
    @Override
    public List<EntitlementPolicyGroup> getPolicyGroupListByApplication(int appId) throws
            AppManagementException {
        return appMDAO.getPolicyGroupListByApplication(appId);
    }
    
    // (??????) 2020.01.14 
    @Override
    public List<EntitlementPolicyGroup> getPolicyGroupList() throws AppManagementException {
        return appMDAO.getPolicyGroupList();
    }

    /**
     * Retrieves TRACKING_CODE sequences from APM_APP Table
     *@param uuid : Application UUID
     *@return TRACKING_CODE
     *@throws barley.appmgt.api.AppManagementException
     */
    @Override
    public String getTrackingID(String uuid) throws AppManagementException {
        return appMDAO.getTrackingID(uuid);
    }


    @Override
    public EntitlementPolicyValidationResult validateEntitlementPolicyPartial(String policyPartial) throws
                                                                                                    AppManagementException {

        AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();

        EntitlementService entitlementService = EntitlementServiceFactory.getEntitlementService(config);
        return entitlementService.validatePolicyPartial(policyPartial);
    }

    /**
     * Persist WebApp Status into a property of WebApp Registry resource
     *
     * @param artifactId WebApp artifact ID
     * @param apiStatus Current status of the WebApp
     * @throws barley.appmgt.api.AppManagementException on error
     */
    private void saveAPIStatus(String artifactId, String apiStatus) throws AppManagementException {
        try{
            Resource resource = registry.get(artifactId);
            if (resource != null) {
                String propValue = resource.getProperty(AppMConstants.API_STATUS);
                if (propValue == null) {
                    resource.addProperty(AppMConstants.API_STATUS, apiStatus);
                } else {
                    resource.setProperty(AppMConstants.API_STATUS, apiStatus);
                }
                registry.put(artifactId,resource);
            }
        }catch (RegistryException e) {
            handleException("Error while adding WebApp", e);
        }
    }
    
    
    /**
     * Updates an existing WebApp
     *
     * @param api             WebApp
     * @throws AppManagementException if failed to update WebApp
     * @throws FaultGatewaysException 
     */
    public void updateAPI(WebApp api) throws AppManagementException, FaultGatewaysException {
    	updateAPI(api, null);
    }

    /**
     * Updates an existing WebApp
     *
     * @param api             WebApp
     * @param authorizedAdminCookie Authorized cookie to access IDP admin services
     * @throws AppManagementException if failed to update WebApp
     * @throws FaultGatewaysException 
     */
    public void updateAPI(WebApp api, String authorizedAdminCookie) throws AppManagementException, FaultGatewaysException {
        WebApp oldApi = getAPI(api.getId());
        if (oldApi.getStatus().equals(api.getStatus())) {
            try {

            	// (????????????) 2018.03.12 - ??????????????? ???????????? ????????? ????????????. registry ???????????? ???????????? ?????? ????????? ???????????? ???. 
                boolean updatePermissions = false;
                /* (????????????)
                if(!oldApi.getVisibility().equals(api.getVisibility()) || (oldApi.getVisibility().equals(AppMConstants.API_RESTRICTED_VISIBILITY) && !api.getVisibleRoles().equals(oldApi.getVisibleRoles()))){
                    updatePermissions = true;
                }
                */
                
                // 1. gateway ??????  
                AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                        getAPIManagerConfigurationService().getAPIManagerConfiguration();
                boolean gatewayExists = config.getApiGatewayEnvironments().size() > 0;
                String gatewayType = config.getFirstProperty(AppMConstants.API_GATEWAY_TYPE);
                boolean isAPIPublished = false;

                if (AppMConstants.API_GATEWAY_TYPE_SYNAPSE.equalsIgnoreCase(gatewayType)) {
                	if(gatewayExists) {
                		isAPIPublished = isAPIPublished(api);
	                    if (isAPIPublished) {
	                        WebApp apiPublished = getAPI(api.getId());
	                        apiPublished.setOldInSequence(oldApi.getInSequence());
	                        apiPublished.setOldOutSequence(oldApi.getOutSequence());
	
	                        //update version
	                        if (api.isDefaultVersion() || oldApi.isDefaultVersion()) {
	                            //remove both versioned/non versioned apis
	                            WebApp webApp = new WebApp(api.getId());
	                            webApp.setDefaultVersion(true);
	                            removeFromGateway(webApp);
	                        }
	
	                        //publish to gateway if skipGateway is disabled only
	                        if (!api.getSkipGateway()) {
	                            publishToGateway(apiPublished);
	                        }
	                    }
	                } else {
	                    log.debug("Gateway is not existed for the current WebApp Provider");
	                }
                }

                // 2. registry ?????? 
                updateApiArtifact(api, true, updatePermissions);
            	
                if (!oldApi.getContext().equals(api.getContext())) {
                    api.setApiHeaderChanged(true);
                }

                // 3-1. dao ?????? 
                appMDAO.updateWebApp(api, authorizedAdminCookie);
                
                // 3-2. ?????? ??????
                addTags(api.getId(), api.getTags());

               
                /*Boolean gatewayKeyCacheEnabled=false;
                String gatewayKeyCacheEnabledString = config.getFirstProperty(AppMConstants.API_GATEWAY_KEY_CACHE_ENABLED);
                //If gateway key cache enabled
                if (gatewayKeyCacheEnabledString != null) {
                    gatewayKeyCacheEnabled = Boolean.parseBoolean(gatewayKeyCacheEnabledString);
                }
                //If resource paths being saved are on permission cache, remove them.
                if (gatewayExists && gatewayKeyCacheEnabled) {
                    if (isAPIPublished && !oldApi.getUriTemplates().equals(api.getUriTemplates())) {
                        Set<URITemplate> resourceVerbs = api.getUriTemplates();

                        List<Environment> gatewayEnvs = config.getApiGatewayEnvironments();
                        for(Environment environment : gatewayEnvs){
                            APIAuthenticationAdminClient client =
                                    new APIAuthenticationAdminClient(environment);
                            if(resourceVerbs != null){
                                for(URITemplate resourceVerb : resourceVerbs){
                                    String resourceURLContext = resourceVerb.getUriTemplate();
                                    //If url context ends with the '*' character.
                                    if(resourceURLContext.endsWith("*")){
                                        //Remove the ending '*'
                                        resourceURLContext = resourceURLContext.substring(0, resourceURLContext.length() - 1);
                                    }
                                    client.invalidateResourceCache(api.getContext(),api.getId().getVersion(),resourceURLContext,resourceVerb.getHTTPVerb());
                                    if (log.isDebugEnabled()) {
                                        log.debug("Calling invalidation cache");
                                    }
                                }
                            }
                        }

                    }
                }*/
                /* Update WebApp Definition for Swagger
                createUpdateAPIDefinition(api);*/

                //update apiContext cache
                if (AppManagerUtil.isAPIManagementEnabled()) {
                    Cache contextCache = AppManagerUtil.getAPIContextCache();
                    contextCache.remove(oldApi.getContext());
                    contextCache.put(api.getContext(), true);
                }
                
            } catch (AppManagementException e) {
            	handleException("Error while updating the WebApp :" +api.getId().getApiName(),e);
            }

        } else {
            // We don't allow WebApp status updates via this method.
            // Use changeAPIStatus for that kind of updates.
            throw new AppManagementException("Invalid WebApp update operation involving WebApp status changes");
        }
    }

    @Override
    public void updateApp(App app) throws AppManagementException {
        AppRepository appRepository = new DefaultAppRepository(registry);
        appRepository.updateApp(app);
    }

    /**
     * Updates an existing WebApp
     *
     * @param api WebApp
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to update WebApp
     */
    public void updateMobileApp(MobileApp mobileApp) throws AppManagementException {


            try {

                updateMobileAppArtifact(mobileApp, true);

            } catch (AppManagementException e) {
                handleException("Error while updating the WebApp :" +mobileApp.getAppName(),e);
            }


    }




    private void updateApiArtifact(WebApp api, boolean updateMetadata, boolean updatePermissions) throws
                                                                                                 AppManagementException {

        //Validate Transports
        validateAndSetTransports(api);
        boolean transactionCommitted = false;
        try {
        	registry.beginTransaction();
            String apiArtifactId = registry.get(AppManagerUtil.getAPIPath(api.getId())).getUUID();
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                                AppMConstants.API_KEY);
            GenericArtifact artifact = artifactManager.getGenericArtifact(apiArtifactId);
            GenericArtifact updateApiArtifact = AppManagerUtil.createAPIArtifactContent(artifact, api);
            String artifactPath = GovernanceUtils.getArtifactPath(registry, updateApiArtifact.getId());
            barley.registry.core.Tag[] oldTags = registry.getTags(artifactPath);
            if (oldTags != null) {
                for (barley.registry.core.Tag tag : oldTags) {
                    registry.removeTag(artifactPath, tag.getTagName());
                }
            }

            Set<String> tagSet = api.getTags();
            if (tagSet != null) {
                for (String tag : tagSet) {
                    registry.applyTag(artifactPath, tag);
                }
            }


            if (updateMetadata) {

                if (api.getWsdlUrl() != null && !"".equals(api.getWsdlUrl())) {
                    String path = AppManagerUtil.createWSDL(registry, api);
                    if (path != null) {
                        registry.addAssociation(artifactPath, path, CommonConstants.ASSOCIATION_TYPE01);
                        updateApiArtifact.setAttribute(AppMConstants.API_OVERVIEW_WSDL, api.getWsdlUrl()); //reset the wsdl path to permlink
                    }
                }

                if (api.getUrl() != null && !"".equals(api.getUrl())){
                    String path = AppManagerUtil.createEndpoint(api.getUrl(), registry);
                    if (path != null) {
                        registry.addAssociation(artifactPath, path, CommonConstants.ASSOCIATION_TYPE01);
                    }
                }
            }

            artifactManager.updateGenericArtifact(updateApiArtifact);

            //write WebApp Status to a separate property. This is done to support querying APIs using custom query (SQL)
            //to gain performance
            String apiStatus = api.getStatus().getStatus();
            saveAPIStatus(artifactPath, apiStatus);
            if(updatePermissions){
                clearResourcePermissions(artifactPath, api.getId());
                String visibleRolesList = api.getVisibleRoles();
                String[] visibleRoles = new String[0];
                if (visibleRolesList != null) {
                    visibleRoles = visibleRolesList.split(",");
                }
                AppManagerUtil.setResourcePermissions(api.getId().getProviderName(), api.getVisibility(), visibleRoles, artifactPath);
            }
            registry.commitTransaction();
            transactionCommitted = true;
            
        } catch (Exception e) {
        	 try {
                 registry.rollbackTransaction();
             } catch (RegistryException re) {
                 handleException("Error while rolling back the transaction for WebApp: " +
                                 api.getId().getApiName(), re);
             }
             handleException("Error while performing registry transaction operation", e);

        } finally {
            try {
                if (!transactionCommitted) {
                    registry.rollbackTransaction();
                }
            } catch (RegistryException ex) {
                handleException("Error occurred while rolling back the transaction.", ex);
            }
        }
    }

    private void updateMobileAppArtifact(MobileApp mobileApp, boolean updatePermissions) throws
            AppManagementException {


        try {
            registry.beginTransaction();
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                    AppMConstants.MOBILE_ASSET_TYPE);
            GenericArtifact artifact = artifactManager.getGenericArtifact(mobileApp.getAppId());
            if (artifact != null) {

                GenericArtifact updateApiArtifact = AppManagerUtil.createMobileAppArtifactContent(artifact, mobileApp);
                String artifactPath = GovernanceUtils.getArtifactPath(registry, updateApiArtifact.getId());
                artifactManager.updateGenericArtifact(updateApiArtifact);
            }else{
                handleResourceNotFoundException(
                        "Failed to get Mobile App. The artifact corresponding to artifactId " + mobileApp.getAppId() + " does not exist");
            }
//            org.wso2.carbon.registry.core.Tag[] oldTags = registry.getTags(artifactPath);
//            if (oldTags != null) {
//                for (org.wso2.carbon.registry.core.Tag tag : oldTags) {
//                    registry.removeTag(artifactPath, tag.getTagName());
//                }
//            }

//            Set<String> tagSet = api.getTags();
//            if (tagSet != null) {
//                for (String tag : tagSet) {
//                    registry.applyTag(artifactPath, tag);
//                }
//            }




            registry.commitTransaction();
        } catch (Exception e) {
            try {
                registry.rollbackTransaction();
            } catch (RegistryException re) {
                handleException("Error while rolling back the transaction for WebApp: " +mobileApp.getAppName(), re);
            }
            handleException("Error while performing registry transaction operation", e);

        }
    }

    /**
     * Create WebApp Definition in JSON and save in the registry
     *
     * @param api WebApp
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to generate the content and save
     */
    private void createUpdateAPIDefinition(WebApp api) throws AppManagementException {
    	APIIdentifier identifier = api.getId();

    	try{
    		String jsonText = AppManagerUtil.createSwaggerJSONContent(api);

    		String resourcePath = AppManagerUtil.getAPIDefinitionFilePath(identifier.getApiName(), identifier.getVersion());

    		Resource resource = registry.newResource();

    		resource.setContent(jsonText);
    		resource.setMediaType("application/json");
    		registry.put(resourcePath, resource);

    		/*Set permissions to anonymous role */
    		AppManagerUtil.setResourcePermissions(api.getId().getProviderName(), null, null, resourcePath);

    	} catch (RegistryException e) {
    		handleException("Error while adding WebApp Definition for " + identifier.getApiName() + "-" + identifier.getVersion(), e);
		} catch (AppManagementException e) {
			handleException("Error while adding WebApp Definition for " + identifier.getApiName() + "-" + identifier.getVersion(), e);
		}
    }


    @Override
    public void changeAPIStatus(WebApp api, APIStatus status, String userId,
                                boolean updateGatewayConfig) throws AppManagementException, FaultGatewaysException {
        APIStatus currentStatus = api.getStatus();
        if (!currentStatus.equals(status)) {
            api.setStatus(status);
            try {
            	// 1. gateway ??????  
                AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                        getAPIManagerConfigurationService().getAPIManagerConfiguration();
                String gatewayType = config.getFirstProperty(AppMConstants.API_GATEWAY_TYPE);
                if (!api.isAdvertiseOnly()) { // no need to publish to gateway if webb is only for advertising
                	// (??????) 2019.06.10 - ??????????????? ?????? ?????? 
                    //if (updateGatewayConfig) {
                	if (AppMConstants.API_GATEWAY_TYPE_SYNAPSE.equalsIgnoreCase(gatewayType) && updateGatewayConfig) {

                        if (api.isDefaultVersion()) {
                            if (status.equals(APIStatus.UNPUBLISHED)) {
                                //when un-publishing default version, reset the default published version as null in table
                                APIIdentifier identifier = new APIIdentifier(api.getId().getProviderName(),
                                                                             api.getId().getApiName(),
                                                                             null);
                                WebApp webApp = new WebApp(identifier);
                                appMDAO.updatePublishedDefaultVersion(webApp);
                            }
                        }

                        if (status.equals(APIStatus.PUBLISHED) || status.equals(APIStatus.DEPRECATED) ||
                                status.equals(APIStatus.BLOCKED)) {

                            //update version
                            if (status.equals(APIStatus.PUBLISHED)) {
                                if (api.isDefaultVersion()) {
                                    appMDAO.updateDefaultVersionDetails(api);
                                }
                            }

                            //publish to gateway if skipGateway is disabled only
                            if (!api.getSkipGateway()) {
                                publishToGateway(api);
                            }
                        } else if(status.equals(APIStatus.UNPUBLISHED) || status.equals(APIStatus.RETIRED)) {
                            removeFromGateway(api);
                        }
                    }
                }
            	
            	// 2. registry ?????? 
            	// (????????????) 2018.03.13 - ??? ????????? ??????????????? ????????? ??? ??? ??????. ?????? ????????? ????????? ??????.
            	updateApiArtifact(api, false, false);
            	// 3. dao ?????? 
            	appMDAO.recordAPILifeCycleEvent(api.getId(), currentStatus, status, userId);
            	
            	APIStatusObserverList observerList = APIStatusObserverList.getInstance();
                observerList.notifyObservers(currentStatus, status, api);
                
            } catch (AppManagementException e) {
            	handleException("Error occured in the status change : " + api.getId().getApiName() , e);
            }
        }
    }

    public void updateWebAppSynapse(WebApp api) throws AppManagementException {
       try {
    	   removeFromGateway(api);
		} catch (FaultGatewaysException e) {
			handleException("Error while Updating Synapse to Gateway ", e);
		}
    }

    private void publishToGateway(WebApp api) throws FaultGatewaysException, AppManagementException {
        APITemplateBuilder builder = null;
        String tenantDomain = null;
//        if (api.getId().getProviderName().contains("AT")) {
            String provider = api.getId().getProviderName().replace("-AT-", "@");
            tenantDomain = MultitenantUtils.getTenantDomain( provider);
//        }

        try{
            builder = getAPITemplateBuilder(api);
        }catch(Exception e){
        	// (??????) 2019.09.27 - ??????????????? ????????? ????????? ????????????. 
            handleException("Error while publishing to Gateway ", e);
        }


        APIGatewayManager gatewayManager = APIGatewayManager.getInstance();
        try {
            gatewayManager.publishToGateway(api, builder, tenantDomain);
        } catch (AppManagementException e) {
            // (??????) 2019.09.27 - ??????????????? ????????? ????????? ????????????. 
        	// handleException("Error while publishing to Gateway ", e);
            throw new FaultGatewaysException("Error while publishing to Gateway ", e);
        }
    }

    private void validateAndSetTransports(WebApp api) throws AppManagementException {
        String transports = api.getTransports();
        if(transports != null && !("null".equalsIgnoreCase(transports))){
            if (transports.contains(",")) {
                StringTokenizer st = new StringTokenizer(transports, ",");
                while (st.hasMoreTokens()) {
                    checkIfValidTransport(st.nextToken());
                }
            }else{
                checkIfValidTransport(transports);
            }
        }else{
            api.setTransports(Constants.TRANSPORT_HTTP+","+Constants.TRANSPORT_HTTPS);
            return;
        }
    }

    private void checkIfValidTransport(String transport) throws AppManagementException {
        if(!Constants.TRANSPORT_HTTP.equalsIgnoreCase(transport) && !Constants.TRANSPORT_HTTPS.equalsIgnoreCase(transport)){
            handleException("Unsupported Transport [" + transport + "]");
        }
    }

    private void removeFromGateway(WebApp api) throws FaultGatewaysException {
        String tenantDomain = null;
        if (api.getId().getProviderName().contains("@")) {
            tenantDomain = MultitenantUtils.getTenantDomain( api.getId().getProviderName());
        }

        APIGatewayManager gatewayManager = APIGatewayManager.getInstance();
        try {
            gatewayManager.removeFromGateway(api, tenantDomain);
        } catch (AppManagementException e) {
        	// (??????) 2019.09.27 - ??????????????? ????????? ????????? ????????????. 
            // handleException("Error while removing WebApp from Gateway ", e);
            throw new FaultGatewaysException("Error while removing WebApp from Gateway ", e);
        }
    }

    private boolean isAPIPublished(WebApp api) throws AppManagementException {
    	String tenantDomain = null;
		if (api.getId().getProviderName().contains("AT")) {
			String provider = api.getId().getProviderName().replace("-AT-", "@");
			tenantDomain = MultitenantUtils.getTenantDomain( provider);
		}
        APIGatewayManager gatewayManager = APIGatewayManager.getInstance();
        return gatewayManager.isAPIPublished(api, tenantDomain);
    }

    /**
     * This method dynamically returns the mandatory and selected java policy handlers list for given app
     *
     * @param api :WebApp class which contains details about web applications
     * @return :handlers list with properties to be applied
     * @throws AppManagementException on error
     */
    private APITemplateBuilder getAPITemplateBuilder(WebApp api) throws AppManagementException {
        APITemplateBuilderImpl velocityTemplateBuilder = new APITemplateBuilderImpl(api);

        //List of JavaPolicy class which contains policy related details
        List<JavaPolicy> policies = new ArrayList<JavaPolicy>();
        //contains properties related to relevant policy and will be used to generate the synapse api config file
        Map<String, String> properties;
        int counterPolicies; //counter :policies

        try {
            //fetch all the java policy handlers details which need to be included to synapse api config file
        	// DB?????? ????????? ????????? ???????????? ????????????. 
            policies = appMDAO.getMappedJavaPolicyList(api.getUUID(),true);
            //loop through each policy
            for (counterPolicies = 0; counterPolicies < policies.size(); counterPolicies++) {
                if (policies.get(counterPolicies).getProperties() == null) {
                    //if policy doesn't contain any properties assign an empty map and add java policy as a handler
                    velocityTemplateBuilder.addHandler(policies.get(counterPolicies).getFullQualifiName(),
                            Collections.EMPTY_MAP);
                } else {
                    //contains properties related to all the policies
                    JSONObject objPolicyProperties;
                    properties = new HashMap<String, String>();

                    //get property JSON object related to current policy in the loop
                    objPolicyProperties = policies.get(counterPolicies).getProperties();

                    //if policy contains any properties, run a loop and assign them
                    Set<String> keys = objPolicyProperties.keySet();
                    for (String key : keys) {
                        properties.put(key, objPolicyProperties.get(key).toString());
                    }
                    //add policy as a handler and also the relevant properties
                    velocityTemplateBuilder.addHandler(policies.get(counterPolicies).getFullQualifiName(), properties);
                }
            }

        } catch (AppManagementException e) {
            handleException("Error occurred while adding java policy handlers to Application : " +
                    api.getId().toString(), e);
        }
        return velocityTemplateBuilder;
    }

    /**
     * @param webapp     origin web application
     * @param newVersion The version of the new WebApp
     * @throws AppManagementException
     */
    @Override
    public void copyWebappDocumentations(WebApp webapp, String newVersion) throws AppManagementException {

        try {

            // Retain the docs
            List<Documentation> docs = getAllDocumentation(webapp.getId());
            APIIdentifier newId = new APIIdentifier(webapp.getId().getProviderName(),
                    webapp.getId().getApiName(), newVersion);
            WebApp newAPI = getAPI(newId, webapp.getId());

            if (log.isDebugEnabled()) {
                log.debug("Copying documenatation of the web application - " + webapp.getApiName() +
                        "with the new version - " + newVersion);
            }

            for (Documentation doc : docs) {

			    /* copying the file in registry for new api */
                Documentation.DocumentSourceType sourceType = doc.getSourceType();
                if (sourceType == Documentation.DocumentSourceType.FILE) {
                    String absoluteSourceFilePath = doc.getFilePath();
                    // extract the prepend
                    // ->/registry/resource/_system/governance/ and for
                    // tenant
                    // /t/my.com/registry/resource/_system/governance/
                    int prependIndex =
                            absoluteSourceFilePath.indexOf(AppMConstants.API_LOCATION);
                    String prependPath = absoluteSourceFilePath.substring(0, prependIndex);
                    // get the file name from absolute file path
                    int fileNameIndex =
                            absoluteSourceFilePath.lastIndexOf(RegistryConstants.PATH_SEPARATOR);
                    String fileName = absoluteSourceFilePath.substring(fileNameIndex + 1);
                    // create relative file path of old location
                    String sourceFilePath = absoluteSourceFilePath.substring(prependIndex);
                    // create the relative file path where file should be
                    // copied
                    String targetFilePath =
                            AppMConstants.API_LOCATION +
                                    RegistryConstants.PATH_SEPARATOR +
                                    // (??????) ???????????? 
//                                    newId.getProviderName() +
                                    AppManagerUtil.replaceEmailDomain(newId.getProviderName()) +
                                    RegistryConstants.PATH_SEPARATOR +
                                    newId.getApiName() +
                                    RegistryConstants.PATH_SEPARATOR +
                                    newId.getVersion() +
                                    RegistryConstants.PATH_SEPARATOR +
                                    AppMConstants.DOC_DIR +
                                    RegistryConstants.PATH_SEPARATOR +
                                    AppMConstants.DOCUMENT_FILE_DIR +
                                    RegistryConstants.PATH_SEPARATOR + fileName;
                    // copy the file from old location to new location(for
                    // new api)

                    registry.copy(sourceFilePath, targetFilePath);

                    // update the filepath attribute in doc artifact to
                    // create new doc artifact for new version of api
                    doc.setFilePath(prependPath + targetFilePath);
                }

                createDocumentation(newAPI.getId(), doc);
                String content = getDocumentationContent(webapp.getId(), doc.getName());
                if (content != null) {
                    addDocumentationContent(newAPI.getId(), doc.getName(), content);
                }
            }
        } catch (RegistryException e) {
            handleException("Error occurred while copying web application : " + webapp.getApiName());
        }
    }

    /**
     * Removes a given documentation
     * @param apiId   APIIdentifier
     * @param docName name of the document
     * @param docType the type of the documentation
     * @throws AppManagementException
     */
    @Override
    public void removeDocumentation(APIIdentifier apiId, String docName, String docType)
            throws AppManagementException {
        String docPath = AppManagerUtil.getAPIDocPath(apiId) + docName;

        try {
        	// (??????) 2019.10.17 - filePath??? ????????? ???????????? ????????? ??????????????? ???????????? ?????????. 
            /*
            String apiArtifactId = registry.get(docPath).getUUID();
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                                AppMConstants.DOCUMENTATION_KEY);
            GenericArtifact artifact = artifactManager.getGenericArtifact(apiArtifactId);
            String docFilePath =  artifact.getAttribute(AppMConstants.DOC_FILE_PATH);
			*/
        	String docFilePath =  AppManagerUtil.getAPIDocPath(apiId) + AppMConstants.DOCUMENT_FILE_DIR;
        	// ?????? ?????? 
            if(docFilePath!=null)
            {
            	/*
                File tempFile = new File(docFilePath);
                String fileName = tempFile.getName();
                docFilePath = AppManagerUtil.getDocumentationFilePath(apiId,fileName);
                */
                if(registry.resourceExists(docFilePath))
                {
                    registry.delete(docFilePath);
                }
            }
            
            // (??????) contents ??????
            String contentPath = AppManagerUtil.getAPIDocPath(apiId) + AppMConstants.INLINE_DOCUMENT_CONTENT_DIR;
            if(contentPath != null)   {
                if(registry.resourceExists(contentPath))    {
                    registry.delete(contentPath);
                }
            }

            Association[] associations = registry.getAssociations(docPath,
                                                                  AppMConstants.DOCUMENTATION_KEY);
            for (Association association : associations) {
                registry.delete(association.getDestinationPath());
            }
            //String docContentPath = AppManagerUtil.getAPIDocContentPath(apiId, docName);

            //Remove Inline-documentation contents
            //if (registry.resourceExists(docContentPath)) {
            //    registry.delete(docContentPath);
            //}

        } catch (RegistryException e) {
            handleException("Failed to delete documentation", e);
        }
    }

    /**
     *
     * @param apiId   APIIdentifier
     * @param docId UUID of the doc
     * @throws AppManagementException if failed to remove documentation
     */
    public void removeDocumentation(APIIdentifier apiId, String docId)
            throws AppManagementException {
        String docPath ;

        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                    AppMConstants.DOCUMENTATION_KEY);
            GenericArtifact artifact = artifactManager.getGenericArtifact(docId);
            docPath = artifact.getPath();
            String docFilePath =  artifact.getAttribute(AppMConstants.DOC_FILE_PATH);

            if(docFilePath!=null)
            {
                File tempFile = new File(docFilePath);
                String fileName = tempFile.getName();
                docFilePath = AppManagerUtil.getDocumentationFilePath(apiId,fileName);
                if(registry.resourceExists(docFilePath))
                {
                    registry.delete(docFilePath);
                }
            }

            Association[] associations = registry.getAssociations(docPath,
                    AppMConstants.DOCUMENTATION_KEY);

            for (Association association : associations) {
                registry.delete(association.getDestinationPath());
            }
        } catch (RegistryException e) {
            handleException("Failed to delete documentation", e);
        }
    }

    /**
     * Adds Documentation to an WebApp
     *
     * @param apiId         APIIdentifier
     * @param documentation Documentation
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to add documentation
     */
    @Override
    public void addDocumentation(APIIdentifier apiId, Documentation documentation)
            throws AppManagementException {
        createDocumentation(apiId, documentation);
    }

    /**
     * This method used to save the documentation content
     *
     * @param identifier,        WebApp identifier
     * @param documentationName, name of the inline documentation
     * @param text,              content of the inline documentation
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to add the document as a resource to registry
     */
    public void addDocumentationContent(APIIdentifier identifier, String documentationName, String text)
            throws AppManagementException {

        String documentationPath = AppManagerUtil.getAPIDocPath(identifier) + documentationName;
        String contentPath = AppManagerUtil.getAPIDocPath(identifier) + AppMConstants.INLINE_DOCUMENT_CONTENT_DIR +
                             RegistryConstants.PATH_SEPARATOR + documentationName;
        try {
            Resource docContent;
            if (!registry.resourceExists(contentPath)) {
            	docContent = registry.newResource();
            } else {
            	docContent = registry.get(contentPath);
            }

            /* This is a temporary fix for doc content replace issue. We need to add
             * separate methods to add inline content resource in document update */
            if (!AppMConstants.NO_CONTENT_UPDATE.equals(text)) {
            	docContent.setContent(text);
            }

            docContent.setMediaType(AppMConstants.DOCUMENTATION_INLINE_CONTENT_TYPE);
            registry.put(contentPath, docContent);
            registry.addAssociation(documentationPath, contentPath,
                                    AppMConstants.DOCUMENTATION_CONTENT_ASSOCIATION);
            String[] authorizedRoles = getAuthorizedRoles(documentationPath);
            String apiPath = AppManagerUtil.getAPIPath(identifier);
            AppManagerUtil.setResourcePermissions(getAPI(apiPath).getId().getProviderName(),
            		getAPI(apiPath).getVisibility(),authorizedRoles,contentPath);
        } catch (RegistryException e) {
            String msg = "Failed to add the documentation content of : "
                         + documentationName + " of WebApp :" + identifier.getApiName();
            handleException(msg, e);
        } catch (UserStoreException e) {
            String msg = "Failed to add the documentation content of : "
                         + documentationName + " of WebApp :" + identifier.getApiName();
            handleException(msg, e);
        }
    }

    /**
     * Add a file to a document of source type FILE
     *
     * @param webApp
     * @param documentation document
     * @param filename name of the file
     * @param content content of the file as an Input Stream
     * @param contentType content type of the file
     * @throws AppManagementException if failed to add the file
     */
    public void addFileToDocumentation(APIIdentifier apiId, Documentation documentation, String filename,
                                       InputStream content, String contentType) throws AppManagementException {
        if (Documentation.DocumentSourceType.FILE.equals(documentation.getSourceType())) {
            FileContent documentContent = new FileContent();
            documentContent.setContent(content);
            documentContent.setContentType(contentType);

            String filePath = AppManagerUtil.getDocumentationFilePath(apiId, filename);
            WebApp webApp;
            try {
            	webApp = getAPI(apiId);
                String visibleRolesList = webApp.getVisibleRoles();
                String[] visibleRoles = new String[0];
                if (visibleRolesList != null) {
                    visibleRoles = visibleRolesList.split(",");
                }
                AppManagerUtil.setResourcePermissions(webApp.getId().getProviderName(), webApp.getVisibility(), visibleRoles,
                        filePath);
                documentation.setFilePath(addResourceFile(filePath, documentContent));
                AppManagerUtil.setFilePermission(filePath);
            } catch (AppManagementException e) {
                handleException("Failed to add file to document " + documentation.getName(), e);
            }
        } else {
            String errorMsg = "Cannot add file to the Document. Document " + documentation.getName()
                    + "'s Source type is not FILE.";
            handleException(errorMsg);
        }
    }

    
    // (??????) 2019.06.04 
    public void removeFileFromDocumentation(APIIdentifier apiId, DocumentSourceType docSourceType, String filename) 
    		throws AppManagementException {
        if (Documentation.DocumentSourceType.FILE.equals(docSourceType)) {
	        try {
	        	String docFilePath = AppManagerUtil.getDocumentationFilePath(apiId, filename);	        	
	        	if(registry.resourceExists(docFilePath)) {
                    registry.delete(docFilePath);
                }
	        } catch (RegistryException e) {
	            handleException("Failed to remove documentation file", e);
	        }
    	}
    }


    /**
     * This method used to update the WebApp definition content - Swagger
     *
     * @param identifier,        WebApp identifier
     * @param documentationName, name of the inline documentation
     * @param text,              content of the inline documentation
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to add the document as a resource to registry
     */
    public void addAPIDefinitionContent(APIIdentifier identifier, String documentationName, String text)
    					throws AppManagementException {
    	// api-doc.json????????? ?????????. documentationName ??????????????? ???????????? ?????????.
    	String contentPath = AppManagerUtil.getAPIDefinitionFilePath(identifier.getApiName(), identifier.getVersion());

    	try {
            Resource docContent = registry.newResource();
            docContent.setContent(text);
            docContent.setMediaType("text/plain");
            registry.put(contentPath, docContent);

            String apiPath = AppManagerUtil.getAPIPath(identifier);
            WebApp api = getAPI(apiPath);
            String visibleRolesList = api.getVisibleRoles();
            String[] visibleRoles = new String[0];
            if (visibleRolesList != null) {
                visibleRoles = visibleRolesList.split(",");
            }
    		AppManagerUtil.setResourcePermissions(api.getId().getProviderName(), api.getVisibility(), visibleRoles, contentPath);
    	} catch (RegistryException e) {
            String msg = "Failed to add the WebApp Definition content of : "
                         + documentationName + " of WebApp :" + identifier.getApiName();
            handleException(msg, e);
        }
    }

    /**
     * Updates a given documentation
     *
     * @param apiId         APIIdentifier
     * @param documentation Documentation
     * @throws barley.appmgt.api.AppManagementException
     *          if failed to update docs
     */
    public void updateDocumentation(APIIdentifier apiId, Documentation documentation)
            throws AppManagementException {

    	// (??????) 2018.03.12 - ???????????? 
        String docPath = AppMConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
//                         apiId.getProviderName() + 
        			 	 AppManagerUtil.replaceEmailDomain(apiId.getProviderName()) +
                         RegistryConstants.PATH_SEPARATOR + apiId.getApiName() +
                         RegistryConstants.PATH_SEPARATOR + apiId.getVersion() + RegistryConstants.PATH_SEPARATOR +
                         AppMConstants.DOC_DIR + RegistryConstants.PATH_SEPARATOR + documentation.getName();
        try {
            String apiArtifactId = registry.get(docPath).getUUID();
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                                AppMConstants.DOCUMENTATION_KEY);
            GenericArtifact artifact = artifactManager.getGenericArtifact(apiArtifactId);
            String apiPath = AppManagerUtil.getAPIPath(apiId);
            GenericArtifact updateApiArtifact = AppManagerUtil.createDocArtifactContent(artifact, apiId, documentation);
            artifactManager.updateGenericArtifact(updateApiArtifact);
            clearResourcePermissions(docPath, apiId);

            WebApp api=getAPI(apiPath);
            String visibleRolesList = api.getVisibleRoles();
            String[] visibleRoles = new String[0];
            if (visibleRolesList != null) {
                visibleRoles = visibleRolesList.split(",");
            }

            AppManagerUtil.setResourcePermissions(api.getId().getProviderName(), api.getVisibility(),visibleRoles,artifact.getPath());

            String docFilePath = artifact.getAttribute(AppMConstants.DOC_FILE_PATH);
            if(docFilePath != null && !docFilePath.equals("")) {
                //The docFilePatch comes as /t/tenanatdoman/registry/resource/_system/governance/apimgt/applicationdata..
                //We need to remove the /t/tenanatdoman/registry/resource/_system/governance section to set permissions.
                int startIndex = docFilePath.indexOf("governance") + "governance".length();
                String filePath = docFilePath.substring(startIndex, docFilePath.length());
                AppManagerUtil.setResourcePermissions(getAPI(apiPath).getId().getProviderName(),
                        getAPI(apiPath).getVisibility(), visibleRoles, filePath);
            }

        } catch (RegistryException e) {
            handleException("Failed to update documentation", e);
        }

    }

    /**
     * Copies current Documentation into another version of the same WebApp.
     *
     * @param toVersion Version to which Documentation should be copied.
     * @param apiId     id of the APIIdentifier
     * @throws barley.apimgt.api.APIManagementException
     *          if failed to copy docs
     */
    public void copyAllDocumentation(APIIdentifier apiId, String toVersion)
            throws AppManagementException {

        String oldVersion = AppManagerUtil.getAPIDocPath(apiId);
        String newVersion = AppMConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
        					// (??????) ???????????? 
//                            apiId.getProviderName() +
        					AppManagerUtil.replaceEmailDomain(apiId.getProviderName()) +
                            RegistryConstants.PATH_SEPARATOR + apiId.getApiName() +
                            RegistryConstants.PATH_SEPARATOR + toVersion + RegistryConstants.PATH_SEPARATOR +
                            AppMConstants.DOC_DIR;

        try {
            Resource resource = registry.get(oldVersion);
            if (resource instanceof barley.registry.core.Collection) {
                String[] docsPaths = ((barley.registry.core.Collection) resource).getChildren();
                for (String docPath : docsPaths) {
                    registry.copy(docPath, newVersion);
                }
            }
        } catch (RegistryException e) {
            handleException("Failed to copy docs to new version : " + newVersion, e);
        }
    }

    /**
     * Create an Api
     *
     * @param api WebApp
     * @throws barley.appmgt.api.AppManagementException if failed to create WebApp
     */
    private void createAPI(WebApp api) throws AppManagementException {
        GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                            AppMConstants.API_KEY);

        //Validate Transports
        validateAndSetTransports(api);
        try {
            registry.beginTransaction();
            GenericArtifact genericArtifact =
                    artifactManager.newGovernanceArtifact(new QName(api.getId().getApiName()));
            GenericArtifact artifact = AppManagerUtil.createAPIArtifactContent(genericArtifact, api);
            artifactManager.addGenericArtifact(artifact);
            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
            String providerPath = AppManagerUtil.getAPIProviderPath(api.getId());
            //provider ------provides----> WebApp
            registry.addAssociation(providerPath, artifactPath, AppMConstants.PROVIDER_ASSOCIATION);
            Set<String> tagSet = api.getTags();
            if (tagSet != null && tagSet.size() > 0) {
                for (String tag : tagSet) {
                    registry.applyTag(artifactPath, tag);
                }
            }
            if (api.getWsdlUrl() != null && !"".equals(api.getWsdlUrl())) {
                String path = AppManagerUtil.createWSDL(registry, api);
                if (path != null) {
                    registry.addAssociation(artifactPath, path, CommonConstants.ASSOCIATION_TYPE01);
                    artifact.setAttribute(AppMConstants.API_OVERVIEW_WSDL, api.getWsdlUrl()); //reset the wsdl path to permlink
                    artifactManager.updateGenericArtifact(artifact); //update the  artifact
                }
            }

            if (api.getUrl() != null && !"".equals(api.getUrl())){
                String path = AppManagerUtil.createEndpoint(api.getUrl(), registry);
                if (path != null) {
                    registry.addAssociation(artifactPath, path, CommonConstants.ASSOCIATION_TYPE01);
                }
            }
            //write WebApp Status to a separate property. This is done to support querying APIs using custom query (SQL)
            //to gain performance
            String apiStatus = api.getStatus().getStatus();
            saveAPIStatus(artifactPath, apiStatus);
            String visibleRolesList = api.getVisibleRoles();
            String[] visibleRoles = new String[0];
            if (visibleRolesList != null) {
                visibleRoles = visibleRolesList.split(",");
            }
            AppManagerUtil.setResourcePermissions(api.getId().getProviderName(), api.getVisibility(), visibleRoles, artifactPath);
            
            // (??????) 2018.03.05 - ?????????????????? ????????? created??? ??????
            // (??????) 2018.03.07 - ?????????????????? ???????????? ????????? ???????????? ?????? ????????? ???????????? ???????????? ????????? ?????????. lifecycle.xml?????? initial ????????? ?????????.
//            GenericArtifact persistedArtifact = artifactManager.getGenericArtifact(artifact.getId());
//            persistedArtifact.invokeAction(AppMConstants.LifecycleActions.CREATE, AppMConstants.WEBAPP_LIFE_CYCLE);
            
            registry.commitTransaction();

            /* Generate WebApp Definition for Swagger */
            createUpdateAPIDefinition(api);
            
            // (??????) 2018.03.05 - uuid ????????? ???????????? app??? uuid??? ????????????.
            api.setUUID(artifact.getId());

        } catch (Exception e) {
        	 try {
                 registry.rollbackTransaction();
             } catch (RegistryException re) {
                 handleException("Error while rolling back the transaction for WebApp: " +
                                 api.getId().getApiName(), re);
             }
             handleException("Error while performing registry transaction operation", e);
        }

    }

    /**
     * This function is to set resource permissions based on its visibility
     *
     * @param artifactPath WebApp resource path
     * @throws barley.appmgt.api.AppManagementException Throwing exception
     */
    private void clearResourcePermissions(String artifactPath, APIIdentifier apiId)
            throws AppManagementException {
        try {
            String resourcePath = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                    RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH
                            + artifactPath);
            String tenantDomain = MultitenantUtils.getTenantDomain(
                    AppManagerUtil.replaceEmailDomainBack(apiId.getProviderName()));
            if (!tenantDomain.equals(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)) {
                AuthorizationManager authManager = ServiceReferenceHolder.getInstance().
                        getRealmService().getTenantUserRealm(((UserRegistry) registry).getTenantId()).
                        getAuthorizationManager();
                authManager.clearResourceAuthorizations(resourcePath);
            } else {
                RegistryAuthorizationManager authorizationManager = new RegistryAuthorizationManager(ServiceReferenceHolder.getUserRealm());
                authorizationManager.clearResourceAuthorizations(resourcePath);
            }
        } catch (UserStoreException e) {
            handleException("Error while adding role permissions to WebApp", e);
        }
    }
    /**
     * Create a documentation
     *
     * @param apiId         APIIdentifier
     * @param documentation Documentation
     * @throws barley.appmgt.api.AppManagementException if failed to add documentation
     */
    private void createDocumentation(APIIdentifier apiId, Documentation documentation)
            throws AppManagementException {
        try {
            GenericArtifactManager artifactManager = new GenericArtifactManager(registry,
                                                                                AppMConstants.DOCUMENTATION_KEY);
            GenericArtifact artifact =
                    artifactManager.newGovernanceArtifact(new QName(documentation.getName()));
            artifactManager.addGenericArtifact(
                    AppManagerUtil.createDocArtifactContent(artifact, apiId, documentation));
            documentation.setId(artifact.getId());
            String apiPath = AppManagerUtil.getAPIPath(apiId);
            //Adding association from api to documentation . (WebApp -----> doc)
            registry.addAssociation(apiPath, artifact.getPath(),
                                    AppMConstants.DOCUMENTATION_ASSOCIATION);
            String[] authorizedRoles=getAuthorizedRoles(apiPath);
            AppManagerUtil.setResourcePermissions(getAPI(apiPath).getId().getProviderName(),
            		getAPI(apiPath).getVisibility(), authorizedRoles, artifact.getPath());

            String docFilePath = artifact.getAttribute(AppMConstants.DOC_FILE_PATH);
            if(docFilePath != null && !docFilePath.equals("")){
                //The docFilePatch comes as /t/tenanatdoman/registry/resource/_system/governance/apimgt/applicationdata..
                //We need to remove the /t/tenanatdoman/registry/resource/_system/governance section to set permissions.
                int startIndex = docFilePath.indexOf("governance") + "governance".length();
                String filePath = docFilePath.substring(startIndex, docFilePath.length());
                AppManagerUtil.setResourcePermissions(getAPI(apiPath).getId().getProviderName(),
                        getAPI(apiPath).getVisibility(), authorizedRoles, filePath);
            }
        } catch (RegistryException e) {
            handleException("Failed to add documentation", e);
        } catch (UserStoreException e) {
            handleException("Failed to add documentation", e);
        }
    }

    private String[] getAuthorizedRoles(String artifactPath) throws UserStoreException {
        String  resourcePath = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                                                             RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH
                                                             + artifactPath);
        RegistryAuthorizationManager authorizationManager = new RegistryAuthorizationManager
                (ServiceReferenceHolder.getUserRealm());
        return authorizationManager.getAllowedRolesForResource(resourcePath,ActionConstants.GET);
    }

    /**
     * Returns the details of all the life-cycle changes done per api
     *
     * @param apiId WebApp Identifier
     * @return List of lifecycle events per given api
     * @throws barley.apimgt.api.APIManagementException
     *          If failed to get Lifecycle Events
     */
    public List<LifeCycleEvent> getLifeCycleEvents(APIIdentifier apiId) throws
                                                                        AppManagementException {
        return appMDAO.getLifeCycleEvents(apiId);
    }

    /**
     * Update the subscription status
     *
     * @param apiId WebApp Identifier
     * @param subStatus Subscription Status
     * @param appId Application Id              *
     * @return int value with subscription id
     * @throws barley.apimgt.api.APIManagementException
     *          If failed to update subscription status
     */
    public void updateSubscription(APIIdentifier apiId,String subStatus,int appId) throws
                                                                                   AppManagementException {
        appMDAO.updateSubscription(apiId, subStatus, appId);
    }

	/**
	 * Moves subscriptions of one app to another app
	 *
	 * @param fromIdentifier subscriptions of this app
	 * @param toIdentifier   will be moved into this app
	 * @return number of subscriptions moved
	 * @throws AppManagementException
	 */
	@Override
	public int moveSubscriptions(APIIdentifier fromIdentifier, APIIdentifier toIdentifier) throws
																						   AppManagementException {
		return appMDAO.moveSubscriptions(fromIdentifier, toIdentifier);
	}

    /**
     * Delete applicatoion
     * @param identifier AppIdentifier
     * @param ssoProvider SSO provider
     * @param authorizedAdminCookie The cookie which was generated from the SAML assertion.
     * @throws barley.appmgt.api.AppManagementException
     * @throws FaultGatewaysException 
     */
    public boolean deleteApp(APIIdentifier identifier, SSOProvider ssoProvider, String authorizedAdminCookie) throws
                                                                                AppManagementException, FaultGatewaysException {

        SSOConfiguratorUtil ssoConfiguratorUtil;
        // (??????) path ?????? 
        String path = AppMConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
//                      identifier.getProviderName() +
        			  AppManagerUtil.replaceEmailDomain(identifier.getProviderName()) + 
                      RegistryConstants.PATH_SEPARATOR +
                      identifier.getApiName() + RegistryConstants.PATH_SEPARATOR + identifier.getVersion();

        String appArtifactPath = AppManagerUtil.getAPIPath(identifier);
        boolean isAppDeleted = false;

        boolean transactionCommitted = false;
        try {
        	registry.beginTransaction();
        	
            Resource appArtifactResource = registry.get(appArtifactPath);
            String applicationStatus = appArtifactResource.getProperty(AppMConstants.WEB_APP_LIFECYCLE_STATUS);
            
            //If SSOProvider exists, remove it
            if (ssoProvider != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Removing the SSO Provider with name : " + ssoProvider.getProviderName());
                }
                ssoConfiguratorUtil = new SSOConfiguratorUtil();

                Map<String, String> serviceConfigs = new HashMap<String, String>();
                serviceConfigs.put(SSOConfiguratorUtil.SP_ADMIN_SERVICE_COOKIE_PROPERTY_KEY, authorizedAdminCookie);

                ssoConfiguratorUtil.deleteSSOProvider(ssoProvider, serviceConfigs);
            }

            GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                                AppMConstants.API_KEY);
            Resource appResource = registry.get(path);
            String artifactId = appResource.getUUID();

            String appArtifactResourceId = appArtifactResource.getUUID();
            if (artifactId == null) {
                throw new AppManagementException("artifact id is null for : " + path);
            }

            GenericArtifact appArtifact = artifactManager.getGenericArtifact(appArtifactResourceId);
            String inSequence = appArtifact.getAttribute(AppMConstants.API_OVERVIEW_INSEQUENCE);
            String outSequence = appArtifact.getAttribute(AppMConstants.API_OVERVIEW_OUTSEQUENCE);
            
            // app-gateway ?????? 
            AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                    getAPIManagerConfigurationService().getAPIManagerConfiguration();
            boolean gatewayExists = config.getApiGatewayEnvironments().size() > 0;
            String gatewayType = config.getFirstProperty(AppMConstants.API_GATEWAY_TYPE);

            WebApp webapp = new WebApp(identifier);
            // gatewayType check is required when WebApp Management is deployed on other servers to avoid synapse
            if (gatewayExists && AppMConstants.API_GATEWAY_TYPE_SYNAPSE.equalsIgnoreCase(gatewayType)) {
            	// ????????? ?????? ????????? ?????? 
            	if(isAPIPublished(webapp)) {
	                webapp.setInSequence(inSequence); //need to remove the custom sequences
	                webapp.setOutSequence(outSequence);
	                // ????????????????????? app ??????
	                removeFromGateway(webapp);
            	}
            } else {
                if(log.isDebugEnabled()) {
                    log.debug("Gateway is not existed for the current applications Provider");
                }
            }

            // 1. ???????????? ????????? ?????? 
            //Delete the dependencies associated  with the api artifact
            GovernanceArtifact[] dependenciesArray = appArtifact.getDependencies();

            if (dependenciesArray.length > 0) {
                for (int i = 0; i < dependenciesArray.length; i++) {
                    registry.delete(dependenciesArray[i].getPath());
                }
            }

            // 2. App ?????? 
            artifactManager.removeGenericArtifact(appArtifact);
            artifactManager.removeGenericArtifact(artifactId);

            String thumbPath = AppManagerUtil.getIconPath(identifier);
            if (registry.resourceExists(thumbPath)) {
                registry.delete(thumbPath);
            }

            /*remove empty directories*/
            // (??????) ???????????? 
            String appCollectionPath = AppMConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
//                                       identifier.getProviderName() +
            						   AppManagerUtil.replaceEmailDomain(identifier.getProviderName()) + 
                                       RegistryConstants.PATH_SEPARATOR +
                                       identifier.getApiName();
            if (registry.resourceExists(appCollectionPath)) {
                Resource appCollection = registry.get(appCollectionPath);
                CollectionImpl collection = (CollectionImpl) appCollection;
                //if there is no other versions of applications delete the directory of the applications
                if (collection.getChildCount() == 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("No more versions of the applications found, removing applications collection from registry");
                    }
                    registry.delete(appCollectionPath);
                }
            }

            // (??????) ???????????? 
            String appProviderPath = AppMConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
//                    identifier.getProviderName();
            						 AppManagerUtil.replaceEmailDomain(identifier.getProviderName());
            if (registry.resourceExists(appProviderPath)) {
                Resource providerCollection = registry.get(appProviderPath);
                CollectionImpl collection = (CollectionImpl) providerCollection;
                //if there is no applications for given provider delete the provider directory
                if (collection.getChildCount() == 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("No more Applications from the provider " + identifier.getProviderName() + " found. " +
                                "Removing provider collection from registry");
                    }
                    registry.delete(appProviderPath);
                }
            }
            
            // (??????) registry ?????? ?????? ???  dao ??????????????? ?????? ??????
            long subsCount = appMDAO.getAPISubscriptionCountByAPI(identifier);
            if (subsCount > 0 && !applicationStatus.equals("Retired")) {
                //remove subscriptions per app
                appMDAO.removeAPISubscription(identifier);
            }
            
            //?????? ??????
            appMDAO.removeTag(identifier);
            appMDAO.deleteAPI(identifier, authorizedAdminCookie);
            
            registry.commitTransaction();
            
            transactionCommitted = true;
            isAppDeleted = true;
        } catch (RegistryException e) {
        	try {
                registry.rollbackTransaction();
            } catch (RegistryException re) {
                // Throwing an error from this level will mask the original exception
                log.error("Error while rolling back the transaction for API: " + identifier.getApiName(), re);
            }
        	handleException("Failed to remove the WebApp from : " + path, e);
        } finally {
            try {
                if (!transactionCommitted) {
                    registry.rollbackTransaction();
                }
            } catch (RegistryException ex) {
                handleException("Error occurred while rolling back the transaction.", ex);
            }
        }

        return isAppDeleted;
    }

    public List<WebApp> searchAPIs(String searchTerm, String searchType, String providerId) throws AppManagementException {
        List<WebApp> apiSortedList = new ArrayList<WebApp>();
        String regex = "(?i)[\\w.|-]*" + searchTerm.trim() + "[\\w.|-]*";

        Pattern pattern;
        Matcher matcher;
        try {
            List<WebApp> apiList;
            if(providerId != null){
                apiList= getAPIsByProvider(providerId);
                
                pattern = Pattern.compile(regex);
                for (WebApp api : apiList) {

                	// ????????? ?????? title ??????
                	if (searchType.equalsIgnoreCase("Title")) {
                        String title = api.getTitle();
                        matcher = pattern.matcher(title);
                    } else if (searchType.equalsIgnoreCase("Name")) {
                        String api1 = api.getId().getApiName();
                        matcher = pattern.matcher(api1);
                    } else if (searchType.equalsIgnoreCase("Provider")) {
                        String api1 = api.getId().getProviderName();
                        matcher = pattern.matcher(api1);
                    } else if (searchType.equalsIgnoreCase("Version")) {
                        String api1 = api.getId().getVersion();
                        matcher = pattern.matcher(api1);
                    } else if (searchType.equalsIgnoreCase("Context")) {
                        String api1 = api.getContext();
                        matcher = pattern.matcher(api1);
                    } else {
                        String apiName = api.getId().getApiName();
                        matcher = pattern.matcher(apiName);
                    }

                    if (matcher.find()) {
                        apiSortedList.add(api);
                    }
                }
            } else {
            	// (??????) ?????????????????? ?????? ????????? ?????? 
                //apiList= getAllAPIs();
            	apiSortedList = searchAPIs(searchTerm, searchType);
            }
            
            // (??????) ??????????????? ???????????? 
//            if (apiList == null || apiList.size() == 0) {
//                return apiSortedList;
//            }            
        } catch (AppManagementException e) {
            handleException("Failed to search APIs with type", e);
        }
        Collections.sort(apiSortedList, new APINameComparator());
        return apiSortedList;
    }
    
    private List<WebApp> searchAPIs(String searchTerm, String searchType) throws AppManagementException {
        List<WebApp> apiList = new ArrayList<WebApp>();
        Pattern pattern;
		Matcher matcher;
		// ????????? ?????? 
		String searchCriteria = AppMConstants.API_OVERVIEW_TITLE;
		boolean isTenantFlowStarted = false;
		String userName = this.username;
		try {
			if (tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
				isTenantFlowStarted = true;
				PrivilegedBarleyContext.startTenantFlow();
				PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
			}
			PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(userName);
			GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, AppMConstants.API_KEY);
			if (artifactManager != null) {
				if ("Title".equalsIgnoreCase(searchType)) {
					// ????????? ????????? ?????? ????????????  
					searchCriteria = AppMConstants.API_OVERVIEW_TITLE;
				} else if ("Name".equalsIgnoreCase(searchType)) {
					searchCriteria = AppMConstants.API_OVERVIEW_NAME;
				} else if ("Version".equalsIgnoreCase(searchType)) {
					searchCriteria = AppMConstants.API_OVERVIEW_VERSION;
				} else if ("Context".equalsIgnoreCase(searchType)) {
					searchCriteria = AppMConstants.API_OVERVIEW_CONTEXT;
				} else if ("Provider".equalsIgnoreCase(searchType)) {
					searchCriteria = AppMConstants.API_OVERVIEW_PROVIDER;
					searchTerm = searchTerm.replaceAll("@", "-AT-");
				} else if ("Status".equalsIgnoreCase(searchType)) {
					searchCriteria = AppMConstants.API_OVERVIEW_STATUS;
				}

				String regex = "(?i)[\\w.|-]*" + searchTerm.trim() + "[\\w.|-]*";
				pattern = Pattern.compile(regex);
				
				// api ????????? ????????????. 
				GenericArtifact[] genericArtifacts = artifactManager.getAllGenericArtifacts();
				if (genericArtifacts == null || genericArtifacts.length == 0) {
					return apiList;
				}

				for (GenericArtifact artifact : genericArtifacts) {
					String value = artifact.getAttribute(searchCriteria);

					if (value != null) {
						matcher = pattern.matcher(value);
						if (matcher.find()) {
							WebApp resultAPI = AppManagerUtil.getAPI(artifact, registry);
                            if (resultAPI != null) {
                                apiList.add(resultAPI);
                            }
						}
					}
			    }
			}
		} catch (RegistryException e) {
			handleException("Failed to search APIs with type", e);
		} finally {
			if (isTenantFlowStarted) {
				PrivilegedBarleyContext.endTenantFlow();
			}
		}
        return apiList;
    }

    /**
     * Get a list of APIs published by the given provider. If a given WebApp has multiple APIs, only the latest version
     * will be included in this list.
     *
     * @param providerId , provider id
     * @param appType    Asset Type(either webapp/mobileapp)
     * @return set of WebApp
     * @throws barley.appmgt.api.AppManagementException if failed to get set of WebApp
     */
    public List<WebApp> getAPIsByProvider(String providerId, String appType) throws AppManagementException {

        List<WebApp> apiSortedList = new ArrayList<WebApp>();

        try {
            providerId = AppManagerUtil.replaceEmailDomain(providerId);
            String providerPath = AppMConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
                    providerId;
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, appType);
            Association[] associations = registry.getAssociations(providerPath,
                                                                  AppMConstants.PROVIDER_ASSOCIATION);
            for (Association association : associations) {
                String apiPath = association.getDestinationPath();
                Resource resource = registry.get(apiPath);
                String apiArtifactId = resource.getUUID();
                if (apiArtifactId != null) {
                    GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiArtifactId);
                    apiSortedList.add(AppManagerUtil.getGenericApp(apiArtifact, registry));
                } else {
                    throw new GovernanceException("artifact id is null of " + apiPath);
                }
            }

        } catch (RegistryException e) {
            handleException("Failed to get APIs for provider : " + providerId, e);
        }
        Collections.sort(apiSortedList, new APINameComparator());

        return apiSortedList;

    }

    @Override
    public List<App> searchApps(String appType, Map<String, String> searchTerms) throws AppManagementException {


        // If the app type is 'webapp' use the App Repository implementation path.
        if(AppMConstants.WEBAPP_ASSET_TYPE.equalsIgnoreCase(appType)){
            return new DefaultAppRepository(registry).searchApps(appType, searchTerms);
        }else{
            List<App> apps = new ArrayList<App>();
            List<GenericArtifact> appArtifacts = getAppArtifacts(appType);

            for(GenericArtifact artifact : appArtifacts){
                if(isSearchHit(artifact, searchTerms)){
                    apps.add(createApp(artifact, appType));
                }
            }
            return apps;
        }
    }


    /**
     * Update the Tier Permissions
     *
     * @param tierName Tier Name
     * @param permissionType Permission Type
     * @param roles Roles
     * @throws barley.apimgt.api.APIManagementException
     *          If failed to update subscription status
     */
    public void updateTierPermissions(String tierName, String permissionType, String roles) throws
                                                                                            AppManagementException {
        appMDAO.updateTierPermissions(tierName, permissionType, roles, tenantId);
    }

	@Override
	public Set<TierPermissionDTO> getTierPermissions() throws AppManagementException {
		Set<TierPermissionDTO> tierPermissions = appMDAO.getTierPermissions(tenantId);
		return tierPermissions;
	}

	/**
	 * Get stored custom inSequences from governanceSystem registry
	 *
	 * @throws barley.appmgt.api.AppManagementException
	 */

	public List<String> getCustomInSequences() throws AppManagementException {

		List<String> sequenceList = new ArrayList<String>();
		try {
			UserRegistry registry = ServiceReferenceHolder.getInstance().getRegistryService()
			                                              .getGovernanceSystemRegistry(tenantId);
			if (registry.resourceExists(AppMConstants.API_CUSTOM_INSEQUENCE_LOCATION)) {
				barley.registry.api.Collection inSeqCollection =
	                                                                      (barley.registry.api.Collection) registry.get(AppMConstants.API_CUSTOM_INSEQUENCE_LOCATION);
	            if (inSeqCollection != null) {
	             //   SequenceMediatorFactory factory = new SequenceMediatorFactory();
	                String[] inSeqChildPaths = inSeqCollection.getChildren();
	                for (int i = 0; i < inSeqChildPaths.length; i++) {
		                Resource inSequence = registry.get(inSeqChildPaths[i]);
		                OMElement seqElment = AppManagerUtil.buildOMElement(inSequence.getContentStream());
		                sequenceList.add(seqElment.getAttributeValue(new QName("name")));
	                }
                }
            }

		} catch (Exception e) {
			handleException("Issue is in getting custom InSequences from the Registry", e);
		}
		return sequenceList;
	}

	/**
	 * Get stored custom outSequences from governanceSystem registry
	 *
	 * @throws barley.appmgt.api.AppManagementException
	 */

	public List<String> getCustomOutSequences() throws AppManagementException {

		List<String> sequenceList = new ArrayList<String>();
		try {
			UserRegistry registry = ServiceReferenceHolder.getInstance().getRegistryService()
			                                              .getGovernanceSystemRegistry(tenantId);
			if (registry.resourceExists(AppMConstants.API_CUSTOM_OUTSEQUENCE_LOCATION)) {
				barley.registry.api.Collection outSeqCollection =
	                                                                       (barley.registry.api.Collection) registry.get(AppMConstants.API_CUSTOM_OUTSEQUENCE_LOCATION);
	            if (outSeqCollection !=null) {
	                String[] outSeqChildPaths = outSeqCollection.getChildren();
	                for (int i = 0; i < outSeqChildPaths.length; i++) {
		                Resource outSequence = registry.get(outSeqChildPaths[i]);
		                OMElement seqElment = AppManagerUtil.buildOMElement(outSequence.getContentStream());

		                sequenceList.add(seqElment.getAttributeValue(new QName("name")));
	                }
                }
            }

		} catch (Exception e) {
			handleException("Issue is in getting custom OutSequences from the Registry", e);
		}
		return sequenceList;
	}

    @Override
    public List<WebApp> getAllWebApps() throws AppManagementException {
        return appMDAO.getAllWebApps();
    }

    @Override
    public List<WebApp> getAllWebApps(String tenantDomain) throws AppManagementException {
        return appMDAO.getAllWebApps(tenantDomain);
    }

    @Override
    public List<SubscriptionCount> getSubscriptionCountByAPPs(String provider, String fromDate, String toDate,
                                                              boolean isSubscriptionOn) throws AppManagementException {
        //Map<String, Long> subscriptions = null;
        List<SubscriptionCount> subscriptions = null;
        try {
            subscriptions = appMDAO.getSubscriptionCountByApp(provider, fromDate, toDate, tenantId, isSubscriptionOn);
        } catch (AppManagementException e) {
            handleException("Failed to get subscriptionCount by apps for provider :" + provider + "for the period "
                                    + fromDate + "to" + toDate, e);
        }
        return subscriptions;
    }

    public List<WebApp> getAppsWithEndpoint(String tenantDomain) throws AppManagementException {
        List<WebApp> appSortedList = appMDAO.getAllWebApps(tenantDomain);
        Collections.sort(appSortedList, new APINameComparator());
        return appSortedList;
    }

    @Override
    public Set<AppStore> getExternalAppStores(APIIdentifier identifier)
            throws AppManagementException {
        // get all stores from configuration
        Set<AppStore> storesFromConfig = AppManagerUtil.getExternalStores(tenantId);
        if (storesFromConfig != null && storesFromConfig.size() > 0) {
            AppManagerUtil.validateStoreName(storesFromConfig);
            //get already published stores from db
            Set<AppStore> publishedStores = appMDAO.getExternalAppStoresDetails(identifier);
            if (publishedStores != null && publishedStores.size() > 0) {
                //Retains only the stores that contained in configuration
                publishedStores.retainAll(storesFromConfig);

                for (AppStore publishedStore : publishedStores) {
                    for (AppStore configuredStore : storesFromConfig) {
                        if (publishedStore.getName().equals(configuredStore.getName())) { //If the configured appstore
                            // already stored in db, change the published state to true
                            configuredStore.setPublished(true);
                        }
                    }
                }
            }
        }
        return storesFromConfig;
    }

    @Override
    public void updateAppsInExternalAppStores(WebApp webApp, Set<AppStore> appStores)
            throws AppManagementException {
        // get the stores where given app is already published
        Set<AppStore> publishedStores = getPublishedExternalAppStores(webApp.getId());
        Set<AppStore> notPublishedAppStores = new HashSet<AppStore>();
        Set<AppStore> removedAppStores = new HashSet<AppStore>();

        if (publishedStores != null && publishedStores.size() > 0) {
            removedAppStores.addAll(publishedStores);
            removedAppStores.removeAll(appStores);

            notPublishedAppStores.addAll(appStores);
            notPublishedAppStores.removeAll(publishedStores);
        } else {
            notPublishedAppStores.addAll(appStores);
        }

        //Publish app to external app Store which are not yet published
        try {
            publishToExternalAppStores(webApp, notPublishedAppStores);
        } catch (AppManagementException e) {
            handleException("Failed to publish app to external store.App -> " + webApp.getApiName(), e);
        }
        //Delete app from external app Store
        try {
            if(removedAppStores.size() > 0) {
                deleteFromExternalAppStores(webApp, removedAppStores);
            }
        } catch (AppManagementException e) {
            handleException("Failed to delete app from external store.App -> " + webApp.getApiName(), e);
        }
    }


    /**
     * Get the stores where given app is already published.
     * @param identifier WebApp Identifier
     * @return
     * @throws AppManagementException
     */
    private Set<AppStore> getPublishedExternalAppStores(APIIdentifier identifier)
            throws AppManagementException {
        Set<AppStore> configuredAppStores = new HashSet<AppStore>();
        configuredAppStores.addAll(AppManagerUtil.getExternalStores(tenantId));
        if (configuredAppStores.size() != 0) {
            Set<AppStore> storesSet = appMDAO.getExternalAppStoresDetails(identifier);
            //Retains only the stores that contained in configuration
            configuredAppStores.retainAll(storesSet);
            return configuredAppStores;

        } else {
            return null;
        }
    }


    /**
     * Publish the APP to external App Stores and add the published
     * external store details to DB.
     *
     * @param webApp    The APP which need to published
     * @param appStores The APPStores set, to which need to publish APP
     * @throws AppManagementException If failed to publish to any external store
     */
    private void publishToExternalAppStores(WebApp webApp, Set<AppStore> appStores)
            throws AppManagementException {
        if (log.isDebugEnabled()) {
            String msg = String.format("Publish the web app -> %s to external stores ", webApp.getApiName());
            log.debug(msg);
        }
        Set<AppStore> publishedStores = new HashSet<AppStore>();
        StringBuilder errorStatus = new StringBuilder("Failure to publish to External Stores : ");
        List<String> failedAppStores = new ArrayList<String>();
        boolean failure = false;
        if (appStores.size() > 0) {
            for (AppStore store : appStores) {
                try {
                    String publisherClassName = store.getPublisherClassName();
                    if (publisherClassName == null) {
                        throw new AppManagementException("Publisher class name is not defined in the external " +
                                "store configuration for store with id" + store.getName());
                    }
                    ExternalAppStorePublisher publisher = AppManagerUtil.getExternalStorePublisher(publisherClassName);
                    // First  publish the APP to external APP Store
                    publisher.publishToStore(webApp, store);
                    //collect the published store to add to DB
                    publishedStores.add(store);
                } catch (AppManagementException e) {
                    failure = true;
                    String msg = "Could not publish app :" + webApp.getApiName() +
                            " to external store :" + store.getDisplayName();
                    log.error(msg,e);
                    failedAppStores.add(store.getDisplayName());
                }
            }
            if (publishedStores.size() > 0) {
                //Save the detail of published app stores in DB
                addExternalAppStoresDetails(webApp.getId(), publishedStores);
            }
        }

        if (failure) {
            String failedStores = StringUtils.join(failedAppStores,',');
            errorStatus.append(failedStores);
            throw new AppManagementException(errorStatus.toString());
        }

    }

    /**
     * Delete the given web app from given external stores and remove the related
     * records from DB.
     *
     * @param webApp           Web App
     * @param removedAppStores App Stores
     * @throws AppManagementException
     */
    private void deleteFromExternalAppStores(WebApp webApp, Set<AppStore> removedAppStores)
            throws AppManagementException {
        Set<AppStore> removalCompletedStores = new HashSet<AppStore>();
        StringBuilder errorStatus = new StringBuilder("Failed to delete from External Stores : ");
        List<String> failedAppStores = new ArrayList<String>();
        boolean failure = false;
        if (removedAppStores.size() > 0) {
            for (AppStore store : removedAppStores) {

                try {
                    String publisherClassName = store.getPublisherClassName();
                    ExternalAppStorePublisher publisher = AppManagerUtil.getExternalStorePublisher(publisherClassName);
                    //delete from external store
                    publisher.deleteFromStore(webApp, store);
                    removalCompletedStores.add(store);
                } catch (AppManagementException e) {
                    failure = true;
                    String msg = "Could not delete app :" + webApp.getApiName() +
                            " from external store :" + store.getDisplayName();
                    log.error(msg,e);
                    failedAppStores.add(store.getDisplayName());
                }

            }
            if (removalCompletedStores.size() != 0) {
                //remove records from database
                removeExternalAppStoreDetails(webApp.getId(), removalCompletedStores);
            }

            if (failure) {
                String failedStores = StringUtils.join(failedAppStores,',');
                errorStatus.append(failedStores);
            }
        }
    }


    /**
     * Store the published external stores details in DB.
     * @param apiId       WebApp Identifier
     * @param apiStoreSet stores
     * @return
     * @throws AppManagementException
     */
    private void addExternalAppStoresDetails(APIIdentifier apiId, Set<AppStore> apiStoreSet)
            throws AppManagementException {
        if (log.isDebugEnabled()) {
            String msg = String.format("Save published external app store details to DB " +
                    "for web app %s ", apiId.getApiName());
            log.debug(msg);
        }
        appMDAO.addExternalAppStoresDetails(apiId, apiStoreSet);
    }

    /**
     * Remove the records of unpublished external store details from DB.
     *
     * @param identifier    WebApp Identifier
     * @param removalCompletedStores stores
     * @throws AppManagementException
     */
    private void removeExternalAppStoreDetails(APIIdentifier identifier, Set<AppStore> removalCompletedStores)
            throws AppManagementException {
        if (log.isDebugEnabled()) {
            String msg = String.format("Delete  external app store details from DB " +
                    "for web app %s ", identifier.getApiName());
            log.debug(msg);
        }
        appMDAO.deleteExternalAppStoresDetails(identifier, removalCompletedStores);
    }

    /**
     * Get web app default version.
     *
     * @param appName
     * @param providerName
     * @param appStatus
     * @return
     * @throws AppManagementException
     */
    @Override
    public String getDefaultVersion(String appName, String providerName, AppDefaultVersion appStatus)
            throws AppManagementException {
        return AppMDAO.getDefaultVersion(appName, providerName, appStatus);
    }

    /**
     * Check if the given app is the default version.
     *
     * @param identifier
     * @return true if given app is the default version
     * @throws AppManagementException
     */
    @Override
    public boolean isDefaultVersion(APIIdentifier identifier) throws AppManagementException {
        return appMDAO.isDefaultVersion(identifier);
    }

    /**
     * Check if the given app has any other versions in any state.
     *
     * @param identifier
     * @return true if given app has more version
     * @throws AppManagementException
     */
    @Override
    public boolean hasMoreVersions(APIIdentifier identifier) throws AppManagementException {
        return appMDAO.hasMoreVersions(identifier);
    }

    /**
     * Get WebApp basic details by app uuid.
     *
     * @param uuid
     * @return Asset details
     * @throws AppManagementException
     */
    @Override
    public WebApp getAppDetailsFromUUID(String uuid) throws AppManagementException {
        WebApp webApp = appMDAO.getAppDetailsFromUUID(uuid);
        if (webApp == null) {
            handleResourceNotFoundException("Webapp does not exist with requested appID : " + uuid);
        }
        return webApp;
    }

    /**
     * Change the lifecycle state of a given application
     *
     * @param appType         application type ie: webapp, mobileapp
     * @param appId           application uuid
     * @param lifecycleAction lifecycle action perform on the application
     * @throws AppManagementException
     */
    public void changeLifeCycleStatus(String appType, String appId, String lifecycleAction) throws AppManagementException {

        try {
            String requiredPermission = null;

            if (AppMConstants.LifecycleActions.SUBMIT_FOR_REVIEW.equals(lifecycleAction)) {
                if (AppMConstants.MOBILE_ASSET_TYPE.equals(appType)) {
                    requiredPermission = AppMConstants.Permissions.MOBILE_APP_CREATE;
                } else if (AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)) {
                    requiredPermission = AppMConstants.Permissions.WEB_APP_CREATE;
                }
            } else {
                if (AppMConstants.MOBILE_ASSET_TYPE.equals(appType)) {
                    requiredPermission = AppMConstants.Permissions.MOBILE_APP_PUBLISH;
                } else if (AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)) {
                    requiredPermission = AppMConstants.Permissions.WEB_APP_PUBLISH;
                }
            }

            // (??????) this.username??? ????????? provider?????? ????????? ??????????????? ????????????. 
//            if (!AppManagerUtil.checkPermissionQuietly(this.username, requiredPermission)) {
            String providerName = this.username + "@" + this.tenantDomain;
            if (!AppManagerUtil.checkPermissionQuietly(providerName, requiredPermission)) {
                handleResourceAuthorizationException("The user " + this.username +
                        " is not authorized to perform lifecycle action " + lifecycleAction + " on " +
                        appType + " with uuid " + appId);
            }
            //Check whether the user has enough permissions to change lifecycle
            PrivilegedBarleyContext.startTenantFlow();
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(this.tenantDomain, true);

            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().
                    getTenantId(this.tenantDomain);

            AuthorizationManager authManager = ServiceReferenceHolder.getInstance().getRealmService().
                    getTenantUserRealm(tenantId).getAuthorizationManager();

            //Get system registry for logged in tenant domain
            Registry systemRegistry = ServiceReferenceHolder.getInstance().
                    getRegistryService().getGovernanceSystemRegistry(tenantId);             
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(systemRegistry, appType);
            GenericArtifact appArtifact = artifactManager.getGenericArtifact(appId);
            String resourcePath = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                    RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH + appArtifact.getPath());

            if (appArtifact != null) {
                if (!authManager.isUserAuthorized(username, resourcePath, "authorize")) {
                    //Throws resource authorization exception
                    handleResourceAuthorizationException("The user " + this.username +
                            " is not authorized to" + appType + " with uuid " + appId);
                }
                //Change lifecycle status
                if (AppMConstants.MOBILE_ASSET_TYPE.equals(appType)) {
                    appArtifact.invokeAction(lifecycleAction, AppMConstants.MOBILE_LIFE_CYCLE);
                } else if (AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)) {
                    appArtifact.invokeAction(lifecycleAction, AppMConstants.WEBAPP_LIFE_CYCLE);
                }

                //If application is role restricted, deny read rights for Internal/everyone and system/wso2.anonymous.role roles
                if ((AppMConstants.LifecycleActions.PUBLISH.equals(lifecycleAction) ||
                        AppMConstants.LifecycleActions.RE_PUBLISH.equals(lifecycleAction)) &&
                        !StringUtils.isBlank(appArtifact.getAttribute("overview_visibleRoles"))) {

                    authManager.denyRole(AppMConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                    authManager.denyRole(AppMConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
                }

                if (log.isDebugEnabled()) {
                    String logMessage =
                            "Lifecycle action " + lifecycleAction + " has been successfully performed on " + appType
                                    + " with id" + appId;
                    log.debug(logMessage);
                }
            } else {
                handleResourceNotFoundException("Failed to get " + appType + " artifact corresponding to artifactId " +
                		appId + ". Artifact does not exist");
            }
        } catch (UserStoreException e) {
            handleException("Error occurred while performing lifecycle action : " + lifecycleAction + " on " + appType +
                    " with id : " + appId + ". Failed to retrieve tenant id for user : ", e);
        } catch (RegistryException e) {
            handleException("Error occurred while performing lifecycle action : " + lifecycleAction + " on " + appType +
                    " with id : " + appId, e);
        } finally {
            PrivilegedBarleyContext.endTenantFlow();
        }
    }
    
    public void changeLifeCycleStatus(String appType, APIIdentifier apiIdentifier, String lifecycleAction) throws AppManagementException {
    	GenericArtifact appArtifact = AppManagerUtil.getAPIArtifact(apiIdentifier, registry);
    	changeLifeCycleStatus(appType, appArtifact.getId(), lifecycleAction);
    }

    /**
     * Get the available next lifecycle actions of a given application
     *
     * @param appId   application type
     * @param appType application type
     * @return
     */
    public String[] getAllowedLifecycleActions(String appId, String appType) throws AppManagementException {

        String[] actions = null;
        try {
            PrivilegedBarleyContext.startTenantFlow();
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(this.tenantDomain, true);

            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, appType);
            GenericArtifact appArtifact = artifactManager.getGenericArtifact(appId);
            if (appArtifact != null) {
                if (AppMConstants.MOBILE_ASSET_TYPE.equals(appType)) {
                    //Get all the actions corresponding to current state of the api artifact
                    actions = appArtifact.getAllLifecycleActions(AppMConstants.MOBILE_LIFE_CYCLE);
                }else if(AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)){
                    actions = appArtifact.getAllLifecycleActions(AppMConstants.WEBAPP_LIFE_CYCLE);
                } else {
                    handleException("Unsupported application type : " + appType +" provided");
                }
            } else {
                handleResourceNotFoundException("Failed to get " + appType + " artifact corresponding to artifactId " +
                        appId + ". Artifact does not exist");
            }
        } catch (GovernanceException e) {
            handleException("Error occurred while retrieving allowed lifecycle actions to perform on " + appType +
                    " with id : " + appId, e);
        } finally {
            PrivilegedBarleyContext.endTenantFlow();
        }
        return actions;
    }

    public boolean subscribeMobileApp(String userId, String appId) throws AppManagementException {

        String path = "users/" + userId + "/subscriptions/mobileapp/" + appId;
        Resource resource = null;
        boolean isSubscribed = false;
        try {
            UserRegistry sysRegistry = ServiceReferenceHolder.getInstance().getRegistryService()
                    .getGovernanceSystemRegistry(tenantId);
            if (!sysRegistry.resourceExists(path)) {
                resource = sysRegistry.newResource();
                resource.setContent("");
                sysRegistry.put(path, resource);
                isSubscribed = true;
            }
        } catch (barley.registry.api.RegistryException e) {
            handleException("Error occurred while adding subscription registry resource for mobileapp with id :" +
                    appId, e);
        }
        return isSubscribed;
    }


    public boolean unSubscribeMobileApp(String userId, String appId) throws AppManagementException {
        String path = "users/" + userId + "/subscriptions/mobileapp/" + appId;
        boolean isUnSubscribed = false;
        try {
            if (registry.resourceExists(path)) {
                registry.delete(path);
                isUnSubscribed = true;
            }
        } catch (barley.registry.api.RegistryException e) {
            handleException("Error occurred while removing subscription registry resource for mobileapp with id :" +
                    appId, e);
        }
        return isUnSubscribed;
    }

    /**
     *
     * Returns the 'app' (e.g. webapp, mobileapp) registry artifacts.
     *
     * @param appType
     * @return
     * @throws AppManagementException
     */
    private List<GenericArtifact> getAppArtifacts(String appType) throws AppManagementException {

        List<GenericArtifact> appArtifacts = new ArrayList<GenericArtifact>();

        boolean isTenantFlowStarted = false;
        try {
            if (tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                isTenantFlowStarted = true;
                PrivilegedBarleyContext.startTenantFlow();
                PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            }
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, appType);
            GenericArtifact[] artifacts = artifactManager.getAllGenericArtifacts();
            for (GenericArtifact artifact : artifacts) {
                appArtifacts.add(artifact);
            }

        } catch (RegistryException e) {
            handleException("Failed to get APIs from the registry", e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedBarleyContext.endTenantFlow();
            }
        }

        return appArtifacts;
    }


    private App createApp(GenericArtifact artifact, String appType) throws AppManagementException {

        AppFactory appFactory = null;

        if(AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)){
            appFactory = new WebAppFactory();
        }else if(AppMConstants.MOBILE_ASSET_TYPE.equals(appType)){
            appFactory = new MobileAppFactory();
        }

        return appFactory.createApp(artifact, registry);
    }

    private boolean isSearchHit(GenericArtifact artifact, Map<String, String> searchTerms) throws AppManagementException {

        boolean isSearchHit = true;

        for(Map.Entry<String, String> term : searchTerms.entrySet()){
            try {
                if("ID".equalsIgnoreCase(term.getKey())) {
                    if(!artifact.getId().equals(term.getValue())){
                        isSearchHit = false;
                        break;
                    }
                }else if(!term.getValue().equals(artifact.getAttribute(getRxtAttributeName(term.getKey())))){
                    isSearchHit = false;
                    break;
                }
            } catch (GovernanceException e) {
                String errorMessage = String.format("Error while determining whether artifact '%s' is a search hit.", artifact.getId());
                throw new AppManagementException(errorMessage, e);
            }
        }

        return isSearchHit;
    }

    private String getRxtAttributeName(String searchKey) {

        String rxtAttributeName = null;

        if(searchKey.equalsIgnoreCase("NAME")){
            rxtAttributeName = AppMConstants.API_OVERVIEW_NAME;
        }else if(searchKey.equalsIgnoreCase("PROVIDER")){
            rxtAttributeName = AppMConstants.API_OVERVIEW_PROVIDER;
        }else if(searchKey.equalsIgnoreCase("VERSION")){
            rxtAttributeName = AppMConstants.API_OVERVIEW_VERSION;
        }

        return rxtAttributeName;
    }

    @Override
    public void addTags(String appType, String appId, List<String> tags) throws AppManagementException {
        try {
            PrivilegedBarleyContext.startTenantFlow();
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(this.tenantDomain, true);

            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, appType);
            GenericArtifact appArtifact = artifactManager.getGenericArtifact(appId);
            if (appArtifact != null) {
                for(String tag : tags){
                    registry.applyTag(appArtifact.getPath(), tag);
                }
            } else {
                handleResourceNotFoundException("Failed to get " + appType + " artifact corresponding to artifactId " +
                        appId + ". Artifact does not exist");
            }
        } catch (RegistryException e) {
            handleException("Error occurred while adding tags"+StringUtils.join(tags, ",")+" to " + appType +" with id : " + appId, e);
        } finally {
            PrivilegedBarleyContext.endTenantFlow();
        }
    }

    @Override
    public void removeTag(String appType, String appId, List<String> tags) throws AppManagementException {
        try {
            PrivilegedBarleyContext.startTenantFlow();
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(this.tenantDomain, true);

            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, appType);
            GenericArtifact appArtifact = artifactManager.getGenericArtifact(appId);
            if (appArtifact != null) {
                for(String tag : tags) {
                    registry.removeTag(appArtifact.getPath(), tag);
                }
            } else {
                handleResourceNotFoundException("Failed to retrieve " + appType +
                        " artifact corresponding to artifactId " +appId + ". Artifact does not exist");
            }
        } catch (RegistryException e) {
            handleException("Error occurred while removing tags '" + StringUtils.join(tags, ",") + "' from "
                    + appType + " with id : " + appId, e);
        } finally {
            PrivilegedBarleyContext.endTenantFlow();
        }
    }

    @Override
    public Set<Tag> getAllTags(String appType) throws AppManagementException {
        Set<Tag> tagSet = new HashSet<>();
        try {
            PrivilegedBarleyContext.startTenantFlow();
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(this.tenantDomain, true);

            Map<String, String> params = new HashMap<String, String>();
            if (AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)){
                params.put("1",AppMConstants.MediaType.WEB_APP);
                params.put("2",AppMConstants.WEB_APP_LIFECYCLE_STATUS);
                params.put("3","%");
            } else if(AppMConstants.MOBILE_ASSET_TYPE.equals(appType)) {
                params.put("1",AppMConstants.MediaType.MOBILE_APP);
                params.put("2",AppMConstants.MOBILE_APP_LIFECYCLE_STATUS);
                params.put("3","%");
            } else {
                handleException("Could not retrieve tags. Unsupported applictaion type :" + appType +" provided");
            }

            String tagsQueryPath = RegistryConstants.QUERIES_COLLECTION_PATH + "/tag-summary-appmgt";

            barley.registry.core.Collection collection = registry.executeQuery(tagsQueryPath, params);
            for (String fullTag : collection.getChildren()) {

                String tagName = fullTag.substring(fullTag.indexOf(";") + 1, fullTag.indexOf(":"));
                int numberOfOccurrence = Integer.parseInt(fullTag.substring(fullTag.indexOf(":")+1));
                tagSet.add(new Tag(tagName, numberOfOccurrence));
            }

        } catch (RegistryException e) {
            handleException("Error occurred while retrieving tags for " + appType +"s" , e);
        } finally {
            PrivilegedBarleyContext.endTenantFlow();
        }
        return tagSet;
    }

    @Override
    public Set<Tag> getAllTags(String appType, String appId) throws AppManagementException {
        Set<Tag> tagSet = new HashSet<>();
        try {
        	barley.registry.core.Tag[] tags = null;
            PrivilegedBarleyContext.startTenantFlow();
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(this.tenantDomain, true);

            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, appType);
            GenericArtifact appArtifact = artifactManager.getGenericArtifact(appId);
            if (appArtifact != null) {
              String artifactPath = appArtifact.getPath();
                tags = registry.getTags(artifactPath);
                for(barley.registry.core.Tag tag : tags){
                    tagSet.add(new Tag(tag.getTagName()));
                }
            } else {
                handleResourceNotFoundException("Failed to get " + appType + " artifact corresponding to artifactId " +
                        appId + ". Artifact does not exist");
            }
        } catch (RegistryException e) {
            handleException("Error occurred while retrieving tags from " + appType +" with id : " + appId, e);
        } finally {
            PrivilegedBarleyContext.endTenantFlow();
        }
        return tagSet;
    }

    public String addResourceFile(String resourcePath, FileContent resourceFile) throws AppManagementException {
        try {
            Resource thumb = registry.newResource();
            thumb.setContentStream(resourceFile.getContent());
            thumb.setMediaType(resourceFile.getContentType());
            registry.put(resourcePath, thumb);
            if(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equalsIgnoreCase(tenantDomain)){
                return RegistryConstants.PATH_SEPARATOR + "registry"
                        + RegistryConstants.PATH_SEPARATOR + "resource"
                        + RegistryConstants.PATH_SEPARATOR + "_system"
                        + RegistryConstants.PATH_SEPARATOR + "governance"
                        + resourcePath;
            }
            else{
                return "/t/"+tenantDomain+ RegistryConstants.PATH_SEPARATOR + "registry"
                        + RegistryConstants.PATH_SEPARATOR + "resource"
                        + RegistryConstants.PATH_SEPARATOR + "_system"
                        + RegistryConstants.PATH_SEPARATOR + "governance"
                        + resourcePath;
            }
        } catch (RegistryException e) {
            handleException("Error while adding the resource to the registry", e);
        }
        return null;
    }

    /**
     * Remove mobile applications binary files from storage
     * @param filePath file path of the banner image, thumbnail, screenshots and app binary
     * @throws AppManagementException
     */
    public void removeBinaryFromStorage(String filePath) throws AppManagementException {
        if (StringUtils.isEmpty(filePath)) {
            handleException("Mobile Application BinaryFileStorage Configuration cannot be found." +
                    " Pleas check the configuration in app-management.xml ");
        }

        File binaryFile = new File(filePath);
        if (!binaryFile.exists()) {
            handleException("Binary file " + filePath + " does not exist");
        }

        boolean isDeleted = binaryFile.delete();
        if (!isDeleted) {
            handleException("Error occurred while deleting file " + filePath);
        }
    }

    /**
     * Generate one-time download link content in Database
     * @param appId mobile application id that the one-time download link generated for
     * @return UUID of the download link
     * @throws AppManagementException
     */
    public String generateOneTimeDownloadLink(String appId) throws AppManagementException {

        String downloadLinkUUID = null;
        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                    AppMConstants.MOBILE_ASSET_TYPE);
            GenericArtifact mobileAppArtifact = artifactManager.getGenericArtifact(appId);
            if (mobileAppArtifact == null) {
                handleResourceNotFoundException(
                        "Failed to generate one-time download link for Mobile App. The artifact corresponding to artifactId "
                                + appId + " does not exist");
            }

            if (!AppMConstants.MOBILE_APP_TYPE_PUBLIC.equals(mobileAppArtifact.getAttribute(AppMConstants.MOBILE_APP_OVERVIEW_TYPE))) {
                OneTimeDownloadLink oneTimeDownloadLink = new OneTimeDownloadLink();
                UUID contentUUID = UUID.randomUUID();
                downloadLinkUUID = contentUUID.toString();
                oneTimeDownloadLink.setUUID(downloadLinkUUID);
                oneTimeDownloadLink.setFileName(mobileAppArtifact.getAttribute(AppMConstants.MOBILE_APP_OVERVIEW_URL));
                oneTimeDownloadLink.setDownloaded(false);
                appRepository.persistOneTimeDownloadLink(oneTimeDownloadLink);
            }
        } catch (RegistryException e) {
            handleException("Error occurred while generating one-time download link for mobile application : " + appId, e);
        }
        return downloadLinkUUID;
    }


    /**
     * Retrieve one-time download link details from database
     * @param UUID UUID of the one-time download link
     * @return
     * @throws AppManagementException
     */
    public OneTimeDownloadLink getOneTimeDownloadLinkDetails(String UUID) throws AppManagementException{
        return appRepository.getOneTimeDownloadLinkDetails(UUID);
    }

    /**
     * Update one-time download link details in database
     * @param oneTimeDownloadLink OneTimeDownloadLink content
     * @throws AppManagementException
     */
    public void updateOneTimeDownloadLinkStatus(OneTimeDownloadLink oneTimeDownloadLink) throws AppManagementException{
        appRepository.updateOneTimeDownloadLinkStatus(oneTimeDownloadLink);
    }

    public String getGatewayEndpoint() {
        Environment gatewayEnvironment = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().
                getAPIManagerConfiguration().getApiGatewayEnvironments().get(0);

        String gatewayUrl = gatewayEnvironment.getApiGatewayEndpoint().split(",")[0];
        return gatewayUrl;
    }

    public String getAppUUIDbyName(String appName, String appVersion, int tenantId) throws AppManagementException {
       return appRepository.getAppUUIDbyName(appName, appVersion, tenantId);
    }

    public String uploadImage(FileContent fileContent) throws AppManagementException {
        UUID contentUUID = UUID.randomUUID();
        String fileExtension = FilenameUtils.getExtension(fileContent.getFileName());
        String filename = generateBinaryUUID() + "." + fileExtension;
        fileContent.setFileName(filename);
        fileContent.setContentType("image/" + fileExtension);
        fileContent.setUuid(contentUUID.toString());
        try {
            fileContent.setContentLength(fileContent.getContent().available());
        } catch (IOException e) {
            handleException("Error occurred while uploading static content", e);
        }
        appRepository.persistStaticContents(fileContent);
        return contentUUID.toString() + File.separator + fileContent.getFileName();
    }

    private static String generateBinaryUUID() {
        SecureRandom secRandom = new SecureRandom();
        byte[] result = new byte[8];
        secRandom.nextBytes(result);
        String uuid = String.valueOf(Hex.encodeHex(result));
        return uuid;
    }
    
    // (??????) 2019.06.03 
    @Override
    public Map<String, Object> getAllPaginatedAPIs(String tenantDomain, int start, int end) throws AppManagementException {
        Map<String, Object> result = new HashMap<String, Object>();
        List<WebApp> apiSortedList = new ArrayList<WebApp>();
        int totalLength = 0;
        boolean isTenantFlowStarted = false;

        try {
            final int maxPaginationLimit = Integer.MAX_VALUE;
            Registry userRegistry;
            boolean isTenantMode = (tenantDomain != null);
            if ((isTenantMode && this.tenantDomain == null) ||
                (isTenantMode && isTenantDomainNotMatching(tenantDomain))) {
                if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                    PrivilegedBarleyContext.startTenantFlow();
                    PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
                    isTenantFlowStarted = true;
                }
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                                                     .getTenantId(tenantDomain);
                AppManagerUtil.loadTenantRegistry(tenantId);
                userRegistry = ServiceReferenceHolder.getInstance().
                        getRegistryService().getGovernanceUserRegistry(BarleyConstants.REGISTRY_ANONNYMOUS_USERNAME,
                                                                       tenantId);
                PrivilegedBarleyContext.getThreadLocalCarbonContext()
                                       .setUsername(BarleyConstants.REGISTRY_ANONNYMOUS_USERNAME);
            } else {
                userRegistry = registry;
                PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);
            }
            PaginationContext.init(start, end, "ASC", AppMConstants.PROVIDER_OVERVIEW_NAME, maxPaginationLimit);
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(userRegistry, AppMConstants.API_KEY);
            Map<String, List<String>> listMap = new HashMap<String, List<String>>();

            if (artifactManager != null) {
            	// (??????) ?????? ????????????????????? ??????
                //GenericArtifact[] genericArtifacts = artifactManager.findGenericArtifacts(listMap);
                //totalLength = PaginationContext.getInstance().getLength();
            	GenericArtifact[] genericArtifacts = artifactManager.getAllGenericArtifacts();
            	totalLength = Integer.MAX_VALUE;
                
                if (genericArtifacts == null || genericArtifacts.length == 0) {
                    result.put("apis", apiSortedList);
                    result.put("totalLength", totalLength);
                    return result;
                }
                // Check to see if we can speculate that there are more APIs to be loaded
                if (maxPaginationLimit == totalLength) {
                    // performance hit
                    --totalLength; // Remove the additional 1 we added earlier when setting max pagination limit
                }
                int tempLength = 0;
                for (GenericArtifact artifact : genericArtifacts) {
                	// ????????? ???????????? ?????? ?????? 	
                	//API api = APIUtil.getAPI(artifact);
                    WebApp api = AppManagerUtil.getAPI(artifact, userRegistry);

                    if (api != null) {
                        apiSortedList.add(api);
                    }
                    tempLength++;
                    if (tempLength >= totalLength) {
                        break;
                    }
                }
                // ?????? ???????????? ?????? ????????? ???????????? 
                //Collections.sort(apiSortedList, new APINameComparator());
            }

        } catch (RegistryException e) {
            handleException("Failed to get all APIs", e);
        } catch (UserStoreException e) {
            handleException("Failed to get all APIs", e);
        } finally {
            PaginationContext.destroy();
            if (isTenantFlowStarted) {
                PrivilegedBarleyContext.endTenantFlow();
            }
        }

        result.put("apis", apiSortedList);
        result.put("totalLength", totalLength);
        return result;
    }
    
    // (??????) 2019.06.03 
    private boolean isTenantDomainNotMatching(String tenantDomain) {
        if (this.tenantDomain != null) {
            return !(this.tenantDomain.equals(tenantDomain));
        }
        return true;
    }
    
    public float getAverageRating(APIIdentifier apiId) throws AppManagementException {
    	return appMDAO.getAverageRating(apiId);
    }
    
    /**
     * Function returns true if the specified API already exists in the registry
     * @param identifier
     * @return
     * @throws AppManagementException
     */
    public boolean checkIfAPIExists(APIIdentifier identifier) throws AppManagementException {
        String apiPath = AppManagerUtil.getAPIPath(identifier);
        try {
            String tenantDomain = MultitenantUtils
                    .getTenantDomain(AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            Registry registry;
            if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                int id = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                        .getTenantId(tenantDomain);
                registry = ServiceReferenceHolder.getInstance().getRegistryService().getGovernanceSystemRegistry(id);
            } else {
                if (this.tenantDomain != null
                        && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(this.tenantDomain)) {
                    registry = ServiceReferenceHolder.getInstance().getRegistryService().getGovernanceUserRegistry(
                            identifier.getProviderName(), MultitenantConstants.SUPER_TENANT_ID);
                } else {
                    if (this.tenantDomain != null
                            && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(this.tenantDomain)) {
                        registry = ServiceReferenceHolder.getInstance().getRegistryService().getGovernanceUserRegistry(
                                identifier.getProviderName(), MultitenantConstants.SUPER_TENANT_ID);
                    } else {
                        registry = this.registry;
                    }
                }
            }

            return registry.resourceExists(apiPath);
        } catch (RegistryException e) {
            handleException("Failed to get API from : " + apiPath, e);
            return false;
        } catch (UserStoreException e) {
            handleException("Failed to get API from : " + apiPath, e);
            return false;
        }
    }
    
    // (??????) 2019.06.10 
    @Override
    public String getAPILifeCycleStatus(APIIdentifier apiIdentifier) throws AppManagementException {
        try {
            PrivilegedBarleyContext.startTenantFlow();
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(this.tenantDomain, true);
            String appArtifactPath = AppManagerUtil.getAPIPath(apiIdentifier);
            Resource appArtifactResource = registry.get(appArtifactPath);
            return appArtifactResource.getProperty(AppMConstants.WEB_APP_LIFECYCLE_STATUS);
        } catch (RegistryException e) {
        	handleException("Failed to get the life cycle status : " + e.getMessage(), e);
            return null;
        } finally {
            PrivilegedBarleyContext.endTenantFlow();
        }
    }
    
    // (??????) 2019.06.10
    public String getLifecycleConfiguration(String tenantDomain) throws AppManagementException {
        boolean isTenantFlowStarted = false;
        try {
            if (tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                isTenantFlowStarted = true;
                PrivilegedBarleyContext.startTenantFlow();
                PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            }
             return AppManagerUtil.getFullLifeCycleData(configRegistry);
        } catch (XMLStreamException e) {
            handleException("Parsing error while getting the lifecycle configuration content.", e);
            return null;
        } catch (RegistryException e) {
            handleException("Registry error while getting the lifecycle configuration content.", e);
            return null;
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedBarleyContext.endTenantFlow();
            }
        }

    }

	@Override
	public void addSubscriber(String username) throws AppManagementException {
		throw new UnsupportedOperationException("Unsubscribe operation is not yet implemented");
	}
    
	
	private void addTags(APIIdentifier api, Set<String> tags) throws AppManagementException {
		
		if(tags==null || tags.isEmpty()) {
			//Tag ????????? ?????? ?????? ????????? ?????? ?????? ??????
			return;
		}
		
		try {
			appMDAO.removeTag(api);
			
			for(String tag : tags) {
				appMDAO.addTag(api, tag);
			}
		} catch (SQLException e) {
			throw new AppManagementException("Error in adding Tags", e);
		}		
	}
    
}