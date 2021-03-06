/*
*  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package barley.appmgt.core.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
//import org.osgi.service.component.ComponentContext;

import barley.appmgt.impl.AppManagerConfiguration;
import barley.appmgt.impl.AppManagerConfigurationService;


/**
 * @scr.component name="org.wso2.appmgt.core.services" immediate="true"
 * @scr.reference name="api.manager.config.service"
 * interface="org.wso2.carbon.appmgt.impl.AppManagerConfigurationService" cardinality="1..1"
 * policy="dynamic" bind="setAPIManagerConfigurationService" unbind="unsetAPIManagerConfigurationService"
 */
public class APPManagerCoreComponent {
    //TODO refactor caching implementation

    private static final Log log = LogFactory.getLog(APPManagerCoreComponent.class);

    private static AppManagerConfiguration configuration = null;

    protected void activate() {
        if (log.isDebugEnabled()){
            log.debug("HostObjectComponent activated");
        }
    }

    protected void deactivate() {
        if (log.isDebugEnabled()){
            log.debug("HostObjectComponent deactivated");
        }
    }

    protected void setAPIManagerConfigurationService(AppManagerConfigurationService amcService) {
        if (log.isDebugEnabled()) {
            log.debug("WebApp manager configuration service bound to the WebApp host objects");
        }
        configuration = amcService.getAPIManagerConfiguration();
        ServiceReferenceHolder.getInstance().setAPIManagerConfigurationService(amcService);
    }

    protected void unsetAPIManagerConfigurationService(AppManagerConfigurationService amcService) {
        if (log.isDebugEnabled()) {
            log.debug("WebApp manager configuration service unbound from the WebApp host objects");
        }
        configuration = null;
        ServiceReferenceHolder.getInstance().setAPIManagerConfigurationService(null);
    }
}
