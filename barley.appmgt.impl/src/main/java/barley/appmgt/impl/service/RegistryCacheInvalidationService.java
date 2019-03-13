/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package barley.appmgt.impl.service;

import javax.cache.Cache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.api.AppManagementException;
import barley.core.context.BarleyContext;
import barley.core.context.PrivilegedBarleyContext;
import barley.registry.api.GhostResource;
import barley.registry.core.Registry;
import barley.registry.core.RegistryConstants;
import barley.registry.core.caching.RegistryCacheKey;
import barley.registry.core.config.DataBaseConfiguration;
import barley.registry.core.config.Mount;
import barley.registry.core.config.RemoteConfiguration;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.jdbc.handlers.RequestContext;
import barley.registry.core.utils.RegistryUtils;

public class RegistryCacheInvalidationService {

    private static final Log log = LogFactory.getLog(RegistryCacheInvalidationService.class);

    /**
     * This method invalidates registry cache for given resource path
     *
     * @throws org.wso2.carbon.appmgt.api.AppManagementException
     * @reture boolean
     */
    public boolean invalidateRegistryCache(RequestContext requestContext) throws AppManagementException {

        boolean isTenantFlowStarted = false;
        int tenantId = -1234;
        String tenantDomain = "";
        String resourcePath = "";

        try {
            tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId();
            tenantDomain = BarleyContext.getThreadLocalCarbonContext().getTenantDomain();
            PrivilegedBarleyContext.startTenantFlow();
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            isTenantFlowStarted = true;

            Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().
                    getGovernanceSystemRegistry(tenantId);
            resourcePath = requestContext.getResource().getPath();

            Cache<RegistryCacheKey, GhostResource> cache =
                    RegistryUtils.getResourceCache(RegistryConstants.REGISTRY_CACHE_BACKED_ID);

            //Is registry mounted
            if (registry.getRegistryContext().getRemoteInstances().size() > 0) {
                for (Mount mount : registry.getRegistryContext().getMounts()) {
                    for (RemoteConfiguration configuration : registry.getRegistryContext().getRemoteInstances()) {
                        if (resourcePath.startsWith(mount.getPath())) {
                            DataBaseConfiguration dataBaseConfiguration =
                                    registry.getRegistryContext().getDBConfig(configuration.getDbConfig());
                            removeCacheFromCacheRegistry(cache, dataBaseConfiguration, resourcePath, tenantId);
                        }
                    }
                }
            } else {
                DataBaseConfiguration dataBaseConfiguration = registry.getRegistryContext().
                        getDefaultDataBaseConfiguration();
                removeCacheFromCacheRegistry(cache, dataBaseConfiguration, resourcePath, tenantId);
            }
        } catch (RegistryException e) {
            handleException("Error in accessing governance registry while invalidating cache for "
                    + resourcePath + "in tenant " + tenantDomain, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedBarleyContext.endTenantFlow();
            }
        }
        return true;
    }

    private void removeCacheFromCacheRegistry(Cache<RegistryCacheKey, GhostResource> cache,
                                              DataBaseConfiguration dbConfig,
                                              String resourcePath, int tenantId) {

        RegistryCacheKey cacheKey = constructCacheKey(dbConfig, resourcePath, tenantId);
        if (cacheKey != null && cache.containsKey(cacheKey)) {
            cache.remove(cacheKey);
        }
    }

    private RegistryCacheKey constructCacheKey(DataBaseConfiguration dbConfig, String resourcePath, int tenantId) {
        String connectionId = (dbConfig.getUserName() != null
                ? dbConfig.getUserName().split("@")[0] : dbConfig.getUserName())
                + "@" + dbConfig.getDbUrl();
        return RegistryUtils.buildRegistryCacheKey(connectionId, tenantId, resourcePath);
    }

    private void handleException(String msg, Exception e) throws AppManagementException {
        log.error(msg, e);
        throw new AppManagementException(msg, e);
    }
}