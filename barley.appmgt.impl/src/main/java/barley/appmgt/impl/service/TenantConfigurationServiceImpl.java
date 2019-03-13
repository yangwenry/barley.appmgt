/*
 *
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   you may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package barley.appmgt.impl.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.impl.config.TenantConfiguration;
import barley.core.context.BarleyContext;

/**
 * The registry based implementation of TenantConfigurationService.
 */
public class TenantConfigurationServiceImpl implements TenantConfigurationService{

    private static final Log log = LogFactory.getLog(TenantConfigurationServiceImpl.class);

    private Map<Integer, TenantConfiguration> tenantConfigurations;
    public TenantConfigurationServiceImpl(){
        tenantConfigurations = new HashMap<Integer, TenantConfiguration>();
    }

    public void addTenantConfiguration(TenantConfiguration tenantConfiguration){

        synchronized (this){
            if(tenantConfigurations.get(tenantConfiguration.getTenantID()) == null){
                tenantConfigurations.put(tenantConfiguration.getTenantID(), tenantConfiguration);
            }
        }
    }

    @Override
    public String getFirstProperty(String key) {
        int tenantID = BarleyContext.getThreadLocalCarbonContext().getTenantId();
        return getFirstProperty(key, tenantID);
    }

    @Override
    public String getFirstProperty(String key, int tenantID) {
        return tenantConfigurations.get(tenantID).getFirstProperty(key);
    }

    @Override
    public List<String> getProperties(String key) {
        int tenantID = BarleyContext.getThreadLocalCarbonContext().getTenantId();
        return tenantConfigurations.get(tenantID).getProperties(key);
    }

    @Override
    public List<String> getProperties(String key, int tenantID) {
        return tenantConfigurations.get(tenantID).getProperties(key);
    }
}
