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
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.api.APIConsumer;
import barley.appmgt.api.APIProvider;
import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.APIIdentifier;
import barley.appmgt.api.model.Application;
import barley.appmgt.api.model.BusinessOwner;
import barley.appmgt.api.model.SubscribedAPI;
import barley.appmgt.api.model.Subscriber;
import barley.appmgt.api.model.Subscription;
import barley.appmgt.api.model.Tag;
import barley.appmgt.api.model.Tier;
import barley.appmgt.api.model.WebApp;
import barley.appmgt.api.model.WebAppSearchOption;
import barley.appmgt.api.model.WebAppSortOption;
import barley.appmgt.impl.internal.AppManagerComponent;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.core.BarleyConstants;
import barley.identity.core.util.IdentityConfigParser;
import barley.identity.core.util.IdentityTenantUtil;
import barley.registry.core.service.TenantRegistryLoader;
import barley.registry.indexing.service.TenantIndexingLoader;

public class APIConsumerImplTest extends BaseTestCase {

    private static final Log log = LogFactory.getLog(APIConsumerImplTest.class);
    
    APIConsumer consumer = null;
    APIProvider provider = null;
    String userName = null;
    
    public void setUp() throws Exception {
    	super.setUp();
    	String dbConfigPath = System.getProperty("AppManagerDBConfigurationPath");
		AppManagerConfiguration config = new AppManagerConfiguration();
		config.load(dbConfigPath);
		ServiceReferenceHolder.getInstance()
		                      .setAPIManagerConfigurationService(new AppManagerConfigurationServiceImpl(config));
    	ServiceReferenceHolder.getInstance().setRegistryService(embeddedRegistryService);
        ServiceReferenceHolder.getInstance().setRealmService(ctx.getRealmService());
        ServiceReferenceHolder.getInstance().setIndexLoaderService(new TenantIndexingLoader() {
			@Override
			public void loadTenantIndex(int tenantId) {
				
			}
		});
        
        AppManagerComponent.setTenantRegistryLoader(new TenantRegistryLoader() {			
			@Override
			public void loadTenantRegistry(int tenantId) {
				
			}
		});		
    	AppManagerComponent component = new AppManagerComponent();
    	component.activate();
    	
    	// identity
    	IdentityTenantUtil.setRealmService(ctx.getRealmService());
		String identityConfigPath = System.getProperty("IdentityConfigurationPath");
		IdentityConfigParser.getInstance(identityConfigPath);
		
		// lifecyle 초기화 (system)
        barley.governance.lcm.util.CommonUtil.addDefaultLifecyclesIfNotAvailable(embeddedRegistryService.getConfigSystemRegistry(),
        		embeddedRegistryService.getRegistry(BarleyConstants.REGISTRY_SYSTEM_USERNAME));
        // lifecyle 초기화 (테넌트) - 경로가 /system/.. 로 부터 시작한다. 
        barley.governance.lcm.util.CommonUtil.addDefaultLifecyclesIfNotAvailable(embeddedRegistryService.getConfigSystemRegistry(getTenantId()),
        		embeddedRegistryService.getConfigSystemRegistry(getTenantId()));
        
        this.userName = "admin@codefarm.co.kr";
    	this.consumer = APIManagerFactory.getInstance().getAPIConsumer(userName);
    	this.provider = APIManagerFactory.getInstance().getAPIProvider(userName);
    }
    
    public void testBusinessOwner() throws AppManagementException {
    	int businessOwnerId = 7;
    	BusinessOwner businessOwner = consumer.getBusinessOwner(businessOwnerId);
    	assertNotNull(businessOwner);
    	
    	BusinessOwner businessOwnerForStore = consumer.getBusinessOwnerForAppStore(businessOwnerId, getTenantId());
    	assertNotNull(businessOwnerForStore);
    	
    	// OWNER_NAME 검색 
    	List<String> result = consumer.getBusinessOwnerIdsBySearchPrefix("오", getTenantId());
    	assertEquals(1, result.size());
    }
    
    public void testSubscriber() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	String subscriberName = userName;
    	Subscriber fromSubscriber = consumer.getSubscriber(subscriberName);
    	assertNotNull(fromSubscriber);
    	
//    	consumer.removeSubscriber(identifier, userId);
    	
    	Set<SubscribedAPI> subscribedApis = consumer.getSubscribedAPIs(fromSubscriber);
    	assertFalse(subscribedApis.isEmpty());
    	
    	String applicationName = "DefaultApplication";
    	Set<SubscribedAPI> subscribedApisByApplication = consumer.getSubscribedAPIs(fromSubscriber, applicationName);
    	assertFalse(subscribedApisByApplication.isEmpty());
    	
    	// providerName을 수정할 수 없기에 생성자에서 변경해야한다. 
    	APIIdentifier apiIdWithDomainBack = new APIIdentifier(AppManagerUtil.replaceEmailDomain(userName), "MyFirstApp", "1.0.0");
    	Set<SubscribedAPI> subscribedApisById = consumer.getSubscribedIdentifiers(fromSubscriber, apiIdWithDomainBack);
    	assertFalse(subscribedApisById.isEmpty());
    }
    
    public void testSubscription() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	apiId.setTier(AppMConstants.UNLIMITED_TIER);
    	String subscriberName = userName;
    	WebApp webApp = consumer.getAPI(apiId);
    	String applicationName = "DefaultApplication";
    	consumer.addSubscription(subscriberName, webApp, applicationName);

    	String userId = "yangwenry@codefarm.co.kr";
    	int applicationId = 1;
    	String trustedIdp = "idp";
    	// 추가 및 수정.
//    	consumer.addSubscription(apiId, Subscription.SUBSCRIPTION_TYPE_INDIVIDUAL, userId, applicationId, trustedIdp);
    	
    	
    	// 이 메소드도 구독추가 메소드
//    	consumer.updateSubscriptions(apiId, userId, applicationId);
    	
    	Subscription subscription = consumer.getSubscription(apiId, applicationId, Subscription.SUBSCRIPTION_TYPE_INDIVIDUAL);
    	assertEquals(1, subscription.getSubscriptionId());
    	
    	assertTrue(consumer.isSubscribed(apiId, userId));

    	// 삭제 
//    	consumer.removeSubscription(apiId, userId, applicationId);
//    	consumer.removeAPISubscription(apiId, userId, applicationName);
    	
    	
    }
    
    public void testApi() throws AppManagementException {
    	String tag = "abc";

    	Set<WebApp> appsWithTag = consumer.getAPIsWithTag(tag);
    	assertNull(appsWithTag);
    	
    	Map<String, Object> appsWithPaging = consumer.getPaginatedAPIsWithTag(tag, 0, 10);
    	assertNull(appsWithPaging.get("apis"));
    	
    	Set<WebApp> appsPublished = consumer.getAllPublishedAPIs(getTenantDomain());
    	assertEquals(1, appsPublished.size());
    	
    	//(오류) attribute 서비스 null
//    	Map<String, Object> appsPublishedWithPaging = consumer.getAllPaginatedPublishedAPIs(getTenantDomain(), 0, 10);
//    	assertEquals(0, appsPublishedWithPaging.size());
    	
    	Set<WebApp> appsTopRated = consumer.getTopRatedAPIs(1000);
    	assertEquals(0, appsTopRated.size());
    	
    	Set<WebApp> appsRecently = consumer.getRecentlyAddedAPIs(1000, getTenantDomain());
    	assertEquals(0, appsRecently.size());
    	
    	String uuid = provider.getAppUUIDbyName("MyFirstApp", "1.0.0", getTenantId());
    	WebApp webapp = consumer.getWebApp(uuid);
    	assertEquals("MyFirstApp", webapp.getApiName());
    	
    	String searchTerm = "1";
    	String searchType = "Version";
    	String tenantDomain = "codefarm.co.kr";
    	Set<WebApp> resultApps = consumer.searchAPI(searchTerm, searchType, tenantDomain);
    	assertEquals(1, resultApps.size());
    	
    	Map<String, Object> resultAppsByPaging = consumer.searchPaginatedAPIs(searchTerm, searchType, tenantDomain, 0, 10);
    	assertEquals(1, resultAppsByPaging.get("length"));
    	
    	Set<WebApp> appsByProvider = consumer.getPublishedAPIsByProvider(userName, 1000);
    	assertFalse(appsByProvider.isEmpty());
    	
    	Set<WebApp> appsByProviderUser = consumer.getPublishedAPIsByProvider(userName, "admin@codefarm.co.kr", 1000);
    	assertFalse(appsByProviderUser.isEmpty());
    	
    }
    
    
    public void testRating() throws AppManagementException {
    	String uuid = provider.getAppUUIDbyName("MyFirstApp", "1.0.0", getTenantId());
    	float rating = consumer.getAverageRating(uuid, AppMConstants.API_KEY);
    	System.out.println("rating:" + rating);
    	assertTrue(rating > 0);
    }
    
    public void testTag() throws AppManagementException {
    	Set<Tag> tags = consumer.getAllTags(getTenantDomain());
    	assertNotNull(tags);
    	
    	Map<String, String> attributeMap = null;
    	Set<Tag> tagsByAttribute = consumer.getAllTags(getTenantDomain(), AppMConstants.WEBAPP_ASSET_TYPE, attributeMap);
    	assertNotNull(tagsByAttribute);
    	
    	// getAllTags()를 수행한 후 실행해야 함.
    	Map<String, Set<WebApp>> taggedMap = consumer.getTaggedAPIs();
    	assertNotNull(taggedMap);
    }

    public void testApplication() throws AppManagementException {
    	String userId = userName;
    	Subscriber subscriber = consumer.getSubscriber(userId);
    	Application application = new Application("MyApplication", subscriber);
    	application.setTier(AppMConstants.UNLIMITED_TIER);
    	application.setDescription("내가 만든 어플리케이션");
//    	consumer.addApplication(application, userId);
    	
    	application.setId(3);
    	application.setTier(AppMConstants.APPLICATION_TIER);
//    	consumer.updateApplication(application);
    	
    	Application[] applications = consumer.getApplications(subscriber);
    	assertEquals(2, applications.length);
    }
    
    public void testApplicationToken() throws AppManagementException {
    	String accessToken = "";
    	assertTrue(consumer.isApplicationTokenExists(accessToken));
    }
    
    public void testTier() throws AppManagementException {
    	Set<String> deniedTiers = consumer.getDeniedTiers();
    	assertTrue(deniedTiers.size() == 0);
    	
    	assertFalse(consumer.isTierDeneid("Gold"));
    }
    
    public void testFavoriteApp() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	int tenantIdOfUser = getTenantId();
    	int tenantIdOfStore = tenantIdOfUser;
    	consumer.addToFavouriteApps(apiId, userName, tenantIdOfUser, tenantIdOfStore);
    	
    	assertTrue(consumer.isFavouriteApp(apiId, userName, tenantIdOfUser, tenantIdOfStore));
    	
    	WebAppSortOption sortOption = WebAppSortOption.SORT_BY_CREATED_TIME_DESC;
    	List<APIIdentifier> apiIds = consumer.getFavouriteApps(userName, tenantIdOfUser, tenantIdOfStore, sortOption);
    	assertEquals("MyFirstApp", apiIds.get(0).getApiName());
    	
    	WebAppSearchOption searchOption = WebAppSearchOption.SEARCH_BY_APP_NAME;
    	String searchValue = "First";
    	List<APIIdentifier> apiIdsBySearch = consumer.searchFavouriteApps(userName, tenantIdOfUser, tenantIdOfStore, searchOption, searchValue);
    	assertEquals("MyFirstApp", apiIdsBySearch.get(0).getApiName());
    	
    	consumer.removeFromFavouriteApps(apiId, userName, tenantIdOfUser, tenantIdOfStore);
    }
    
    public void testAccessibleApp() throws AppManagementException {
    	int tenantIdOfUser = getTenantId();
    	int tenantIdOfStore = tenantIdOfUser;
    	WebAppSortOption sortOption = WebAppSortOption.SORT_BY_CREATED_TIME_DESC;    	
    	boolean treatAsSite = false;
    	List<APIIdentifier> apiIds = consumer.getUserAccessibleApps(userName, tenantIdOfUser, tenantIdOfStore, sortOption, treatAsSite);
    	assertEquals("MyFirstApp", apiIds.get(0).getApiName());
    	
    	WebAppSearchOption searchOption = WebAppSearchOption.SEARCH_BY_APP_NAME;
    	String searchValue = "First";
    	List<APIIdentifier> apiIdsBySearch = consumer.searchUserAccessibleApps(userName, tenantIdOfUser, tenantIdOfStore, treatAsSite, searchOption, searchValue);
    	assertEquals("MyFirstApp", apiIdsBySearch.get(0).getApiName());
    }
   
    public void testFavoritePage() throws AppManagementException {
    	int tenantIdOfUser = getTenantId();
    	int tenantIdOfStore = tenantIdOfUser;
    	consumer.setFavouritePage(userName, tenantIdOfUser, tenantIdOfStore);
    	
    	assertTrue(consumer.hasFavouritePage(userName, tenantIdOfUser, tenantIdOfStore));
    	
    	consumer.removeFavouritePage(userName, tenantIdOfUser, tenantIdOfStore);
    }

    public void testGetApi() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "Weather", "1.0.0");
    	WebApp webapp = consumer.getAPI(apiId);
    	Set<Tier> tiers = webapp.getAvailableTiers();
    	for(Tier tier : tiers) {
    		System.out.println(tier.getName());
    	}
    }
    
}


