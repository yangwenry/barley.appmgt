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

import barley.appmgt.api.APIProvider;
import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.FaultGatewaysException;
import barley.appmgt.api.dto.UserApplicationAPIUsage;
import barley.appmgt.api.model.*;
import barley.appmgt.impl.AppMConstants.LifecycleActions;
import barley.appmgt.impl.internal.AppManagerComponent;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.core.BarleyConstants;
import barley.identity.core.util.IdentityConfigParser;
import barley.identity.core.util.IdentityTenantUtil;
import barley.registry.core.Registry;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.service.TenantRegistryLoader;
import barley.registry.indexing.service.TenantIndexingLoader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.stream.XMLStreamException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class APIProviderImplTest extends BaseTestCase {

    private static final Log log = LogFactory.getLog(APIProviderImplTest.class);
    
    APIProvider provider = null;
    String userName = null;
    
    public void setUp() throws Exception {
    	super.setUp();
    	
//    	String dbConfigPath = System.getProperty("AppManagerDBConfigurationPath");
//		AppManagerConfiguration config = new AppManagerConfiguration();
//		config.load(dbConfigPath);
//		ServiceReferenceHolder.getInstance()
//		                      .setAPIManagerConfigurationService(new AppManagerConfigurationServiceImpl(config));
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
		
		// lifecyle ????????? (system)
        barley.governance.lcm.util.CommonUtil.addDefaultLifecyclesIfNotAvailable(embeddedRegistryService.getConfigSystemRegistry(),
        		embeddedRegistryService.getRegistry(BarleyConstants.REGISTRY_SYSTEM_USERNAME));
        // lifecyle ????????? (?????????) - ????????? /system/.. ??? ?????? ????????????. 
        barley.governance.lcm.util.CommonUtil.addDefaultLifecyclesIfNotAvailable(embeddedRegistryService.getConfigSystemRegistry(getTenantId()),
        		embeddedRegistryService.getConfigSystemRegistry(getTenantId()));
        
        this.userName = "admin@codefarm.co.kr";
    	this.provider = APIManagerFactory.getInstance().getAPIProvider(userName);
    }
    
    public void testGetApps() throws AppManagementException {
    	List<WebApp> apps = provider.getAllAPIs();
    	assertNotNull(apps);
    	
    	//String uuid = "48da3ed6-cb60-4eab-8275-996cb0323c57";
    	String uuid = provider.getAppUUIDbyName("MyFirstApp", "1.0.0", getTenantId());
    	assertNotNull(uuid);
    	int id = provider.getWebAppId(uuid);
    	//assertNotEquals(id, 0);
    
    	WebApp webapp = provider.getWebApp(uuid);
    	assertNotNull(webapp);
    	
    	// dao??? ??????
    	WebApp webappByUUID = provider.getAppDetailsFromUUID(uuid);
    	assertNotNull(webappByUUID);
    	
    	// dao??? ?????? 
    	List<WebApp> allApps = provider.getAllWebApps();
    	assertNotNull(allApps);
    	List<WebApp> allDomainApps = provider.getAllWebApps(getTenantDomain());
    	assertNotNull(allDomainApps);
    	
    	// api ?????? 
    	String searchTerm = "1";
    	String searchType = "Version";
    	List<WebApp> resultApps = provider.searchAPIs(searchTerm, searchType, userName);
    	assertEquals(1, resultApps.size());
    	
    	List<WebApp> appsByProvider = provider.getAPIsByProvider(userName);
    	assertEquals(1, appsByProvider.size());
    	
    	List<WebApp> appsWithEndpoint = provider.getAppsWithEndpoint(getTenantDomain());
    	assertEquals(1, appsWithEndpoint.size());
    	
    	int subscriberId = 5;
    	Subscriber subscriber = provider.getSubscriber(subscriberId);
    	Set<WebApp> webapps = provider.getSubscriberAPIs(subscriber);
    	assertFalse(webapps.isEmpty());
    	
    }
    
    // (??????) rest.api ?????????????????? ????????????. (AppsApiServiceImpl)
    /*
    public void testCreateWebApp() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	WebApp app = new WebApp(apiId);
    	app.setApiName("My App");
    	app.setContext("/wso2earth");
    	app.setDisplayName("My First App");
    	app.setTransports("http");
    	app.setUrl("http://app.demo.codefarm.co.kr");
    	app.setStatus(APIStatus.CREATED);
    	app.setType(AppMConstants.WEBAPP_ASSET_TYPE);
    	app.setPolicyGroups("lemon");
    	
    	Set<URITemplate> uriTemplates = new HashSet();
    	URITemplate template = new URITemplate();
    	template.setUriTemplate("/dept");
    	template.setHttpVerbs("GET");
    	template.setHTTPVerb("GET");
    	// AUTH_NO_AUTHENTICATION, AUTH_APPLICATION_LEVEL_TOKEN
    	template.setAuthType(AppMConstants.AUTH_NO_AUTHENTICATION);    	
    	uriTemplates.add(template);
    	
    	app.setUriTemplates(uriTemplates);
    	app.setAllowAnonymous(true);
    	
    	//provider.createWebApp(app);
    }
    */
    
    public void testUpdateWebApp() throws AppManagementException, FaultGatewaysException {
    	String authorizedAdminCookie = null;
    	APIIdentifier apiId = new APIIdentifier(userName, "NCS", "1.0.0");
    	WebApp app = provider.getAPI(apiId);
//    	app.setThumbnailUrl("http://app.codefarm.co.kr/default/images/main/app_icon03m.png");
//    	app.setDescription("??????????????? ???????????? ?????? ???????????????????????????.");
//    	app.setApiName("?????? ??????????????????");
//    	app.setDescription("??????????????? ???????????? ?????? ???????????? ?????????????????????.<br/><br/>" +
//"????????? NCS?????? ???????????? ?????? ??? ?????? ???????????? ??????????????????????????? ???????????? ????????? ?????? ?????? ????????????????????? ?????? ????????? ?????? ???????????? ???????????? ????????? ?????? ?????? ??? ??????????????? ????????? ??????????????? ???????????? ??????????????? ?????????????????? ??????????????? ??? ??? ?????? ????????? ????????? ?????? ????????????.<br/><br/>" +
//"????????? ????????? ????????? ?????? NCS ???????????? ???????????? ??????<br/><br/>" +
//"??????????????????????????? ?????? ??????????????????<br/>" +
//"NCS ??????????????? ????????? NCS?????????????????? ??????????????? ??????<br/>" +
//"NCS???????????? ?????? ????????????????????? ????????? ??????????????? ??????<br/><br/>" +
//"NCS?????? ???????????? ????????????????????? ????????? ??????????????? ??????<br/>" +
//"????????? ????????? ???????????? ?????? ??????<br/>" +
//"???????????? ????????? ????????? ????????? ??????<br/><br/>" +
//"??????????????????????????? ????????? Framework??????<br/>" +
//"JAVA,JSP,iBatis??? ?????? ?????? ?????? ??????(????????? ?????? X-PLATFORM??????)<br/>" +
//"????????????????????? ????????? ????????? ??????<br/><br/>" +
//"????????????????????? ?????? ???????????? ??????<br/>" +
//"NCS??????????????? ????????? NCS ?????????????????? ??????????????? ??????");
    	app.setTitle("?????????????????????");
    	
    	Set<URITemplate> uriTemplates = new HashSet();
    	URITemplate template1 = new URITemplate();
    	template1.setUriTemplate("/*");
    	template1.setHttpVerbs("GET");
    	template1.setHTTPVerb("GET");
    	template1.setPolicyGroupId(1);
    	// AUTH_NO_AUTHENTICATION, AUTH_APPLICATION_LEVEL_TOKEN
    	template1.setAuthType(AppMConstants.AUTH_NO_AUTHENTICATION);
    	uriTemplates.add(template1);
    	app.setUriTemplates(uriTemplates);
    	
    	provider.updateAPI(app, authorizedAdminCookie);

    }
    
    // (??????)
    public void testAddWebApp() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MapDemo", "1.0.0");
    	WebApp app = new WebApp(apiId);
    	app.setApiName("MapDemo");
    	app.setTitle("?????? ??????");
    	app.setContext("/mapdemo/default/jsp");
    	app.setDisplayName("MapDemo");
    	app.setTransports("http");
    	app.setUrl("http://app.codefarm.co.kr");
    	app.setStatus(APIStatus.CREATED);
    	app.setType(AppMConstants.WEBAPP_ASSET_TYPE);
    	app.setTrackingCode("ABC");
    	app.setLogoutURL("http://app.demo.codefarm.co.kr/logout.jsp");    	
    	app.setDefaultVersion(true);
    	
    	Set<URITemplate> uriTemplates = new HashSet();
    	URITemplate template1 = new URITemplate();
    	template1.setUriTemplate("/*");
    	template1.setHttpVerbs("GET");
    	template1.setHTTPVerb("GET");
    	template1.setPolicyGroupId(1);
    	// AUTH_NO_AUTHENTICATION, AUTH_APPLICATION_LEVEL_TOKEN
    	template1.setAuthType(AppMConstants.AUTH_NO_AUTHENTICATION);    	
    	
    	URITemplate template2 = new URITemplate();
    	template2.setUriTemplate("/tv");
    	template2.setHttpVerbs("GET");
    	template2.setHTTPVerb("GET");
    	template2.setPolicyGroupId(1);
    	template2.setAuthType(AppMConstants.AUTH_NO_AUTHENTICATION);
    	
    	uriTemplates.add(template1);
//    	uriTemplates.add(template2);
    	
    	app.setUriTemplates(uriTemplates);
    	app.setAllowAnonymous(true);
    	Set<Tier> availableTiers = new HashSet<Tier>();
    	availableTiers.add(new Tier(AppMConstants.UNLIMITED_TIER));
    	availableTiers.add(new Tier("Gold"));
    	app.addAvailableTiers(availableTiers);
    	
    	provider.addWebApp(app);
    	    	
    }
    
    public void testDeleteApi() throws AppManagementException, FaultGatewaysException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MapDemo", "1.0.0");
    	SSOProvider ssoProvider = null;
    	String authorizedAdminCookie = null;
    	provider.deleteApp(apiId, ssoProvider, authorizedAdminCookie);
    }
    
    public void testChangeApiStatus() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MapDemo", "1.0.0");
    	WebApp api = provider.getAPI(apiId);
    	APIStatus status = APIStatus.PUBLISHED;
    	String userId = userName;	// userId ??????????????? ???????????? ?????????.
    	
//    	boolean updateGatewayConfig = true;    	
//    	provider.changeAPIStatus(api, status, userId, updateGatewayConfig);
    	
//    	String resourceId = provider.getAppUUIDbyName("MyFirstApp", "1.0.0", getTenantId());
    	
    	// ?????? => ?????? 
//    	provider.changeLifeCycleStatus(AppMConstants.WEBAPP_ASSET_TYPE, apiId, LifecycleActions.SUBMIT_FOR_REVIEW);
//		provider.changeLifeCycleStatus(AppMConstants.WEBAPP_ASSET_TYPE, apiId, LifecycleActions.APPROVE);
//    	provider.changeLifeCycleStatus(AppMConstants.WEBAPP_ASSET_TYPE, apiId, LifecycleActions.PUBLISH);
    	
    	// ?????? => ?????? 
    	provider.changeLifeCycleStatus(AppMConstants.WEBAPP_ASSET_TYPE, apiId, LifecycleActions.UNPUBLISH);
    	provider.changeLifeCycleStatus(AppMConstants.WEBAPP_ASSET_TYPE, apiId, LifecycleActions.RECYCLE);
    }
    
    // (??????)
    public void testSaveBusinessOwner() throws AppManagementException {
    	int businessOwnerId = 6;
    	BusinessOwner businessOwner = new BusinessOwner();
    	businessOwner.setBusinessOwnerName("?????????");
    	businessOwner.setBusinessOwnerEmail("yangwenry@gmail.com");
    	businessOwner.setBusinessOwnerSite("http://www.codefarm.co.kr");
    	businessOwner.setBusinessOwnerDescription("?????? ????????? ????????? ?????????.");
    	provider.saveBusinessOwner(businessOwner);
    	
    	// update
    	businessOwner.setBusinessOwnerId(businessOwnerId);
    	businessOwner.setBusinessOwnerName("?????????");
    	businessOwner.setBusinessOwnerEmail("kang@gmail.com");
    	businessOwner.setBusinessOwnerSite("http://www.codefarm.co.kr");
    	businessOwner.setBusinessOwnerDescription("???????????????.");
    	provider.updateBusinessOwner(businessOwner);
    	
    	// get
    	BusinessOwner fromBusinessOwner = provider.getBusinessOwner(businessOwnerId);
    	assertEquals(businessOwner.getBusinessOwnerName(), fromBusinessOwner.getBusinessOwnerName());
    	
    	List<BusinessOwner> fromBusinessOwners = provider.getBusinessOwners();
    	assertEquals(1, fromBusinessOwners.size());
    	
    	assertEquals(1, provider.getBusinessOwnersCount());
    	
    	assertEquals(businessOwnerId, provider.getBusinessOwnerId("?????????", "kang@gmail.com"));
    	
    	fromBusinessOwners = provider.searchBusinessOwners(0, 10, "?????????");
    	assertEquals(1, fromBusinessOwners.size());
    	
    	// delete
    	boolean success = provider.deleteBusinessOwner(String.valueOf(businessOwnerId));
    	assertTrue(success);
    }
    
    // (??????) ?????????????????? ???????????? ?????? ?????? ?????????. ??????????????? ?????????????????? ???????????? ????????? ????????????.
    public void testProvider() throws AppManagementException {
//    	Set<Provider> providers = provider.getAllProviders();
//    	assertEquals(1, providers.size());
    	
    	Provider fromProvider = provider.getProvider(userName);
    	assertNotNull(fromProvider);
    	
    }
    
    // setup?????? tiers/default-tiers.xml ???????????? ????????? ???????????? ????????????. 
    public void testTier() throws AppManagementException, IOException {
    	String tierName = "Lemon";    	
    	Tier addTier = new Tier(tierName);
    	// policy content??? ???????????? ?????? ????????? ???????????????????????? DisplayName?????? tier ??????????????? ???????????? ????????? 
    	// DisplayName??? policyContent??? ????????? ???????????? ??????.  
    	addTier.setDisplayName("Lemon");
//    	addTier.setDescription("mytier");
//    	addTier.setRequestPerMinute(3000);
    	String path = "D:\\Workspace_STS_APPM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\tier-lemon.txt";
    	InputStream inputStream = new FileInputStream(path);
    	byte[] policyContent = IOUtils.toByteArray(inputStream);
    	addTier.setPolicyContent(policyContent);
//    	provider.addTier(addTier);
    	
    	// APIManager ??????????????? 
    	Set<Tier> tiers = provider.getTiers();
    	for(Tier tier : tiers) {
    		System.out.println(tier.getName());
    	}
    	System.out.println("-----------------------");
    	assertEquals(4, tiers.size());
    	
    	Set<Tier> tiersByDomain = provider.getTiers(getTenantDomain());
    	for(Tier tier : tiersByDomain) {
    		System.out.println(tier.getName());
    		//byte[] content = tier.getPolicyContent();
    		//System.out.println(new String(content, 0, content.length));    		
    	}
    	System.out.println("-----------------------");
    	assertEquals(4, tiersByDomain.size());
    	
//    	provider.updateTier(addTier);
//    	provider.removeTier(addTier);
//    	
//    	Set<Tier> finalTiersByDomain = provider.getTiers(getTenantDomain());
//    	assertEquals(3, finalTiersByDomain.size());
    }
    
    public void testTierPermission() throws AppManagementException {
    	// dao APM_TIER_PERMISSIONS ???????????? ?????? 
    	String tierName = "Gold";
    	String permissionType = AppMConstants.TIER_PERMISSION_ALLOW;
    	String roles = "internal/everyone";
    	provider.updateTierPermissions(tierName, permissionType, roles);
    	
    	Set permissions = provider.getTierPermissions();
    	assertEquals(1, permissions.size());
    }
    
    public void testApiUsage() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	// ???????????? ?????? ??????
//    	Usage usage = provider.getUsageByAPI(apiId);
//    	assertNotNull(usage);
    	
    	// (??????)
    	UserApplicationAPIUsage[] usages = provider.getAllAPIUsageByProvider(userName);
    	assertNotNull(usages);
    	
    	// ???????????? ?????? ??????
//    	String userEmail = "yangwenry@gmail.com";
//    	Usage usagesBySub = provider.getAPIUsageBySubscriber(apiId, userEmail);
//    	assertNotNull(usagesBySub);
    	
    }
    
    public void testSubscription() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	Set<Subscriber> subscribers = provider.getSubscribersOfAPI(apiId);
    	assertFalse(subscribers.isEmpty());
    	
    	APIIdentifier toIdentifier = new APIIdentifier(userName, "MyFirstApp", "2.0.0");
//    	provider.moveSubscriptions(apiId, toIdentifier);
    	
    	// ???????????????
    	boolean isSubscriptionOn = true;
    	String fromDate = "2018-01-05";
    	String toDate = "2018-05-15";
		List<SubscriptionCount> subCount = provider.getSubscriptionCountByAPPs(userName, fromDate, toDate, isSubscriptionOn);
    	assertEquals(2, subCount.size());
    	
    	// ??????????????? 
    	long count = provider.getAPISubscriptionCountByAPI(apiId);
    	assertEquals(1, count);
    	
    	Map<String, List> users = provider.getSubscribedAPPsByUsers(fromDate, toDate);
    	assertFalse(users.isEmpty());
    }
    
    
    // (??????) ?????????????????? ?????? ????????????????????? ?????? ?????????  
    public void testEntitlementPolicies() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	String authorizedAdminCookie = "";
    	provider.generateEntitlementPolicies(apiId, authorizedAdminCookie);
    	
    	int appId = 1;
    	List<EntitlementPolicyGroup> policies = provider.getPolicyGroupListByApplication(appId);
    	assertEquals(1, policies.size());
    }
    
    public void testDocument() throws AppManagementException, FileNotFoundException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	WebApp api = provider.getAPI(apiId);
    	String documentationName = "howto5";
    	
    	Documentation doc = new Documentation(DocumentationType.HOWTO, documentationName);
    	doc.setSourceType(Documentation.DocumentSourceType.INLINE);    	
    	doc.setSummary("????????? ???????????? ?????????.");
    	doc.setSourceUrl("http://doc.codefarm.co.kr");
    	doc.setFilePath("C:/doc.html");
//    	provider.addDocumentation(apiId, doc);
    	
    	doc.setFilePath("C:/doc2.html");
//    	provider.updateDocumentation(apiId, doc);
    	
    	// /_system/governance/appmgt/applicationdata/provider/admin-AT-codefarm.co.kr/MyFirstApp/1.0.0/documentation/contents
    	String text = "contentcontentcontentcontentcontentcontent";
    	provider.addDocumentationContent(apiId, documentationName, text);    	
    	
    	// /_system/governance/appmgt/applicationdata/provider/admin-AT-codefarm.co.kr/MyFirstApp/1.0.0/documentation/files
    	doc.setSourceType(Documentation.DocumentSourceType.FILE);
    	String path = "D:\\Workspace_STS_APPM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\tier-lemon.txt";
    	InputStream inputStream = new FileInputStream(path);
    	String filename = "????????????";
//    	provider.addFileToDocumentation(api, doc, filename, inputStream, "txt");
    	
    	// docName ??????????????? ???????????? ?????????. ?????? ???????????? ??????.
    	String apiDocName = "api-doc.json";
//    	provider.addAPIDefinitionContent(apiId, apiDocName, text);
    	
    	// files??? contents??? ???????????? ?????????. 
    	String toVersion = "2.0.0";
//    	provider.copyAllDocumentation(apiId, toVersion);
    	
    	// contents??? ??????.
//    	provider.copyWebappDocumentations(api, toVersion);
    	
    	// docType ??????????????? ???????????? ??????.
//    	provider.removeDocumentation(apiId, documentationName, "HOWTO");
    	
    	String documentId = "a6e5c152-5800-4f20-aeb3-9f66c86e91ce";
//    	provider.removeDocumentation(apiId, documentId);
    }
    
    public void testLifecycle() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	
    	// dao ?????? 
    	List<LifeCycleEvent> events = provider.getLifeCycleEvents(apiId);
    	assertEquals(2, events.size());
    	
    	String resourceId = provider.getAppUUIDbyName("MyFirstApp", "1.0.0", getTenantId());
    	String[] actions = provider.getAllowedLifecycleActions(resourceId, AppMConstants.WEBAPP_ASSET_TYPE);
    	assertEquals(1, actions.length);
    	
    	// changeApiStatus??? ???????????? dao????????? ???????????? ?????? ??? ?????????????????? ???????????? ?????? ??????????????????.
    	String action = LifecycleActions.SUBMIT_FOR_REVIEW;
    	provider.changeLifeCycleStatus(AppMConstants.WEBAPP_ASSET_TYPE, apiId, action);
    }
    
    public void testLifecycleConfiguration() throws RegistryException, XMLStreamException {
    	Registry systemRegistry = embeddedRegistryService.getConfigSystemRegistry(getTenantId());
    	String[] lists = barley.governance.lcm.util.CommonUtil.getLifecycleList(systemRegistry);
    	for(String lifecycle: lists) {
    		System.out.println(lifecycle);
    		//String content = barley.governance.lcm.util.CommonUtil.getLifecycleConfiguration(lifecycle, systemRegistry);
    		//System.out.println(content);
    	}
    	assertTrue(lists.length > 0);
    }
    
    public void testTag() throws AppManagementException {
    	List<String> tags = new ArrayList<String>();
    	tags.add("abc");
    	String uuid = provider.getAppUUIDbyName("MyFirstApp", "1.0.0", getTenantId());
    	provider.addTags(AppMConstants.WEBAPP_ASSET_TYPE, uuid, tags);
    	
    	Set<Tag> allTagsByUUid = provider.getAllTags(AppMConstants.WEBAPP_ASSET_TYPE, uuid);
    	assertEquals(1, allTagsByUUid.size());
    	
    	// ?????? 
//    	Set<Tag> allTags = provider.getAllTags(AppMConstants.WEBAPP_ASSET_TYPE);
//    	assertEquals(1, allTags.size());
    	
//    	provider.removeTag(AppMConstants.WEBAPP_ASSET_TYPE, uuid, tags);
    	
    	allTagsByUUid = provider.getAllTags(AppMConstants.WEBAPP_ASSET_TYPE, uuid);
    	assertEquals(0, allTagsByUUid.size());
    }
    
    public void testSequence() throws AppManagementException {
    	List<String> inSeqs = provider.getCustomInSequences();
    	assertEquals(1, inSeqs.size());
    	
    	List<String> outSeqs = provider.getCustomOutSequences();
    	assertEquals(1, outSeqs.size());
    }
    
    public void testDownloadLink() throws AppManagementException {
    	String uuid = provider.getAppUUIDbyName("MyFirstApp", "1.0.0", getTenantId());
    	provider.generateOneTimeDownloadLink(uuid);
    }
    
    public void testEtc() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	
    	String uuid = provider.getAppUUIDbyName("MyFirstApp", "1.0.0", getTenantId());
    	String trackingId = provider.getTrackingID(uuid);
    	assertEquals("ABC", trackingId);
    	
    	// ???????????? ???????????? ??????.
//    	Set<AppStore> apps = provider.getExternalAppStores(apiId);
//    	assertNotNull(apps);
    	
    	boolean yn = provider.isDefaultVersion(apiId);
    	assertTrue(yn);
    	
    	assertTrue(provider.hasMoreVersions(apiId));
    	
    	String defaultVersion = provider.getDefaultVersion("MyFirstApp", userName, AppDefaultVersion.APP_IS_ANY_LIFECYCLE_STATE);
    	assertEquals("1.0.0", defaultVersion);
    	
    	String endPoint = provider.getGatewayEndpoint();
    	System.out.println(endPoint);
    	assertNotNull(endPoint);
    }
    
    // (??????) resource ???????????? ???????????? ?????? -> jag ????????? ?????? storage.js?????? ?????????.
    public void testUploadImage() throws AppManagementException, FileNotFoundException {
    	FileContent fileContent = new FileContent();
    	fileContent.setFileName("sample.jpg");
    	String path = "D:\\Workspace_STS_APPM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\sample.jpg";
    	InputStream content = new FileInputStream(path);
    	fileContent.setContent(content);
    	provider.uploadImage(fileContent);
    }
    
    public void testRemoveGateway() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	WebApp api = provider.getAPI(apiId);
    	provider.updateWebAppSynapse(api);
    }
    

    public void testUpdateMap() throws AppManagementException, FaultGatewaysException {
    	String authorizedAdminCookie = null;
    	APIIdentifier apiId = new APIIdentifier(userName, "Map", "1.0.0");
    	WebApp app = provider.getAPI(apiId);
//    	app.setThumbnailUrl("http://app.codefarm.co.kr/default/images/main/app_icon03m.png");
//    	app.setDescription("??????????????? ???????????? ?????? ???????????????????????????.");
//    	app.setApiName("?????? ??????????????????");
    	app.setDescription("??????????????? ???????????? ?????? ???????????????????????????.<br/><br/>" +
"????????? NCS?????? ???????????? ?????? ??? ?????? ???????????? ??????????????????????????? ???????????? ????????? ?????? ?????? ????????????????????? ?????? ????????? ?????? ???????????? ???????????? ????????? ?????? ?????? ??? ??????????????? ????????? ??????????????? ???????????? ??????????????? ?????????????????? ??????????????? ??? ??? ?????? ????????? ????????? ?????? ????????????.<br/><br/>" +
"????????? ????????? ????????? ?????? NCS ???????????? ???????????? ??????<br/><br/>" +
"??????????????????????????? ?????? ??????????????????<br/>" +
"NCS ??????????????? ????????? NCS?????????????????? ??????????????? ??????<br/>" +
"NCS???????????? ?????? ????????????????????? ????????? ??????????????? ??????<br/><br/>" +
"NCS?????? ???????????? ????????????????????? ????????? ??????????????? ??????<br/>" +
"????????? ????????? ???????????? ?????? ??????<br/>" +
"???????????? ????????? ????????? ????????? ??????<br/><br/>" +
"??????????????????????????? ????????? Framework??????<br/>" +
"JAVA,JSP,iBatis??? ?????? ?????? ?????? ??????(????????? ?????? X-PLATFORM??????)<br/>" +
"????????????????????? ????????? ????????? ??????<br/><br/>" +
"????????????????????? ?????? ???????????? ??????<br/>" +
"NCS??????????????? ????????? NCS ?????????????????? ??????????????? ??????");
    	provider.updateAPI(app, authorizedAdminCookie);

    }
    
    public void testUpdateWeather() throws AppManagementException, FaultGatewaysException {
    	String authorizedAdminCookie = null;
    	APIIdentifier apiId = new APIIdentifier(userName, "Weather", "1.0.0");
    	WebApp app = provider.getAPI(apiId);
//    	app.setThumbnailUrl("http://app.codefarm.co.kr/default/images/main/app_icon03m.png");
//    	app.setDescription("??????????????? ???????????? ?????? ???????????????????????????.");
//    	app.setApiName("?????? ??????????????????");
    	app.setDescription("??????????????? ???????????? ?????? ???????????????????????????.<br/><br/> " +
"????????? NCS?????? ???????????? ?????? ??? ?????? ???????????? ??????????????????????????? ???????????? ????????? ?????? ?????? ????????????????????? ?????? ????????? ?????? ???????????? ???????????? ????????? ?????? ?????? ??? ??????????????? ????????? ??????????????? ???????????? ??????????????? ?????????????????? ??????????????? ??? ??? ?????? ????????? ????????? ?????? ????????????.<br/><br/>" +
"????????? ????????? ????????? ?????? NCS ???????????? ???????????? ??????<br/><br/>" +
"??????????????????????????? ?????? ??????????????????<br/>" +
"NCS ??????????????? ????????? NCS?????????????????? ??????????????? ??????<br/>" +
"NCS???????????? ?????? ????????????????????? ????????? ??????????????? ??????<br/><br/>" +
"NCS?????? ???????????? ????????????????????? ????????? ??????????????? ??????<br/>" +
"????????? ????????? ???????????? ?????? ??????<br/>" +
"???????????? ????????? ????????? ????????? ??????<br/><br/>" +
"??????????????????????????? ????????? Framework??????<br/>" +
"JAVA,JSP,iBatis??? ?????? ?????? ?????? ??????(????????? ?????? X-PLATFORM??????)<br/>" +
"????????????????????? ????????? ????????? ??????<br/><br/>" +
"????????????????????? ?????? ???????????? ??????<br/>" +
"NCS??????????????? ????????? NCS ?????????????????? ??????????????? ??????");
    	provider.updateAPI(app, authorizedAdminCookie);

    }
    
    public void testUpdateCal() throws AppManagementException, FaultGatewaysException {
    	String authorizedAdminCookie = null;
    	APIIdentifier apiId = new APIIdentifier(userName, "Calendar", "1.0.0");
    	WebApp app = provider.getAPI(apiId);
//    	app.setThumbnailUrl("http://app.codefarm.co.kr/default/images/main/app_icon03m.png");
//    	app.setDescription("??????????????? ???????????? ?????? ???????????????????????????.");
//    	app.setApiName("?????? ??????????????????");
    	app.setDescription("Android ???????????? ??? ???????????? ??????  ???????????? ?????????????????? ????????? ???????????? ????????? ?????? ??????????????? ????????? ?????????.<br/>" +
"??? ?????? ?????? ???????????? ????????? ???????????? - ??????, ??????, ?????? ??? ????????? ????????? ????????? ??? ????????????.<br/>" +
"??? Gmail??? ????????? ?????? - ?????????, ??????, ?????????, ?????? ?????? ?????? ????????? ???????????? ???????????? ???????????????.<br/>" +
"??? ??? ??? - ??????????????? ???????????? ??? ??? ????????? ????????? ????????? ?????? ????????? ??? ????????????.<br/>" +
"??? ?????? - '???????????? 3?????? ????????????' ?????? ???????????? ????????? ???????????? ???????????? ???????????? ????????? ????????? ?????????.<br/>" +
"??? ?????? ???????????? ???????????? ?????? - ???????????? Exchange??? ???????????? ?????????????????? ?????? ?????? ?????? ???????????? ???????????????.<br/><br/>" +
"?????? ???????????? ??????<br/>" +
"??? ?????????: ?????? ??????, ?????? ??? ????????? ??????<br/><br/>" +
"?????? ???????????? ??????<br/>" +
"??? ????????????: ?????? ?????? ?????? ?????? ??? ?????? ?????? ????????? ??????<br/>" +
"??? ?????????: ????????? ?????? ?????? ??? ????????? ????????? ??????<br/><br/>" +
"* ????????? ??????????????? ???????????? ???????????? ????????? ???????????? ????????? ???????????????.");
    	provider.updateAPI(app, authorizedAdminCookie);

    }
    
}



