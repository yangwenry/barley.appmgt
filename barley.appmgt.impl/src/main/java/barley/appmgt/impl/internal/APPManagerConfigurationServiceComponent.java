/*
*  Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package barley.appmgt.impl.internal;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.impl.AppManagerConfiguration;
import barley.appmgt.impl.AppManagerConfigurationService;
import barley.appmgt.impl.AppManagerConfigurationServiceImpl;
import barley.appmgt.impl.AppMgtXACMLPolicyTemplateReader;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.core.utils.BarleyUtils;
import barley.core.utils.ConfigurationContextService;

/**
 * @scr.component name="org.wso2.appmgt.impl.configuration.services" immediate="true"
 * @scr.reference name="config.context.service"
 * interface="org.wso2.carbon.utils.ConfigurationContextService"
 * cardinality="1..1" policy="dynamic"  bind="setConfigurationContextService"
 * unbind="unsetConfigurationContextService"
 */
public class APPManagerConfigurationServiceComponent {

    private static final Log log = LogFactory.getLog(APPManagerConfigurationServiceComponent.class);
    private static AppManagerConfigurationService amConfigService;

    protected void activate(ComponentContext componentContext) throws Exception {

        if (log.isDebugEnabled()) {
            log.debug("App Manager implementation configuration service component activation started");
        }
        String filePath = null;
        try {
            //Initialize AppManager Configuration
            AppManagerConfiguration configuration = new AppManagerConfiguration();
            filePath = BarleyUtils.getCarbonHome() + File.separator + "repository" +
                    File.separator + "conf" + File.separator + "app-manager.xml";
            configuration.load(filePath);
            amConfigService = new AppManagerConfigurationServiceImpl(configuration);
            ServiceReferenceHolder.getInstance().setAPIManagerConfigurationService(amConfigService);

            //Read xacml policy template
            AppMgtXACMLPolicyTemplateReader xacmlPolicy = new AppMgtXACMLPolicyTemplateReader();
            String xacmlTemplateFilePath = BarleyUtils.getCarbonHome() + File.separator + "repository" +
                    File.separator + "resources" + File.separator + "entitlement-templates" + File.separator +
                    "xacml-policy-partial-template.xml";
            xacmlPolicy.load(xacmlTemplateFilePath);

            ServiceReferenceHolder.getInstance().setAppMgtXACMLPolicyTemplateReader(xacmlPolicy);

            if(log.isDebugEnabled()) {
                log.debug("App Manager implementation configuration service component is activated from file path : "+
                filePath);
            }

        } catch (AppManagementException e) {
            log.error("Error occurred while initializing App Manager configuration Service Component from file path : " +
                    filePath, e);
        }
    }

    protected void deactivate(ComponentContext componentContext) {
        if (log.isDebugEnabled()) {
            log.debug("Deactivating App Manager configuration service component");
        }
        amConfigService = null;
        ServiceReferenceHolder.getInstance().setAPIManagerConfigurationService(null);
    }

    protected void setConfigurationContextService(ConfigurationContextService contextService) {
        ServiceReferenceHolder.setContextService(contextService);
    }

    protected void unsetConfigurationContextService(ConfigurationContextService contextService) {
        ServiceReferenceHolder.setContextService(null);
    }
}
