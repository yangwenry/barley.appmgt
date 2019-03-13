/*
 * Copyright (c) 2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package barley.appmgt.impl;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;

import org.apache.commons.dbcp.BasicDataSource;

import barley.appmgt.impl.BaseTestCase.RealmUnawareRegistryCoreServiceComponent;
import barley.core.MultitenantConstants;
import barley.core.ServerConstants;
import barley.core.context.PrivilegedBarleyContext;
import barley.core.internal.BarleyContextDataHolder;
import barley.core.internal.OSGiDataHolder;
import barley.core.utils.BarleyUtils;
import barley.registry.core.RegistryConstants;
import barley.registry.core.config.RegistryContext;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.internal.RegistryCoreServiceComponent;
import barley.registry.core.jdbc.EmbeddedRegistryService;
import barley.registry.core.jdbc.realm.InMemoryRealmService;
import barley.user.core.service.RealmService;
import junit.framework.TestCase;

public class BaseTestCase extends TestCase {

    protected RegistryContext ctx = null;
    protected InputStream is;
    protected static EmbeddedRegistryService embeddedRegistryService = null;

    public void setUp() throws Exception {
        setupCarbonHome();
        setupContext();
        
        OSGiDataHolder.getInstance().setUserRealmService(ctx.getRealmService());
        
        setSession();
        setUpCache();
        setUpRegistry();
    }

    protected void setupCarbonHome() {
    	/*
        if (System.getProperty("carbon.home") == null) {
            File file = new File("../distribution/kernel/carbon-home");
            if (file.exists()) {
                System.setProperty("carbon.home", file.getAbsolutePath());
            }
            file = new File("../../distribution/kernel/carbon-home");
            if (file.exists()) {
                System.setProperty("carbon.home", file.getAbsolutePath());
            }
        }
        */
    	System.setProperty(ServerConstants.CARBON_HOME, "D:\\Workspace_STS_SaaSPlatform\\Workspace_STS_APPM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\");
        System.setProperty(ServerConstants.CARBON_CONFIG_DIR_PATH, "D:\\Workspace_STS_SaaSPlatform\\Workspace_STS_APPM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\repository\\conf\\");
        System.setProperty("registry.config", "registry.xml");
        // BLOB 값을 쓰고 읽을 때 사용 
        System.setProperty("carbon.registry.character.encoding", "UTF-8");
        
        System.setProperty("AppManagerDBConfigurationPath", BarleyUtils.getCarbonConfigDirPath() + "/app-manager.xml");
        System.setProperty("IdentityConfigurationPath", BarleyUtils.getCarbonConfigDirPath() + "/identity.xml");
        
        // The line below is responsible for initializing the cache.
        BarleyContextDataHolder.getCurrentCarbonContextHolder();        
    }
    
    protected void setupContext() {
        try {
        	BasicDataSource dataSource = new BasicDataSource();
        	String connectionUrl = "jdbc:mysql://172.16.2.201:3306/barley_registry";
            dataSource.setUrl(connectionUrl);
            dataSource.setDriverClassName("com.mysql.jdbc.Driver");
            dataSource.setUsername("cdfcloud");
            dataSource.setPassword("cdfcloud");
        	
            RealmService realmService = new InMemoryRealmService(dataSource);
            is = this.getClass().getClassLoader().getResourceAsStream(System.getProperty("registry.config"));
            // registry.xml 정보와 DataSource가 가미된 realmService를 인자로 주어 RegistryContext를 생성한다. 
            ctx = RegistryContext.getBaseInstance(is, realmService);
        } catch (Exception e) {
        	e.printStackTrace();
        }
        ctx.setSetup(true);
//        ctx.selectDBConfig("h2-db");
        ctx.selectDBConfig("mysql-db");
    }
    
    protected void setSession() {
//		String tenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
//		int tenantId = MultitenantConstants.SUPER_TENANT_ID;
    	String tenantDomain = "codefarm.co.kr";
    	// 파라미터가 true라면 tenantId도 디비를 접속하여 세팅한다. 
		PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
    }
    
    protected int getTenantId() {
    	return PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId();
    }
    
    protected String getTenantDomain() {
    	return PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantDomain();
    }
    
    protected void setUpCache() {
    	long sessionCacheTimeout = 1800L;
    	// registry cache 세팅 
    	Caching.getCacheManager(RegistryConstants.REGISTRY_CACHE_MANAGER).
			createCacheBuilder(RegistryConstants.PATH_CACHE_ID).
			setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.SECONDS, sessionCacheTimeout)).
			setStoreByValue(false).build();
	    Caching.getCacheManager(RegistryConstants.REGISTRY_CACHE_MANAGER).
			createCacheBuilder(RegistryConstants.REGISTRY_CACHE_BACKED_ID).
			setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.SECONDS, sessionCacheTimeout)).
			setStoreByValue(false).build();
	    Caching.getCacheManager(RegistryConstants.REGISTRY_CACHE_MANAGER).
			createCacheBuilder(RegistryConstants.UUID_CACHE_ID).
			setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.SECONDS, sessionCacheTimeout)).
			setStoreByValue(false).build();
    }
    
    protected void setUpRegistry() {
        if (embeddedRegistryService != null) {
            return;
        }

        try {
            embeddedRegistryService = ctx.getEmbeddedRegistryService();
            RealmUnawareRegistryCoreServiceComponent comp = new RealmUnawareRegistryCoreServiceComponent();
            comp.setRealmService(ctx.getRealmService());
            // 각종 핸들러를 추가해준다. 
            comp.registerBuiltInHandlers(embeddedRegistryService);
        } catch (RegistryException e) {
            fail("Failed to initialize the registry. Caused by: " + e.getMessage());
        }
    }

    public class RealmUnawareRegistryCoreServiceComponent extends RegistryCoreServiceComponent {

        public void setRealmService(RealmService realmService) {
            super.setRealmService(realmService);
        }
    }
    
    
}
