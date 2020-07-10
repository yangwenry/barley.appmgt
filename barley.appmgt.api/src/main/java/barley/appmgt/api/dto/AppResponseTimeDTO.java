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

public class AppResponseTimeDTO {

    private String appName;
    private double serviceTime;
    private String referer;
    private String context;
    private String version;

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public double getServiceTime() {
        return serviceTime;
    }

    public void setServiceTime(double serviceTime) {
        this.serviceTime = serviceTime;
    }

    public void setReferer(String referer){
         this.referer =referer;
    }

    public String getReferer(){
        return referer;
    }

    public void setContext(String context){
        this.context =context;
    }

    public String getContext(){
        return context;
    }

    public void setVersion(String version){
        this.version =version;
    }

    public String getVersion(){
        return version;
    }

    @Override
    public String toString() {
        return "AppResponseTimeDTO{" +
                "appName='" + appName + '\'' +
                ", serviceTime=" + serviceTime +
                ", referer='" + referer + '\'' +
                ", context='" + context + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
