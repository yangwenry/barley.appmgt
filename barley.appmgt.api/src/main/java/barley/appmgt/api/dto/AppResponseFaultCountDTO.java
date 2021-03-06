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

package barley.appmgt.api.dto;

public class AppResponseFaultCountDTO {

    private String appName;
    private String version;
    private String context;
    private double faultPercentage;
	private String requestTime;
    private long count;
    private String referer;

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(String requestTime) {
        this.requestTime = requestTime;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public double getFaultPercentage() {
        return faultPercentage;
    }

    public void setFaultPercentage(double faultPercentage) {
        this.faultPercentage = faultPercentage;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    @Override
    public String toString() {
        return "AppResponseFaultCountDTO{" +
                "appName='" + appName + '\'' +
                ", version='" + version + '\'' +
                ", context='" + context + '\'' +
                ", faultPercentage=" + faultPercentage +
                ", requestTime='" + requestTime + '\'' +
                ", count=" + count +
                ", referer='" + referer + '\'' +
                '}';
    }

}
