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

public class AppUsageByUserName {
    private String userID;
    private long requestCount;

    public AppUsageByUserName(OMElement row) {
        userID = row.getFirstChildWithName(new QName(
                APIUsageStatisticsClientConstants.USER_ID)).getText();
        requestCount = (long) Double.parseDouble(row.getFirstChildWithName(new QName(
                APIUsageStatisticsClientConstants.REQUEST)).getText());
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }



}
