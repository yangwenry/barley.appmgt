/*
 * Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 */

package barley.appmgt.impl.utils;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.dto.UserRegistrationConfigDTO;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.core.context.PrivilegedBarleyContext;
import barley.registry.core.Registry;
import barley.registry.core.Resource;
import barley.registry.core.exceptions.RegistryException;
import barley.user.api.UserStoreException;
import barley.user.core.UserCoreConstants;
import barley.user.core.UserRealm;

/**
 * This class contains the utility methods used for self sign-up
 */
public final class SelfSignUpUtil {

    private static final Log log = LogFactory.getLog(SelfSignUpUtil.class);

    /**
     * retrieve self signup configuration from registry
     *
     * @param tenantId tenant id
     * @return UserRegistrationConfigDTO self signup configuration for the
     * tenant
     * @throws org.wso2.carbon.appmgt.api.AppManagementException
     */
    public static UserRegistrationConfigDTO getSignupConfiguration(int tenantId)
            throws AppManagementException {

        UserRegistrationConfigDTO config = null;

        int currentFlowTenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId();
        boolean isTenantFlowStarted = false;
        try {

            //start the correct tenant flow to load the tenant's registry
            if (tenantId != -1234 && currentFlowTenantId != tenantId) {
                // if the current flow is not the one related to the domain
                isTenantFlowStarted = true;
                PrivilegedBarleyContext.startTenantFlow();
                PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantId(tenantId, true);

            }
            config = getSignupConfigurationFromRegistry(tenantId);

        } finally {
            if (isTenantFlowStarted) {
            	PrivilegedBarleyContext.endTenantFlow();
            }
        }
        return config;
    }

    /**
     * load configuration from the registry
     *
     * @param tenantId tenant id
     * @return
     * @throws org.wso2.carbon.appmgt.api.AppManagementException
     */
    private static UserRegistrationConfigDTO getSignupConfigurationFromRegistry(int tenantId)
            throws AppManagementException {

        UserRegistrationConfigDTO config = null;
        try {

            Registry registry = ServiceReferenceHolder.getInstance().
                    getRegistryService().getGovernanceSystemRegistry(tenantId);
            if (registry.resourceExists(AppMConstants.SELF_SIGN_UP_CONFIG_LOCATION)) {

                Resource resource = registry.get(AppMConstants.SELF_SIGN_UP_CONFIG_LOCATION);
                // build config from registry resource
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

                String configXml = new String((byte[]) resource.getContent());
                InputSource configInputSource = new InputSource();
                configInputSource.setCharacterStream(new StringReader(configXml.trim()));
                Document doc = builder.parse(configInputSource);
                NodeList nodes = doc.getElementsByTagName(AppMConstants.SELF_SIGN_UP_REG_ROOT);
                if (nodes.getLength() > 0) {
                    config = new UserRegistrationConfigDTO();
                    config.setSignUpDomain(((Element) nodes.item(0)).getElementsByTagName(
                            AppMConstants.SELF_SIGN_UP_REG_DOMAIN_ELEM)
                            .item(0).getTextContent());
                    // set tenant admin credentials
                    config.setAdminUserName(((Element) nodes.item(0)).
                            getElementsByTagName(AppMConstants.SELF_SIGN_UP_REG_USERNAME).item(0).getTextContent());
                    config.setAdminPassword(((Element) nodes.item(0)).
                            getElementsByTagName(AppMConstants.SELF_SIGN_UP_REG_PASSWORD).item(0).getTextContent());

                    config.setSignUpEnabled(Boolean.parseBoolean(((Element) nodes.item(0)).
                            getElementsByTagName(AppMConstants.SELF_SIGN_UP_REG_ENABLED).item(0).getTextContent()));

                    // iterate through sign-up role list
                    Element roleListParent = (Element) ((Element) nodes.item(0)).
                            getElementsByTagName(AppMConstants.SELF_SIGN_UP_REG_ROLES_ELEM).item(0);

                    NodeList rolesEl = roleListParent.getElementsByTagName(AppMConstants.SELF_SIGN_UP_REG_ROLE_ELEM);
                    for (int i = 0; i < rolesEl.getLength(); i++) {
                        Element tmpEl = (Element) rolesEl.item(i);
                        String tmpRole =
                                tmpEl.getElementsByTagName(AppMConstants.SELF_SIGN_UP_REG_ROLE_NAME_ELEMENT)
                                        .item(0).getTextContent();
                        boolean tmpIsExternal = Boolean.parseBoolean(tmpEl.getElementsByTagName(
                                AppMConstants.SELF_SIGN_UP_REG_ROLE_IS_EXTERNAL).item(0).getTextContent());
                        String permissions = tmpEl.getElementsByTagName(AppMConstants.SELF_SIGN_UP_REG_ROLE_PERMISSIONS)
                                .item(0).getTextContent();
                        String[] permissionList = null;
                        if (permissions != null) {
                            permissionList = permissions.split(",");
                        }
                        SignUpRole signUpRole = new SignUpRole();
                        signUpRole.setRoleName(tmpRole);
                        signUpRole.setExternalRole(tmpIsExternal);
                        signUpRole.setPermissionsList(permissionList);
                        config.getSignUpRoles().add(signUpRole);
                    }
                }
            }
        } catch (RegistryException e) {
            throw new AppManagementException("Error while reading sign-up configuration file in registry location : " +
                    AppMConstants.SELF_SIGN_UP_CONFIG_LOCATION, e);
        } catch (ParserConfigurationException e) {
            throw new AppManagementException("Error while building sign-up configuration file in : " +
                    AppMConstants.SELF_SIGN_UP_CONFIG_LOCATION, e);
        } catch (SAXException e) {
            throw new AppManagementException("Error while parsing sign-up configuration in : " +
                    AppMConstants.SELF_SIGN_UP_CONFIG_LOCATION, e);
        } catch (IOException e) {
            throw new AppManagementException("Error while parsing sign-up configuration in : " +
                    AppMConstants.SELF_SIGN_UP_CONFIG_LOCATION, e);
        }
        return config;
    }

    /**
     * Check whether user can sign-up to the tenant domain
     *
     * @param userName
     * @param realm
     * @return
     * @throws org.wso2.carbon.appmgt.api.AppManagementException
     */
    public static boolean isUserNameWithAllowedDomainName(String userName, UserRealm realm)
            throws AppManagementException {
        int index;
        index = userName.indexOf('/');

        // Check whether we have a secondary UserStoreManager setup.
        if (index > 0) {
            // Using the short-circuit. User name comes with the domain name.
            try {
                return !realm.getRealmConfiguration()
                        .isRestrictedDomainForSlefSignUp(userName.substring(0, index));
            } catch (UserStoreException e) {
                throw new AppManagementException(e.getMessage(), e);
            }
        }
        return true;
    }

    /**
     * get the full role name list (ex: internal/subscriber)
     *
     * @param config
     * @return
     */
    public static List<String> getRoleNames(UserRegistrationConfigDTO config) {

        ArrayList<String> roleNamesArr = new ArrayList<String>();
        List<SignUpRole> signUpRoles = config.getSignUpRoles();
        for (SignUpRole signUpRole : signUpRoles) {
            String roleName;
            if (signUpRole.isExternalRole()) {
                // external role
                roleName =
                        config.getSignUpDomain().toUpperCase() +
                                UserCoreConstants.DOMAIN_SEPARATOR + signUpRole.getRoleName();
            } else {
                // internal role
                roleName =
                        UserCoreConstants.INTERNAL_DOMAIN + UserCoreConstants.DOMAIN_SEPARATOR +
                                signUpRole.getRoleName();
            }
            roleNamesArr.add(roleName);
        }
        return roleNamesArr;

    }

    /**
     * modify user name with user storeage information.
     *
     * @param username
     * @param signupConfig
     * @return
     */
    public static String getDomainSpecificUserName(String username, UserRegistrationConfigDTO signupConfig) {
        String modifiedUsername = null;

        // set tenant specific sign up user storage
        if (signupConfig != null && !("".equals(signupConfig.getSignUpDomain()))) {

            int index = username.indexOf(UserCoreConstants.DOMAIN_SEPARATOR);
            /*
             * if there is a different domain provided by the user other than one
			 * given in the configuration, add the correct signup domain. Here signup
			 * domain refers to the user storage
			 */

            if (index > 0) {
                modifiedUsername = signupConfig.getSignUpDomain().toUpperCase() + UserCoreConstants.DOMAIN_SEPARATOR +
                        username.substring(index + 1);
            } else {
                modifiedUsername = signupConfig.getSignUpDomain().toUpperCase() + UserCoreConstants.DOMAIN_SEPARATOR +
                        username;
            }
        }

        return modifiedUsername;
    }
}
