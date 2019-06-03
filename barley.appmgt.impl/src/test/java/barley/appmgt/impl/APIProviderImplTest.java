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

import static org.testng.Assert.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.api.APIProvider;
import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.dto.UserApplicationAPIUsage;
import barley.appmgt.api.model.APIIdentifier;
import barley.appmgt.api.model.APIStatus;
import barley.appmgt.api.model.AppDefaultVersion;
import barley.appmgt.api.model.AppStore;
import barley.appmgt.api.model.BusinessOwner;
import barley.appmgt.api.model.Documentation;
import barley.appmgt.api.model.DocumentationType;
import barley.appmgt.api.model.EntitlementPolicyGroup;
import barley.appmgt.api.model.FileContent;
import barley.appmgt.api.model.LifeCycleEvent;
import barley.appmgt.api.model.Provider;
import barley.appmgt.api.model.SSOProvider;
import barley.appmgt.api.model.Subscriber;
import barley.appmgt.api.model.Tag;
import barley.appmgt.api.model.Tier;
import barley.appmgt.api.model.URITemplate;
import barley.appmgt.api.model.Usage;
import barley.appmgt.api.model.WebApp;
import barley.appmgt.api.model.entitlement.EntitlementPolicy;
import barley.appmgt.impl.AppMConstants.LifecycleActions;
import barley.appmgt.impl.internal.AppManagerComponent;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.core.BarleyConstants;
import barley.core.utils.BarleyUtils;
import barley.identity.core.util.IdentityConfigParser;
import barley.identity.core.util.IdentityTenantUtil;
import barley.registry.core.ActionConstants;
import barley.registry.core.Registry;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.service.TenantRegistryLoader;
import barley.registry.core.utils.UUIDGenerator;
import barley.registry.indexing.service.TenantIndexingLoader;
import barley.user.core.UserMgtConstants;
import barley.user.core.service.RealmService;

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
		
		// lifecyle 초기화 (system)
        barley.governance.lcm.util.CommonUtil.addDefaultLifecyclesIfNotAvailable(embeddedRegistryService.getConfigSystemRegistry(),
        		embeddedRegistryService.getRegistry(BarleyConstants.REGISTRY_SYSTEM_USERNAME));
        // lifecyle 초기화 (테넌트) - 경로가 /system/.. 로 부터 시작한다. 
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
    	assertNotEquals(id, 0);
    
    	WebApp webapp = provider.getWebApp(uuid);
    	assertNotNull(webapp);
    	
    	// dao만 조회
    	WebApp webappByUUID = provider.getAppDetailsFromUUID(uuid);
    	assertNotNull(webappByUUID);
    	
    	// dao만 조회 
    	List<WebApp> allApps = provider.getAllWebApps();
    	assertNotNull(allApps);
    	List<WebApp> allDomainApps = provider.getAllWebApps(getTenantDomain());
    	assertNotNull(allDomainApps);
    	
    	// api 검색 
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
    
    // (실패) rest.api 프로젝트에서 사용한다. (AppsApiServiceImpl)
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
    
    public void testUpdateWebApp() throws AppManagementException {
    	String authorizedAdminCookie = null;
    	APIIdentifier apiId = new APIIdentifier(userName, "NCS", "1.0.0");
    	WebApp app = provider.getAPI(apiId);
//    	app.setThumbnailUrl("http://app.codefarm.co.kr/default/images/main/app_icon03m.png");
//    	app.setDescription("코드팜에서 제공하는 날씨 어플리케이션입니다.");
//    	app.setApiName("날씨 어플리케이션");
//    	app.setDescription("코드팜에서 제공하는 통합 수업관리 프로그램입니다.<br/><br/>" +
//"코드팜 NCS기반 교육과정 운영 및 관리 시스템은 대학교육업무처리의 효율성을 높이기 위해 현행 학사시스템과의 연계 처리를 통한 확장성과 유연성을 고려한 환경 조성 및 유지보수가 용이한 시스템으로 대학교의 교육품질을 개선함으로써 직무능력을 갖 춘 인재 양성에 목적을 두고 있습니다.<br/><br/>" +
//"교육부 특성화 지침에 따른 NCS 교과과정 프로세스 확보<br/><br/>" +
//"직무능력구성요소에 맞는 교과과정수립<br/>" +
//"NCS 교과과정이 반영된 NCS기반교육과정 관리시스템 확보<br/>" +
//"NCS교육과정 운영 관리시스템과의 연계한 학사시스템 보유<br/><br/>" +
//"NCS기반 교육과정 관리스스템과의 연동된 학사시스템 확보<br/>" +
//"학습자 주도의 학습관리 체계 구축<br/>" +
//"최신요소 기술이 적용된 시스템 보유<br/><br/>" +
//"전자정부프레임워크 기반의 Framework도입<br/>" +
//"JAVA,JSP,iBatis의 최신 개발 방법 적용(협의에 따른 X-PLATFORM도입)<br/>" +
//"최신요소기술이 적용된 시스템 보유<br/><br/>" +
//"직무능력구성에 맞는 교과과정 수립<br/>" +
//"NCS교과과정이 반영된 NCS 기반교육과정 관리시스템 확보");
    	app.setTitle("종합학사시스템");
    	
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
    
    // (성공)
    public void testAddWebApp() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MapDemo", "1.0.0");
    	WebApp app = new WebApp(apiId);
    	app.setApiName("MapDemo");
    	app.setTitle("메모 데모");
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
    
    public void testDeleteApi() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MapDemo", "1.0.0");
    	SSOProvider ssoProvider = null;
    	String authorizedAdminCookie = null;
    	provider.deleteApp(apiId, ssoProvider, authorizedAdminCookie);
    }
    
    public void testChangeApiStatus() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MapDemo", "1.0.0");
    	WebApp api = provider.getAPI(apiId);
    	APIStatus status = APIStatus.PUBLISHED;
    	String userId = userName;	// userId 파라미터는 사용되지 않는다.
    	
    	boolean updateGatewayConfig = true;    	
    	provider.changeAPIStatus(api, status, userId, updateGatewayConfig);
    	
//    	String resourceId = provider.getAppUUIDbyName("MyFirstApp", "1.0.0", getTenantId());
//    	String action = LifecycleActions.PUBLISH;
//    	provider.changeLifeCycleStatus(AppMConstants.WEBAPP_ASSET_TYPE, resourceId, action);
    }
    
    // (성공)
    public void testSaveBusinessOwner() throws AppManagementException {
    	int businessOwnerId = 6;
    	BusinessOwner businessOwner = new BusinessOwner();
    	businessOwner.setBusinessOwnerName("오세창");
    	businessOwner.setBusinessOwnerEmail("yangwenry@gmail.com");
    	businessOwner.setBusinessOwnerSite("http://www.codefarm.co.kr");
    	businessOwner.setBusinessOwnerDescription("저는 당신의 파트너 입니다.");
    	provider.saveBusinessOwner(businessOwner);
    	
    	// update
    	businessOwner.setBusinessOwnerId(businessOwnerId);
    	businessOwner.setBusinessOwnerName("강민경");
    	businessOwner.setBusinessOwnerEmail("kang@gmail.com");
    	businessOwner.setBusinessOwnerSite("http://www.codefarm.co.kr");
    	businessOwner.setBusinessOwnerDescription("사랑합니다.");
    	provider.updateBusinessOwner(businessOwner);
    	
    	// get
    	BusinessOwner fromBusinessOwner = provider.getBusinessOwner(businessOwnerId);
    	assertEquals(businessOwner.getBusinessOwnerName(), fromBusinessOwner.getBusinessOwnerName());
    	
    	List<BusinessOwner> fromBusinessOwners = provider.getBusinessOwners();
    	assertEquals(1, fromBusinessOwners.size());
    	
    	assertEquals(1, provider.getBusinessOwnersCount());
    	
    	assertEquals(businessOwnerId, provider.getBusinessOwnerId("강민경", "kang@gmail.com"));
    	
    	fromBusinessOwners = provider.searchBusinessOwners(0, 10, "강민경");
    	assertEquals(1, fromBusinessOwners.size());
    	
    	// delete
    	boolean success = provider.deleteBusinessOwner(String.valueOf(businessOwnerId));
    	assertTrue(success);
    }
    
    // (실패) 프로바이더를 저장하는 곳을 찾지 못했다. 회원가입시 프로바이더를 저장하지 않을까 추측된다.
    public void testProvider() throws AppManagementException {
//    	Set<Provider> providers = provider.getAllProviders();
//    	assertEquals(1, providers.size());
    	
    	Provider fromProvider = provider.getProvider(userName);
    	assertNotNull(fromProvider);
    	
    }
    
    // setup에서 tiers/default-tiers.xml 데이터가 리소스 테이블에 들어간다. 
    public void testTier() throws AppManagementException, IOException {
    	String tierName = "Lemon";    	
    	Tier addTier = new Tier(tierName);
    	// policy content로 데이터를 넣기 때문에 주석처리하였으며 DisplayName으로 tier 존재여부를 확인하기 때문에 
    	// DisplayName과 policyContent의 이름이 동일해야 한다.  
    	addTier.setDisplayName("Lemon");
//    	addTier.setDescription("mytier");
//    	addTier.setRequestPerMinute(3000);
    	String path = "D:\\Workspace_STS_APPM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\tier-lemon.txt";
    	InputStream inputStream = new FileInputStream(path);
    	byte[] policyContent = IOUtils.toByteArray(inputStream);
    	addTier.setPolicyContent(policyContent);
//    	provider.addTier(addTier);
    	
    	// APIManager 인터페이스 
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
    	// dao APM_TIER_PERMISSIONS 테이블에 저장 
    	String tierName = "Gold";
    	String permissionType = AppMConstants.TIER_PERMISSION_ALLOW;
    	String roles = "internal/everyone";
    	provider.updateTierPermissions(tierName, permissionType, roles);
    	
    	Set permissions = provider.getTierPermissions();
    	assertEquals(1, permissions.size());
    }
    
    public void testApiUsage() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	// 구현되어 있지 않음
//    	Usage usage = provider.getUsageByAPI(apiId);
//    	assertNotNull(usage);
    	
    	// (오류)
    	UserApplicationAPIUsage[] usages = provider.getAllAPIUsageByProvider(userName);
    	assertNotNull(usages);
    	
    	// 구현되어 있지 않음
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
    	
    	// 구독카운트
    	boolean isSubscriptionOn = true;
    	String fromDate = "2018-01-05";
    	String toDate = "2018-05-15";
    	Map<String, Long> subCount = provider.getSubscriptionCountByAPPs(userName, fromDate, toDate, isSubscriptionOn);
    	assertEquals(2, subCount.size());
    	
    	// 구독카운트 
    	long count = provider.getAPISubscriptionCountByAPI(apiId);
    	assertEquals(1, count);
    	
    	Map<String, List> users = provider.getSubscribedAPPsByUsers(fromDate, toDate);
    	assertFalse(users.isEmpty());
    }
    
    
    // (실패) 인증서비스는 차후 구현예정이므로 차후 테스트  
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
    	doc.setSummary("이렇게 사용하면 됩니다.");
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
    	String filename = "첨부파일";
//    	provider.addFileToDocumentation(api, doc, filename, inputStream, "txt");
    	
    	// docName 파라미터를 사용하지 않는다. 값이 고정되어 있음.
    	String apiDocName = "api-doc.json";
//    	provider.addAPIDefinitionContent(apiId, apiDocName, text);
    	
    	// files과 contents는 복사되지 않는다. 
    	String toVersion = "2.0.0";
//    	provider.copyAllDocumentation(apiId, toVersion);
    	
    	// contents도 복사.
//    	provider.copyWebappDocumentations(api, toVersion);
    	
    	// docType 파라미터는 사용하지 않음.
//    	provider.removeDocumentation(apiId, documentationName, "HOWTO");
    	
    	String documentId = "a6e5c152-5800-4f20-aeb3-9f66c86e91ce";
//    	provider.removeDocumentation(apiId, documentId);
    }
    
    public void testLifecycle() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	
    	// dao 검색 
    	List<LifeCycleEvent> events = provider.getLifeCycleEvents(apiId);
    	assertEquals(2, events.size());
    	
    	String resourceId = provider.getAppUUIDbyName("MyFirstApp", "1.0.0", getTenantId());
    	String[] actions = provider.getAllowedLifecycleActions(resourceId, AppMConstants.WEBAPP_ASSET_TYPE);
    	assertEquals(1, actions.length);
    	
    	// changeApiStatus를 실행하여 dao쪽에도 데이터를 입력 후 라이프사이클 상태값도 따로 변경해야한다.
    	String action = LifecycleActions.SUBMIT_FOR_REVIEW;
    	provider.changeLifeCycleStatus(AppMConstants.WEBAPP_ASSET_TYPE, resourceId, action);
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
    	
    	// 실패 
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
    	
    	// 데이터가 존재하지 않음.
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
    
    // (실패) resource 테이블이 존재하지 않음 -> jag 소스를 보면 storage.js에서 처리함.
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
    

    public void testUpdateMap() throws AppManagementException {
    	String authorizedAdminCookie = null;
    	APIIdentifier apiId = new APIIdentifier(userName, "Map", "1.0.0");
    	WebApp app = provider.getAPI(apiId);
//    	app.setThumbnailUrl("http://app.codefarm.co.kr/default/images/main/app_icon03m.png");
//    	app.setDescription("코드팜에서 제공하는 날씨 어플리케이션입니다.");
//    	app.setApiName("날씨 어플리케이션");
    	app.setDescription("코드팜에서 제공하는 지도 어플리케이션입니다.<br/><br/>" +
"코드팜 NCS기반 교육과정 운영 및 관리 시스템은 대학교육업무처리의 효율성을 높이기 위해 현행 학사시스템과의 연계 처리를 통한 확장성과 유연성을 고려한 환경 조성 및 유지보수가 용이한 시스템으로 대학교의 교육품질을 개선함으로써 직무능력을 갖 춘 인재 양성에 목적을 두고 있습니다.<br/><br/>" +
"교육부 특성화 지침에 따른 NCS 교과과정 프로세스 확보<br/><br/>" +
"직무능력구성요소에 맞는 교과과정수립<br/>" +
"NCS 교과과정이 반영된 NCS기반교육과정 관리시스템 확보<br/>" +
"NCS교육과정 운영 관리시스템과의 연계한 학사시스템 보유<br/><br/>" +
"NCS기반 교육과정 관리스스템과의 연동된 학사시스템 확보<br/>" +
"학습자 주도의 학습관리 체계 구축<br/>" +
"최신요소 기술이 적용된 시스템 보유<br/><br/>" +
"전자정부프레임워크 기반의 Framework도입<br/>" +
"JAVA,JSP,iBatis의 최신 개발 방법 적용(협의에 따른 X-PLATFORM도입)<br/>" +
"최신요소기술이 적용된 시스템 보유<br/><br/>" +
"직무능력구성에 맞는 교과과정 수립<br/>" +
"NCS교과과정이 반영된 NCS 기반교육과정 관리시스템 확보");
    	provider.updateAPI(app, authorizedAdminCookie);

    }
    
    public void testUpdateWeather() throws AppManagementException {
    	String authorizedAdminCookie = null;
    	APIIdentifier apiId = new APIIdentifier(userName, "Weather", "1.0.0");
    	WebApp app = provider.getAPI(apiId);
//    	app.setThumbnailUrl("http://app.codefarm.co.kr/default/images/main/app_icon03m.png");
//    	app.setDescription("코드팜에서 제공하는 날씨 어플리케이션입니다.");
//    	app.setApiName("날씨 어플리케이션");
    	app.setDescription("코드팜에서 제공하는 날씨 어플리케이션입니다.<br/><br/> " +
"코드팜 NCS기반 교육과정 운영 및 관리 시스템은 대학교육업무처리의 효율성을 높이기 위해 현행 학사시스템과의 연계 처리를 통한 확장성과 유연성을 고려한 환경 조성 및 유지보수가 용이한 시스템으로 대학교의 교육품질을 개선함으로써 직무능력을 갖 춘 인재 양성에 목적을 두고 있습니다.<br/><br/>" +
"교육부 특성화 지침에 따른 NCS 교과과정 프로세스 확보<br/><br/>" +
"직무능력구성요소에 맞는 교과과정수립<br/>" +
"NCS 교과과정이 반영된 NCS기반교육과정 관리시스템 확보<br/>" +
"NCS교육과정 운영 관리시스템과의 연계한 학사시스템 보유<br/><br/>" +
"NCS기반 교육과정 관리스스템과의 연동된 학사시스템 확보<br/>" +
"학습자 주도의 학습관리 체계 구축<br/>" +
"최신요소 기술이 적용된 시스템 보유<br/><br/>" +
"전자정부프레임워크 기반의 Framework도입<br/>" +
"JAVA,JSP,iBatis의 최신 개발 방법 적용(협의에 따른 X-PLATFORM도입)<br/>" +
"최신요소기술이 적용된 시스템 보유<br/><br/>" +
"직무능력구성에 맞는 교과과정 수립<br/>" +
"NCS교과과정이 반영된 NCS 기반교육과정 관리시스템 확보");
    	provider.updateAPI(app, authorizedAdminCookie);

    }
    
    public void testUpdateCal() throws AppManagementException {
    	String authorizedAdminCookie = null;
    	APIIdentifier apiId = new APIIdentifier(userName, "Calendar", "1.0.0");
    	WebApp app = provider.getAPI(apiId);
//    	app.setThumbnailUrl("http://app.codefarm.co.kr/default/images/main/app_icon03m.png");
//    	app.setDescription("코드팜에서 제공하는 날씨 어플리케이션입니다.");
//    	app.setApiName("날씨 어플리케이션");
    	app.setDescription("Android 스마트폰 및 태블릿용 공식  캘린더를 다운로드하여 시간을 절약하고 하루를 더욱 효율적으로 활용해 보세요.<br/>" +
"• 여러 가지 방법으로 캘린더 확인하기 - 월간, 주간, 일간 뷰 사이에 빠르게 전환할 수 있습니다.<br/>" +
"• Gmail에 포함된 일정 - 항공편, 호텔, 콘서트, 식당 예약 등의 정보가 캘린더에 자동으로 입력됩니다.<br/>" +
"• 할 일 - 리마인더를 사용하여 할 일 목록을 만들고 일정과 함께 확인할 수 있습니다.<br/>" +
"• 목표 - '일주일에 3번씩 조깅하기' 같은 개인적인 목표를 추가하면 캘린더가 자동으로 시간을 예약해 줍니다.<br/>" +
"• 모든 캘린더를 한곳에서 보기 - 캘린더는 Exchange를 비롯하여 휴대전화에서 사용 중인 모든 캘린더와 호환됩니다.<br/><br/>" +
"필수 접근권한 안내<br/>" +
"• 캘린더: 일정 생성, 수정 및 표시에 필요<br/><br/>" +
"선택 접근권한 안내<br/>" +
"• 위치정보: 일정 생성 또는 수정 시 주변 장소 추천에 필요<br/>" +
"• 연락처: 참석자 정보 표시 및 참석자 초대에 필요<br/><br/>" +
"* 선택적 접근권한의 허용에는 동의하지 않아도 서비스의 이용이 가능합니다.");
    	provider.updateAPI(app, authorizedAdminCookie);

    }
    
}



