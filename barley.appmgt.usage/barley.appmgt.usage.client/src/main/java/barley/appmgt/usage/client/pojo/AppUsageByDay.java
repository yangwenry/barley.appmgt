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
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*
*/

package barley.appmgt.usage.client.pojo;

import barley.appmgt.usage.client.APIUsageStatisticsClientConstants;
import org.apache.axiom.om.OMElement;

import javax.xml.namespace.QName;

public class AppUsageByDay {
    private int year;
    private int month;
    private int day;
    private String apiName;
    private String apiVersion;
    private String context;
    private String apiPublisher;
    private long requestCount;

    public AppUsageByDay(OMElement row) {
        year = Integer.parseInt(row.getFirstChildWithName(new QName(
                APIUsageStatisticsClientConstants.YEAR)).getText());
        month = Integer.parseInt(row.getFirstChildWithName(new QName(
                APIUsageStatisticsClientConstants.MONTH)).getText());
        day = Integer.parseInt(row.getFirstChildWithName(new QName(
                APIUsageStatisticsClientConstants.DAY)).getText());
        apiName = row.getFirstChildWithName(new QName(
                APIUsageStatisticsClientConstants.API)).getText();
        apiVersion = row.getFirstChildWithName(new QName(
                APIUsageStatisticsClientConstants.VERSION)).getText();
        context = row.getFirstChildWithName(new QName(
                APIUsageStatisticsClientConstants.CONTEXT)).getText();
        apiPublisher = row.getFirstChildWithName(new QName(
                APIUsageStatisticsClientConstants.API_PUBLISHER)).getText();
        requestCount = (long) Double.parseDouble(row.getFirstChildWithName(new QName(
                APIUsageStatisticsClientConstants.REQUEST)).getText());
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getApiPublisher() {
        return apiPublisher;
    }

    public void setApiPublisher(String apiPublisher) {
        this.apiPublisher = apiPublisher;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }
}
