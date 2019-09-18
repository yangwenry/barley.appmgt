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
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.io.FileUtils;

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
        initializeDatabase();
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
    	System.setProperty(ServerConstants.CARBON_HOME, "D:\\Workspace_STS_SaaSPlatform\\Workspace_STS_APIM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\");
        System.setProperty(ServerConstants.CARBON_CONFIG_DIR_PATH, "D:\\Workspace_STS_SaaSPlatform\\Workspace_STS_APIM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\repository\\conf\\");
        System.setProperty(AppMConstants.APP_MANAGER_HOME, "D:\\Workspace_STS_SaaSPlatform\\Workspace_STS_APIM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\");
        System.setProperty(AppMConstants.APP_MANAGER_CONFIG_DIR_PATH, "D:\\Workspace_STS_SaaSPlatform\\Workspace_STS_APIM\\barley.appmgt\\barley.appmgt.impl\\src\\test\\resources\\repository\\conf\\");
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
    
    private void initializeDatabase() {

    	String configFilePath = BarleyUtils.getCarbonConfigDirPath() + "app-manager.xml";
        InputStream in = null;
        try {
            in = FileUtils.openInputStream(new File(configFilePath));
            StAXOMBuilder builder = new StAXOMBuilder(in);
            OMElement databaseElement = builder.getDocumentElement().getFirstChildWithName(new QName("Database"));
            String databaseURL = databaseElement.getFirstChildWithName(new QName("URL")).getText();
            String databaseUser = databaseElement.getFirstChildWithName(new QName("Username")).getText();
            String databasePass = databaseElement.getFirstChildWithName(new QName("Password")).getText();
            String databaseDriver = databaseElement.getFirstChildWithName(new QName("Driver")).getText();

            BasicDataSource basicDataSource = new BasicDataSource();
            basicDataSource.setDriverClassName(databaseDriver);
            basicDataSource.setUrl(databaseURL);
            basicDataSource.setUsername(databaseUser);
            basicDataSource.setPassword(databasePass);

            // Create initial context
            System.setProperty(Context.INITIAL_CONTEXT_FACTORY,
                        "org.apache.naming.java.javaURLContextFactory");
            System.setProperty(Context.URL_PKG_PREFIXES,
                    "org.apache.naming");
            try {
                InitialContext.doLookup("java:/comp/env/jdbc/WSO2APPM_DB");
            } catch (NamingException e) {
                InitialContext ic = new InitialContext();
                ic.createSubcontext("java:");
                ic.createSubcontext("java:/comp");
                ic.createSubcontext("java:/comp/env");
                ic.createSubcontext("java:/comp/env/jdbc");

                ic.bind("java:/comp/env/jdbc/WSO2APPM_DB", basicDataSource);
            }
        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NamingException e) {
            e.printStackTrace();
        }
    }
    

    public class RealmUnawareRegistryCoreServiceComponent extends RegistryCoreServiceComponent {

        public void setRealmService(RealmService realmService) {
            super.setRealmService(realmService);
        }
    }
    
    
}
