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

package barley.appmgt.impl;

import barley.appmgt.api.APIConsumer;
import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.*;
import barley.appmgt.api.model.Comment;
import barley.appmgt.api.model.Tag;
import barley.appmgt.impl.dto.SubscriptionWorkflowDTO;
import barley.appmgt.impl.dto.TierPermissionDTO;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.APINameComparator;
import barley.appmgt.impl.utils.APIVersionComparator;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.appmgt.impl.workflow.*;
import barley.core.BarleyConstants;
import barley.core.MultitenantConstants;
import barley.core.context.PrivilegedBarleyContext;
import barley.core.multitenancy.MultitenantUtils;
import barley.governance.api.exception.GovernanceException;
import barley.governance.api.generic.GenericArtifactManager;
import barley.governance.api.generic.dataobjects.GenericArtifact;
import barley.registry.core.*;
import barley.registry.core.Collection;
import barley.registry.core.config.RegistryContext;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.pagination.PaginationContext;
import barley.registry.core.session.UserRegistry;
import barley.registry.core.utils.RegistryUtils;
import barley.user.api.UserStoreException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class provides the core WebApp store functionality. It is implemented in a very
 * self-contained and 'pure' manner, without taking requirements like security into account,
 * which are subject to frequent change. Due to this 'pure' nature and the significance of
 * the class to the overall WebApp management functionality, the visibility of the class has
 * been reduced to package level. This means we can still use it for internal purposes and
 * possibly even extend it, but it's totally off the limits of the users. Users wishing to
 * programmatically access this functionality should use one of the extensions of this
 * class which is visible to them. These extensions may add additional features like
 * security to this class.
 */
class APIConsumerImpl extends AbstractAPIManager implements APIConsumer {

    private static final Log log = LogFactory.getLog(APIConsumerImpl.class);
    public static final char COLON_CHAR = ':';
    public static final String EMPTY_STRING = "";

    /* Map to Store APIs against Tag */
    private Map<String, Set<WebApp>> taggedAPIs;
    private boolean isTenantModeStoreView;
    private String requestedTenant;
    private boolean isTagCacheEnabled;
    private Set<Tag> tagSet;
    private long tagCacheValidityTime;
    private long lastUpdatedTime;
    private Object tagCacheMutex = new Object();

    public APIConsumerImpl() throws AppManagementException {
        super();
        readTagCacheConfigs();
    }

    public APIConsumerImpl(String username) throws AppManagementException {
        super(username);
        readTagCacheConfigs();
    }

    private void readTagCacheConfigs() {
        AppManagerConfiguration config = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().
                getAPIManagerConfiguration();
        String enableTagCache = config.getFirstProperty(AppMConstants.API_STORE_TAG_CACHE_DURATION);
        if (enableTagCache == null) {
            isTagCacheEnabled = false;
            tagCacheValidityTime = 0;
        } else {
            isTagCacheEnabled = true;
            tagCacheValidityTime = Long.parseLong(enableTagCache);
        }
    }

    public Subscriber getSubscriber(String subscriberId) throws AppManagementException {
        Subscriber subscriber = null;
        try {
            subscriber = appMDAO.getSubscriber(subscriberId);
        } catch (AppManagementException e) {
            handleException("Failed to get Subscriber", e);
        }
        return subscriber;
    }

    /**
     * Get business owner for a given business owner id.
     * @param businessOwnerId Id of business owner.
     * @return
     * @throws AppManagementException
     */
    @Override
    public BusinessOwner  getBusinessOwner(int businessOwnerId) throws AppManagementException {
        return appMDAO.getBusinessOwner(businessOwnerId, tenantId);
    }

    /**
     * Get business owner for a given business owner id in public store.
     * @param businessOwnerId
     * @param appTenantId
     * @return
     * @throws AppManagementException
     */
    @Override
    public BusinessOwner getBusinessOwnerForAppStore(int businessOwnerId, int appTenantId) throws
                                                                                          AppManagementException {
        return appMDAO.getBusinessOwner(businessOwnerId, appTenantId);
    }

    /**
     * Returns business owner Ids by a prefix of business owner name.
     * @param searchPrefix
     * @param appTenantId
     * @return
     * @throws AppManagementException
     */
    @Override
    public List<String> getBusinessOwnerIdsBySearchPrefix(String searchPrefix, int appTenantId) throws
                                                                                          AppManagementException {
        return appMDAO.getBusinessOwnerIdsBySearchPrefix(searchPrefix, appTenantId);
    }

    /**
     * Returns the set of APIs with the given tag from the taggedAPIs Map
     *
     * @param tag
     * @return
     * @throws barley.appmgt.api.AppManagementException
     */
    public Set<WebApp> getAPIsWithTag(String tag) throws AppManagementException {
        if (taggedAPIs != null) {
            return taggedAPIs.get(tag);
        }
        this.getAllTags(this.tenantDomain);
        if (taggedAPIs != null) {
            return taggedAPIs.get(tag);
        }
        return null;
    }

    /**
     * Returns the set of APIs with the given tag from the taggedAPIs Map
     *
     * @param tag
     * @return
     * @throws barley.appmgt.api.AppManagementException
     */
    public Map<String,Object> getPaginatedAPIsWithTag(String tag,int start,int end) throws
                                                                                    AppManagementException {
        List<WebApp> apiSet = new ArrayList<WebApp>();
        Set<WebApp> resultSet = new TreeSet<WebApp>(new APIVersionComparator());
        Map<String,Object> results = new HashMap<String, Object>();
        Set<WebApp> taggedAPISet=this.getAPIsWithTag(tag);
        if(taggedAPISet!=null){
        if(taggedAPISet.size()<end){
        end=taggedAPISet.size();
        }

        apiSet.addAll(taggedAPISet);
        for(int i=start;i<end;i++) {
          resultSet.add(apiSet.get(i));
        }

            results.put("apis",resultSet);
            results.put("length",taggedAPISet.size());
        }else{
            results.put("apis",null);
            results.put("length",0);

        }
        return results ;
    }


    /**
     * Returns the set of apps with the given tag, retrieved from registry.
     *
     * @param registry Current registry; tenant/SuperTenant.
     * @param tag
     * @param attributeMap
     * @return
     * @throws barley.appmgt.api.AppManagementException
     */
    private Set<WebApp> getAppsWithTag(Registry registry, String tag, String assetType,
                                       Map<String, String> attributeMap)
            throws AppManagementException {
        Set<WebApp> apiSet = new TreeSet<WebApp>(new APINameComparator());
        boolean isTenantFlowStarted = false;
        try {
        	if(tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)){
        		isTenantFlowStarted = true;
                PrivilegedBarleyContext.startTenantFlow();
                PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
        	}
            String resourceByTagQueryPath = RegistryConstants.QUERIES_COLLECTION_PATH + "/resource-by-tag";
            Map<String, String> params = new HashMap<String, String>();
            params.put("1", tag);
            if (AppMConstants.WEBAPP_ASSET_TYPE.equals(assetType) || AppMConstants.SITE_ASSET_TYPE.equals(assetType)){
                params.put("2",AppMConstants.MediaType.WEB_APP);
            } else if(AppMConstants.MOBILE_ASSET_TYPE.equals(assetType)) {
                params.put("2",AppMConstants.MediaType.MOBILE_APP);
            } else {
                handleException("Could not retrieved app for tag.App type :" + assetType +" does not exist");
            }

            params.put(RegistryConstants.RESULT_TYPE_PROPERTY_NAME, RegistryConstants.RESOURCE_UUID_RESULT_TYPE);
            Collection collection = registry.executeQuery(resourceByTagQueryPath, params);

            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, assetType);

            for (String row : collection.getChildren()) {
                boolean isTagRetrievable = false;
                String uuid = row.substring(row.indexOf(";") + 1, row.length());
                GenericArtifact genericArtifact = artifactManager.getGenericArtifact(uuid);
                if(attributeMap == null || attributeMap.isEmpty()) {
                    isTagRetrievable = true;
                } else {
                    //genericArtifact can be null when user doesn't have permission to artifact.
                    if (genericArtifact != null) {
                        String artifactTreatAsASiteValue = genericArtifact.getAttribute(
                                AppMConstants.APP_OVERVIEW_TREAT_AS_A_SITE).toLowerCase();
                        String attributeMapTreatAsASiteValue = attributeMap.get(
                                AppMConstants.APP_OVERVIEW_TREAT_AS_A_SITE).toString().toLowerCase();
                        if (attributeMapTreatAsASiteValue.equals(artifactTreatAsASiteValue)) {
                            isTagRetrievable = true;
                        }
                    }
                }

                if (genericArtifact != null && genericArtifact.getLifecycleState().equals(AppMConstants.APP_LC_PUBLISHED)
                        && isTagRetrievable) {
                    apiSet.add(AppManagerUtil.getAPI(genericArtifact));
                }
            }

        } catch (RegistryException e) {
            handleException("Failed to get WebApp for tag " + tag, e);
        } finally {
        	if (isTenantFlowStarted) {
        		PrivilegedBarleyContext.endTenantFlow();
        	}
        }
        return apiSet;
    }

    /**
     * The method to get APIs to Store view      *
     *
     * @return Set<WebApp>  Set of APIs
     * @throws barley.appmgt.api.AppManagementException
     */
    public Set<WebApp> getAllPublishedAPIs(String tenantDomain) throws AppManagementException {
        SortedSet<WebApp> apiSortedSet = new TreeSet<WebApp>(new APINameComparator());
        SortedSet<WebApp> apiVersionsSortedSet = new TreeSet<WebApp>(new APIVersionComparator());
        try {
            Registry userRegistry;
            boolean isTenantMode=(tenantDomain != null);
            if ((isTenantMode && this.tenantDomain==null) || (isTenantMode && isTenantDomainNotMatching(tenantDomain))) {//Tenant store anonymous mode
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                        .getTenantId(tenantDomain);
                userRegistry = ServiceReferenceHolder.getInstance().
                        getRegistryService().getGovernanceUserRegistry(BarleyConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                userRegistry = registry;
            }
            this.isTenantModeStoreView = isTenantMode;
            this.requestedTenant = tenantDomain;
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(userRegistry, AppMConstants.API_KEY);
            if (artifactManager != null) {
                GenericArtifact[] genericArtifacts = artifactManager.getAllGenericArtifacts();
                if (genericArtifacts == null || genericArtifacts.length == 0) {
                    return apiSortedSet;
                }

                Map<String, WebApp> latestPublishedAPIs = new HashMap<String, WebApp>();
                List<WebApp> multiVersionedAPIs = new ArrayList<WebApp>();
                Comparator<WebApp> versionComparator = new APIVersionComparator();
                Boolean displayMultipleVersions = isAllowDisplayMultipleVersions();
                Boolean displayAPIsWithMultipleStatus = isAllowDisplayAPIsWithMultipleStatus();
                for (GenericArtifact artifact : genericArtifacts) {
                    // adding the WebApp provider can mark the latest WebApp .
                    String status = artifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);

                    WebApp api = null;
                    //Check the app-manager.xml config file entry <DisplayAllAPIs> value is false
                    if (!displayAPIsWithMultipleStatus) {
                        // then we are only interested in published APIs here...
                        if (status.equals(AppMConstants.PUBLISHED)) {
                            api = AppManagerUtil.getAPI(artifact, userRegistry);
                        }
                    } else {   // else we are interested in both deprecated/published APIs here...
                        if (status.equals(AppMConstants.PUBLISHED) || status.equals(AppMConstants.DEPRECATED)) {
                            api = AppManagerUtil.getAPI(artifact, userRegistry);

                        }

                    }
                    if (api != null) {
                        String key;
                        //Check the configuration to allow showing multiple versions of an WebApp true/false
                        if (!displayMultipleVersions) { //If allow only showing the latest version of an WebApp
                        	/*
                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName();
                            WebApp existingAPI = latestPublishedAPIs.get(key);
                            if (existingAPI != null) {
                                // If we have already seen an WebApp with the same name, make sure
                                // this one has a higher version number
                                if (versionComparator.compare(api, existingAPI) > 0) {
                                    latestPublishedAPIs.put(key, api);
                                }
                            } else {
                                // We haven't seen this WebApp before
                                latestPublishedAPIs.put(key, api);
                            }
                            */
               
                        	key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId().getVersion();
                        	latestPublishedAPIs.put(key, api);
                        } else { //If allow showing multiple versions of an WebApp
                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId()
                                    .getVersion();
                            multiVersionedAPIs.add(api);
                        }
                    }
                }
                if (!displayMultipleVersions) {
                    for (WebApp api : latestPublishedAPIs.values()) {
                        apiSortedSet.add(api);
                    }
                    return apiSortedSet;
                } else {
                    for (WebApp api : multiVersionedAPIs) {
                        apiVersionsSortedSet.add(api);
                    }
                    return apiVersionsSortedSet;
                }
            }

        } catch (RegistryException e) {
            handleException("Failed to get all published APIs", e);
        } catch (UserStoreException e) {
            handleException("Failed to get all published APIs", e);
        }
        return apiSortedSet;

    }


    /**
     * The method to get APIs to Store view      *
     *
     * @return Set<WebApp>  Set of APIs
     * @throws barley.appmgt.api.AppManagementException
     */
    public Map<String,Object> getAllPaginatedPublishedAPIs(String tenantDomain,int start,int end) throws
                                                                                                  AppManagementException {
    	// (??????)
    	/*
    	Boolean displayAPIsWithMultipleStatus = isAllowDisplayAPIsWithMultipleStatus();
    	Map<String, List<String>> listMap = new HashMap<String, List<String>>();
        //Check the app-manager.xml config file entry <DisplayAllAPIs> value is false
        if (!displayAPIsWithMultipleStatus) {
            //Create the search attribute map
            listMap.put(AppMConstants.API_OVERVIEW_STATUS, new ArrayList<String>() {{
                add(AppMConstants.PUBLISHED);
            }});
        } else{
            return getAllPaginatedAPIs(tenantDomain, start, end);
        }
        */
        
        Map<String,Object> result=new HashMap<String, Object>();
        //SortedSet<WebApp> apiSortedSet = new TreeSet<WebApp>(new APINameComparator());
        //SortedSet<WebApp> apiVersionsSortedSet = new TreeSet<WebApp>(new APIVersionComparator());
        // (??????) ?????? ????????? ???????????? ??????????????? ?????? ???????????? ???????????? ?????????.
        List<WebApp> apiSortedSet = new ArrayList<WebApp>();
        List<WebApp> apiVersionsSortedSet = new ArrayList<WebApp>();
        
        int totalLength=0;
        try {
            Registry userRegistry;
            boolean isTenantMode=(tenantDomain != null);
            if ((isTenantMode && this.tenantDomain==null) || (isTenantMode && isTenantDomainNotMatching(tenantDomain))) {//Tenant store anonymous mode
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                        .getTenantId(tenantDomain);
                userRegistry = ServiceReferenceHolder.getInstance().
                        getRegistryService().getGovernanceUserRegistry(BarleyConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                userRegistry = registry;
            }
            this.isTenantModeStoreView = isTenantMode;
            this.requestedTenant = tenantDomain;
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);

            //(??????) ????????? ???????????? ?????? ????????? ??????
            //Map<String, WebApp> latestPublishedAPIs = new HashMap<String, WebApp>();
            Map<String, WebApp> latestPublishedAPIs = new LinkedHashMap<String, WebApp>();
            List<WebApp> multiVersionedAPIs = new ArrayList<WebApp>();
            Comparator<WebApp> versionComparator = new APIVersionComparator();
            Boolean displayMultipleVersions = isAllowDisplayMultipleVersions();
            
            PaginationContext.init(start, end, "ASC", AppMConstants.API_OVERVIEW_NAME, Integer.MAX_VALUE);
            
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(userRegistry, AppMConstants.API_KEY);
            if (artifactManager != null) {
            	// (??????) - ????????????????????? ??????????????? ??????????????? ??????
                //GenericArtifact[] genericArtifacts = artifactManager.findGenericArtifacts(listMap);
                //totalLength=PaginationContext.getInstance().getLength();
                String status = APIStatus.PUBLISHED.getStatus();
            	GenericArtifact[] genericArtifacts = artifactManager.getAllGenericArtifactsByLifecycleStatus(AppMConstants.WEBAPP_LIFE_CYCLE, status);
            	totalLength = Integer.MAX_VALUE;
            	
                if (genericArtifacts == null || genericArtifacts.length == 0) {
                    result.put("apis", apiSortedSet);
                    result.put("totalLength", totalLength);
                    return result;
                }

                for (GenericArtifact artifact : genericArtifacts) {
                    // adding the WebApp provider can mark the latest WebApp .
                    //String status = artifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);

                    WebApp api  = AppManagerUtil.getAPI(artifact, userRegistry);

                    if (api != null) {
                        String key;
                        //Check the configuration to allow showing multiple versions of an WebApp true/false
                        if (!displayMultipleVersions) { //If allow only showing the latest version of an WebApp
                        	/*
                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName();
                            WebApp existingAPI = latestPublishedAPIs.get(key);
                            if (existingAPI != null) {
                                // If we have already seen an WebApp with the same name, make sure
                                // this one has a higher version number
                                if (versionComparator.compare(api, existingAPI) > 0) {
                                    latestPublishedAPIs.put(key, api);
                                }
                            } else {
                                // We haven't seen this WebApp before
                                latestPublishedAPIs.put(key, api);
                            }
                            */
                        	key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId().getVersion();
                        	latestPublishedAPIs.put(key, api);
                        } else { //If allow showing multiple versions of an WebApp
                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId()
                                    .getVersion();
                            multiVersionedAPIs.add(api);
                        }
                    }
                }
                if (!displayMultipleVersions) {
                    for (WebApp api : latestPublishedAPIs.values()) {
                        apiSortedSet.add(api);
                    }
                    result.put("apis",apiSortedSet);
                    result.put("totalLength",totalLength);
                    return result;

                } else {
                    for (WebApp api : multiVersionedAPIs) {
                        apiVersionsSortedSet.add(api);
                    }
                    result.put("apis",apiVersionsSortedSet);
                    result.put("totalLength",totalLength);
                    return result;

                }
            }

        } catch (RegistryException e) {
            handleException("Failed to get all published APIs", e);
        } catch (UserStoreException e) {
            handleException("Failed to get all published APIs", e);
        }finally {
            PaginationContext.destroy();
        }
        result.put("apis",apiSortedSet);
        result.put("totalLength",totalLength);
        return result;

    }
    
    /**
     * The method to get All PUBLISHED and DEPRECATED APIs, to Store view      
     *
     * @return Set<WebApp>  Set of APIs
     * @throws barley.appmgt.api.AppManagementException
     */
    public Map<String,Object> getAllPaginatedAPIs(String tenantDomain,int start,int end) throws
                                                                                         AppManagementException {
        Map<String,Object> result=new HashMap<String, Object>();
        SortedSet<WebApp> apiSortedSet = new TreeSet<WebApp>(new APINameComparator());
        SortedSet<WebApp> apiVersionsSortedSet = new TreeSet<WebApp>(new APIVersionComparator());
        int totalLength=0;
        try {
            Registry userRegistry;
            boolean isTenantMode=(tenantDomain != null);
            if ((isTenantMode && this.tenantDomain==null) || (isTenantMode && isTenantDomainNotMatching(tenantDomain))) {//Tenant store anonymous mode
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                        .getTenantId(tenantDomain);
                userRegistry = ServiceReferenceHolder.getInstance().
                        getRegistryService().getGovernanceUserRegistry(BarleyConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                userRegistry = registry;
            }
            this.isTenantModeStoreView = isTenantMode;
            this.requestedTenant = tenantDomain;
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);

            Map<String, WebApp> latestPublishedAPIs = new HashMap<String, WebApp>();
            List<WebApp> multiVersionedAPIs = new ArrayList<WebApp>();
            Comparator<WebApp> versionComparator = new APIVersionComparator();
            Boolean displayMultipleVersions = isAllowDisplayMultipleVersions();
            
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(userRegistry, AppMConstants.API_KEY);
            
            PaginationContext.init(start, end, "ASC", AppMConstants.API_OVERVIEW_NAME, Integer.MAX_VALUE);
                       
           
            boolean noPublishedAPIs = false;
            if (artifactManager != null) {
            	
            	//Create the search attribute map for PUBLISHED APIs
            	Map<String, List<String>> listMap = new HashMap<String, List<String>>();
                listMap.put(AppMConstants.API_OVERVIEW_STATUS, new ArrayList<String>() {{
                        add(AppMConstants.PUBLISHED);
                    }});
                
                GenericArtifact[] genericArtifacts = artifactManager.findGenericArtifacts(listMap);
                totalLength = PaginationContext.getInstance().getLength();
                if (genericArtifacts == null || genericArtifacts.length == 0) {
                	noPublishedAPIs = true;
                }
                int publishedAPICount = 0;
                for (GenericArtifact artifact : genericArtifacts) {
                    // adding the WebApp provider can mark the latest WebApp .
                    String status = artifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);

                    WebApp api  = AppManagerUtil.getAPI(artifact, userRegistry);

                    if (api != null) {
                        String key;
                        //Check the configuration to allow showing multiple versions of an WebApp true/false
                        if (!displayMultipleVersions) { //If allow only showing the latest version of an WebApp
                        	/*
                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName();
                            WebApp existingAPI = latestPublishedAPIs.get(key);
                            if (existingAPI != null) {
                                // If we have already seen an WebApp with the same name, make sure
                                // this one has a higher version number
                                if (versionComparator.compare(api, existingAPI) > 0) {
                                    latestPublishedAPIs.put(key, api);
                                }
                            } else {
                                // We haven't seen this WebApp before
                                latestPublishedAPIs.put(key, api);
                            }
                            */
                        	key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId().getVersion();
                        	latestPublishedAPIs.put(key, api);
                        } else { //If allow showing multiple versions of an WebApp
                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId()
                                    .getVersion();
                            multiVersionedAPIs.add(api);
                        }
                    }
                }
                if (!displayMultipleVersions) {
                	publishedAPICount = latestPublishedAPIs.size();
                } else {
                	publishedAPICount = multiVersionedAPIs.size();
                }
                if ((start + end) > publishedAPICount) {
                	if (publishedAPICount > 0) {
                		/*Starting to retrieve DEPRECATED APIs*/
                		start = 0;
                		/* publishedAPICount is always less than end*/
                		end = end - publishedAPICount;
                	} else {
                		start = start - totalLength;
                	}
                	PaginationContext.init(start, end, "ASC", AppMConstants.API_OVERVIEW_NAME, Integer.MAX_VALUE);
	                //Create the search attribute map for DEPRECATED APIs
	                Map<String, List<String>> listMapForDeprecatedAPIs = new HashMap<String, List<String>>();
	                listMapForDeprecatedAPIs.put(AppMConstants.API_OVERVIEW_STATUS, new ArrayList<String>() {{
	                        add(AppMConstants.DEPRECATED);
	                    }});
	                
	                GenericArtifact[] genericArtifactsForDeprecatedAPIs = artifactManager.findGenericArtifacts(listMapForDeprecatedAPIs);
	                totalLength = totalLength + PaginationContext.getInstance().getLength();
	                if ((genericArtifactsForDeprecatedAPIs == null || genericArtifactsForDeprecatedAPIs.length == 0) && noPublishedAPIs) {
	                	result.put("apis",apiSortedSet);
	                    result.put("totalLength",totalLength);
	                    return result;
	                }
	
	                for (GenericArtifact artifact : genericArtifactsForDeprecatedAPIs) {
	                    // adding the WebApp provider can mark the latest WebApp .
	                    String status = artifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);
	
	                    WebApp api  = AppManagerUtil.getAPI(artifact, userRegistry);
	
	                    if (api != null) {
	                        String key;
	                        //Check the configuration to allow showing multiple versions of an WebApp true/false
	                        if (!displayMultipleVersions) { //If allow only showing the latest version of an WebApp
	                        	/*
	                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName();
	                            WebApp existingAPI = latestPublishedAPIs.get(key);
	                            if (existingAPI != null) {
	                                // If we have already seen an WebApp with the same name, make sure
	                                // this one has a higher version number
	                                if (versionComparator.compare(api, existingAPI) > 0) {
	                                    latestPublishedAPIs.put(key, api);
	                                }
	                            } else {
	                                // We haven't seen this WebApp before
	                                latestPublishedAPIs.put(key, api);
	                            }
	                            */
	                        	key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId().getVersion();
	                        	latestPublishedAPIs.put(key, api);
	                        } else { //If allow showing multiple versions of an WebApp
	                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId()
	                                    .getVersion();
	                            multiVersionedAPIs.add(api);
	                        }
	                    }
	                }
                }
                
                if (!displayMultipleVersions) {
                    for (WebApp api : latestPublishedAPIs.values()) {
                        apiSortedSet.add(api);
                    }
                    result.put("apis",apiSortedSet);
                    result.put("totalLength",totalLength);
                    return result;

                } else {
                    for (WebApp api : multiVersionedAPIs) {
                        apiVersionsSortedSet.add(api);
                    }
                    result.put("apis",apiVersionsSortedSet);
                    result.put("totalLength",totalLength);
                    return result;

                }
            }

        } catch (RegistryException e) {
            handleException("Failed to get all published APIs", e);
        } catch (UserStoreException e) {
            handleException("Failed to get all published APIs", e);
        }finally {
            PaginationContext.destroy();
        }
        result.put("apis",apiSortedSet);
        result.put("totalLength",totalLength);
        return result;

    }

    private <T> T[] concatArrays(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }


    public Set<WebApp> getTopRatedAPIs(int limit) throws AppManagementException {
        int returnLimit = 0;
        SortedSet<WebApp> apiSortedSet = new TreeSet<WebApp>(new APINameComparator());
        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, AppMConstants.API_KEY);
            GenericArtifact[] genericArtifacts = artifactManager.getAllGenericArtifacts();
            if (genericArtifacts == null || genericArtifacts.length == 0) {
                return apiSortedSet;
            }
            for (GenericArtifact genericArtifact : genericArtifacts) {
                String status = genericArtifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);
                if (status.equals(AppMConstants.PUBLISHED)) {
                    String artifactPath = genericArtifact.getPath();

                    float rating = registry.getAverageRating(artifactPath);
                    if (rating > AppMConstants.TOP_TATE_MARGIN && (returnLimit < limit)) {
                        returnLimit++;
                        apiSortedSet.add(AppManagerUtil.getAPI(genericArtifact, registry));
                    }
                }
            }
        } catch (RegistryException e) {
            handleException("Failed to get top rated WebApp", e);
        }
        return apiSortedSet;
    }

    public float getAverageRating(String uuid, String assetType) throws AppManagementException {
        float rating = 0;
        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, assetType);
            GenericArtifact genericArtifact = artifactManager.getGenericArtifact(uuid);
            rating = registry.getAverageRating(genericArtifact.getPath());
        } catch (RegistryException e) {
            handleException("Failed to retrieve rating", e);
        }
        return rating;
    }
    
    @Override
    public void rateAPI(APIIdentifier apiId, APIRating rating,
                        String user) throws AppManagementException {
    	appMDAO.addRating(apiId, rating.getRating(), user);
    }

    @Override
    public void removeAPIRating(APIIdentifier apiId, String user) throws AppManagementException {
    	appMDAO.removeAPIRating(apiId, user);
    }

    @Override
    public int getUserRating(APIIdentifier apiId, String user) throws AppManagementException {
        return appMDAO.getUserRating(apiId, user);
    }
    
    /**
     * Get the recently added APIs set
     *
     * @param limit no limit. Return everything else, limit the return list to specified value.
     * @return Set<WebApp>
     * @throws barley.appmgt.api.AppManagementException
     */
    public Set<WebApp> getRecentlyAddedAPIs(int limit, String tenantDomain)
            throws AppManagementException {
        SortedSet<WebApp> recentlyAddedAPIs = new TreeSet<WebApp>(new APINameComparator());
        SortedSet<WebApp> recentlyAddedAPIsWithMultipleVersions = new TreeSet<WebApp>(new APIVersionComparator());
        Registry userRegistry = null;
        String latestAPIQueryPath = null;
        try {
            boolean isTenantMode=(tenantDomain != null);
            if ((isTenantMode && this.tenantDomain==null) || (isTenantMode && isTenantDomainNotMatching(tenantDomain))) {//Tenant based store anonymous mode
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                        .getTenantId(tenantDomain);
                userRegistry = ServiceReferenceHolder.getInstance().
                        getRegistryService().getGovernanceUserRegistry(BarleyConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                userRegistry = registry;
            }

            latestAPIQueryPath = RegistryConstants.QUERIES_COLLECTION_PATH + "/latest-apis";
            Map<String, String> params = new HashMap<String, String>();
            params.put(RegistryConstants.RESULT_TYPE_PROPERTY_NAME, RegistryConstants.RESOURCES_RESULT_TYPE);
            if (userRegistry != null) {
                Collection collection = userRegistry.executeQuery(latestAPIQueryPath, params);
                int resultSetSize = Math.min(limit, collection.getChildCount());
                String[] recentlyAddedAPIPaths = new String[resultSetSize];
                for (int i = 0; i < resultSetSize; i++) {
                    recentlyAddedAPIPaths[i] = collection.getChildren()[i];
                }
                Set<WebApp> apisSet = getAPIs(userRegistry, limit, recentlyAddedAPIPaths);
                if (!isAllowDisplayMultipleVersions()) {

                recentlyAddedAPIs.addAll(apisSet);
                    return recentlyAddedAPIs;
                }else{
                recentlyAddedAPIsWithMultipleVersions.addAll(apisSet);
                return recentlyAddedAPIsWithMultipleVersions;
                }
            }
         return recentlyAddedAPIs;



        } catch (RegistryException e) {
        	try {
        		//Before a tenant login to the store or publisher at least one time, 
        		//a registry exception is thrown when the tenant store is accessed the store in anonymous mode.
        		//This fix checks whether query resource available in the registry. If not
        		// give a warn. 
				if (!userRegistry.resourceExists(latestAPIQueryPath)) {
					log.warn("Failed to retrieve recently added WebApp query resource at " + latestAPIQueryPath);
					return recentlyAddedAPIs;
				}
			} catch (RegistryException e1) {
				//ignore
			}
            handleException("Failed to get recently added APIs", e);
            return null;
        } catch (barley.user.api.UserStoreException e) {
            handleException("Failed to get recently added APIs", e);
            return null;
        }


    }

    public Set<Tag> getAllTags(String requestedTenantDomain) throws AppManagementException {
    	// (??????) assetType??? null?????? ????????? ????????????. AppMConstants.WEBAPP_ASSET_TYPE?????? ????????? ?????? 
//        return getAllTags(requestedTenantDomain, null, null);
    	return getAllTags(requestedTenantDomain, AppMConstants.WEBAPP_ASSET_TYPE, null);
    }

    /**
     * @param requestedTenantDomain
     * @param assetType Currently we don't use asset type. Asset type could be webapp, mobileapp or any other asset type.
     * @param attributeMap Attribute map for the give assetType.
     * @return matching tag set which qualified the conditions of assetTye and attributeMap.
     * @throws AppManagementException
     */
    public Set<Tag> getAllTags(String requestedTenantDomain, String assetType, Map<String, String> attributeMap) throws
            AppManagementException {

        this.isTenantModeStoreView = (requestedTenantDomain != null);

        if(requestedTenantDomain != null){
            this.requestedTenant = requestedTenantDomain;
        }

        /* We keep track of the lastUpdatedTime of the TagCache to determine its freshness.
         */
        long lastUpdatedTimeAtStart = lastUpdatedTime;
        long currentTimeAtStart = System.currentTimeMillis();
        if(isTagCacheEnabled && ( (currentTimeAtStart- lastUpdatedTimeAtStart) < tagCacheValidityTime)){
            if(tagSet != null){
                return tagSet;
            }
        }

        Map<String, Set<WebApp>> tempTaggedAPIs = new HashMap<String, Set<WebApp>>();
        TreeSet<Tag> tempTagSet = new TreeSet<Tag>(new Comparator<Tag>() {
            @Override
            public int compare(Tag o1, Tag o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        Registry userRegistry = null;
        String tagsQueryPath = null;
        try {
            tagsQueryPath = RegistryConstants.QUERIES_COLLECTION_PATH + "/tag-summary-appmgt";
            Map<String, String> params = new HashMap<String, String>();
            params.put(RegistryConstants.RESULT_TYPE_PROPERTY_NAME, RegistryConstants.TAG_SUMMARY_RESULT_TYPE);

            if (AppMConstants.WEBAPP_ASSET_TYPE.equals(assetType) || AppMConstants.SITE_ASSET_TYPE.equals(assetType)){
                params.put("1",AppMConstants.MediaType.WEB_APP);
                params.put("2",AppMConstants.WEB_APP_LIFECYCLE_STATUS);
                params.put("3",AppMConstants.APP_LC_PUBLISHED);
            } else if(AppMConstants.MOBILE_ASSET_TYPE.equals(assetType)) {
                params.put("1",AppMConstants.MediaType.MOBILE_APP);
                params.put("2",AppMConstants.MOBILE_APP_LIFECYCLE_STATUS);
                params.put("3",AppMConstants.APP_LC_PUBLISHED);
            } else {
                handleException("Could not retrieved tags.App type :" + assetType +" does not exist");
            }

            if ((this.isTenantModeStoreView && this.tenantDomain==null) || (this.isTenantModeStoreView && isTenantDomainNotMatching(requestedTenantDomain))) {//Tenant based store anonymous mode
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                        .getTenantId(this.requestedTenant);
                userRegistry = ServiceReferenceHolder.getInstance().
                        getRegistryService().getGovernanceUserRegistry(BarleyConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                userRegistry = registry;
            }
            Collection collection = userRegistry.executeQuery(tagsQueryPath, params);
            for (String fullTag : collection.getChildren()) {
                //remove hardcoded path value
                String tagName = fullTag.substring(fullTag.indexOf(";") + 1, fullTag.indexOf(COLON_CHAR));

                Set<WebApp> apisWithTag = getAppsWithTag(userRegistry, tagName, assetType, attributeMap);
                    /* Add the APIs against the tag name */
                    if (apisWithTag.size() != 0) {
                        if (tempTaggedAPIs.containsKey(tagName)) {
                            for (WebApp api : apisWithTag) {
                                tempTaggedAPIs.get(tagName).add(api);
                            }
                        } else {
                            tempTaggedAPIs.put(tagName, apisWithTag);
                        }
                    }
            }

            if(tempTaggedAPIs != null){
                Iterator<Map.Entry<String,Set<WebApp>>>  entryIterator = tempTaggedAPIs.entrySet().iterator();
                while (entryIterator.hasNext()){
                    Map.Entry<String,Set<WebApp>> entry = entryIterator.next();
                    tempTagSet.add(new Tag(entry.getKey(),entry.getValue().size()));
                }
            }
            synchronized (tagCacheMutex) {
                lastUpdatedTime = System.currentTimeMillis();
                this.taggedAPIs = tempTaggedAPIs;
                this.tagSet = tempTagSet;
            }

        } catch (RegistryException e) {
        	try {
        		//Before a tenant login to the store or publisher at least one time, 
        		//a registry exception is thrown when the tenant store is accessed in anonymous mode.
        		//This fix checks whether query resource available in the registry. If not
        		// give a warn. 
				if (!userRegistry.resourceExists(tagsQueryPath)) {
					log.warn("Failed to retrieve tags query resource at " + tagsQueryPath);
					return tagSet;
				}
			} catch (RegistryException e1) {
				//ignore
			}
            handleException("Failed to get all the tags", e);
        } catch (UserStoreException e) {
            handleException("Failed to get all the tags", e);
        }
        return tagSet;
    }

    public Set<WebApp> getPublishedAPIsByProvider(String providerId, int limit)
            throws AppManagementException {
        SortedSet<WebApp> apiSortedSet = new TreeSet<WebApp>(new APINameComparator());
        SortedSet<WebApp> apiVersionsSortedSet = new TreeSet<WebApp>(new APIVersionComparator());
        try {
            Map<String, WebApp> latestPublishedAPIs = new HashMap<String, WebApp>();
            List<WebApp> multiVersionedAPIs = new ArrayList<WebApp>();
            Comparator<WebApp> versionComparator = new APIVersionComparator();
            Boolean displayMultipleVersions = isAllowDisplayMultipleVersions();
            Boolean displayAPIsWithMultipleStatus = isAllowDisplayAPIsWithMultipleStatus();
            String providerPath = AppMConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
            		// (??????) ???????????? 
//                    providerId;
            		AppManagerUtil.replaceEmailDomain(providerId);
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                    AppMConstants.API_KEY);
            Association[] associations = registry.getAssociations(providerPath,
                    AppMConstants.PROVIDER_ASSOCIATION);
            if (associations.length < limit || limit == -1) {
                limit = associations.length;
            }
            for (int i = 0; i < limit; i++) {
                Association association = associations[i];
                String apiPath = association.getDestinationPath();
                Resource resource = registry.get(apiPath);
                String apiArtifactId = resource.getUUID();
                if (apiArtifactId != null) {
                    GenericArtifact artifact = artifactManager.getGenericArtifact(apiArtifactId);
                    // check the WebApp status
                    String status = artifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);

                    WebApp api = null;
                    //Check the app-manager.xml config file entry <DisplayAllAPIs> value is false
                    if (!displayAPIsWithMultipleStatus) {
                        // then we are only interested in published APIs here...
                        if (status.equals(AppMConstants.PUBLISHED)) {
                        	api = AppManagerUtil.getAPI(artifact, registry);
                        }
                    } else {   // else we are interested in both deprecated/published APIs here...
                        if (status.equals(AppMConstants.PUBLISHED) || status.equals(AppMConstants.DEPRECATED)) {
                        	api = AppManagerUtil.getAPI(artifact, registry);

                        }

                    }
                    if (api != null) {
                        String key;
                        //Check the configuration to allow showing multiple versions of an WebApp true/false
                        if (!displayMultipleVersions) { //If allow only showing the latest version of an WebApp
                        	/*
                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName();
                            WebApp existingAPI = latestPublishedAPIs.get(key);
                            if (existingAPI != null) {
                                // If we have already seen an WebApp with the same name, make sure
                                // this one has a higher version number
                                if (versionComparator.compare(api, existingAPI) > 0) {
                                    latestPublishedAPIs.put(key, api);
                                }
                            } else {
                                // We haven't seen this WebApp before
                                latestPublishedAPIs.put(key, api);
                            }
                            */
                        	key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId().getVersion();
                        	latestPublishedAPIs.put(key, api);
                        } else { //If allow showing multiple versions of an WebApp
                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId()
                                    .getVersion();
                            multiVersionedAPIs.add(api);
                        }
                    }
                } else {
                    throw new GovernanceException("artifact id is null of " + apiPath);
                }
            }
            if (!displayMultipleVersions) {
                for (WebApp api : latestPublishedAPIs.values()) {
                    apiSortedSet.add(api);
                }
                return apiSortedSet;
            } else {
                for (WebApp api : multiVersionedAPIs) {
                    apiVersionsSortedSet.add(api);
                }
                return apiVersionsSortedSet;
            }

        } catch (RegistryException e) {
            handleException("Failed to get Published APIs for provider : " + providerId, e);
            return null;
        }


    }

    public Set<WebApp> getPublishedAPIsByProvider(String providerId, String loggedUsername, int limit)
            throws AppManagementException {
        SortedSet<WebApp> apiSortedSet = new TreeSet<WebApp>(new APINameComparator());
        SortedSet<WebApp> apiVersionsSortedSet = new TreeSet<WebApp>(new APIVersionComparator());
        try {
            Map<String, WebApp> latestPublishedAPIs = new HashMap<String, WebApp>();
            List<WebApp> multiVersionedAPIs = new ArrayList<WebApp>();
            Comparator<WebApp> versionComparator = new APIVersionComparator();
            Boolean allowMultipleVersions = isAllowDisplayMultipleVersions();
            Boolean showAllAPIs = isAllowDisplayAPIsWithMultipleStatus();

            String providerDomain = MultitenantUtils.getTenantDomain(AppManagerUtil.replaceEmailDomainBack(providerId));
            int id = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(providerDomain);
            Registry registry = ServiceReferenceHolder.getInstance().
                    getRegistryService().getGovernanceSystemRegistry(id);

            barley.user.api.AuthorizationManager manager = ServiceReferenceHolder.getInstance().
                    getRealmService().getTenantUserRealm(id).
                    getAuthorizationManager();

            String providerPath = AppMConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
            		// (??????) ???????????? 
//                                  providerId;
            						AppManagerUtil.replaceEmailDomain(providerId);
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                                AppMConstants.API_KEY);
            Association[] associations = registry.getAssociations(providerPath,
                                                                  AppMConstants.PROVIDER_ASSOCIATION);
            int publishedAPICount = 0;

            for (Association association1 : associations) {

                if (publishedAPICount >= limit) {
                    break;
                }

                Association association = association1;
                String apiPath = association.getDestinationPath();

                Resource resource;
                String path = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                                                            RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH + apiPath);
                boolean checkAuthorized = false;
                String userNameWithoutDomain = loggedUsername;

                String loggedDomainName = "";
                if (!"".equals(loggedUsername) &&
                    !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(super.tenantDomain)) {
                    String[] nameParts = loggedUsername.split("@");
                    loggedDomainName = nameParts[1];
                    userNameWithoutDomain = nameParts[0];
                }

                if (loggedUsername.equals("")) {
                    // Anonymous user is viewing.
                    checkAuthorized = manager.isRoleAuthorized(AppMConstants.ANONYMOUS_ROLE, path, ActionConstants.GET);
                } else {
                    // Some user is logged in.
                    checkAuthorized = manager.isUserAuthorized(userNameWithoutDomain, path, ActionConstants.GET);
                }

                String apiArtifactId = null;
                if (checkAuthorized) {
                    resource = registry.get(apiPath);
                    apiArtifactId = resource.getUUID();
                }

                if (apiArtifactId != null) {
                    GenericArtifact artifact = artifactManager.getGenericArtifact(apiArtifactId);

                    // check the WebApp status
                    String status = artifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);

                    WebApp api = null;
                    //Check the app-manager.xml config file entry <DisplayAllAPIs> value is false
                    if (!showAllAPIs) {
                        // then we are only interested in published APIs here...
                        if (status.equals(AppMConstants.PUBLISHED)) {
                        	api = AppManagerUtil.getAPI(artifact, registry);
                            publishedAPICount++;
                        }
                    } else {   // else we are interested in both deprecated/published APIs here...
                        if (status.equals(AppMConstants.PUBLISHED) || status.equals(AppMConstants.DEPRECATED)) {
                        	api = AppManagerUtil.getAPI(artifact, registry);
                            publishedAPICount++;

                        }

                    }
                    if (api != null) {
                        String key;
                        //Check the configuration to allow showing multiple versions of an WebApp true/false
                        if (!allowMultipleVersions) { //If allow only showing the latest version of an WebApp
                        	/*
                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName();
                            WebApp existingAPI = latestPublishedAPIs.get(key);
                            if (existingAPI != null) {
                                // If we have already seen an WebApp with the same name, make sure
                                // this one has a higher version number
                                if (versionComparator.compare(api, existingAPI) > 0) {
                                    latestPublishedAPIs.put(key, api);
                                }
                            } else {
                                // We haven't seen this WebApp before
                                latestPublishedAPIs.put(key, api);
                            }
                            */
                        	key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId().getVersion();
                        	latestPublishedAPIs.put(key, api);
                        } else { //If allow showing multiple versions of an WebApp
                            key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId()
                                    .getVersion();
                            multiVersionedAPIs.add(api);
                        }
                    }
                }
            }
            if (!allowMultipleVersions) {
                for (WebApp api : latestPublishedAPIs.values()) {
                    apiSortedSet.add(api);
                }
                return apiSortedSet;
            } else {
                for (WebApp api : multiVersionedAPIs) {
                    apiVersionsSortedSet.add(api);
                }
                return apiVersionsSortedSet;
            }

        } catch (RegistryException e) {
            handleException("Failed to get Published APIs for provider : " + providerId, e);
            return null;
        } catch (barley.user.core.UserStoreException e) {
            handleException("Failed to get Published APIs for provider : " + providerId, e);
            return null;
        } catch (barley.user.api.UserStoreException e) {
            handleException("Failed to get Published APIs for provider : " + providerId, e);
            return null;
        }

    }

    public Set<WebApp> searchAPI(String searchTerm, String searchType, String requestedTenantDomain)
            throws AppManagementException {
        Set<WebApp> apiSet = new HashSet<WebApp>();
        try {
            Registry userRegistry;
            boolean isTenantMode=(requestedTenantDomain != null);
            if ((isTenantMode && this.tenantDomain==null) || (isTenantMode && isTenantDomainNotMatching(requestedTenantDomain))) {//Tenant store anonymous mode
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                        .getTenantId(requestedTenantDomain);
                userRegistry = ServiceReferenceHolder.getInstance().
                        getRegistryService().getGovernanceUserRegistry(BarleyConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                userRegistry = this.registry;
            }
            apiSet.addAll(searchAPI(userRegistry, searchTerm, searchType));

        } catch (Exception e) {
            handleException("Failed to Search APIs", e);
        }
        return apiSet;
    }

    public Set<WebApp> searchAPI(Registry registry, String searchTerm, String searchType) throws
                                                                                          AppManagementException {
        SortedSet<WebApp> apiSet = new TreeSet<WebApp>(new APINameComparator());
        String regex = "(?i)[\\w.|-]*" + searchTerm.trim() + "[\\w.|-]*";
        Pattern pattern;
        Matcher matcher;
        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, AppMConstants.API_KEY);
            if (artifactManager != null) {
                GenericArtifact[] genericArtifacts = artifactManager
                        .getAllGenericArtifacts();
                if (genericArtifacts == null || genericArtifacts.length == 0) {
                    return apiSet;
                }
                pattern = Pattern.compile(regex);

                for (GenericArtifact artifact : genericArtifacts) {
                    String status = artifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);

                    if (searchType.equalsIgnoreCase("Provider")) {
                        String api = AppManagerUtil.replaceEmailDomainBack(artifact.getAttribute(AppMConstants.API_OVERVIEW_PROVIDER));
                        matcher = pattern.matcher(api);
                    } else if (searchType.equalsIgnoreCase("Version")) {
                        String api = artifact.getAttribute(AppMConstants.API_OVERVIEW_VERSION);
                        matcher = pattern.matcher(api);
                    } else if (searchType.equalsIgnoreCase("Context")) {
                        String api = artifact.getAttribute(AppMConstants.API_OVERVIEW_CONTEXT);
                        matcher = pattern.matcher(api);
                    } else {
                        String apiName = artifact.getAttribute(AppMConstants.API_OVERVIEW_NAME);
                        matcher = pattern.matcher(apiName);
                    }
                    if (isAllowDisplayAPIsWithMultipleStatus()) {
                        if (matcher.find() && (status.equals(AppMConstants.PUBLISHED) || status.equals(AppMConstants.DEPRECATED))) {
                            apiSet.add(AppManagerUtil.getAPI(artifact, registry));
                        }
                    } else {
                        if (matcher.find() && status.equals(AppMConstants.PUBLISHED)) {
                            apiSet.add(AppManagerUtil.getAPI(artifact, registry));
                        }
                    }

                }
            }
        } catch (RegistryException e) {
            handleException("Failed to search APIs with type", e);
        }
        return apiSet;
    }

    public Map<String,Object> searchPaginatedAPIs(String searchTerm, String searchType, String requestedTenantDomain,int start,int end)
            throws AppManagementException {
        Map<String,Object> result = new HashMap<String,Object>();
        try {
            Registry userRegistry;
            boolean isTenantMode=(requestedTenantDomain != null);
            if ((isTenantMode && this.tenantDomain==null) || (isTenantMode && isTenantDomainNotMatching(requestedTenantDomain))) {//Tenant store anonymous mode
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                        .getTenantId(requestedTenantDomain);
                userRegistry = ServiceReferenceHolder.getInstance().
                        getRegistryService().getGovernanceUserRegistry(BarleyConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                userRegistry = this.registry;
            }
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(this.username);
            result=searchPaginatedAPIs(userRegistry, searchTerm, searchType,start,end);

        } catch (Exception e) {
            handleException("Failed to Search APIs", e);
        }
        return result;
    }

    public Map<String,Object> searchPaginatedAPIs(Registry registry, String searchTerm, String searchType,int start,int end) throws
                                                                                                                             AppManagementException {
        SortedSet<WebApp> apiSet = new TreeSet<WebApp>(new APINameComparator());
        List<WebApp> apiList = new ArrayList<WebApp>();
        String regex = "(?i)[\\w.|-]*" + searchTerm.trim() + "[\\w.|-]*";
        Pattern pattern;
        Matcher matcher;
        Map<String,Object> result=new HashMap<String, Object>();
        try {

            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, AppMConstants.API_KEY);
            if (artifactManager != null) {
                GenericArtifact[] genericArtifacts = artifactManager
                        .getAllGenericArtifacts();
                if (genericArtifacts == null || genericArtifacts.length == 0) {

                    result.put("apis",apiSet);
                    result.put("length",0);
                    return result;
                }
                pattern = Pattern.compile(regex);

                for (GenericArtifact artifact : genericArtifacts) {
                    String status = artifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);

                    if (searchType.equalsIgnoreCase("Provider")) {
                        String api = AppManagerUtil.replaceEmailDomainBack(artifact.getAttribute(AppMConstants.API_OVERVIEW_PROVIDER));
                        matcher = pattern.matcher(api);
                    } else if (searchType.equalsIgnoreCase("Version")) {
                        String api = artifact.getAttribute(AppMConstants.API_OVERVIEW_VERSION);
                        matcher = pattern.matcher(api);
                    } else if (searchType.equalsIgnoreCase("Context")) {
                        String api = artifact.getAttribute(AppMConstants.API_OVERVIEW_CONTEXT);
                        matcher = pattern.matcher(api);
                    } else {
                        String apiName = artifact.getAttribute(AppMConstants.API_OVERVIEW_NAME);
                        matcher = pattern.matcher(apiName);
                    }
                    if (isAllowDisplayAPIsWithMultipleStatus()) {
                        if (matcher.find() && (status.equals(AppMConstants.PUBLISHED) || status.equals(AppMConstants.DEPRECATED))) {
                            apiList.add(AppManagerUtil.getAPI(artifact, registry));
                        }
                    } else {
                        if (matcher.find() && status.equals(AppMConstants.PUBLISHED)) {
                            apiList.add(AppManagerUtil.getAPI(artifact, registry));
                        }
                    }

                }
                if(apiList.size()<end){
                    end=apiList.size();
                }
                for(int i=start;i<end;i++){
                   apiSet.add(apiList.get(i));


                }
            }
        } catch (RegistryException e) {
            handleException("Failed to search APIs with type", e);
        }
        result.put("apis",apiSet);
        result.put("length",apiList.size());
        return result;
    }

    public Set<SubscribedAPI> getSubscribedAPIs(Subscriber subscriber) throws
                                                                       AppManagementException {
        Set<SubscribedAPI> originalSubscribedAPIs = null;
        Set<SubscribedAPI> subscribedAPIs = new HashSet<SubscribedAPI>();
        try {
            originalSubscribedAPIs= appMDAO.getSubscribedAPIs(subscriber);
            for(SubscribedAPI subscribedApi:originalSubscribedAPIs) {
                subscribedApi.getTier().setDisplayName(AppManagerUtil.getTierDisplayName(tenantId,subscribedApi.getTier().getName()));
                subscribedAPIs.add(subscribedApi);
            }

        } catch (AppManagementException e) {
            handleException("Failed to get APIs of " + subscriber.getName(), e);
        }
        return subscribedAPIs;
    }

    public Set<SubscribedAPI> getSubscribedAPIs(Subscriber subscriber, String applicationName) throws
                                                                                               AppManagementException {
        Set<SubscribedAPI> subscribedAPIs = null;
        try {
            subscribedAPIs = appMDAO.getSubscribedAPIs(subscriber, applicationName);
        } catch (AppManagementException e) {
            handleException("Failed to get APIs of " + subscriber.getName() + " under application " + applicationName, e);
        }
        return subscribedAPIs;
    }

    public boolean isSubscribed(APIIdentifier apiIdentifier, String userId)
            throws AppManagementException {
        boolean isSubscribed;
        try {
            isSubscribed = appMDAO.isSubscribed(apiIdentifier, userId);
        } catch (AppManagementException e) {
            String msg = "Failed to check if user(" + userId + ") has subscribed to " + apiIdentifier;
            log.error(msg, e);
            throw new AppManagementException(msg, e);
        }
        return isSubscribed;
    }

    public String addSubscription(APIIdentifier identifier, String subscriptionType, String userId, int applicationId, String trustedIdps)
            throws AppManagementException {

        WebApp api = getAPI(identifier);
        String subscribedTier = identifier.getTier();

        if (APIStatus.PUBLISHED.equals(api.getStatus())) {
            if (applicationId == -1) {
                applicationId = appMDAO.getDefaultApplicationForSubscriber(userId);
            }

            int subscriptionId = -1;

            /* (??????) 2019.06.11 - ????????? ????????? ????????? ?????????????????? ?????? 
            boolean shouldUpdate = false;
            Subscription subscription = appMDAO.getSubscription(identifier, applicationId, subscriptionType);

            if (subscription != null) {
                subscriptionId = subscription.getSubscriptionId();
                shouldUpdate = true;
            }

            if (shouldUpdate) {
                if (Subscription.SUBSCRIPTION_TYPE_ENTERPRISE.equals(subscriptionType)) {
                    appMDAO.updateSubscription(subscriptionId, subscriptionType, trustedIdps, subscription.getSubscriptionStatus());
                } else if (Subscription.SUBSCRIPTION_TYPE_INDIVIDUAL.equals(subscriptionType)) {
                    appMDAO.updateSubscription(subscriptionId, subscriptionType, trustedIdps, AppMConstants.SubscriptionStatus.ON_HOLD);
                }
            } else {
                subscriptionId = appMDAO.addSubscription(identifier, subscriptionType, api.getContext(),
                        applicationId, AppMConstants.SubscriptionStatus.ON_HOLD, trustedIdps);
            }
            */
            subscriptionId = appMDAO.addSubscription(identifier, subscriptionType, api.getContext(), applicationId, AppMConstants.SubscriptionStatus.ON_HOLD, trustedIdps);

            // Execute workflow.
            try {
                executeWorkflow(api, applicationId, userId, subscriptionId, subscribedTier);
            } catch (WorkflowException e) {
                //If the workflow execution fails, roll back transaction by removing the subscription entry.
                appMDAO.removeSubscriptionById(subscriptionId);
                log.error("Could not execute Workflow", e);
                throw new AppManagementException("Could not execute Workflow", e);
            }

            return appMDAO.getSubscriptionStatusById(subscriptionId);
        } else {
            throw new AppManagementException("Subscriptions not allowed on APIs in the state: " +
                    api.getStatus().getStatus());
        }
    }

    @Override
    public Subscription getSubscription(APIIdentifier apiIdentifier, int applicationId, String subscriptionType)throws
                                                                                                                AppManagementException {
        return appMDAO.getSubscription(apiIdentifier, applicationId, subscriptionType);
    }

    private void executeWorkflow(WebApp webApp, int applicationId, String userId, int subscriptionId,String subscribedTier)throws WorkflowException{

        try{

            WorkflowExecutor addSubscriptionWFExecutor = WorkflowExecutorFactory.getInstance().
                    getWorkflowExecutor(WorkflowConstants.WF_TYPE_AM_SUBSCRIPTION_CREATION);

            SubscriptionWorkflowDTO workflowDTO = new SubscriptionWorkflowDTO();
            workflowDTO.setStatus(WorkflowStatus.CREATED);
            workflowDTO.setCreatedTime(System.currentTimeMillis());
            workflowDTO.setTenantDomain(tenantDomain);
            workflowDTO.setTenantId(tenantId);
            workflowDTO.setExternalWorkflowReference(addSubscriptionWFExecutor.generateUUID());
            workflowDTO.setWorkflowReference(String.valueOf(subscriptionId));
            workflowDTO.setWorkflowType(WorkflowConstants.WF_TYPE_AM_SUBSCRIPTION_CREATION);
            workflowDTO.setCallbackUrl(addSubscriptionWFExecutor.getCallbackURL());
            workflowDTO.setApiName(webApp.getId().getApiName());
            workflowDTO.setApiContext(webApp.getContext());
            workflowDTO.setApiVersion(webApp.getId().getVersion());
            workflowDTO.setApiProvider(webApp.getId().getProviderName());
            workflowDTO.setTierName(subscribedTier);
            workflowDTO.setApplicationName(appMDAO.getApplicationNameFromId(applicationId));
            workflowDTO.setSubscriber(userId);

            addSubscriptionWFExecutor.execute(workflowDTO);

        }catch (Exception e){
            throw new WorkflowException("Cannot execute workflow. ", e);
        }
    }

    //This method has been introduced for rest-api workflow execution
    private void executeWorkflow(WebApp webApp, String applicationName, String userId, int subscriptionId, String subscribedTier) throws AppManagementException {


        WorkflowExecutor addSubscriptionWFExecutor = null;
        try {
            addSubscriptionWFExecutor = WorkflowExecutorFactory.getInstance().
                    getWorkflowExecutor(WorkflowConstants.WF_TYPE_AM_SUBSCRIPTION_CREATION);
            SubscriptionWorkflowDTO workflowDTO = new SubscriptionWorkflowDTO();
            workflowDTO.setStatus(WorkflowStatus.CREATED);
            workflowDTO.setCreatedTime(System.currentTimeMillis());
            workflowDTO.setTenantDomain(tenantDomain);
            workflowDTO.setTenantId(tenantId);
            workflowDTO.setExternalWorkflowReference(addSubscriptionWFExecutor.generateUUID());
            workflowDTO.setWorkflowReference(String.valueOf(subscriptionId));
            workflowDTO.setWorkflowType(WorkflowConstants.WF_TYPE_AM_SUBSCRIPTION_CREATION);
            workflowDTO.setCallbackUrl(addSubscriptionWFExecutor.getCallbackURL());
            workflowDTO.setApiName(webApp.getId().getApiName());
            workflowDTO.setApiContext(webApp.getContext());
            workflowDTO.setApiVersion(webApp.getId().getVersion());
            workflowDTO.setApiProvider(webApp.getId().getProviderName());
            workflowDTO.setTierName(subscribedTier);
            workflowDTO.setApplicationName(applicationName);
            workflowDTO.setSubscriber(userId);

            addSubscriptionWFExecutor.execute(workflowDTO);
        } catch (WorkflowException e) {
            handleException("Error occurred while executing Subscription workflow for webapp with name '" +
                    webApp.getId().getApiName() + "' with version '" + webApp.getId().getVersion());
        }
    }

    public void removeSubscription(APIIdentifier identifier, String userId, int applicationId)
            throws AppManagementException {
        appMDAO.removeSubscription(identifier, applicationId);
    }

    public void removeAPISubscription(APIIdentifier identifier, String userId, String applicationName)
            throws AppManagementException {
        appMDAO.removeAPISubscription(identifier, userId, applicationName);
        /*if (AppManagerUtil.isAPIGatewayKeyCacheEnabled()) {
            invalidateCachedKeys(applicationId, identifier);
        }*/
    }
    
    public void addSubscriber(String username) throws AppManagementException {
    	Subscriber subscriber = new Subscriber(username);
        subscriber.setSubscribedDate(new Date());
        //TODO : need to set the proper email
        subscriber.setEmail("");
        try {
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                    .getTenantId(MultitenantUtils.getTenantDomain(username));
            subscriber.setTenantId(tenantId);
    	
	        appMDAO.addSubscriber(subscriber);
	        
	        Application defaultApp = getApplicationsByName(username, AppMConstants.DEFAULT_APPLICATION_NAME);
	    	if (defaultApp == null) {
	    		addDefaultApplicationForSubscriber(subscriber);
	    	} 
        } catch (AppManagementException e) {
            handleException("Error while adding the subscriber " + subscriber.getName(), e);
        } catch (barley.user.api.UserStoreException e) {
            handleException("Error while adding the subscriber " + subscriber.getName(), e);
        }
    }
    
    private void addDefaultApplicationForSubscriber(Subscriber subscriber) throws AppManagementException {
        Application defaultApp = new Application(AppMConstants.DEFAULT_APPLICATION_NAME, subscriber);
        defaultApp.setTier(AppMConstants.UNLIMITED_TIER);
        appMDAO.addApplication(defaultApp, subscriber.getName());
    }

    public void removeSubscriber(String subscriberName) throws AppManagementException {
        //throw new UnsupportedOperationException("Unsubscribe operation is not yet implemented");
    	Subscriber subscriber = getSubscriber(subscriberName);
    	if (subscriber == null) {
    		throw new AppManagementException("Subscriber for subscriberName:" + subscriberName +" does not exist.");
    	} 
    	Application defaultApp = getApplicationsByName(subscriberName, AppMConstants.DEFAULT_APPLICATION_NAME);
    	if (defaultApp != null) {
    		// ?????? ?????????????????? ??????
        	appMDAO.deleteApplication(defaultApp);
    	} 
    	appMDAO.removeSubscriber(subscriber.getId());
    }

    public void updateSubscriptions(APIIdentifier identifier, String userId, int applicationId)
            throws AppManagementException {
        WebApp api = getAPI(identifier);
        appMDAO.updateSubscriptions(identifier, api.getContext(), applicationId);
    }

    /**
     * Retrieve webapp from the UUID
     * @param uuid uuid of the application
     * @return WebApp
     * @throws AppManagementException
     */
    @Override
    public WebApp getWebApp(String uuid) throws AppManagementException {
        boolean isTenantFlowStarted = false;

        if (tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
            isTenantFlowStarted = true;
            PrivilegedBarleyContext.startTenantFlow();
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
        }
        GenericArtifact artifact = null;
        WebApp webApp = null;

        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, AppMConstants.WEBAPP_ASSET_TYPE);
            artifact = artifactManager.getGenericArtifact(uuid);
            if (artifact == null) {
                handleResourceNotFoundException("Webapp does not exist with app id :" + uuid);
            }
            webApp = AppManagerUtil.getAPI(artifact, registry);

        } catch (GovernanceException e) {
            handleException("Error occurred while retrieving webapp registry artifact with uuid " + uuid);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedBarleyContext.endTenantFlow();
            }
        }
        return webApp;
    }

    /**
     * Add a new Application from the store.
     * @param application - {@link Application}
     * @param userId - {@link String} 
     * @return {@link String}
     */
    //ToDo: Refactor addApplication method return type to void
    public String addApplication(Application application, String userId)
            throws AppManagementException {
    	
    	appMDAO.addApplication(application, userId);
        return appMDAO.getApplicationStatus(application.getName(),userId);
    }

    public void updateApplication(Application application) throws AppManagementException {
        appMDAO.updateApplication(application);
    }

    public boolean isApplicationTokenExists(String accessToken) throws AppManagementException {
        return appMDAO.isAccessTokenExists(accessToken);
    }

    public Set<SubscribedAPI> getSubscribedIdentifiers(Subscriber subscriber, APIIdentifier identifier)
            throws AppManagementException {
        Set<SubscribedAPI> subscribedAPISet = new HashSet<SubscribedAPI>();
        Set<SubscribedAPI> subscribedAPIs = getSubscribedAPIs(subscriber);
        
        for (SubscribedAPI api : subscribedAPIs) {
            if (api.getApiId().equals(identifier)) {
                subscribedAPISet.add(api);
            }
        }
        return subscribedAPISet;
    }

    private boolean isAllowDisplayAPIsWithMultipleStatus() {
        AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();
        String displayAllAPIs = config.getFirstProperty(AppMConstants.API_STORE_DISPLAY_ALL_APIS);
        if (displayAllAPIs == null) {
            log.warn("The configurations related to show deprecated Apps in AppStore " +
                    "are missing in app-manager.xml.");
            return false;
        }
        return Boolean.parseBoolean(displayAllAPIs);
    }

    private boolean isAllowDisplayMultipleVersions() {
        AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();

        String displayMultiVersions = config.getFirstProperty(AppMConstants.API_STORE_DISPLAY_MULTIPLE_VERSIONS);
        if (displayMultiVersions == null) {
            log.warn("The configurations related to show multiple versions of WebApp in AppStore " +
                    "are missing in app-manager.xml.");
            return false;
        }
        return Boolean.parseBoolean(displayMultiVersions);
    }

    /**
     * Returns a list of tiers denied
     *
     * @return Set<Tier>
     */
    public Set<String> getDeniedTiers() throws AppManagementException {
        Set<String> deniedTiers = new HashSet<String>();
        String[] currentUserRoles = new String[0];
        try {
            if (tenantId != 0) {
                /* Get the roles of the Current User */
                currentUserRoles = ((UserRegistry) ((UserAwareAPIConsumer) this).registry).
                        getUserRealm().getUserStoreManager().getRoleListOfUser(((UserRegistry) this.registry).getUserName());

                Set<TierPermissionDTO> tierPermissions = appMDAO.getTierPermissions(tenantId);
                for (TierPermissionDTO tierPermission : tierPermissions) {
                    String type = tierPermission.getPermissionType();

                    List<String> currentRolesList = new ArrayList<String>(Arrays.asList(currentUserRoles));
                    List<String> roles = new ArrayList<String>(Arrays.asList(tierPermission.getRoles()));
                    currentRolesList.retainAll(roles);

                    if (AppMConstants.TIER_PERMISSION_ALLOW.equals(type)) {
                        /* Current User is not allowed for this Tier*/
                        if (currentRolesList.size() == 0) {
                            deniedTiers.add(tierPermission.getTierName());
                        }
                    } else {
                        /* Current User is denied for this Tier*/
                        if (currentRolesList.size() > 0) {
                            deniedTiers.add(tierPermission.getTierName());
                        }
                    }
                }
            }
        } catch (barley.user.api.UserStoreException e) {
            log.error("cannot retrieve user role list for tenant" + tenantDomain);
        }
        return deniedTiers;
    }

    /**
     * Check whether given Tier is denied for the user
     *
     * @param tierName
     * @return
     * @throws barley.appmgt.api.AppManagementException if failed to get the tiers
     */
    public boolean isTierDeneid(String tierName) throws AppManagementException {
        String[] currentUserRoles = new String[0];
        try {
            if (tenantId != 0) {
                /* Get the roles of the Current User */
                currentUserRoles = ((UserRegistry) ((UserAwareAPIConsumer) this).registry).
                        getUserRealm().getUserStoreManager().getRoleListOfUser(((UserRegistry) this.registry).getUserName());
                TierPermissionDTO tierPermission = appMDAO.getTierPermission(tierName, tenantId);
                if (tierPermission == null) {
                    return false;
                } else {
                    List<String> currentRolesList = new ArrayList<String>(Arrays.asList(currentUserRoles));
                    List<String> roles = new ArrayList<String>(Arrays.asList(tierPermission.getRoles()));
                    currentRolesList.retainAll(roles);
                    if (AppMConstants.TIER_PERMISSION_ALLOW.equals(tierPermission.getPermissionType())) {
                        if (currentRolesList.size() == 0) {
                            return true;
                        }
                    } else {
                        if (currentRolesList.size() > 0) {
                            return true;
                        }
                    }
                }
            }
        } catch (barley.user.api.UserStoreException e) {
            log.error("cannot retrieve user role list for tenant" + tenantDomain);
        }
        return false;
    }

    /**
     * Returned an WebApp set from a set of registry paths
     *
     * @param registry Registry object from which the APIs retrieving,
     * @param limit    Specifies the number of APIs to add.
     * @param apiPaths Array of WebApp paths.
     * @return Set<WebApp> set of APIs
     * @throws RegistryException
     * @throws barley.appmgt.api.AppManagementException
     */
    private Set<WebApp> getAPIs(Registry registry, int limit, String[] apiPaths)
            throws RegistryException, AppManagementException,
            barley.user.api.UserStoreException {

        SortedSet<WebApp> apiSortedSet = new TreeSet<WebApp>(new APINameComparator());
        SortedSet<WebApp> apiVersionsSortedSet = new TreeSet<WebApp>(new APIVersionComparator());

        Boolean allowMultipleVersions = isAllowDisplayMultipleVersions();
        Boolean showAllAPIs = isAllowDisplayAPIsWithMultipleStatus();
        Map<String, WebApp> latestPublishedAPIs = new HashMap<String, WebApp>();
        List<WebApp> multiVersionedAPIs = new ArrayList<WebApp>();
        Comparator<WebApp> versionComparator = new APIVersionComparator();

        //Find UUID
        GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                AppMConstants.API_KEY);
        for (int a = 0; a < apiPaths.length; a++) {
            Resource resource = registry.get(apiPaths[a]);
            if (resource != null && artifactManager != null) {
                GenericArtifact genericArtifact = artifactManager.getGenericArtifact(resource.getUUID());
                WebApp api = null;
                String status = genericArtifact.getAttribute(AppMConstants.API_OVERVIEW_STATUS);
                //Check the app-manager.xml config file entry <DisplayAllAPIs> value is false
                if (!showAllAPIs) {
                    // then we are only interested in published APIs here...
                    if (status.equals(AppMConstants.PUBLISHED)) {
                        api = AppManagerUtil.getAPI(genericArtifact, registry);
                    }
                } else {   // else we are interested in both deprecated/published APIs here...
                    if (status.equals(AppMConstants.PUBLISHED) || status.equals(AppMConstants.DEPRECATED)) {
                        api = AppManagerUtil.getAPI(genericArtifact, registry);

                    }

                }
                if (api != null) {
                    String key;
                    //Check the configuration to allow showing multiple versions of an WebApp true/false
                    if (!allowMultipleVersions) { //If allow only showing the latest version of an WebApp
                    	/*
                        key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName();
                        WebApp existingAPI = latestPublishedAPIs.get(key);
                        if (existingAPI != null) {
                            // If we have already seen an WebApp with the same name, make sure
                            // this one has a higher version number
                            if (versionComparator.compare(api, existingAPI) > 0) {
                                latestPublishedAPIs.put(key, api);
                            }
                        } else {
                            // We haven't seen this WebApp before
                            latestPublishedAPIs.put(key, api);
                        }
                        */
                    	key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId().getVersion();
                    	latestPublishedAPIs.put(key, api);
                    } else { //If allow showing multiple versions of an WebApp
                        key = api.getId().getProviderName() + COLON_CHAR + api.getId().getApiName() + COLON_CHAR + api.getId()
                                .getVersion();
                        multiVersionedAPIs.add(api);
                    }
                }

            }
        }
        if (!allowMultipleVersions) {
            for (WebApp api : latestPublishedAPIs.values()) {
                apiSortedSet.add(api);
            }
            return apiSortedSet;
        } else {
            for (WebApp api : multiVersionedAPIs) {
                apiVersionsSortedSet.add(api);
            }
            return apiVersionsSortedSet;
        }

    }

    private boolean isAllowDisplayAllAPIs() {
        AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();
        String displayAllAPIs = config.getFirstProperty(AppMConstants.API_STORE_DISPLAY_ALL_APIS);
        if (displayAllAPIs == null) {
            log.warn("The configurations related to show deprecated Apps in AppStore " +
                    "are missing in app-manager.xml.");
            return false;
        }
        return Boolean.parseBoolean(displayAllAPIs);
    }
    
    private boolean isTenantDomainNotMatching(String tenantDomain) {
    	if (this.tenantDomain != null) {
    		return !(this.tenantDomain.equals(tenantDomain));
    	}
    	return true;
    }

    public Application[] getApplications(Subscriber subscriber) throws AppManagementException {
        return appMDAO.getApplications(subscriber);
    }
    
    @Override
    public Application getApplicationsByName(String userId, String ApplicationName) throws AppManagementException {
        return appMDAO.getApplicationByName(ApplicationName, userId);
    }

    @Override
    public void addToFavouriteApps(APIIdentifier identifier, String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        appMDAO.addToFavouriteApps(identifier, username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public void removeFromFavouriteApps(APIIdentifier identifier, String username, int tenantIdOfUser,
                                        int tenantIdOfStore)
            throws AppManagementException {
        appMDAO.removeFromFavouriteApps(identifier, username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public boolean isFavouriteApp(APIIdentifier identifier, String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        return appMDAO.isFavouriteApp(identifier, username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public List<APIIdentifier> getFavouriteApps(String username, int tenantIdOfUser, int tenantIdOfStore,
                                                WebAppSortOption sortOption)
            throws AppManagementException {
        return appMDAO.getFavouriteApps(username, tenantIdOfUser, tenantIdOfStore, sortOption);
    }

    @Override
    public List<APIIdentifier> searchFavouriteApps(String username, int tenantIdOfUser, int tenantIdOfStore,
                                                   WebAppSearchOption searchOption, String searchValue)
            throws AppManagementException {
        return appMDAO.searchFavouriteApps(username, tenantIdOfUser, tenantIdOfStore, searchOption, searchValue);
    }

    @Override
    public List<APIIdentifier> getUserAccessibleApps(String username, int tenantIdOfUser, int tenantIdOfStore,
                                                     WebAppSortOption sortOption, boolean treatAsSite)
            throws AppManagementException {
        return appMDAO.getUserAccessibleApps(username, tenantIdOfUser, tenantIdOfStore, sortOption, treatAsSite);
    }

    @Override
    public List<APIIdentifier> searchUserAccessibleApps(String username, int tenantIdOfUser, int tenantIdOfStore,
                                                        boolean treatAsSite, WebAppSearchOption searchOption,
                                                        String searchValue) throws AppManagementException {
        Registry anonnymousUserRegistry = null;
        try {
            if (tenantIdOfStore != tenantIdOfUser) {
                // Get registry for anonnymous users when searching is going in tenant.
                anonnymousUserRegistry = ServiceReferenceHolder.getInstance().getRegistryService()
                        .getGovernanceUserRegistry(BarleyConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantIdOfStore);
            } else {
                anonnymousUserRegistry = registry;
            }
        } catch (RegistryException e) {
            handleException("Error while obtaining registry.", e);
        }

        return appMDAO.searchUserAccessibleApps(username, tenantIdOfUser, tenantIdOfStore, treatAsSite, searchOption,
                                                searchValue, anonnymousUserRegistry);
    }

    @Override
    public void setFavouritePage(String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        appMDAO.addToStoreFavouritePage(username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public void removeFavouritePage(String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        appMDAO.removeFromStoreFavouritePage(username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public boolean hasFavouritePage(String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        return appMDAO.hasFavouritePage(username, tenantIdOfUser, tenantIdOfStore);
    }

    @Override
    public boolean isSubscribedToMobileApp(String userId, String appId) throws AppManagementException {
        String path = "users/" + userId + "/subscriptions/mobileapp/" + appId;
        boolean isSubscribed = false;
        try {
            if (registry.resourceExists(path)) {
                isSubscribed = true;
            }
        } catch (barley.registry.api.RegistryException e) {
            handleException("Error while checking subscription in registry for mobileapp with id : " + appId, e);
        }
        return isSubscribed;
    }

    public Map<String, Set<WebApp>> getTaggedAPIs() {
        return taggedAPIs;
    }

    @Override
    public void addSubscription(String subscriberName, WebApp webApp, String applicationName) throws AppManagementException {
        AppRepository appRepository = new DefaultAppRepository(null);
        webApp.getId().setTier(AppMConstants.UNLIMITED_TIER);
        int subscriptionId = appRepository.addSubscription(subscriberName, webApp, applicationName);
        executeWorkflow(webApp, applicationName, subscriberName, subscriptionId, webApp.getId().getTier());
    }
    
    // (??????) 
    @Override
    public void addComment(APIIdentifier identifier, String commentText, String userId) throws AppManagementException {
    	appMDAO.addComment(identifier, commentText, userId);
    }
    
    @Override
	public void updateComment(int commentId, String comment) throws AppManagementException {
    	appMDAO.updateComment(commentId, comment);
	}

	@Override
	public void deleteComment(int commentId) throws AppManagementException {
		appMDAO.deleteComment(commentId);
	}
	
	@Override
	public void deleteComment(String userId, int commentId) throws AppManagementException {
		appMDAO.deleteComment(userId, commentId);
	}

    @Override
    public Comment[] getComments(APIIdentifier identifier) throws AppManagementException {
        return appMDAO.getComments(identifier);
    }
    
    @Override
    public Comment[] getSortedCreatedTimeComments(APIIdentifier identifier, int page, int count) throws AppManagementException {
        return appMDAO.getSortedCreatedTimeComments(identifier, page, count);
    }
    
    @Override
    public Comment[] getSortedAgreeCountComments(APIIdentifier identifier, int page, int count) throws AppManagementException {
        return appMDAO.getSortedAgreeCountComments(identifier, page, count);
    }

    @Override
    public List<WebApp> getAllAppList(String tenantDomain, int page, int count, String appState) throws AppManagementException {
        List<WebApp> apiList = null;
        // api ????????????
        String appStateValue = "";
        if("ALL".equals(appState)) {
            apiList = appMDAO.getAllAppList(tenantDomain, page, count, appStateValue);
        } else {
            appStateValue = APIStatus.valueOf(appState).getStatus();
            if(appState != null) {
                apiList = appMDAO.getAllAppList(tenantDomain, page, count, appStateValue);
            }
        }
        return apiList;
    }

    @Override
    public int getAllAppCount(String tenantDomain, String appState) throws AppManagementException {
        // app ????????????
        String appStateValue = "";
        int totalCnt = 0;
        if("ALL".equals(appState)) {
            totalCnt = appMDAO.getAllAppCount(tenantDomain, appStateValue);
        } else {
            appStateValue = APIStatus.valueOf(appState).getStatus();
            if(appState != null) {
                totalCnt = appMDAO.getAllAppCount(tenantDomain, appStateValue);
            }
        }
        return totalCnt;
    }

    
    @Override
    public List<WebApp> getPublishedAppList(String tenantDomain, String orderBy, int page, int count, String keyword, String tag, String category) throws AppManagementException {
        List<WebApp> appList = null;

        if("rating".equals(orderBy)) {
            appList = appMDAO.getSortedRatingApp(tenantDomain, page, count, keyword, tag, category);
        } else if("subcnt".equals(orderBy)) {
            appList = appMDAO.getSortedSubscribersCountApp(tenantDomain, page, count, keyword, tag, category);
        } else if("createdTime".equals(orderBy)) {
            appList = appMDAO.getSortedCreatedTimeApp(tenantDomain, page, count, keyword, tag, category);
        } else {
            appList = appMDAO.getSortedCreatedTimeApp(tenantDomain, page, count, keyword, tag, category);
        }

        return appList;
    }
    
    @Override
    public int getPublishedAppCount(String tenantDomain, String keyword, String tag, String category) throws AppManagementException {
    	return appMDAO.getPublishedAppCount(tenantDomain, keyword, tag, category);
    }
    
    @Override
    public int setCommentAgreeValue(String userName, int commnetId, int agreeValue) throws AppManagementException {
    	return appMDAO.setCommentAgreeValue(userName, commnetId, agreeValue);
    }
    
    private List<WebApp> addAppAttributeFromRegistry(List<WebApp> appList) throws AppManagementException {
    	List<WebApp> result = new ArrayList<WebApp>();
    	for(int i=0; i < appList.size(); i++) {
    		
    		WebApp app = appList.get(i);
    		addAppTagsFromRegistry(app);
    		
    		result.add(app);
    	}
    	return result;
    }
    
    private void addAppTagsFromRegistry(WebApp api) throws AppManagementException {
    	Set<String> tags = new HashSet<String>();
    	String apiPath = AppManagerUtil.getAPIPath(api.getId());
		try {
			barley.registry.core.Tag[] tag = registry.getTags(apiPath);
			for (barley.registry.core.Tag tag1 : tag) {
	            tags.add(tag1.getTagName());
	        }
	        api.addTags(tags);
		} catch (RegistryException e) {
			handleException("RegistryException thrown when gstting API tags from registry", e);
		}
    }

}
