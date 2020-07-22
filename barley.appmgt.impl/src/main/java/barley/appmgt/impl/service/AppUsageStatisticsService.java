/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package barley.appmgt.impl.service;

import barley.appmgt.api.AppUsageStatisticsClient;
import barley.appmgt.api.dto.*;
import barley.appmgt.api.exception.AppUsageQueryServiceClientException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

public class AppUsageStatisticsService {

    private AppUsageStatisticsClient appUsageStatisticsClient;
    private static final Log log = LogFactory.getLog(AppUsageStatisticsService.class);
    private String userName;

    public AppUsageStatisticsService(String userName) throws AppUsageQueryServiceClientException {
        appUsageStatisticsClient = ServiceReferenceHolder.getInstance().getAppUsageStatClient();
        if (appUsageStatisticsClient == null) {
            throw new AppUsageQueryServiceClientException("Cant find appUsageStatisticsClient.");
        }
        this.userName = userName;
    }

    public List<AppResponseTimeDTO> getResponseTimesByApps(String providerName, String appName, String fromDate, String toDate,
                                                           int limit, String tenantDomain)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getResponseTimesByApps(providerName, appName, fromDate, toDate, limit, tenantDomain);
        }
    }

    public List<AppVersionUsageDTO> getUsageByAppVersions(String providerName,
                                                          String apiName)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getUsageByAppVersions(providerName, apiName);
        }
    }

    public List<AppUsageDTO> getUsageByApps(String providerName, String fromDate, String toDate,
                                            int limit, String tenantDomainName)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getUsageByApps(providerName, fromDate, toDate, limit, tenantDomainName);
        }
    }

    public List<PerUserAPIUsageDTO> getUsageBySubscribers(String providerName, String apiName, int limit)
            throws AppUsageQueryServiceClientException {
        return appUsageStatisticsClient.getUsageBySubscribers(providerName, apiName, limit);
    }

    public List<AppResourcePathUsageDTO> getAppUsageByResourcePath(String providerName, String fromDate, String toDate)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getAppUsageByResourcePath(providerName, fromDate, toDate);
        }
    }

    public List<AppPageUsageDTO> getAppUsageByPage(String providerName, String fromDate, String toDate
            , String tenantDomainName)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getAppUsageByPage(providerName, fromDate, toDate, tenantDomainName);
        }
    }

    public List<AppUsageByUserDTO> getAppUsageByUser(String providerName, String fromDate, String toDate,
                                                     String tenantDomainName)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getAppUsageByUser(providerName, fromDate, toDate, tenantDomainName);
        }
    }

    public List<PerUserAPIUsageDTO> getUsageBySubscribers(String providerName, String apiName,
                                                          String apiVersion, int limit)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getUsageBySubscribers(providerName, apiName, apiVersion, limit);
        }
    }

    public List<AppVersionLastAccessTimeDTO> getLastAccessTimesByApps(String providerName, String fromDate,
                                                                      String toDate, int limit, String tenantDomainName)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getLastAccessTimesByApps(providerName, fromDate, toDate, limit,
                                                                     tenantDomainName);
        }
    }

    public List<AppResponseFaultCountDTO> getAppFaultyAnalyzeByTime(String providerName)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getAppFaultyAnalyzeByTime(providerName);
        }
    }

    public List<String> getFirstAccessTime(String providerName, int limit)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getFirstAccessTime(providerName, limit);
        }
    }

    public List<AppResponseFaultCountDTO> getAppResponseFaultCount(String providerName, String appName, String fromDate, String toDate)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getAppResponseFaultCount(providerName, appName, fromDate, toDate);
        }
    }

    public List<AppHitsStatsDTO> getAppHitsOverTime(String fromDate, String toDate, int tenantId)
            throws AppUsageQueryServiceClientException {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getAppHitsOverTime(fromDate, toDate, tenantId);
        }
    }

    public List<AppVersionUserUsageDTO> getUsageBySubscriber(String subscriberName, String period)
            throws Exception {
        synchronized (userName) {
            appUsageStatisticsClient.initialize(userName);
            return appUsageStatisticsClient.getUsageBySubscriber(subscriberName, period);
        }
    }
}
