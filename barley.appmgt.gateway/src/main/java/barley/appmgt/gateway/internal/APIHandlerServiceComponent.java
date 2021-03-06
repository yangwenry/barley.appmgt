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

package barley.appmgt.gateway.internal;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
//import org.osgi.framework.BundleContext;
//import org.osgi.service.component.ComponentContext;

import barley.appmgt.gateway.service.AppManagerOAuth2Service;
import barley.appmgt.impl.AppManagerConfiguration;
import barley.appmgt.impl.AppManagerConfigurationService;
import barley.appmgt.impl.AppManagerConfigurationServiceImpl;
import barley.core.utils.BarleyUtils;
import barley.core.utils.ConfigurationContextService;

/**
 * @scr.component name="org.wso2.carbon.appmgt.handlers" immediate="true"
 * @scr.reference name="configuration.context.service"
 * interface="org.wso2.carbon.utils.ConfigurationContextService" cardinality="1..1"
 * policy="dynamic" bind="setConfigurationContextService" unbind="unsetConfigurationContextService"
 */
public class APIHandlerServiceComponent {
    
    private static final Log log = LogFactory.getLog(APIHandlerServiceComponent.class);

    //private static BundleContext bundleContext;
    private static AppManagerConfigurationService amConfigService;

    protected void activate() {
        //Registering AppManagerOAuth2Service as a OSGIService
        //bundleContext = context.getBundleContext();
        //bundleContext.registerService(AppManagerOAuth2Service.class.getName(), new AppManagerOAuth2Service(), null);
        if (log.isDebugEnabled()) {
            log.debug("App Manager sampl deployer component activated");
        }
        String filePath = null;
        try {
            //Initializing ApiManager Configuration
            AppManagerConfiguration configuration = new AppManagerConfiguration();
            filePath = BarleyUtils.getCarbonHome() + File.separator + "repository" +
                    File.separator + "conf" + File.separator + "app-manager.xml";
            configuration.load(filePath);
            amConfigService = new AppManagerConfigurationServiceImpl(configuration);
            ServiceReferenceHolder.getInstance().setAPIManagerConfigurationService(amConfigService);

        } catch (Throwable e) {
            log.error("APPManagerConfiguration initialization failed from file path : " + filePath, e);
        }
    }

    protected void deactivate() {
        if (log.isDebugEnabled()) {
            log.debug("WebApp handlers component deactivated");
        }

    }

    protected void setConfigurationContextService(ConfigurationContextService cfgCtxService) {
        if (log.isDebugEnabled()) {
            log.debug("Configuration context service bound to the WebApp handlers");
        }
        ServiceReferenceHolder.getInstance().setConfigurationContextService(cfgCtxService);
    }

    protected void unsetConfigurationContextService(ConfigurationContextService cfgCtxService) {
        if (log.isDebugEnabled()) {
            log.debug("Configuration context service unbound from the WebApp handlers");
        }
        ServiceReferenceHolder.getInstance().setConfigurationContextService(null);
    }

}
