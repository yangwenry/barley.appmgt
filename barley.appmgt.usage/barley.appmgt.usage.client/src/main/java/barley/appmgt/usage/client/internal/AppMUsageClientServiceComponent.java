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

package barley.appmgt.usage.client.internal;

import barley.appmgt.api.exception.AppUsageQueryServiceClientException;
import barley.appmgt.impl.AppManagerConfiguration;
import barley.appmgt.impl.AppManagerConfigurationService;
import barley.appmgt.usage.client.impl.AppUsageStatisticsRdbmsClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @scr.component name="org.wso2.appmgt.usage.client" immediate="true"
 * @scr.reference name="api.manager.config.service"
 * interface="org.wso2.carbon.appmgt.impl.AppManagerConfigurationService" cardinality="1..1"
 * policy="dynamic" bind="setAPIManagerConfigurationService" unbind="unsetAPIManagerConfigurationService"
 */
public class AppMUsageClientServiceComponent {

    private static final Log log = LogFactory.getLog(AppMUsageClientServiceComponent.class);

    private static AppManagerConfiguration configuration = null;

    public void activate()
            throws AppUsageQueryServiceClientException {
        if (log.isDebugEnabled()) {
            log.debug("barley.appmgt.usage.client component has been activating");
        }

        // (추가) - 데이터베이스 초기화
        AppUsageStatisticsRdbmsClient.initializeDataSource();

        // (임시주석)
        /*
        BundleContext bundleContext = componentContext.getBundleContext();
        //Register the default App usage stat client as a OSGi service.
        bundleContext.registerService(AppUsageStatisticsClient.class.getName(), new AppUsageStatisticsRdbmsClient(),
                                      null);*/
    }

    public void deactivate() {
        log.debug("App usage client component deactivated");
    }

    public void setAPIManagerConfigurationService(AppManagerConfigurationService amcService) {
        log.debug("App manager configuration service bound to the WebApp usage client component");
        configuration = amcService.getAPIManagerConfiguration();
    }

    public void unsetAPIManagerConfigurationService(AppManagerConfigurationService amcService) {
        log.debug("App manager configuration service unbound from the WebApp usage client component");
        configuration = null;
    }

    public static AppManagerConfiguration getAPIManagerConfiguration() {
        return configuration;
    }


}
