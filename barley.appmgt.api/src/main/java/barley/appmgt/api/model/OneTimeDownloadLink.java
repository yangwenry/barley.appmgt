/*
 *
 *  * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *  *
 *  * WSO2 Inc. licenses this file to you under the Apache License,
 *  * Version 2.0 (the "License"); you may not use this file except
 *  * in compliance with the License.
 *  * you may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  * KIND, either express or implied.  See the License for the
 *  * specific language governing permissions and limitations
 *  * under the License.
 *
 */

package barley.appmgt.api.model;

/**
 * This class is dedicated to store one-time downloadable link details
 */
public class OneTimeDownloadLink {
    private String UUID;
    private String fileName;
    private boolean isDownloaded;
    private String createdUserName;
    private int createdTenantID;
    private String createdTenantDomain;
    private long createdTime;

    public String getCreatedUserName() {
        return createdUserName;
    }

    public void setCreatedUserName(String createdUserName) {
        this.createdUserName = createdUserName;
    }

    public int getCreatedTenantID() {
        return createdTenantID;
    }

    public void setCreatedTenantID(int createdTenantID) {
        this.createdTenantID = createdTenantID;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public String getUUID() {

        return UUID;
    }

    public void setUUID(String UUID) {
        this.UUID = UUID;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isDownloaded() {
        return isDownloaded;
    }

    public void setDownloaded(boolean isDownloadable) {
        this.isDownloaded = isDownloadable;
    }    public String getCreatedTenantDomain() {
        return createdTenantDomain;
    }

    public void setCreatedTenantDomain(String createdTenantDomain) {
        this.createdTenantDomain = createdTenantDomain;
    }


}
