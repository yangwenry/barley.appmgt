/*
*  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package barley.appmgt.api.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is used to store and process mobile application details
 */
public class MobileApp extends App{

    private String appName;
    private String appId;
    private String appUrl;
    private String version;
    private String appProvider;
    private String bundleVersion;
    private String category;
    private String packageName;
    private String platform;
    private String marketType;
    private String appType;
    private String description;
    private String thumbnail;
    private String[] availableLifecycleActions;
    private String recentChanges;
    private List<String> screenShots;
    private String createdTime;
    private String originVersion;
    private String previousVersionAppID;

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    private Set<String> tags = new LinkedHashSet<String>();

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public void addTags(Set<String> tags) {
        this.tags.addAll(tags);
    }

    public void removeTags(Set<String> tags) {
        this.tags.removeAll(tags);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }


    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppUrl() {
        return appUrl;
    }

    public void setAppUrl(String appUrl) {
        this.appUrl = appUrl;
    }

    public String getAppProvider() {
        return appProvider;
    }

    public void setAppProvider(String appProvider) {
        this.appProvider = appProvider;
    }

    public String getBundleVersion() {
        return bundleVersion;
    }

    public void setBundleVersion(String bundleVersion) {
        this.bundleVersion = bundleVersion;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getMarketType() {
        return marketType;
    }

    public void setMarketType(String marketType) {
        this.marketType = marketType;
    }

    public String getAppType() {
        return appType;
    }

    public void setAppType(String appType) {
        this.appType = appType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String[] getAvailableLifecycleActions() {
        return availableLifecycleActions;
    }

    public void setAvailableLifecycleActions(String[] availableLifecycleActions) {
        this.availableLifecycleActions = availableLifecycleActions;
    }

    public String getRecentChanges() {
        return recentChanges;
    }

    public void setRecentChanges(String recentChanges) {
        this.recentChanges = recentChanges;
    }

    public List<String> getScreenShots() {
        return screenShots;
    }

    public void setScreenShots(List<String> screenShots) {
        this.screenShots = screenShots;
    }

    public String getOriginVersion() {
        return originVersion;
    }

    public void setOriginVersion(String originVersion) {
        this.originVersion = originVersion;
    }

	public String getPreviousVersionAppID() {
		return previousVersionAppID;
	}

	public void setPreviousVersionAppID(String previousVersionAppID) {
		this.previousVersionAppID = previousVersionAppID;
	}
}
