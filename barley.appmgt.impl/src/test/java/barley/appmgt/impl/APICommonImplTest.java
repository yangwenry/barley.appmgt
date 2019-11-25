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

import static org.testng.Assert.assertNotEquals;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.api.APIProvider;
import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.dto.UserApplicationAPIUsage;
import barley.appmgt.api.model.APIIdentifier;
import barley.appmgt.api.model.APIKey;
import barley.appmgt.api.model.APIStatus;
import barley.appmgt.api.model.BusinessOwner;
import barley.appmgt.api.model.Documentation;
import barley.appmgt.api.model.DocumentationType;
import barley.appmgt.api.model.Icon;
import barley.appmgt.api.model.Provider;
import barley.appmgt.api.model.SSOProvider;
import barley.appmgt.api.model.Subscriber;
import barley.appmgt.api.model.Tier;
import barley.appmgt.api.model.URITemplate;
import barley.appmgt.api.model.Usage;
import barley.appmgt.api.model.WebApp;
import barley.appmgt.impl.internal.AppManagerComponent;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.AppManagerUtil;
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
import barley.user.api.UserStoreException;
import barley.user.core.UserMgtConstants;
import barley.user.core.service.RealmService;

public class APICommonImplTest extends BaseTestCase {

    private static final Log log = LogFactory.getLog(APICommonImplTest.class);
    
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
    	this.provider = APIManagerFactory.getInstance().getAPIProvider(userName);
    }
    
    public void testGetApi() throws AppManagementException {
    	List<WebApp> apps = provider.getAllAPIs();
    	assertNotNull(apps);
    	
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	WebApp app = provider.getAPI(apiId);
    	assertNotNull(app);
    	
    	assertTrue(provider.isAPIAvailable(apiId));
    	
    	assertTrue(provider.isContextExist("/t/codefarm.co.kr/wso2earth"));
    	
    	Set<String> versions = provider.getAPIVersions(userName, "MyFirstApp");
    	assertTrue(versions.contains("1.0.0"));
    }
    
    public void testDocument() throws AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	String docName = "howto5";
    	assertTrue(provider.isDocumentationExist(apiId, docName));
    	
    	String content = provider.getDocumentationContent(apiId, docName);
    	assertEquals("contentcontentcontentcontentcontentcontent", content);
    	
    	List<Documentation> docs = provider.getAllDocumentation(apiId);
    	assertEquals(1, docs.size());
    	
    	List<Documentation> docsByUserName = provider.getAllDocumentation(apiId, userName);
    	assertEquals(1, docsByUserName.size());
    	
    	String docId = "05553aee-406a-4937-a5cf-b49b649aa06d";
    	Documentation doc = provider.getDocumentation(docId, getTenantDomain());
    	assertEquals(docName, doc.getName());
    	
    	// docType 파라미터는 사용되지 않는다. 
    	Documentation docByType = provider.getDocumentation(apiId, DocumentationType.HOWTO, docName);
    	assertEquals(docName, docByType.getName());
    	
    }
    
    // subscriber를 추가하면 자동으로 application을 생성한다.
    public void testSubscriber() throws AppManagementException {
    	String userId = "yangwenry@codefarm.co.kr";
    	provider.addSubscriber(userId);
    	
    	Subscriber subscriber = new Subscriber(userId);
    	subscriber.setEmail("yangwenry@gmail.com");
    	subscriber.setDescription("이제 구독의 시작입니다. 코드팜에서");
    	subscriber.setSubscribedDate(new Date());
    	subscriber.setTenantId(getTenantId());
    	
    	int subscriberId = 6;
    	subscriber.setId(subscriberId);
    	subscriber.setEmail("osc@codefarm.co.kr");
    	subscriber.setDescription("처음부터 다시 시작하는 마음으로");
//    	provider.updateSubscriber(subscriber);
    	
    	Subscriber fromSubscriber = provider.getSubscriber(subscriberId);
    	assertEquals(subscriber.getName(), fromSubscriber.getName());
    	
    	// 실패 
    	Set<WebApp> app = provider.getSubscriberAPIs(subscriber);
    	assertTrue(app.size() > 0);
    }

    // 오류 
    public void testApplicationKey() throws AppManagementException {
    	String accessToken = "";
    	assertFalse(provider.isApplicationTokenExists(accessToken));
    	
    	assertFalse(provider.isApplicationTokenRevoked(accessToken));
    	
    	APIKey key = provider.getAccessTokenData(accessToken);
    	assertNotNull(key);
    	
    	String searchType = "User";
    	String searchTerm = "admin";
    	String loggedInUser = userName;
    	Map<Integer, APIKey> keys = provider.searchAccessToken(searchType, searchTerm, loggedInUser);
//    	assertTrue(keys.containsValue("abc"));
    	
    	
    }
    
    public void testIcon() throws FileNotFoundException, AppManagementException {
    	APIIdentifier apiId = new APIIdentifier(userName, "MyFirstApp", "1.0.0");
    	
    	String path = "D:\\Workspace_STS_APPM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\sample_icon.png";
    	InputStream input = new FileInputStream(path);
    	Icon icon = new Icon(input, "image/png");
    	String resourcePath = AppManagerUtil.getIconPath(apiId);
    	provider.addIcon(resourcePath, icon);
    	
    	Icon apiIcon = provider.getIcon(apiId);
    	assertNotNull(apiIcon);
    }
    
    
    //---------------- 기초 데이터 입력 ----------------------//
    // registry 삭제 
    public void testDeleteResource() throws RegistryException {
    	String appCollectionPath = "/appmgt/applicationdata/provider/admin-AT-codefarm.co.kr/MyFirstApp";
    	String tenantUserName = "admin";
    	Registry registry = embeddedRegistryService.getGovernanceUserRegistry(tenantUserName, getTenantId());
    	registry.delete(appCollectionPath);
    }
    
    public void testAddRole() throws Exception {
    	RealmService realmService = ServiceReferenceHolder.getInstance().getRealmService();
    	int tenantId = 1;
    	
    	// um_role -> um_user_role 데이터 추가 - 사용자에게 롤을 부여 
//    	String roleName = "internal/everyone";
//    	String[] userList = {"wso2.system.user"};
//    	String roleName = "everyone";
//    	String[] userList = {"wso2.anonymous.user"};
    	
//    	Permission[] permissions = new Permission[] {
//    		new Permission(APIConstants.Permissions.API_CREATE, UserMgtConstants.EXECUTE_ACTION)
//    	};
//    	realmService.getTenantUserRealm(tenantId).getUserStoreManager().addRole(roleName, userList, permissions);
    	
    	// um_role_permission => um_permission 테이블 데이터 추가. 즉, 롤별 권한 부여 
    	String roleName = "admin";
//    	String roleName = "barley.anonymous.role";	// um_user_role에서 um_role 테이블을 조회하면 사용자의 롤을 조회할 수 있음.
//    	String action = UserMgtConstants.EXECUTE_ACTION;
//    	String resourceId = AppMConstants.Permissions.WEB_APP_DELETE;
//    	realmService.getTenantUserRealm(tenantId).getAuthorizationManager().authorizeRole(roleName, resourceId, action);
    	
    	// um_user_role
    	String userName = "wso2.anonymous.user";
    	String[] deletedRoles = null;
    	String[] newRoles = {"everyone"};
    	realmService.getTenantUserRealm(tenantId).getUserStoreManager().updateRoleListOfUser(userName, deletedRoles, newRoles);
    	
    	// um_user_permission => um_permission
    	// 디프리케이트된 이유를 알겠음. 사용자는 롤별로 움직이기 때문에 굳이 사용자에게 퍼미션을 추가해 줄 필요가 없다. (굳이 사용하지 말자)
    	//String userName = "yangwenry";
//    	String userName = "wso2.anonymous.use";
//    	realmService.getTenantUserRealm(tenantId).getAuthorizationManager().authorizeUser(userName, resourceId, action);
    	
    }
    
    public void testAddUser() throws UserStoreException {
    	RealmService realmService = ServiceReferenceHolder.getInstance().getRealmService();
    	int tenantId = -1234;
    	String accountName = "wso2.system.user";
    	// password 
    	Object credential = new String("admin");
    	String[] roleList = {"internal/everyone"};
    	Map<String, String> claims = new HashMap<String, String>();
    	String profileName = "default";
    	//realmService.getBootstrapRealm().getUserStoreManager().addUser(userName, credential, roleList, claims, profileName);    	
    	realmService.getTenantUserRealm(tenantId).getUserStoreManager().addUser(accountName, credential, roleList, claims, profileName);
    }
    
    
    
    
}


