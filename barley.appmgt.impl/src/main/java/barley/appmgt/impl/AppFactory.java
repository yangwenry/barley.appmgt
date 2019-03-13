/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package barley.appmgt.impl;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
//import org.wso2.carbon.social.core.SocialActivityException;
//import org.wso2.carbon.social.core.service.SocialActivityService;

import com.google.gson.JsonObject;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.App;
import barley.core.context.PrivilegedBarleyContext;
import barley.governance.api.generic.dataobjects.GenericArtifact;
import barley.registry.core.Registry;
import barley.registry.core.exceptions.RegistryException;

/**
 * Parent class for the factories for app types.
 */
public abstract class AppFactory {

    private static final Log log = LogFactory.getLog(AppFactory.class);

    public App createApp(GenericArtifact artifact, Registry registry) throws AppManagementException{

        App app = doCreateApp(artifact, registry);
        setRating(app);
        setCustomProperties(app, artifact, registry);

        return app;
    }

    /**
     *
     * Creates an App from the given registry artifact.
     *
     * @param artifact
     * @param registry
     * @return
     * @throws AppManagementException
     */
    protected abstract App doCreateApp(GenericArtifact artifact, Registry registry) throws AppManagementException;

    private void setRating(App app) throws AppManagementException {
    	/* (임수주석)
        try {
        	PrivilegedBarleyContext carbonContext = PrivilegedBarleyContext.getThreadLocalCarbonContext();
            SocialActivityService socialActivityService = (SocialActivityService) carbonContext.getOSGiService(org.wso2.carbon.social.core.service.SocialActivityService.class, null);
            JsonObject rating = socialActivityService.getRating(app.getType() + ":" + app.getUUID());

            if(rating != null && rating.get("rating") != null){
                app.setRating(rating.get("rating").getAsFloat());
            }

        } catch (SocialActivityException e) {
            String errorMessage = String.format("Can't get the rating for the app '%s:%s'", app.getType(), app.getUUID());
            log.error(errorMessage, e);
            throw new AppManagementException(errorMessage, e);
        }
		*/
    }

    private void setCustomProperties(App app, GenericArtifact artifact, Registry registry) throws AppManagementException {

        String customPropertyDefinitionsResourcePath = getCustomPropertyDefinitionsResourcePath(app.getType());

        try {

            if(registry.resourceExists(customPropertyDefinitionsResourcePath)){

                String customPropertyDefinitions = IOUtils.toString(registry.get(customPropertyDefinitionsResourcePath).getContentStream());

                JSONParser parser = new JSONParser();
                JSONObject definitionsJson = (JSONObject) parser.parse(customPropertyDefinitions);

                JSONArray definitionsListJson = null;
                if((definitionsListJson = (JSONArray) definitionsJson.get("customPropertyDefinitions")) != null){
                    for(Object definition : definitionsListJson){
                        JSONObject definitionJson = (JSONObject) definition;

                        String propertyName = (String) definitionJson.get("name");
                        String propertyValue = artifact.getAttribute(propertyName);

                        app.addCustomProperty(propertyName, propertyValue);
                    }
                }


            }
        } catch (IOException e) {
            String errorMessage = String.format("Can't read 'custom property definitions' registry resource from the path '%s'.", customPropertyDefinitionsResourcePath);
            log.error(errorMessage, e);
            throw new AppManagementException(e);
        } catch (RegistryException e) {
            String errorMessage = String.format("Can't read 'custom property definitions' registry resource from the path '%s'.", customPropertyDefinitionsResourcePath);
            log.error(errorMessage, e);
            throw new AppManagementException(e);
        } catch (ParseException e) {
            String errorMessage = String.format("Can't parse 'custom property definitions' registry resource in the path '%s'.", customPropertyDefinitionsResourcePath);
            log.error(errorMessage, e);
            throw new AppManagementException(e);
        }
    }

    private String getCustomPropertyDefinitionsResourcePath(String appType) {
        return String.format("%s/%s/%s.json", AppMConstants.APPMGT_APPLICATION_DATA_LOCATION, AppMConstants.CUSTOM_PROPERTY_DEFINITIONS_PATH, appType);
    }

}
