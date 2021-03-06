/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package barley.appmgt.impl.dao;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.EntitlementService;
import barley.appmgt.api.SubscriptionAlreadyExistingException;
import barley.appmgt.api.dto.UserApplicationAPIUsage;
import barley.appmgt.api.model.*;
import barley.appmgt.api.model.entitlement.EntitlementPolicyPartial;
import barley.appmgt.api.model.entitlement.XACMLPolicyTemplateContext;
import barley.appmgt.impl.APIGatewayManager;
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.AppManagerConfiguration;
import barley.appmgt.impl.dto.*;
import barley.appmgt.impl.entitlement.EntitlementServiceFactory;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.*;
import barley.appmgt.impl.workflow.WorkflowStatus;
import barley.core.BarleyConstants;
import barley.core.MultitenantConstants;
import barley.core.context.PrivilegedBarleyContext;
import barley.core.multitenancy.MultitenantUtils;
import barley.core.utils.CryptoException;
import barley.core.utils.DBUtils;
import barley.governance.api.generic.GenericArtifactManager;
import barley.governance.api.generic.dataobjects.GenericArtifact;
import barley.governance.api.util.GovernanceUtils;
import barley.identity.base.IdentityException;
import barley.identity.base.IdentityRuntimeException;
import barley.identity.core.util.IdentityDatabaseUtil;
import barley.identity.core.util.IdentityTenantUtil;
import barley.identity.oauth.IdentityOAuthAdminException;
import barley.identity.oauth.OAuthUtil;
import barley.identity.oauth.common.OAuthConstants;
import barley.identity.oauth.config.OAuthServerConfiguration;
import barley.registry.core.Registry;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.session.UserRegistry;
import barley.user.api.UserRealmService;
import barley.user.api.UserStoreException;
import barley.user.core.util.UserCoreUtil;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data Access Layer for App Management
 */
public class AppMDAO {

    private static final Log log = LogFactory.getLog(AppMDAO.class);

    private static final String ENABLE_JWT_GENERATION =
            "AppConsumerAuthConfiguration.EnableTokenGeneration";
    private static final String ENABLE_JWT_CACHE = "APIKeyManager.EnableJWTCache";

    private static final String GATEWAY_URL = "APIGateway.Environments.Environment.GatewayEndpoint";

    // Primary/Secondary Login configuration
    private static final String USERID_LOGIN = "UserIdLogin";
    private static final String EMAIL_LOGIN = "EmailLogin";
    private static final String PRIMARY_LOGIN = "primary";
    private static final String CLAIM_URI = "ClaimUri";

    private static final String oracleDriverName = "Oracle";
    private static final String mySQLDriverName = "MySQL";
    private static final String msSQLDriverName = "MS SQL";
    private static final String microsoftDriverName = "Microsoft";
    private static final String postgreDriverName = "PostgreSQL";

    public AppMDAO() {
    }

    /**
     * This methode is to return a List of existing business owners with out their custom properties.
     * @param businessOwnerId
     * @return
     * @throws AppManagementException
     */
    public BusinessOwner getBusinessOwner(int businessOwnerId, int tenantId) throws AppManagementException {

        Connection connection = null;
        PreparedStatement statementToGetBusinessOwners = null;
        BusinessOwner businessOwner = null;
        ResultSet businessOwnerResultSet = null;

        String queryToGetBusinessOwner = "SELECT OWNER_NAME, OWNER_EMAIL, OWNER_DESC, OWNER_SITE FROM " +
                "APM_BUSINESS_OWNER WHERE OWNER_ID = ? AND TENANT_ID = ?";

        try {
            connection = APIMgtDBUtil.getConnection();
            statementToGetBusinessOwners = connection.prepareStatement(queryToGetBusinessOwner);
            statementToGetBusinessOwners.setInt(1, businessOwnerId);
            statementToGetBusinessOwners.setInt(2, tenantId);
            businessOwnerResultSet = statementToGetBusinessOwners.executeQuery();

            if (businessOwnerResultSet.next()) {
                businessOwner = new BusinessOwner();
                businessOwner.setBusinessOwnerId(businessOwnerId);
                businessOwner.setBusinessOwnerName(businessOwnerResultSet.getString("OWNER_NAME"));
                businessOwner.setBusinessOwnerDescription(businessOwnerResultSet.getString("OWNER_DESC"));
                businessOwner.setBusinessOwnerEmail(businessOwnerResultSet.getString("OWNER_EMAIL"));
                businessOwner.setBusinessOwnerSite(businessOwnerResultSet.getString("OWNER_SITE"));
                businessOwner.setBusinessOwnerPropertiesList(getBusinessOwnerCustomPropertiesById(businessOwnerId,
                                                                                                  connection));
            }
        } catch (SQLException e) {
            handleException("Failed to retrieve business owners.", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToGetBusinessOwners, connection, businessOwnerResultSet);

        }
        return businessOwner;
    }

    /**
     * Delete a given business owner.
     *
     * @param businessOwnerId
     */
    public void deleteBusinessOwner(String businessOwnerId) throws AppManagementException {

        Connection connection = null;
        PreparedStatement statementToDeleteRecord = null;
        PreparedStatement statementToDeleteRecordTwo = null;
        String deletedOwnerId = "";
        try {
            if (log.isDebugEnabled()) {
                log.debug("Deleting a Business Owner :" + businessOwnerId);
            }
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);

            String queryToDeleteRecordTwo = "DELETE FROM APM_BUSINESS_OWNER_PROPERTY WHERE OWNER_ID = ?";

            statementToDeleteRecordTwo = connection.prepareStatement(queryToDeleteRecordTwo);
            statementToDeleteRecordTwo.setString(1, businessOwnerId);
            statementToDeleteRecordTwo.executeUpdate();

            String queryToDeleteRecord = "DELETE FROM APM_BUSINESS_OWNER WHERE OWNER_ID = ?";

            statementToDeleteRecord = connection.prepareStatement(queryToDeleteRecord);
            statementToDeleteRecord.setString(1, businessOwnerId);
            statementToDeleteRecord.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                   handleException("Failed to delete business owner " + businessOwnerId, e1);
                }
            }
            handleException("Cannot delete business owner",  e );
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToDeleteRecord, connection, null);
        }
    }

    /**
     * Check the business owner usage of web app from the registry.
     * @param businessOwnerId
     * @param registry
     * @param tenantDomain
     * @return
     * @throws AppManagementException
     */
    public boolean isBusinessOwnerAssociatedWithApps(String businessOwnerId, Registry registry, String tenantDomain)
            throws AppManagementException {
        boolean isTenantFlowStarted = false;
        try {
            GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);
            if (tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                isTenantFlowStarted = true;
                PrivilegedBarleyContext.startTenantFlow();
                PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            }
            GenericArtifactManager artifactManager = new GenericArtifactManager(registry,
                                                                                AppMConstants.API_KEY);
            GenericArtifact[] artifacts = artifactManager.getAllGenericArtifacts();
            for (GenericArtifact artifact : artifacts) {
                String artifactContext = artifact.getAttribute(AppMConstants.API_OVERVIEW_BUSS_OWNER);
                if (artifactContext != null) {
                    if (artifactContext.equalsIgnoreCase(businessOwnerId)) {
                        return true;
                    }
                }
            }
        } catch (RegistryException e) {
            handleException("Failed to check business owner availability : " + businessOwnerId, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedBarleyContext.endTenantFlow();
            }
        }
        return false;
    }
    /**
     * Update business owner.
     * @param businessOwner
     * @throws AppManagementException
     */
    public void updateBusinessOwner(BusinessOwner businessOwner) throws AppManagementException {

        Connection connection = null;
        PreparedStatement statementToInsertRecord = null;
        PreparedStatement statementToInsertRecordTwo = null;
        PreparedStatement statementToDelete = null;

        try {

            if (log.isDebugEnabled()) {
                log.debug("Updating a Business Owner" + businessOwner.getBusinessOwnerId());
            }
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            String queryToInsertRecord = "UPDATE APM_BUSINESS_OWNER SET OWNER_NAME=?,OWNER_EMAIL=?,OWNER_DESC=?," +
                    "OWNER_SITE=? WHERE OWNER_ID=?";

            statementToInsertRecord = connection.prepareStatement(queryToInsertRecord);
            statementToInsertRecord.setString(1, businessOwner.getBusinessOwnerName());
            statementToInsertRecord.setString(2, businessOwner.getBusinessOwnerEmail());
            statementToInsertRecord.setString(3, businessOwner.getBusinessOwnerDescription());
            statementToInsertRecord.setString(4, businessOwner.getBusinessOwnerSite());
            statementToInsertRecord.setInt(5, businessOwner.getBusinessOwnerId());

            statementToInsertRecord.executeUpdate();
            String queryToDelete = "DELETE FROM APM_BUSINESS_OWNER_PROPERTY WHERE OWNER_ID = ?";

            statementToDelete = connection.prepareStatement(queryToDelete);
            statementToDelete.setInt(1, businessOwner.getBusinessOwnerId());
            statementToDelete.executeUpdate();
            String queryToInsertRecordTwo = "INSERT INTO APM_BUSINESS_OWNER_PROPERTY(OWNER_ID, NAME, VALUE, " +
                    "SHOW_IN_STORE) VALUES(?,?,?, ?)";

            statementToInsertRecordTwo = connection.prepareStatement(queryToInsertRecordTwo);
            List<BusinessOwnerProperty> businessOwnerPropertiesList = businessOwner.getBusinessOwnerPropertiesList();
            if (businessOwnerPropertiesList != null) {
                for (int i = 0 ; i < businessOwnerPropertiesList.size(); i++) {
                    BusinessOwnerProperty businessOwnerProperties = businessOwnerPropertiesList.get(i);
                    String propertyId = businessOwnerProperties.getPropertyId();
                    if(!StringUtils.isEmpty(propertyId)) {
                        statementToInsertRecordTwo.setInt(1, businessOwner.getBusinessOwnerId());
                        statementToInsertRecordTwo.setString(2, propertyId);
                        statementToInsertRecordTwo.setString(3, businessOwnerProperties.getPropertyValue());
                        statementToInsertRecordTwo.setBoolean(4, businessOwnerProperties.isShowingInStore());
                        statementToInsertRecordTwo.executeUpdate();
                    }
                }
            }
            connection.commit();

        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to Update owner : ", e1);
                }
            }
            handleException("Could not update business owner", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToInsertRecord, connection, null);
        }
    }

    /**
     * Get custom properties of a given business owner.
     *
     * @param businessOwnerId
     * @param connection
     * @return
     * @throws AppManagementException
     */
    private List<BusinessOwnerProperty> getBusinessOwnerCustomPropertiesById(int businessOwnerId, Connection connection)
            throws AppManagementException, SQLException {
        PreparedStatement statementToGetBusinessOwnersDetails = null;
        List<BusinessOwnerProperty> businessOwnerPropertiesList = new ArrayList<BusinessOwnerProperty>();
        ResultSet resultSetOfbusinessOwnerDetails = null;
        String queryToGetKeyValue = "SELECT NAME, VALUE, SHOW_IN_STORE FROM APM_BUSINESS_OWNER_PROPERTY WHERE" +
                " OWNER_ID = ?";
        try {
            statementToGetBusinessOwnersDetails = connection.prepareStatement(queryToGetKeyValue);
            statementToGetBusinessOwnersDetails.setInt(1, businessOwnerId);
            resultSetOfbusinessOwnerDetails = statementToGetBusinessOwnersDetails.executeQuery();
            while (resultSetOfbusinessOwnerDetails.next()) {
                BusinessOwnerProperty businessOwnerProperty = new BusinessOwnerProperty();
                businessOwnerProperty.setPropertyId(resultSetOfbusinessOwnerDetails.getString("NAME"));
                businessOwnerProperty.setPropertyValue(resultSetOfbusinessOwnerDetails.getString("VALUE"));
                businessOwnerProperty.setShowingInStore(resultSetOfbusinessOwnerDetails.getBoolean("SHOW_IN_STORE"));
                businessOwnerPropertiesList.add(businessOwnerProperty);
            }
        } catch (SQLException e) {
            /* In the code it is using a single SQL connection passed from the parent function so the error is logged
             here and throwing the SQLException so the connection will be disposed by the parent function. */
            log.error("Error when getting the additional properties of Business Owner: " +
                              businessOwnerId, e);
            throw e;
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToGetBusinessOwnersDetails, null,
                                             resultSetOfbusinessOwnerDetails);
        }
        return businessOwnerPropertiesList;
    }


    /**
     * This methode is to return a List of existing business owners with their properties.
     *
     * @return
     * @throws AppManagementException
     */
    public List<BusinessOwner> getBusinessOwners(int tenantId) throws AppManagementException {

        Connection connection = null;
        PreparedStatement statementToGetBusinessOwners = null;
        List<BusinessOwner> businessOwnersList = new ArrayList<BusinessOwner>();
        ResultSet businessOwnerResultSet = null;

        String queryToGetBusinessOwner = "SELECT * FROM APM_BUSINESS_OWNER WHERE TENANT_ID = ?";

        try {
            connection = APIMgtDBUtil.getConnection();
            statementToGetBusinessOwners = connection.prepareStatement(queryToGetBusinessOwner);
            statementToGetBusinessOwners.setInt(1, tenantId);
            businessOwnerResultSet = statementToGetBusinessOwners.executeQuery();

            while (businessOwnerResultSet.next()) {
                BusinessOwner businessOwner = new BusinessOwner();
                int businessOwnerId = businessOwnerResultSet.getInt("OWNER_ID");

                businessOwner.setBusinessOwnerId(businessOwnerId);
                businessOwner.setBusinessOwnerName(businessOwnerResultSet.getString("OWNER_NAME"));
                businessOwner.setBusinessOwnerDescription(businessOwnerResultSet.getString("OWNER_DESC"));
                businessOwner.setBusinessOwnerEmail(businessOwnerResultSet.getString("OWNER_EMAIL"));
                businessOwner.setBusinessOwnerSite(businessOwnerResultSet.getString("OWNER_SITE"));
                businessOwner.setBusinessOwnerPropertiesList(getBusinessOwnerCustomPropertiesById(businessOwnerId,
                                                                                                  connection));
                businessOwnersList.add(businessOwner);
            }
        } catch (SQLException e) {
            handleException("Failed to retrieve business owners.", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToGetBusinessOwners, connection, businessOwnerResultSet);
        }
        return businessOwnersList;
    }

    /**
     * Save a business owner.
     * @param businessOwner
     */
    public int saveBusinessOwner(BusinessOwner businessOwner, int tenantId) throws AppManagementException {

        Connection connection = null;
        PreparedStatement statementToInserBusinessOwner = null;
        PreparedStatement statementToInsertBusinessOwnerDetails = null;
        String businessOwnerName = "";
        int businessOwnerId = 0;
        try {
            businessOwnerName = businessOwner.getBusinessOwnerName();
            if (log.isDebugEnabled()) {
                log.debug("Adding a Business Owner" + businessOwnerName);
            }
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            String queryToInsertRecord = "INSERT INTO APM_BUSINESS_OWNER(OWNER_NAME,OWNER_EMAIL,OWNER_DESC," +
                    "OWNER_SITE, TENANT_ID) VALUES (?,?,?,?,?)";

            statementToInserBusinessOwner = connection.prepareStatement(queryToInsertRecord, new String[]{"OWNER_ID"});
            statementToInserBusinessOwner.setString(1, businessOwnerName);
            statementToInserBusinessOwner.setString(2, businessOwner.getBusinessOwnerEmail());
            statementToInserBusinessOwner.setString(3, businessOwner.getBusinessOwnerDescription());
            statementToInserBusinessOwner.setString(4, businessOwner.getBusinessOwnerSite());
            statementToInserBusinessOwner.setInt(5, tenantId);
            int affectedRows = statementToInserBusinessOwner.executeUpdate();

           if (affectedRows == 0) {
                throw new AppManagementException("Saving business owner user : " + businessOwnerName + " is failed, no "
                                                 + "rows affected.");
            }
            ResultSet generatedKeys = statementToInserBusinessOwner.getGeneratedKeys();
            if (generatedKeys.next()) {
                businessOwnerId = generatedKeys.getInt(1);
            }
            else {
                throw new SQLException("Creating user failed, no ID obtained.");
                   }

            String queryToInsertRecordTwo =
                    "INSERT INTO APM_BUSINESS_OWNER_PROPERTY(OWNER_ID, NAME, VALUE, SHOW_IN_STORE) VALUES" +
                            "(?,?,?,?)";

            statementToInsertBusinessOwnerDetails = connection.prepareStatement(queryToInsertRecordTwo);
            List<BusinessOwnerProperty> businessOwnerPropertiesList = businessOwner.getBusinessOwnerPropertiesList();
            if (businessOwnerPropertiesList != null) {
                for (int i = 0 ; i < businessOwnerPropertiesList.size(); i++) {
                    BusinessOwnerProperty businessOwnerProperties = businessOwnerPropertiesList.get(i);
                    String propertyId = businessOwnerProperties.getPropertyId();
                    if(!StringUtils.isEmpty(propertyId)) {
                        statementToInsertBusinessOwnerDetails.setInt(1, businessOwnerId);
                        statementToInsertBusinessOwnerDetails.setString(2, propertyId);
                        statementToInsertBusinessOwnerDetails.setString(3, businessOwnerProperties.getPropertyValue());
                        statementToInsertBusinessOwnerDetails.setBoolean(4, businessOwnerProperties.isShowingInStore());
                        statementToInsertBusinessOwnerDetails.executeUpdate();
                    }
                }
            }
            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    log.error("Failed the rollback of save business owner with name : " + businessOwnerName, e1);
                }
            }
            handleException("Failed to save business owner with name : " + businessOwnerName, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToInserBusinessOwner, connection, null);
        }
        return businessOwnerId;
    }

    /**
     * Search business owners.
     * @param startIndex
     * @param pageSize
     * @param searchValue
     * @return
     * @throws AppManagementException
     */
    public List<BusinessOwner> searchBusinessOwners(int startIndex, int pageSize, String searchValue, int tenantId)
            throws AppManagementException {
        Connection connection = null;
        PreparedStatement statementToGetBusinessOwners = null;
        List<BusinessOwner> businessOwnersList = new ArrayList<BusinessOwner>();
        ResultSet businessOwnerResultSet = null;

        try {
            connection = APIMgtDBUtil.getConnection();
            String queryToGetBusinessOwner = null;
            if (connection.getMetaData().getDriverName().contains("Oracle")) {
                queryToGetBusinessOwner = "SELECT * FROM APM_BUSINESS_OWNER WHERE (OWNER_NAME LIKE ? OR " +
                        "OWNER_EMAIL LIKE ? OR OWNER_SITE LIKE ? OR OWNER_DESC LIKE ?) AND TENANT_ID = ? AND ROWNUM >= ? AND ROWNUM <= ?";
            } else {
                queryToGetBusinessOwner = "SELECT * FROM APM_BUSINESS_OWNER WHERE (OWNER_NAME LIKE ? OR " +
                        "OWNER_EMAIL LIKE ? OR OWNER_SITE LIKE ? OR OWNER_DESC LIKE ?) AND TENANT_ID = ? LIMIT ? , ? ";
            }
            statementToGetBusinessOwners = connection.prepareStatement(queryToGetBusinessOwner);
            searchValue = "%" + searchValue + "%";
            statementToGetBusinessOwners.setString(1, searchValue);
            statementToGetBusinessOwners.setString(2, searchValue);
            statementToGetBusinessOwners.setString(3, searchValue);
            statementToGetBusinessOwners.setString(4, searchValue);
            statementToGetBusinessOwners.setInt(5, tenantId);
            statementToGetBusinessOwners.setInt(6, startIndex);
            statementToGetBusinessOwners.setInt(7, pageSize);
            businessOwnerResultSet = statementToGetBusinessOwners.executeQuery();

            while (businessOwnerResultSet.next()) {
                BusinessOwner businessOwner = new BusinessOwner();
                int businessOwnerId = businessOwnerResultSet.getInt("OWNER_ID");

                businessOwner.setBusinessOwnerId(businessOwnerId);
                businessOwner.setBusinessOwnerName(businessOwnerResultSet.getString("OWNER_NAME"));
                businessOwner.setBusinessOwnerDescription(businessOwnerResultSet.getString("OWNER_DESC"));
                businessOwner.setBusinessOwnerEmail(businessOwnerResultSet.getString("OWNER_EMAIL"));
                businessOwner.setBusinessOwnerSite(businessOwnerResultSet.getString("OWNER_SITE"));
                businessOwnersList.add(businessOwner);
            }
        } catch (SQLException e) {
            handleException("Failed to retrieve business owner for the search value " + searchValue, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToGetBusinessOwners, connection, businessOwnerResultSet);
        }
        return businessOwnersList;
    }

    /**
     * Get business owner count.
     * @return
     * @throws AppManagementException
     */
    public int getBusinessOwnersCount(int tenantId) throws AppManagementException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        int rowcount = 0;
        ResultSet resultSet = null;

        String sqlQuery = "SELECT COUNT(*) AS ROWCOUNT FROM APM_BUSINESS_OWNER WHERE TENANT_ID = ?";

        try {
            connection = APIMgtDBUtil.getConnection();
            preparedStatement = connection.prepareStatement(sqlQuery);
            preparedStatement.setInt(1, tenantId);
            resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                rowcount = resultSet.getInt("ROWCOUNT");
            }
        } catch (SQLException e) {
            handleException("Error when getting the row count of business owners table. ", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, resultSet);
        }
        return rowcount;
    }

    /**
     * Search business owners.
     * @param searchPrefix
     * @param tenantId
     * @return
     * @throws AppManagementException
     */
    public List<String> getBusinessOwnerIdsBySearchPrefix(String searchPrefix, int tenantId) throws AppManagementException {
        Connection connection = null;
        PreparedStatement statementToGetBusinessOwners = null;
        List<BusinessOwner> businessOwnersList = new ArrayList<BusinessOwner>();
        ResultSet businessOwnerResultSet = null;

        List<String> businessOwnerIdsList = new ArrayList<>();
        try {
            connection = APIMgtDBUtil.getConnection();
            String queryToGetBusinessOwner = "SELECT OWNER_ID FROM APM_BUSINESS_OWNER WHERE OWNER_NAME LIKE ? AND TENANT_ID = ? ";

            statementToGetBusinessOwners = connection.prepareStatement(queryToGetBusinessOwner);
            searchPrefix = "%" + searchPrefix + "%";
            statementToGetBusinessOwners.setString(1, searchPrefix);
            statementToGetBusinessOwners.setInt(2, tenantId);
            businessOwnerResultSet = statementToGetBusinessOwners.executeQuery();

            while (businessOwnerResultSet.next()) {
                BusinessOwner businessOwner = new BusinessOwner();
                int businessOwnerId = businessOwnerResultSet.getInt("OWNER_ID");
                businessOwnerIdsList.add(String.valueOf(businessOwnerId));
            }
        } catch (SQLException e) {
            handleException("Failed to retrieve business Ids for the search value " + searchPrefix, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToGetBusinessOwners, connection, businessOwnerResultSet);
        }
        return businessOwnerIdsList;
    }

    /**
     * Get Businesss owner Id by owner name, email and tenant Id
     * @param businessOwnerName
     * @param businessOwnerEmail
     * @param tenantId
     * @return
     * @throws AppManagementException
     */
    public int getBusinessOwnerId(String businessOwnerName, String businessOwnerEmail, int tenantId) throws
                                                                                                AppManagementException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet businessOwnerResultSet = null;

        String query = "SELECT OWNER_ID FROM APM_BUSINESS_OWNER WHERE OWNER_NAME = ? AND OWNER_EMAIL = ? AND " +
                "TENANT_ID = ?";

        int businessOwnerId = -1; // if any business owner doesn't exit for the input parameters, return owner id as -1
        try {
            connection = APIMgtDBUtil.getConnection();
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, businessOwnerName);
            preparedStatement.setString(2, businessOwnerEmail);
            preparedStatement.setInt(3, tenantId);
            businessOwnerResultSet = preparedStatement.executeQuery();

            if (businessOwnerResultSet.next()) {
                businessOwnerId = businessOwnerResultSet.getInt("OWNER_ID");
            }
        } catch (SQLException e) {
            handleException("Failed to retrieve business owners.", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, businessOwnerResultSet);

        }
        return businessOwnerId;
    }

    /**
	 * Get Subscribed APIs for given userId
	 *
	 * @param userId
	 *            id of the user
	 * @return APIInfoDTO[]
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to get Subscribed APIs
	 * @throws barley.identity.base.IdentityException
	 *             if failed to get tenant id
	 */
	public APIInfoDTO[] getSubscribedAPIsOfUser(String userId) throws AppManagementException {

		// identify loggedinuser
		String loginUserName = getLoginUserName(userId);

		int tenantId = IdentityTenantUtil.getTenantIdOfUser(loginUserName);
		List<APIInfoDTO> apiInfoDTOList = new ArrayList<APIInfoDTO>();
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
        String sqlQuery = "SELECT API.APP_PROVIDER AS APP_PROVIDER, API.APP_NAME AS APP_NAME, " +
                "API.APP_VERSION AS APP_VERSION " +
                "FROM APM_SUBSCRIPTION SP, APM_APP API, APM_SUBSCRIBER SB, APM_APPLICATION APP " +
                "WHERE SB.USER_ID = ? AND SB.TENANT_ID = ? AND SB.SUBSCRIBER_ID = APP.SUBSCRIBER_ID " +
                "AND APP.APPLICATION_ID = SP.APPLICATION_ID AND API.APP_ID = SP.APP_ID";
        try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(sqlQuery);
			ps.setString(1, loginUserName);
			ps.setInt(2, tenantId);
			rs = ps.executeQuery();
			while (rs.next()) {
				APIInfoDTO infoDTO = new APIInfoDTO();
				// (??????)
				//infoDTO.setProviderId(AppManagerUtil.replaceEmailDomain(rs.getString("APP_PROVIDER")));
				infoDTO.setProviderId(AppManagerUtil.replaceEmailDomainBack(rs.getString("APP_PROVIDER")));
				infoDTO.setApiName(rs.getString("APP_NAME"));
				infoDTO.setVersion(rs.getString("APP_VERSION"));
				apiInfoDTOList.add(infoDTO);
			}
		} catch (SQLException e) {
			handleException("Error while executing SQL", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return apiInfoDTOList.toArray(new APIInfoDTO[apiInfoDTOList.size()]);
	}

	/**
	 * Get WebApp key information for given WebApp
	 *
	 * @param apiInfoDTO
	 *            WebApp info
	 * @return APIKeyInfoDTO[]
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to get key info for given WebApp
	 */
	public APIKeyInfoDTO[] getSubscribedUsersForAPI(APIInfoDTO apiInfoDTO)
	                                                                      throws
                                                                          AppManagementException {

		APIKeyInfoDTO[] apiKeyInfoDTOs = null;
		// APP_ID store as "providerName_apiName_apiVersion" in APM_SUBSCRIPTION
		// table
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
        String sqlQuery = "SELECT SB.USER_ID, SB.TENANT_ID " +
                "FROM APM_SUBSCRIBER SB, APM_APPLICATION APP, APM_SUBSCRIPTION SP, APM_APP API " +
                "WHERE API.APP_PROVIDER = ? AND API.APP_NAME = ? AND API.APP_VERSION = ? " +
                "AND SP.APPLICATION_ID = APP.APPLICATION_ID AND APP.SUBSCRIBER_ID=SB.SUBSCRIBER_ID " +
                "AND API.APP_ID = SP.APP_ID";
        try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(sqlQuery);
			ps.setString(1, AppManagerUtil.replaceEmailDomainBack(apiInfoDTO.getProviderId()));
			ps.setString(2, apiInfoDTO.getApiName());
			ps.setString(3, apiInfoDTO.getVersion());
			rs = ps.executeQuery();
			List<APIKeyInfoDTO> apiKeyInfoList = new ArrayList<APIKeyInfoDTO>();
			while (rs.next()) {
				String userId = rs.getString(AppMConstants.SUBSCRIBER_FIELD_USER_ID);
				APIKeyInfoDTO apiKeyInfoDTO = new APIKeyInfoDTO();
				apiKeyInfoDTO.setUserId(userId);
				apiKeyInfoList.add(apiKeyInfoDTO);
			}
			apiKeyInfoDTOs = apiKeyInfoList.toArray(new APIKeyInfoDTO[apiKeyInfoList.size()]);
		} catch (SQLException e) {
			handleException("Error while executing SQL", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return apiKeyInfoDTOs;
	}

	/**
	 * Validate the provided key against the given WebApp. First it will
	 * validate the key is valid
	 * , ACTIVE and not expired.
	 *
	 * @param context
	 *            Requested Context
	 * @param version
	 *            version of the WebApp
	 * @param accessToken
	 *            Provided Access Token
	 * @return APIKeyValidationInfoDTO instance with authorization status and
	 *         tier information if
	 *         authorized.
	 * @throws barley.appmgt.api.AppManagementException
	 *             Error when accessing the database or registry.
	 */
	public APIKeyValidationInfoDTO validateKey(String context, String version, String accessToken,
	                                           String requiredAuthenticationLevel)
	                                                                              throws
                                                                                  AppManagementException {

		if (log.isDebugEnabled()) {
			log.debug("A request is received to process the token : " + accessToken + " to access" +
			          " the context URL : " + context);
		}
		APIKeyValidationInfoDTO keyValidationInfoDTO = new APIKeyValidationInfoDTO();
		keyValidationInfoDTO.setAuthorized(false);

		String status;
		String tier;
		String type;
		String userType;
		String subscriberName;
		String subscriptionStatus;
		String applicationId;
		String applicationName;
		String applicationTier;
		String endUserName;
		long validityPeriod;
		long issuedTime;
		long timestampSkew;
		long currentTime;
		String apiName;
		String consumerKey;
		String apiPublisher;

		String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
		    AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromAccessToken(accessToken);
		}

		// First check whether the token is valid, active and not expired.
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

        String applicationSqlQuery = String.format("SELECT IAT.VALIDITY_PERIOD, IAT.TIME_CREATED, IAT.TOKEN_STATE, IAT.USER_TYPE, " +
                "IAT.AUTHZ_USER, IAT.TIME_CREATED, SUB.TIER_ID, SUBS.USER_ID, SUB.SUB_STATUS, APP.APPLICATION_ID, " +
                "APP.NAME, APP.APPLICATION_TIER, API.APP_NAME, API.APP_PROVIDER " +
                "FROM %s IAT, APM_SUBSCRIPTION SUB, APM_SUBSCRIBER SUBS, " +
                "APM_APPLICATION APP, APM_APP API " +
                "WHERE IAT.ACCESS_TOKEN = ? AND API.CONTEXT = ? AND API.APP_VERSION = ? " +
                "AND SUB.APPLICATION_ID = APP.APPLICATION_ID AND APP.SUBSCRIBER_ID = SUBS.SUBSCRIBER_ID " +
                "AND API.APP_ID = SUB.APP_ID", accessTokenStoreTable);

		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
			ps = conn.prepareStatement(applicationSqlQuery);
			String encryptedAccessToken = AppManagerUtil.encryptToken(accessToken);
			ps.setString(1, encryptedAccessToken);
			ps.setString(2, context);
			ps.setString(3, version);
			rs = ps.executeQuery();
			if (rs.next()) {
				status = rs.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_TOKEN_STATE);
				tier = rs.getString(AppMConstants.SUBSCRIPTION_FIELD_TIER_ID);
				type = rs.getString(AppMConstants.SUBSCRIPTION_KEY_TYPE);
				userType = rs.getString(AppMConstants.SUBSCRIPTION_USER_TYPE);
				subscriberName = rs.getString(AppMConstants.SUBSCRIBER_FIELD_USER_ID);
				applicationId = rs.getString(AppMConstants.APPLICATION_ID);
				applicationName = rs.getString(AppMConstants.APPLICATION_NAME);
				applicationTier = rs.getString(AppMConstants.APPLICATION_TIER);
				endUserName = rs.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_AUTHORIZED_USER);
				issuedTime =
				             rs.getTimestamp(AppMConstants.IDENTITY_OAUTH2_FIELD_TIME_CREATED,
				                             Calendar.getInstance(TimeZone.getTimeZone("UTC")))
				               .getTime();
				validityPeriod = rs.getLong(AppMConstants.IDENTITY_OAUTH2_FIELD_VALIDITY_PERIOD);
				timestampSkew =
				                OAuthServerConfiguration.getInstance().getTimeStampSkewInSeconds() * 1000;
				currentTime = System.currentTimeMillis();
				subscriptionStatus = rs.getString(AppMConstants.SUBSCRIPTION_FIELD_SUB_STATUS);
				apiName = rs.getString(AppMConstants.FIELD_API_NAME);
				consumerKey = rs.getString(AppMConstants.FIELD_CONSUMER_KEY);
				apiPublisher = rs.getString(AppMConstants.FIELD_API_PUBLISHER);

				/*
				 * If Subscription Status is PROD_ONLY_BLOCKED, block production
				 * access only
				 */
				if (subscriptionStatus.equals(AppMConstants.SubscriptionStatus.BLOCKED)) {
					keyValidationInfoDTO.setValidationStatus(AppMConstants.KeyValidationStatus.API_BLOCKED);
					keyValidationInfoDTO.setAuthorized(false);
					return keyValidationInfoDTO;
				} else if (AppMConstants.SubscriptionStatus.ON_HOLD.equals(subscriptionStatus) ||
				           AppMConstants.SubscriptionStatus.REJECTED.equals(subscriptionStatus)) {
					keyValidationInfoDTO.setValidationStatus(AppMConstants.KeyValidationStatus.SUBSCRIPTION_INACTIVE);
					keyValidationInfoDTO.setAuthorized(false);
					return keyValidationInfoDTO;
				} else if (subscriptionStatus.equals(AppMConstants.SubscriptionStatus.PROD_ONLY_BLOCKED) &&
				           !AppMConstants.API_KEY_TYPE_SANDBOX.equals(type)) {
					keyValidationInfoDTO.setValidationStatus(AppMConstants.KeyValidationStatus.API_BLOCKED);
					keyValidationInfoDTO.setAuthorized(false);
					return keyValidationInfoDTO;
				}

				// check if 'requiredAuthenticationLevel' & the one associated
				// with access token matches
				// This check should only be done for 'Application' and
				// 'Application_User' levels
				if (requiredAuthenticationLevel.equals(AppMConstants.AUTH_APPLICATION_LEVEL_TOKEN) ||
				    requiredAuthenticationLevel.equals(AppMConstants.AUTH_APPLICATION_USER_LEVEL_TOKEN)) {
					if (log.isDebugEnabled()) {
						log.debug("Access token's userType : " + userType + ".Required type : " +
						          requiredAuthenticationLevel);
					}

					if (!(userType.equalsIgnoreCase(requiredAuthenticationLevel))) {
						keyValidationInfoDTO.setValidationStatus(AppMConstants.KeyValidationStatus.API_AUTH_INCORRECT_ACCESS_TOKEN_TYPE);
						keyValidationInfoDTO.setAuthorized(false);
						return keyValidationInfoDTO;
					}
				}

				// Check whether the token is ACTIVE
				if (AppMConstants.TokenStatus.ACTIVE.equals(status)) {
					if (log.isDebugEnabled()) {
						log.debug("Checking Access token: " +
						          accessToken +
						          " for validity." +
						          "((currentTime - timestampSkew) > (issuedTime + validityPeriod)) : " +
						          "((" + currentTime + "-" + timestampSkew + ")" + " > (" +
						          issuedTime + " + " + validityPeriod + "))");
					}
					if (validityPeriod != Long.MAX_VALUE &&
					    (currentTime - timestampSkew) > (issuedTime + validityPeriod)) {
						keyValidationInfoDTO.setValidationStatus(AppMConstants.KeyValidationStatus.API_AUTH_ACCESS_TOKEN_EXPIRED);
						if (log.isDebugEnabled()) {
							log.debug("Access token: " +
							          accessToken +
							          " has expired. " +
							          "Reason ((currentTime - timestampSkew) > (issuedTime + validityPeriod)) : " +
							          "((" + currentTime + "-" + timestampSkew + ")" + " > (" +
							          issuedTime + " + " + validityPeriod + "))");
						}
						// update token status as expired
						updateTokenState(accessToken, conn, ps);
						conn.commit();
					} else {
						keyValidationInfoDTO.setAuthorized(true);
						keyValidationInfoDTO.setTier(tier);
						keyValidationInfoDTO.setType(type);
						keyValidationInfoDTO.setSubscriber(subscriberName);
						keyValidationInfoDTO.setIssuedTime(issuedTime);
						keyValidationInfoDTO.setValidityPeriod(validityPeriod);
						keyValidationInfoDTO.setUserType(userType);
						keyValidationInfoDTO.setEndUserName(endUserName);
						keyValidationInfoDTO.setApplicationId(applicationId);
						keyValidationInfoDTO.setApplicationName(applicationName);
						keyValidationInfoDTO.setApplicationTier(applicationTier);
						keyValidationInfoDTO.setApiName(apiName);
						keyValidationInfoDTO.setConsumerKey(AppManagerUtil.decryptToken(consumerKey));
						keyValidationInfoDTO.setApiPublisher(apiPublisher);
					}
				} else {
					keyValidationInfoDTO.setValidationStatus(AppMConstants.KeyValidationStatus.API_AUTH_ACCESS_TOKEN_INACTIVE);
					if (log.isDebugEnabled()) {
						log.debug("Access token: " + accessToken + " is inactive");
					}
				}
			} else {
				// no record found. Invalid access token received
				keyValidationInfoDTO.setValidationStatus(AppMConstants.KeyValidationStatus.API_AUTH_INVALID_CREDENTIALS);
				if (log.isDebugEnabled()) {
					log.debug("Access token: " + accessToken + " is invalid");
				}
			}
		} catch (SQLException e) {
			handleException("Error when executing the SQL ", e);
		} catch (CryptoException e) {
			handleException("Error when encrypting/decrypting token(s)", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return keyValidationInfoDTO;
	}

	/**
     * Validate the provided key against the given WebApp. First it will
     * validate the key is valid
     * , ACTIVE and not expired.
     *
     * @param appId
     *            webApp Id
     * @return ArrayList URL_Patterns
     * @throws java.sql.SQLException
     */


    public ArrayList<String> getInUrlMappingById(int appId)
            throws AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String userRoles = null;
        ArrayList<String> urlsPattern = new ArrayList<String>();

        String ssoInfoSqlQuery = "SELECT URL_PATTERN FROM APM_APP_URL_MAPPING WHERE APP_ID = ?";
            try {
                conn = APIMgtDBUtil.getConnection();
                ps = conn.prepareStatement(ssoInfoSqlQuery);
                ps.setInt(1, appId);
                rs = ps.executeQuery();
                while (rs.next()) {
                    userRoles = rs.getString("URL_PATTERN");
                    urlsPattern.add(userRoles);
                }
                if (urlsPattern!=null){
                    return urlsPattern;
                }
                else{
                    return null;
                }
            } catch (SQLException e) {
                handleException("Error when executing the SQL ", e);
                return null;
            } finally {
                APIMgtDBUtil.closeAllConnections(ps, conn, rs);
            }

    }


    /**
     * Validate the provided key against the given WebApp. First it will
     * validate the key is valid
     * , ACTIVE and not expired.
     *
     * @param appId
     *            webApp Id
     * @param urlMapping
     *            request resource of the WebApp
     * @return String roles
     * @throws java.sql.SQLException
     */


    public String getInUrlMappingRoles(int appId, String urlMapping, String httpVerb)
            throws AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String userRoles = null;

		String ssoInfoSqlQuery = "SELECT USER_ROLES FROM APM_APP_URL_MAPPING MAP " +
				"LEFT JOIN APM_POLICY_GROUP POLICY ON MAP.POLICY_GRP_ID=POLICY.POLICY_GRP_ID " +
				"WHERE MAP.APP_ID= ? AND URL_PATTERN= ? AND HTTP_METHOD= ?";


        try {
            conn = APIMgtDBUtil.getConnection();
            ps = conn.prepareStatement(ssoInfoSqlQuery);
            ps.setInt(1, appId);
            ps.setString(2, urlMapping);
            ps.setString(3,httpVerb);
            rs = ps.executeQuery();
            if (rs.next()) {
                userRoles = rs.getString("USER_ROLES");
            }
            if (userRoles!=null){
                return userRoles;
            }
            else{
                return "";
            }
        } catch (SQLException e) {
            handleException("Error when executing the SQL ", e);
            return null;
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
    }

	public static WebAppInfoDTO getSAML2SSOConfigInfo(String context, String version)
	                                                                                 throws
                                                                                     AppManagementException {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		WebAppInfoDTO webAppInfoDTO = new WebAppInfoDTO();
		String saml2SsoIssuer;

        String ssoInfoSqlQuery = "SELECT app.APP_NAME, app.LOG_OUT_URL, app.APP_ID, " +
                "APP_ALLOW_ANONYMOUS " +
                "FROM APM_APP app " +
                "WHERE app.CONTEXT = ? AND app.APP_VERSION = ? ";

		try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(ssoInfoSqlQuery);
			ps.setString(1, context);
			ps.setString(2, version);
			rs = ps.executeQuery();
			if (rs.next()) {
                //use the web-app name as the Issuer
				saml2SsoIssuer = rs.getString("APP_NAME");
                webAppInfoDTO.setSaml2SsoIssuer(saml2SsoIssuer);
                webAppInfoDTO.setLogoutUrl(rs.getString("LOG_OUT_URL"));
                webAppInfoDTO.setContext(context);
				webAppInfoDTO.setVersion(version);
                webAppInfoDTO.setAppID(rs.getInt("APP_ID"));
				webAppInfoDTO.setAllowAnonymous(rs.getBoolean("APP_ALLOW_ANONYMOUS"));
			}
		} catch (SQLException e) {
			handleException("Error when executing the SQL: " + ssoInfoSqlQuery + " (Context:" +
					context + " ,Version:" + version + ")", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}

		return webAppInfoDTO;
	}



	/**
	 * Access info against each URL pattern verb
	 * @param context : App Context
	 * @param version : App Version
	 * @return VerbInfoDTO class
	 * @throws AppManagementException if any error occur while accessing data from DB
	 */
	public static VerbInfoDTO getVerbConfigInfo(String context, String version)
			throws AppManagementException {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		VerbInfoDTO verbInfoDTO = new VerbInfoDTO();

        String query = "SELECT HTTP_METHOD, URL_PATTERN, URL_ALLOW_ANONYMOUS " +
                "FROM APM_APP_URL_MAPPING MAP " +
                "LEFT JOIN APM_POLICY_GROUP POLICY ON MAP.POLICY_GRP_ID=POLICY.POLICY_GRP_ID " +
                "WHERE MAP.APP_ID = " +
                "(SELECT APP_ID FROM APM_APP WHERE CONTEXT = ? AND APP_VERSION = ?)";

		try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(query);
			ps.setString(1, context);
			ps.setString(2, version);
			rs = ps.executeQuery();

			String mapKey; // contains the unique key
			String urlPattern; // url pattern
			while (rs.next()) {

				urlPattern = rs.getString("URL_PATTERN");

                // Key is constructed using the http Method + URL pattern
                if (urlPattern != null && urlPattern.startsWith("/")) {
                    mapKey = rs.getString("HTTP_METHOD") + context + "/" + version + urlPattern;
                } else {
                    mapKey = rs.getString("HTTP_METHOD") + context + "/" + version + "/" + urlPattern;
                }

				// store the values (is anonymous allowed) per each URL pattern
				verbInfoDTO.addAllowAnonymousUrl(mapKey, rs.getBoolean("URL_ALLOW_ANONYMOUS"));
			}
		} catch (SQLException e) {
			handleException("Error when executing the SQL : " + query + " (Context:" + context +
					" ,Version:" + version + ")", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return verbInfoDTO;
	}

	public long getApplicationAccessTokenRemainingValidityPeriod(String accessToken)
	                                                                                throws
                                                                                    AppManagementException {
		String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
		    AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromAccessToken(accessToken);
		}
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		long validityPeriod;
		long issuedTime;
		long timestampSkew;
		long currentTime;
		long remainingTime = 0;

        String applicationSqlQuery = "SELECT IAT.VALIDITY_PERIOD, IAT.TIME_CREATED " +
                "FROM " + accessTokenStoreTable + " IAT " +
                "WHERE IAT.ACCESS_TOKEN = ? ";

		try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(applicationSqlQuery);
			ps.setString(1, accessToken);
			rs = ps.executeQuery();
			if (rs.next()) {
				issuedTime =
				             rs.getTimestamp(AppMConstants.IDENTITY_OAUTH2_FIELD_TIME_CREATED,
				                             Calendar.getInstance(TimeZone.getTimeZone("UTC")))
				               .getTime();
				validityPeriod = rs.getLong(AppMConstants.IDENTITY_OAUTH2_FIELD_VALIDITY_PERIOD);
				timestampSkew =
				                OAuthServerConfiguration.getInstance().getTimeStampSkewInSeconds() * 1000;
				currentTime = System.currentTimeMillis();
				remainingTime = ((currentTime) - (issuedTime + validityPeriod));
			}
		} catch (SQLException e) {
			handleException("Error when executing the SQL ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return remainingTime;
	}

	private void updateTokenState(String accessToken, Connection conn, PreparedStatement ps)
	                                                                                        throws SQLException,
                                                                                                   AppManagementException,
	                                                                                        CryptoException {

		String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
		    AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromAccessToken(accessToken);
		}
		String encryptedAccessToken = AppManagerUtil.encryptToken(accessToken);
        String UPDATE_TOKE_STATE_SQL = "UPDATE " + accessTokenStoreTable +
                " SET TOKEN_STATE = ? , TOKEN_STATE_ID = ? WHERE ACCESS_TOKEN = ?";
        ps = conn.prepareStatement(UPDATE_TOKE_STATE_SQL);
		ps.setString(1, "EXPIRED");
		ps.setString(2, UUID.randomUUID().toString());
		ps.setString(3, encryptedAccessToken);
		ps.executeUpdate();
	}

	public void addSubscriber(Subscriber subscriber) throws AppManagementException {
		Connection conn = null;
		ResultSet rs = null;
		PreparedStatement ps = null;
		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
			String query = SQLConstants.ADD_SUBSCRIBER_SQL;

			ps = conn.prepareStatement(query, new String[] { "subscriber_id" });

			ps.setString(1, subscriber.getName());
			ps.setInt(2, subscriber.getTenantId());
			ps.setString(3, subscriber.getEmail());
			ps.setTimestamp(4, new Timestamp(subscriber.getSubscribedDate().getTime()));
			ps.executeUpdate();

			int subscriberId = 0;
			rs = ps.getGeneratedKeys();
			if (rs.next()) {
				subscriberId = Integer.valueOf(rs.getString(1)).intValue();
			}
			subscriber.setId(subscriberId);

			// Add default application
			Application defaultApp =
			                         new Application(AppMConstants.DEFAULT_APPLICATION_NAME,
			                                         subscriber);
			defaultApp.setTier(AppMConstants.UNLIMITED_TIER);
			addApplication(defaultApp, subscriber.getName(), conn);

			conn.commit();
		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Error while rolling back the failed operation", e);
				}
			}
			handleException("Error in adding new subscriber: " + e.getMessage(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
	}
	
	// (??????) 2019.07.23 - ????????? ?????? 
    public void removeSubscriber(int subscriberId) throws AppManagementException {
        Connection conn = null;
        PreparedStatement deleteRatingPstm = null;
        PreparedStatement ps = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            
            String deleteRatingQuery = SQLConstants.REMOVE_SUBSCRIBER_FROM_API_RATING_SQL;
            deleteRatingPstm = conn.prepareStatement(deleteRatingQuery);
            deleteRatingPstm.setInt(1, subscriberId);
            deleteRatingPstm.executeUpdate();

            String query = SQLConstants.REMOVE_SUBSCRIBER_SQL;
            ps = conn.prepareStatement(query);
            ps.setInt(1, subscriberId);
            ps.executeUpdate();
            
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Error while rolling back the failed operation", e1);
                }
            }
            handleException("Error in deleting subscriber: " + e.getMessage(), e);
        } finally {
        	APIMgtDBUtil.closeAllConnections(deleteRatingPstm, conn, null);
            APIMgtDBUtil.closeAllConnections(ps, conn, null);
        }
    }

	public void updateSubscriber(Subscriber subscriber) throws AppManagementException {
		Connection conn = null;
		ResultSet rs = null;
		PreparedStatement ps = null;
		try {
			conn = APIMgtDBUtil.getConnection();
			String query = SQLConstants.UPDATE_SUBSCRIBER_SQL;

            ps = conn.prepareStatement(query);
			ps.setString(1, subscriber.getName());
			ps.setInt(2, subscriber.getTenantId());
			ps.setString(3, subscriber.getEmail());
			ps.setTimestamp(4, new Timestamp(subscriber.getSubscribedDate().getTime()));
			ps.setInt(5, subscriber.getId());
			ps.executeUpdate();
		} catch (SQLException e) {
			handleException("Error in updating subscriber: " + e.getMessage(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
	}

	public Subscriber getSubscriber(int subscriberId) throws AppManagementException {
		Connection conn = null;
		ResultSet rs = null;
		PreparedStatement ps = null;
		try {
			conn = APIMgtDBUtil.getConnection();
			String query = SQLConstants.GET_SUBSCRIBER_SQL;

            ps = conn.prepareStatement(query);
			ps.setInt(1, subscriberId);
			rs = ps.executeQuery();
			if (rs.next()) {
				Subscriber subscriber = new Subscriber(rs.getString("USER_ID"));
				subscriber.setId(subscriberId);
				subscriber.setTenantId(rs.getInt("TENANT_ID"));
				subscriber.setEmail(rs.getString("EMAIL_ADDRESS"));
				subscriber.setSubscribedDate(new java.util.Date(rs.getTimestamp("DATE_SUBSCRIBED")
				                                                  .getTime()));
				return subscriber;
			}
		} catch (SQLException e) {
			handleException("Error while retrieving subscriber: " + e.getMessage(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return null;
	}

	public int getDefaultApplicationForSubscriber(String subscriber) throws AppManagementException {
		Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;
		int application = -1;

		try {
			conn = APIMgtDBUtil.getConnection();
            String getAppQuery = "SELECT APPLICATION_ID " +
                    "FROM APM_APPLICATION " +
                    "INNER JOIN APM_SUBSCRIBER " +
                    "ON APM_APPLICATION.SUBSCRIBER_ID = APM_SUBSCRIBER.SUBSCRIBER_ID " +
                    "WHERE APM_SUBSCRIBER.USER_ID = ?";
            ps = conn.prepareStatement(getAppQuery);
			ps.setString(1, subscriber);
			resultSet = ps.executeQuery();
			if (resultSet.next()) {
				application = resultSet.getInt("APPLICATION_ID");
			}

			if (application == -1) {
				String msg = "Unable to get the Application ID for: " + subscriber;
				log.error(msg);
				throw new AppManagementException(msg);
			}
		} catch (SQLException e) {
			handleException("Failed to get application data ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
		return application;
	}

    public Subscription getSubscription(APIIdentifier identifier, int applicationId, String subscriptionTyp) throws
                                                                                                             AppManagementException {

        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        Subscription subscription = null;

        try{
            connection = APIMgtDBUtil.getConnection();

            String queryToGetSubscriptionId = "SELECT SUBSCRIPTION_ID, SUBSCRIPTION_TYPE, SUB_STATUS, SUBSCRIPTION_TIME, " +
                     "USER_ID , APM_APP.APP_ID, APM_APPLICATION.APPLICATION_ID, TRUSTED_IDP " +
                    "FROM APM_SUBSCRIPTION, APM_APPLICATION, APM_SUBSCRIBER, APM_APP " +
                    "WHERE APM_APPLICATION.APPLICATION_ID = APM_SUBSCRIPTION.APPLICATION_ID " +
                    "AND APM_SUBSCRIBER.SUBSCRIBER_ID = APM_APPLICATION.SUBSCRIBER_ID " +
                    "AND APM_SUBSCRIPTION.APP_ID = APM_APP.APP_ID " +
                    "AND APM_APP.APP_PROVIDER = ? AND APM_APP.APP_NAME = ? AND APM_APP.APP_VERSION = ? " +
                    "AND APM_APPLICATION.APPLICATION_ID = ? AND SUBSCRIPTION_TYPE = ?";

            preparedStatement = connection.prepareStatement(queryToGetSubscriptionId);
            preparedStatement.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            preparedStatement.setString(2, identifier.getApiName());
            preparedStatement.setString(3, identifier.getVersion());
            preparedStatement.setInt(4, applicationId);
            preparedStatement.setString(5, subscriptionTyp);

            resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                subscription = new Subscription();

                subscription.setSubscriptionId(resultSet.getInt("SUBSCRIPTION_ID"));
                subscription.setWebAppId(resultSet.getInt("APP_ID"));
                subscription.setApplicationId(resultSet.getInt("APPLICATION_ID"));
                subscription.setSubscriptionType(resultSet.getString("SUBSCRIPTION_TYPE"));
                subscription.setSubscriptionStatus(resultSet.getString("SUB_STATUS"));
                subscription.setUserId(resultSet.getString("USER_ID"));
                subscription.setSubscriptionTime(resultSet.getString("SUBSCRIPTION_TIME"));

                String trustedIdpsJson = resultSet.getString("TRUSTED_IDP");
                Object decodedJson = null;
                if (trustedIdpsJson != null) {
                    decodedJson = JSONValue.parse(trustedIdpsJson);
                }
                if(decodedJson != null){
                    for(Object item : (JSONArray)decodedJson){
                        subscription.addTrustedIdp(item.toString());
                    }
                }
            }
            preparedStatement.close();
        }catch (SQLException e){
            handleException(String.format("Failed to get subscription for app identifier : %d and application id : %s", identifier.toString(), identifier), e);
        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, resultSet);
        }

        return subscription;

    }

    public boolean updateSubscription(int subscriptionId, String subscriptionType, String trustedIdps,String subStatus)throws
                                                                                                                       AppManagementException {

        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try{
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);

            String queryToUpdateSubscription = "UPDATE " +
                    "APM_SUBSCRIPTION " +
                    "SET " +
                    "SUBSCRIPTION_TYPE = ?, TRUSTED_IDP = ? , SUB_STATUS = ?" +
                    "WHERE SUBSCRIPTION_ID = ?";

            preparedStatement = connection.prepareStatement(queryToUpdateSubscription);

            preparedStatement.setString(1, subscriptionType);
            preparedStatement.setString(2,trustedIdps);
            preparedStatement.setString(3,subStatus);
            preparedStatement.setInt(4, subscriptionId);

            int result = preparedStatement.executeUpdate();
            connection.commit();

            return result == 1;

        }catch (SQLException e){
            handleException(String.format("Failed updating subscription. Id : %d", subscriptionId), e);
        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, resultSet);
        }

        return false;

    }

    public int addSubscription(APIIdentifier identifier, String subscriptionType, String context, int applicationId,
                               String status, String trustedIdps) throws AppManagementException {

        Connection conn = null;
        ResultSet resultSet = null;
        ResultSet rs = null;
        PreparedStatement ps = null;
        int subscriptionId = -1;
        int apiId = -1;

        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            String checkDuplicateQuery = 
            		"SELECT SUB_STATUS FROM APM_SUBSCRIPTION WHERE APP_ID = ? AND " +
            				"APPLICATION_ID = ?";
            ps = conn.prepareStatement(checkDuplicateQuery);
            ps.setInt(1, apiId);
            ps.setInt(2, applicationId);
            resultSet = ps.executeQuery();

            //If the subscription already exists
            if (resultSet.next()) {
                String subStatus = resultSet.getString("SUB_STATUS");
                String applicationName = getApplicationNameFromId(applicationId);

                if ((AppMConstants.SubscriptionStatus.UNBLOCKED.equals(subStatus) ||
                		AppMConstants.SubscriptionStatus.ON_HOLD.equals(subStatus) ||
                		AppMConstants.SubscriptionStatus.REJECTED.equals(subStatus))) {

                    //Throw error saying subscription already exists.
                    log.error("Subscription already exists for API " + identifier.getApiName() + " in Application " +
                              applicationName);
                    throw new SubscriptionAlreadyExistingException("Subscription already exists for API " +
                                                                   identifier.getApiName() + " in Application " +
                                                                   applicationName);
                } else if (AppMConstants.SubscriptionStatus.BLOCKED.equals(subStatus) || AppMConstants
                        .SubscriptionStatus.PROD_ONLY_BLOCKED.equals(subStatus)) {
                    log.error("Subscription to API " + identifier.getApiName() + " through application " +
                              applicationName + " was blocked");
                    throw new AppManagementException("Subscription to API " + identifier.getApiName() + " through " +
                                                     "application " + applicationName + " was blocked");
                }
            }
            ps.close();
            
            String getApiQuery =
                    "SELECT APP_ID FROM APM_APP API WHERE APP_PROVIDER = ? AND APP_NAME = ? AND " +
                            "APP_VERSION = ?";
            ps = conn.prepareStatement(getApiQuery);
            ps.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            ps.setString(2, identifier.getApiName());
            ps.setString(3, identifier.getVersion());
            resultSet = ps.executeQuery();
            if (resultSet.next()) {
                apiId = resultSet.getInt("APP_ID");
            }
            ps.close();

            if (apiId == -1) {
                String msg = "Unable to get the WebApp ID for: " + identifier;
                log.error(msg);
                throw new AppManagementException(msg);
            }

            // This query to update the APM_SUBSCRIPTION table
            String sqlQuery =
                    "INSERT INTO APM_SUBSCRIPTION (TIER_ID,SUBSCRIPTION_TYPE, APP_ID, " +
                            "APPLICATION_ID,SUB_STATUS, TRUSTED_IDP, SUBSCRIPTION_TIME) "
                            + "VALUES (?,?,?,?,?,?,?)";

            // Adding data to the APM_SUBSCRIPTION table
            // ps = conn.prepareStatement(sqlQuery,
            // Statement.RETURN_GENERATED_KEYS);
            ps = conn.prepareStatement(sqlQuery, new String[] { "SUBSCRIPTION_ID" });
            if (conn.getMetaData().getDriverName().contains("PostgreSQL")) {
                ps = conn.prepareStatement(sqlQuery, new String[] { "subscription_id" });
            }

            byte count = 0;
            ps.setString(++count, identifier.getTier());
            ps.setString(++count, subscriptionType);
            ps.setInt(++count, apiId);
            ps.setInt(++count, applicationId);
            ps.setString(++count, status != null ? status : AppMConstants.SubscriptionStatus.UNBLOCKED);
            ps.setString(++count, trustedIdps);
            ps.setTimestamp(++count, new Timestamp(new java.util.Date().getTime()));

            ps.executeUpdate();
            rs = ps.getGeneratedKeys();
            while (rs.next()) {
                // subscriptionId = rs.getInt(1);
                subscriptionId = Integer.valueOf(rs.getString(1)).intValue();
            }
            ps.close();

            // finally commit transaction
            conn.commit();

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback the add subscription ", e);
                }
            }
            handleException("Failed to add subscriber data ", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
            APIMgtDBUtil.closeAllConnections(null, null, rs);
        }
        return subscriptionId;
    }

	public void removeSubscription(APIIdentifier identifier, int applicationId)
	                                                                           throws
                                                                               AppManagementException {
		Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;
		int subscriptionId = -1;
		int apiId = -1;

		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
            String getApiQuery =
                    "SELECT APP_ID FROM APM_APP API WHERE APP_PROVIDER = ? AND APP_NAME = ? AND " +
                            "APP_VERSION = ?";

            ps = conn.prepareStatement(getApiQuery);
			ps.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
			ps.setString(2, identifier.getApiName());
			ps.setString(3, identifier.getVersion());
			resultSet = ps.executeQuery();
			if (resultSet.next()) {
				apiId = resultSet.getInt("APP_ID");
			}
			resultSet.close();
			ps.close();

			if (apiId == -1) {
				throw new AppManagementException("Unable to get the WebApp ID for: " + identifier);
			}

			// This query to updates the APM_SUBSCRIPTION table
            String sqlQuery =
                    "DELETE FROM APM_SUBSCRIPTION WHERE APP_ID = ? AND APPLICATION_ID = ?";

			ps = conn.prepareStatement(sqlQuery);
			ps.setInt(1, apiId);
			ps.setInt(2, applicationId);
			ps.executeUpdate();

			// finally commit transaction
			conn.commit();

		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the add subscription ", e);
				}
			}
			handleException("Failed to add subscriber data ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
	}

	/**
	 * Moves subscriptions of one app to another app
	 *
	 * @param fromIdentifier subscriptions of this app
	 * @param toIdentifier   will be moved into this app
	 * @return number of subscriptions moved
	 * @throws AppManagementException
	 */
	public int moveSubscriptions(APIIdentifier fromIdentifier, APIIdentifier toIdentifier)
			throws AppManagementException {

		Connection conn = null;
		ResultSet results = null;
		PreparedStatement ps = null;
		int fromAppId = -1;
		int toAppId = -1;
		int count;

		try {

            String getAppIdQuery = "SELECT APP_ID FROM APM_APP " +
                    "WHERE APP_PROVIDER = ? AND APP_NAME = ? AND APP_VERSION = ?";

			try {
				conn = APIMgtDBUtil.getConnection();
				conn.setAutoCommit(false);
				ps = conn.prepareStatement(getAppIdQuery);
			} catch (SQLException e) {
				handleException("Failed to create database connection ", e);
			}

			try {
				ps.setString(1, fromIdentifier.getProviderName());
				ps.setString(2, fromIdentifier.getApiName());
				ps.setString(3, fromIdentifier.getVersion());
				results = ps.executeQuery();
				if (results.next()) {
					fromAppId = results.getInt(1);
				}
			} catch (SQLException e) {
				APIMgtDBUtil.closeAllConnections(ps, conn, results);
				String msg = "Could not retrieve app ID of " + fromIdentifier.getProviderName() +
						"-" + fromIdentifier.getApiName() + "-" + fromIdentifier.getVersion();
				handleException(msg, e);
			}
			APIMgtDBUtil.closeAllConnections(null, null, results);
			if (fromAppId == -1) {
				if (log.isDebugEnabled()) {
					log.debug("Could not find app ID of 'from' app " + fromIdentifier);
				}
				return -1;
			}

			try {
				ps.setString(1, toIdentifier.getProviderName());
				ps.setString(2, toIdentifier.getApiName());
				ps.setString(3, toIdentifier.getVersion());
				results = ps.executeQuery();
				if (results.next()) {
					toAppId = results.getInt(1);
				}
			} catch (SQLException e) {
				APIMgtDBUtil.closeAllConnections(ps, conn, results);
				String msg = "Could not retrieve app ID of " + toIdentifier.getProviderName() + "-"
						+ toIdentifier.getApiName() + "-" + toIdentifier.getVersion();
				handleException(msg, e);
			}
			APIMgtDBUtil.closeAllConnections(null, null, results);
			if (toAppId == -1) {
				if (log.isDebugEnabled()) {
					log.debug("Could not find app ID of 'to' app " + toIdentifier);
				}
				return -1;
			}

            //String moveQuery = "UPDATE APM_SUBSCRIPTION SET APP_ID = ? WHERE APP_ID = ?";

            String moveQuery = "INSERT INTO APM_SUBSCRIPTION(SUBSCRIPTION_TYPE, TIER_ID, APP_ID, APPLICATION_ID, " +
                    "SUB_STATUS, TRUSTED_IDP, SUBSCRIPTION_TIME ) " +
                    "SELECT SUBSCRIPTION_TYPE, TIER_ID , ? ,APPLICATION_ID ,SUB_STATUS , TRUSTED_IDP ,? " +
                    "FROM APM_SUBSCRIPTION WHERE APP_ID = ?";

            count = 0;
            try {
                ps = conn.prepareStatement(moveQuery);
                ps.setInt(1, toAppId);
                ps.setTimestamp(2, new Timestamp(new java.util.Date().getTime()));
                ps.setInt(3, fromAppId);
                count = ps.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                String msg = "Could not move subscriptions from " + fromIdentifier.getProviderName() + "-" +
                        fromIdentifier.getApiName() + "-" + fromIdentifier.getVersion() + " app to " +
                        toIdentifier.getProviderName() + "-" + toIdentifier.getApiName() + "-" +
                        toIdentifier.getVersion() + " app";
                handleException(msg, e);
            }

		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, results);
		}

		if (log.isDebugEnabled()) {
			log.debug(count + " subscribers were moved from" + fromIdentifier + " to " +
							  toIdentifier);
		}
		return count;

	}

    public void removeAPISubscription(APIIdentifier identifier) throws AppManagementException {
        Connection conn = null;
        ResultSet resultSet = null;
        PreparedStatement ps = null;

        int subscriptionId = -1;
        int apiId = -1;
        int appID = -1;

        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            String getApiQuery =
                    "SELECT APP_ID FROM APM_APP API WHERE APP_PROVIDER = ? AND APP_NAME = ? AND " +
                            "APP_VERSION = ?";
            ps = conn.prepareStatement(getApiQuery);
            ps.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            ps.setString(2, identifier.getApiName());
            ps.setString(3, identifier.getVersion());
            resultSet = ps.executeQuery();
            if (resultSet.next()) {
                apiId = resultSet.getInt("APP_ID");
            }

            resultSet.close();
            ps.close();

            if (apiId == -1) {
                throw new AppManagementException("Unable to get the WebApp ID for: " + identifier);
            }

            // This query to updates the APM_SUBSCRIPTION table
            String sqlQuery = "DELETE FROM APM_SUBSCRIPTION WHERE APP_ID = ?";

            ps = conn.prepareStatement(sqlQuery);
            ps.setInt(1, apiId);
            ps.executeUpdate();

            // finally commit transaction
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback the add subscription ", e);
                }
            }
            handleException("Failed to add subscriber data ", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
        }
    }


    public void removeAPISubscription(APIIdentifier identifier, String userID,String applicationName)
            throws AppManagementException {
        Connection conn = null;
        ResultSet resultSet = null;
        PreparedStatement ps = null;
        int subscriptionId = -1;
        int apiId = -1;
        int appID = -1;

        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            String getApiQuery =
                    "SELECT APP_ID FROM APM_APP API WHERE APP_PROVIDER = ? AND "
                            + "APP_NAME = ? AND APP_VERSION = ?";
            ps = conn.prepareStatement(getApiQuery);
            ps.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            ps.setString(2, identifier.getApiName());
            ps.setString(3, identifier.getVersion());
            resultSet = ps.executeQuery();
            if (resultSet.next()) {
                apiId = resultSet.getInt("APP_ID");
            }
            resultSet.close();
            ps.close();

            if (apiId == -1) {
                throw new AppManagementException("Unable to get the WebApp ID for: " + identifier);
            }

            String applicationIdQuery =
                    "SELECT APP.APPLICATION_ID AS APPID FROM APM_APPLICATION APP, APM_SUBSCRIBER " +
                            " SUBR  WHERE APP.NAME= ?  AND APP.SUBSCRIBER_ID  =SUBR.SUBSCRIBER_ID" +
                            " AND SUBR.USER_ID  = ?";

            ps = conn.prepareStatement(applicationIdQuery);
            ps.setString(1,applicationName);
            ps.setString(2,userID);
            resultSet = ps.executeQuery();
            if (resultSet.next()) {
                appID = resultSet.getInt("APPID");
            }

            if (appID == -1) {
                throw new AppManagementException("Unable to get the WebApp ID for: " + identifier);
            }

            // This query to updates the APM_SUBSCRIPTION table
            String sqlQuery = "DELETE FROM APM_SUBSCRIPTION WHERE APP_ID = ? AND APPLICATION_ID = ? AND SUBSCRIPTION_TYPE='INDIVIDUAL'";

            ps = conn.prepareStatement(sqlQuery);
            ps.setInt(1, apiId);
            ps.setInt(2, appID);
            ps.executeUpdate();

            // finally commit transaction
            conn.commit();

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback the add subscription ", e);
                }
            }
            handleException("Failed to add subscriber data ", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
        }
    }


	public void removeSubscriptionById(int subscription_id) throws AppManagementException {
		Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;

		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
			// Remove entry from APM_SUBSCRIPTION table
			String sqlQuery = "DELETE FROM APM_SUBSCRIPTION WHERE SUBSCRIPTION_ID = ?";

			ps = conn.prepareStatement(sqlQuery);
			ps.setInt(1, subscription_id);
			ps.executeUpdate();

			// Commit transaction
			conn.commit();
		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback remove subscription ", e);
				}
			}
			handleException("Failed to remove subscription data ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
	}

	public String getSubscriptionStatusById(int subscriptionId) throws AppManagementException {

		Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;
		String subscriptionStatus = null;

		try {
			conn = APIMgtDBUtil.getConnection();
			String getApiQuery = "SELECT SUB_STATUS FROM APM_SUBSCRIPTION WHERE SUBSCRIPTION_ID = ?";
			ps = conn.prepareStatement(getApiQuery);
			ps.setInt(1, subscriptionId);
			resultSet = ps.executeQuery();
			if (resultSet.next()) {
				subscriptionStatus = resultSet.getString("SUB_STATUS");
			}
			return subscriptionStatus;
		} catch (SQLException e) {
			handleException("Failed to retrieve subscription status", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
		return null;
	}

	/**
	 * This method used tot get Subscriber from subscriberId.
	 *
	 * @param subscriberName
	 *            id
	 * @return Subscriber
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to get Subscriber from subscriber id
	 */
	public Subscriber getSubscriber(String subscriberName) throws AppManagementException {

		Connection conn = null;
		Subscriber subscriber = null;
		PreparedStatement ps = null;
		ResultSet result = null;

		int tenantId;
		try {
			tenantId = IdentityTenantUtil.getTenantIdOfUser(subscriberName);
		} catch (IdentityRuntimeException e) {
			String msg = "Failed to get tenant id of user : " + subscriberName;
			log.error(msg, e);
			throw new AppManagementException(msg, e);
		}

        String sqlQuery =
                "SELECT SUBSCRIBER_ID, USER_ID, TENANT_ID, EMAIL_ADDRESS, DATE_SUBSCRIBED FROM " +
                        "APM_SUBSCRIBER WHERE USER_ID = ? AND TENANT_ID = ?";
        try {
			conn = APIMgtDBUtil.getConnection();

			ps = conn.prepareStatement(sqlQuery);
			ps.setString(1, subscriberName);
			ps.setInt(2, tenantId);
			result = ps.executeQuery();

			if (result.next()) {
				subscriber =
						new Subscriber(
								result.getString(AppMConstants.SUBSCRIBER_FIELD_EMAIL_ADDRESS));
				subscriber.setEmail(result.getString("EMAIL_ADDRESS"));
				subscriber.setId(result.getInt("SUBSCRIBER_ID"));
				subscriber.setName(subscriberName);
				subscriber.setSubscribedDate(result.getDate(AppMConstants.SUBSCRIBER_FIELD_DATE_SUBSCRIBED));
				subscriber.setTenantId(result.getInt("TENANT_ID"));
			}

		} catch (SQLException e) {
			handleException("Failed to get Subscriber for :" + subscriberName, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, result);
		}
		return subscriber;
	}

	/**
	 * This method returns the set of APIs for given subscriber, subscribed
	 * under the specified application.
	 *
	 * @param subscriber
	 *            subscriber
	 * @param applicationName
	 *            Application Name
	 * @return Set<WebApp>
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to get SubscribedAPIs
	 */
	public Set<SubscribedAPI> getSubscribedAPIs(Subscriber subscriber, String applicationName)
	                                                                                          throws
                                                                                              AppManagementException {
		Set<SubscribedAPI> subscribedAPIs = new LinkedHashSet<SubscribedAPI>();
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet result = null;

		try {
			connection = APIMgtDBUtil.getConnection();

            String sqlQuery = "SELECT SUBS.SUBSCRIPTION_ID "
                    + ",API.APP_PROVIDER AS APP_PROVIDER "
                    + ",API.APP_NAME AS APP_NAME "
                    + ",API.APP_VERSION AS APP_VERSION "
                    + ",SUBS.TIER_ID AS TIER_ID "
                    + ",APP.APPLICATION_ID AS APP_ID "
                    + ",SUBS.LAST_ACCESSED AS LAST_ACCESSED "
                    + ",SUBS.SUB_STATUS AS SUB_STATUS "
                    + ",APP.NAME AS APP_NAME "
                    + ",APP.CALLBACK_URL AS CALLBACK_URL "
                    + "FROM APM_SUBSCRIBER SUB, APM_APPLICATION APP, "
                    + "APM_SUBSCRIPTION SUBS, APM_APP API "
                    + "WHERE SUB.USER_ID = ? "
                    + "AND SUB.TENANT_ID = ? "
                    + "AND SUB.SUBSCRIBER_ID = APP.SUBSCRIBER_ID "
                    + "AND APP.APPLICATION_ID = SUBS.APPLICATION_ID "
                    + "AND API.APP_ID = SUBS.APP_ID AND APP.NAME = ? ";

			ps = connection.prepareStatement(sqlQuery);
			ps.setString(1, subscriber.getName());
			int tenantId = IdentityTenantUtil.getTenantIdOfUser(subscriber.getName());
			ps.setInt(2, tenantId);
			ps.setString(3, applicationName);
			result = ps.executeQuery();

			if (result == null) {
				return subscribedAPIs;
			}

            while (result.next()) {
            	// (??????)
            	/*
                APIIdentifier apiIdentifier =
                        new APIIdentifier(
                                AppManagerUtil.replaceEmailDomain(result.getString("APP_PROVIDER")),
                                result.getString("APP_NAME"),
                                result.getString("APP_VERSION"));
                                */
            	APIIdentifier apiIdentifier =
                        new APIIdentifier(
                                AppManagerUtil.replaceEmailDomainBack(result.getString("APP_PROVIDER")),
                                result.getString("APP_NAME"),
                                result.getString("APP_VERSION"));

				SubscribedAPI subscribedAPI = new SubscribedAPI(subscriber, apiIdentifier);
				subscribedAPI.setSubStatus(result.getString("SUB_STATUS"));
				subscribedAPI.setTier(new Tier(
				                               result.getString(AppMConstants.SUBSCRIPTION_FIELD_TIER_ID)));
				subscribedAPI.setLastAccessed(result.getDate(AppMConstants.SUBSCRIPTION_FIELD_LAST_ACCESS));

				Application application = new Application(result.getString("APP_NAME"), subscriber);
				subscribedAPI.setApplication(application);
				subscribedAPIs.add(subscribedAPI);
			}

		} catch (SQLException e) {
			handleException("Failed to get SubscribedAPI of :" + subscriber.getName(), e);
		} catch (IdentityRuntimeException e) {
			handleException("Failed get tenant id of user " + subscriber.getName(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, result);
		}
		return subscribedAPIs;
	}


     public String getAPISubscibedStatus() throws AppManagementException, IdentityException {
         Connection connection = null;
         PreparedStatement ps = null;
         ResultSet result = null;

         String apiNames = "";


         try {
             connection = APIMgtDBUtil.getConnection();

             String sqlQuery = "SELECT API.APP_NAME AS APP_NAME "
                     + "FROM APM_SUBSCRIPTION SUBS, APM_APP API "
                     + "WHERE SUBS.APP_ID = API.APP_ID ";

             ps = connection.prepareStatement(sqlQuery);
             result = ps.executeQuery();

             while (result.next()) {
                    //apiNames =  apiNames +  result.getString("APP_NAME");
                 apiNames = apiNames + AppManagerUtil.decryptToken(result.getString("APP_NAME"));
             }
         } catch (SQLException e) {
             handleException("Failed to get SubscribedAPI of :" , e);
         } catch (CryptoException e) {
             e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
         } finally {
             APIMgtDBUtil.closeAllConnections(ps, connection, result);
         }

         return  apiNames;
     }


	/**
	 * This method returns the set of APIs for given subscriber
	 *
	 * @param subscriber
	 *            subscriber
	 * @return Set<API>
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to get SubscribedAPIs
	 */
	public Set<SubscribedAPI> getSubscribedAPIs(Subscriber subscriber)
	                                                                  throws
                                                                      AppManagementException {
		Set<SubscribedAPI> subscribedAPIs = new LinkedHashSet<SubscribedAPI>();
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet result = null;

		// identify subscribeduser used email/ordinalusername
		String subscribedUserName = getLoginUserName(subscriber.getName());
		subscriber.setName(subscribedUserName);

		try {
			connection = APIMgtDBUtil.getConnection();

            String sqlQuery =
                    "SELECT SUBS.SUBSCRIPTION_ID"
                            + "   ,API.APP_PROVIDER AS APP_PROVIDER"
                            + "   ,API.APP_NAME AS APP_NAME"
                            + "   ,API.APP_VERSION AS APP_VERSION"
                            + "   ,SUBS.TIER_ID AS TIER_ID"
                            + "   ,APP.APPLICATION_ID AS APP_ID"
                            + "   ,SUBS.LAST_ACCESSED AS LAST_ACCESSED"
                            + "   ,SUBS.SUB_STATUS AS SUB_STATUS"
                            + "   FROM "
                            + "   APM_SUBSCRIBER SUB," + "   APM_APPLICATION APP, "
                            + "   APM_SUBSCRIPTION SUBS, " + "   APM_APP API "
                            + "   WHERE SUB.USER_ID = ? "
                            + "   AND SUB.TENANT_ID = ? "
                            + "   AND SUB.SUBSCRIBER_ID=APP.SUBSCRIBER_ID "
                            + "   AND APP.APPLICATION_ID=SUBS.APPLICATION_ID "
                            + "   AND API.APP_ID=SUBS.APP_ID";

			ps = connection.prepareStatement(sqlQuery);
			ps.setString(1, subscriber.getName());
			int tenantId = IdentityTenantUtil.getTenantIdOfUser(subscriber.getName());
			ps.setInt(2, tenantId);
			result = ps.executeQuery();

			if (result == null) {
				return subscribedAPIs;
			}

			Map<String, Set<SubscribedAPI>> map = new TreeMap<String, Set<SubscribedAPI>>();
			LRUCache<Integer, Application> applicationCache =
			                                                  new LRUCache<Integer, Application>(
			                                                                                     100);

			while (result.next()) {
				// (??????)
				APIIdentifier apiIdentifier =
				                              new APIIdentifier(
				                                                //AppManagerUtil.replaceEmailDomain(result.getString("APP_PROVIDER")),
				                            		  			AppManagerUtil.replaceEmailDomainBack(result.getString("APP_PROVIDER")),
				                                                result.getString("APP_NAME"),
				                                                result.getString("APP_VERSION")
                                                                );

				SubscribedAPI subscribedAPI = new SubscribedAPI(subscriber, apiIdentifier);
				subscribedAPI.setSubStatus(result.getString("SUB_STATUS"));
				String tierName = result.getString(AppMConstants.SUBSCRIPTION_FIELD_TIER_ID);
				subscribedAPI.setTier(new Tier(tierName));
				subscribedAPI.setLastAccessed(result.getDate(AppMConstants.SUBSCRIPTION_FIELD_LAST_ACCESS));
				// setting NULL for subscriber. If needed, Subscriber object
				// should be constructed &
				// passed in
				int applicationId = result.getInt("APP_ID");
				Application application = applicationCache.get(applicationId);
				if (application == null) {
					application = new Application(result.getString("APP_NAME"), subscriber);
					application.setId(result.getInt("APP_ID"));
					applicationCache.put(applicationId, application);
				}
				subscribedAPI.setApplication(application);

				if (!map.containsKey(application.getName())) {
					map.put(application.getName(),
					        new TreeSet<SubscribedAPI>(new Comparator<SubscribedAPI>() {
						        public int compare(SubscribedAPI o1, SubscribedAPI o2) {
							        int placement =
							                        o1.getApiId().getApiName()
							                          .compareTo(o2.getApiId().getApiName());
							        if (placement == 0) {
								        return new APIVersionComparator().compare(new WebApp(
								                                                             o1.getApiId()),
								                                                  new WebApp(
								                                                             o2.getApiId()));
							        }
							        return placement;
						        }
					        }));
				}
				map.get(application.getName()).add(subscribedAPI);
			}

			for (String application : map.keySet()) {
				Set<SubscribedAPI> apis = map.get(application);
				for (SubscribedAPI api : apis) {
					subscribedAPIs.add(api);
				}
			}

		} catch (SQLException e) {
			handleException("Failed to get SubscribedAPI of :" + subscriber.getName(), e);
		} catch (IdentityRuntimeException e) {
			handleException("Failed get tenant id of user " + subscriber.getName(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, result);
		}
		return subscribedAPIs;
	}
	
	// (??????) 2019.11.20 - ????????? app??? ???????????? ?????? id ??????  
    public List<APIIdentifier> getSubscribedApiIds(Subscriber subscriber, String groupingId)
            throws AppManagementException {
        List<APIIdentifier> apiIds = new ArrayList<APIIdentifier>();
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;
        
        //identify subscribeduser used email/ordinalusername
        String subscribedUserName = getLoginUserName(subscriber.getName());
        subscriber.setName(subscribedUserName);

        String sqlQuery = SQLConstants.GET_SUBSCRIBED_APPS_OF_SUBSCRIBER_SQL;
        //String whereClause = " AND  SUB.USER_ID = ? ";
        String whereClauseCaseInSensitive = " AND  LOWER(SUB.USER_ID) = LOWER(?) ";
        //String whereClauseWithGroupId = " AND (APP.GROUP_ID = ? OR (APP.GROUP_ID = '' AND SUB.USER_ID = ?))";
        String whereClauseWithGroupIdorceCaseInsensitiveComp = " AND (APP.GROUP_ID = ? OR (APP.GROUP_ID = '' " + "AND" +
                                                               " LOWER(SUB.USER_ID) = LOWER(?)))";
        try {
            connection = APIMgtDBUtil.getConnection();

            if (groupingId != null && !"null".equals(groupingId) && !groupingId.isEmpty()) {
            	sqlQuery += whereClauseWithGroupIdorceCaseInsensitiveComp;
            } else {
            	sqlQuery += whereClauseCaseInSensitive;
            }

            ps = connection.prepareStatement(sqlQuery);
            int tenantId = AppManagerUtil.getTenantId(subscriber.getName());
            ps.setInt(1, tenantId);
            if (groupingId != null && !"null".equals(groupingId) && !groupingId.isEmpty()) {
                ps.setString(2, groupingId);
                ps.setString(3, subscriber.getName());
            } else {
                ps.setString(2, subscriber.getName());
            }

            result = ps.executeQuery();

            while (result.next()) {
            	APIIdentifier apiIdentifier = new APIIdentifier(AppManagerUtil.replaceEmailDomainBack(result.getString
                        ("APP_PROVIDER")), result.getString("APP_NAME"), result.getString("APP_VERSION"));
            	apiIds.add(apiIdentifier);
            }
            
        } catch (SQLException e) {
            handleException("Failed to get SubscribedAPI of :" + subscriber.getName(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
        
        return apiIds;
    }

	public String getTokenScope(String consumerKey) throws AppManagementException {
		String tokenScope = null;

		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
				AppManagerUtil.checkUserNameAssertionEnabled()) {
			String[] keyStoreTables = AppManagerUtil.getAvailableKeyStoreTables();
			if (keyStoreTables != null) {
				for (String keyStoreTable : keyStoreTables) {
					tokenScope = getTokenScope(consumerKey, getScopeSql(keyStoreTable));
					if (tokenScope != null) {
						break;
					}
				}
			}
		} else {
			tokenScope = getTokenScope(consumerKey, getScopeSql(null));
		}
		return tokenScope;
	}

	private String getTokenScope(String consumerKey, String getScopeSql)
	                                                                    throws
                                                                        AppManagementException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet result = null;
		String tokenScope = null;

		try {

			consumerKey = AppManagerUtil.encryptToken(consumerKey);
			connection = APIMgtDBUtil.getConnection();
			PreparedStatement nestedPS = connection.prepareStatement(getScopeSql);
			nestedPS.setString(1, consumerKey);
			ResultSet nestedRS = nestedPS.executeQuery();
			if (nestedRS.next()) {
				tokenScope = nestedRS.getString("TOKEN_SCOPE");
			}
		} catch (SQLException e) {
			handleException("Failed to get token scope from consumer key: " + consumerKey, e);
		} catch (CryptoException e) {
			handleException("Error while encrypting consumer key", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, result);
		}

		return tokenScope;
	}

	private String getScopeSql(String accessTokenStoreTable) {
		String tokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (accessTokenStoreTable != null) {
			tokenStoreTable = accessTokenStoreTable;
		}

        return "SELECT IAT.TOKEN_SCOPE AS TOKEN_SCOPE FROM " + tokenStoreTable + " IAT, " +
                "IDN_OAUTH_CONSUMER_APPS ICA " +
                "WHERE IAT.CONSUMER_KEY = ? AND IAT.CONSUMER_KEY = ICA.CONSUMER_KEY " +
                "AND IAT.AUTHZ_USER = ICA.USERNAME";
    }

	public Boolean isAccessTokenExists(String accessToken) throws AppManagementException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet result = null;

		String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
		    AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromAccessToken(accessToken);
		}

		String getTokenSql = SQLConstants.IS_ACCESS_TOKEN_EXISTS_PREFIX + accessTokenStoreTable +
                SQLConstants.IS_ACCESS_TOKEN_EXISTS_SUFFIX;
        Boolean tokenExists = false;
		try {
			connection = APIMgtDBUtil.getConnection();
			PreparedStatement getToken = connection.prepareStatement(getTokenSql);
			String encryptedAccessToken = AppManagerUtil.encryptToken(accessToken);
			getToken.setString(1, encryptedAccessToken);
			ResultSet getTokenRS = getToken.executeQuery();
			while (getTokenRS.next()) {
				tokenExists = true;
			}
		} catch (SQLException e) {
			handleException("Failed to check availability of the access token. ", e);
		} catch (CryptoException e) {
			handleException("Failed to check availability of the access token. ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, result);
		}
		return tokenExists;
	}


	public Boolean isAccessTokenRevoked(String accessToken) throws AppManagementException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet result = null;

		String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
		    AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromAccessToken(accessToken);
		}

		String getTokenSql = SQLConstants.IS_ACCESS_TOKEN_REVOKED_PREFIX + accessTokenStoreTable +
                SQLConstants.IS_ACCESS_TOKE_REVOKED_SUFFIX;
        Boolean tokenExists = false;
		try {
			connection = APIMgtDBUtil.getConnection();
			PreparedStatement getToken = connection.prepareStatement(getTokenSql);
			String encryptedAccessToken = AppManagerUtil.encryptToken(accessToken);
			getToken.setString(1, encryptedAccessToken);
			ResultSet getTokenRS = getToken.executeQuery();
			while (getTokenRS.next()) {
				if (!getTokenRS.getString("TOKEN_STATE").equals("REVOKED")) {
					tokenExists = true;
				}
			}
		} catch (SQLException e) {
			handleException("Failed to check availability of the access token. ", e);
		} catch (CryptoException e) {
			handleException("Failed to check availability of the access token. ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, result);
		}
		return tokenExists;
	}

	/* (??????) 2018.03.21 - ?????????????????? ???????????? ??????????????? ????????? 
	public APIKey getAccessTokenData(String accessToken) throws AppManagementException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet getTokenRS = null;
		APIKey apiKey = new APIKey();

		String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
		    AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromAccessToken(accessToken);
		}

        String getTokenSql =
                "SELECT ACCESS_TOKEN, AUTHZ_USER, TOKEN_SCOPE, CONSUMER_KEY, " +
                        "TIME_CREATED, VALIDITY_PERIOD " +
                        "FROM " + accessTokenStoreTable +
                        " WHERE ACCESS_TOKEN = ? AND TOKEN_STATE = 'ACTIVE' ";
        try {
			connection = APIMgtDBUtil.getConnection();
			PreparedStatement getToken = connection.prepareStatement(getTokenSql);
			getToken.setString(1, AppManagerUtil.encryptToken(accessToken));
			getTokenRS = getToken.executeQuery();
			while (getTokenRS.next()) {

                String decryptedAccessToken =
                        AppManagerUtil.decryptToken(getTokenRS.getString("ACCESS_TOKEN")); // todo
                // -
                // check
                // redundant
                // decryption
                apiKey.setAccessToken(decryptedAccessToken);
				apiKey.setAuthUser(getTokenRS.getString("AUTHZ_USER"));
				apiKey.setTokenScope(getTokenRS.getString("TOKEN_SCOPE"));
				apiKey.setCreatedDate(getTokenRS.getTimestamp("TIME_CREATED").toString()
				                                .split("\\.")[0]);
				String consumerKey = getTokenRS.getString("CONSUMER_KEY");
				apiKey.setConsumerKey(AppManagerUtil.decryptToken(consumerKey));
				apiKey.setValidityPeriod(getTokenRS.getLong("VALIDITY_PERIOD"));

			}
		} catch (SQLException e) {
			handleException("Failed to get the access token data. ", e);
		} catch (CryptoException e) {
			handleException("Failed to get the access token data. ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, getTokenRS);
		}
		return apiKey;
	}
	
	public Map<Integer, APIKey> getAccessTokens(String query) throws AppManagementException {
		Map<Integer, APIKey> tokenDataMap = new HashMap<Integer, APIKey>();
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
		    AppManagerUtil.checkUserNameAssertionEnabled()) {
			String[] keyStoreTables = AppManagerUtil.getAvailableKeyStoreTables();
            if (keyStoreTables != null) {
                for (String keyStoreTable : keyStoreTables) {
                    Map<Integer, APIKey> tokenDataMapTmp =
                            getAccessTokens(query,
                                            getTokenSql(keyStoreTable));
                    tokenDataMap.putAll(tokenDataMapTmp);
                }
            }
        } else {
			tokenDataMap = getAccessTokens(query, getTokenSql(null));
		}
		return tokenDataMap;
	}

	private Map<Integer, APIKey> getAccessTokens(String query, String getTokenSql)
	                                                                              throws
                                                                                  AppManagementException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet getTokenRS = null;
		Map<Integer, APIKey> tokenDataMap = new HashMap<Integer, APIKey>();

		try {
			connection = APIMgtDBUtil.getConnection();
			PreparedStatement getToken = connection.prepareStatement(getTokenSql);
			getTokenRS = getToken.executeQuery();
			while (getTokenRS.next()) {
				String accessToken = AppManagerUtil.decryptToken(getTokenRS.getString("ACCESS_TOKEN"));
				String regex = "(?i)[a-zA-Z0-9_.-|]*" + query.trim() + "(?i)[a-zA-Z0-9_.-|]*";
				Pattern pattern;
				Matcher matcher;
				pattern = Pattern.compile(regex);
				matcher = pattern.matcher(accessToken);
				Integer i = 0;
				if (matcher.matches()) {
					APIKey apiKey = new APIKey();
					apiKey.setAccessToken(accessToken);
					apiKey.setAuthUser(getTokenRS.getString("AUTHZ_USER"));
					apiKey.setTokenScope(getTokenRS.getString("TOKEN_SCOPE"));
					apiKey.setCreatedDate(getTokenRS.getTimestamp("TIME_CREATED").toString()
					                                .split("\\.")[0]);
					String consumerKey = getTokenRS.getString("CONSUMER_KEY");
					apiKey.setConsumerKey(AppManagerUtil.decryptToken(consumerKey));
					apiKey.setValidityPeriod(getTokenRS.getLong("VALIDITY_PERIOD"));
					tokenDataMap.put(i, apiKey);
					i++;
				}
			}
		} catch (SQLException e) {
			handleException("Failed to get access token data. ", e);
		} catch (CryptoException e) {
			handleException("Failed to get access token data. ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, getTokenRS);

		}
		return tokenDataMap;
	}
	*/
	
	public APIKey getAccessTokenData(String accessToken) throws AppManagementException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;
        APIKey apiKey = new APIKey();

        String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
		    AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromAccessToken(accessToken);
		}

        String getTokenSql = SQLConstants.GET_ACCESS_TOKEN_DATA_PREFIX + accessTokenStoreTable + SQLConstants
                .GET_ACCESS_TOKEN_DATA_SUFFIX;
        try {
        	connection = APIMgtDBUtil.getConnection();
            ps = connection.prepareStatement(getTokenSql);
            ps.setString(1, AppManagerUtil.encryptToken(accessToken));
            result = ps.executeQuery();
            if (result.next()) {
                String decryptedAccessToken = AppManagerUtil.decryptToken(result.getString("ACCESS_TOKEN")); // todo - check

                String endUserName = result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_AUTHORIZED_USER);
                String domainName = result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_USER_DOMAIN);
                String endUsernameWithDomain = UserCoreUtil.addDomainToName(endUserName, domainName);
                apiKey.setAuthUser(endUsernameWithDomain);

                apiKey.setAccessToken(decryptedAccessToken);
                apiKey.setCreatedDate(result.getTimestamp("TIME_CREATED").toString().split("\\.")[0]);
                String consumerKey = result.getString("CONSUMER_KEY");
                apiKey.setConsumerKey(consumerKey);
                apiKey.setValidityPeriod(result.getLong("VALIDITY_PERIOD"));
                List<String> scopes = new ArrayList<String>();
                do {
                    scopes.add(result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_TOKEN_SCOPE));
                } while (result.next());
                apiKey.setTokenScope(getScopeString(scopes));
            }
        } catch (SQLException e) {
            handleException("Failed to get the access token data. ", e);
        } catch (CryptoException e) {
            handleException("Failed to get the access token data. ", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
        return apiKey;
    }
	
	public Map<Integer, APIKey> getAccessTokens(String query) throws AppManagementException {
        Map<Integer, APIKey> tokenDataMap = new HashMap<Integer, APIKey>();
        if (AppManagerUtil.checkAccessTokenPartitioningEnabled() && AppManagerUtil.checkUserNameAssertionEnabled()) {
            String[] keyStoreTables = AppManagerUtil.getAvailableKeyStoreTables();
            if (keyStoreTables != null) {
                for (String keyStoreTable : keyStoreTables) {
                    Map<Integer, APIKey> tokenDataMapTmp = getAccessTokens(query, getTokenSql(keyStoreTable));
                    tokenDataMap.putAll(tokenDataMapTmp);
                }
            }
        } else {
            tokenDataMap = getAccessTokens(query, getTokenSql(null));
        }
        return tokenDataMap;
    }

    private Map<Integer, APIKey> getAccessTokens(String query, String getTokenSql) throws AppManagementException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;
        Map<Integer, APIKey> tokenDataMap = new HashMap<Integer, APIKey>();

        try {
            connection = APIMgtDBUtil.getConnection();
            ps = connection.prepareStatement(getTokenSql);
            result = ps.executeQuery();
            boolean accessTokenRowBreaker = false;

            Integer i = 0;
            while (accessTokenRowBreaker || result.next()) {
                accessTokenRowBreaker = false;
                String accessToken = AppManagerUtil.decryptToken(result.getString("ACCESS_TOKEN"));
                String regex = "(?i)[a-zA-Z0-9_.-|]*" + query.trim() + "(?i)[a-zA-Z0-9_.-|]*";
                Pattern pattern;
                Matcher matcher;
                pattern = Pattern.compile(regex);
                matcher = pattern.matcher(accessToken);
                if (matcher.matches()) {
                    APIKey apiKey = new APIKey();
                    apiKey.setAccessToken(accessToken);

                    String username = result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_AUTHORIZED_USER);
                    String domainName = result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_USER_DOMAIN);
                    String endUsernameWithDomain = UserCoreUtil.addDomainToName(username, domainName);
                    apiKey.setAuthUser(endUsernameWithDomain);

                    apiKey.setCreatedDate(result.getTimestamp("TIME_CREATED").toString().split("\\.")[0]);
                    String consumerKey = result.getString("CONSUMER_KEY");
                    apiKey.setConsumerKey(consumerKey);
                    apiKey.setValidityPeriod(result.getLong("VALIDITY_PERIOD"));
                    // Load all the rows to in memory and build the scope string
                    List<String> scopes = new ArrayList<String>();
                    String tokenString = result.getString("ACCESS_TOKEN");
                    do {
                        String currentRowTokenString = result.getString("ACCESS_TOKEN");
                        if (tokenString.equals(currentRowTokenString)) {
                            scopes.add(result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_TOKEN_SCOPE));
                        } else {
                            accessTokenRowBreaker = true;
                            break;
                        }
                    } while (result.next());
                    apiKey.setTokenScope(getScopeString(scopes));
                    tokenDataMap.put(i, apiKey);
                    i++;
                }
            }
        } catch (SQLException e) {
            handleException("Failed to get access token data. ", e);
        } catch (CryptoException e) {
            handleException("Failed to get access token data. ", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
        return tokenDataMap;
    }

	private String getTokenSql(String accessTokenStoreTable) {
		String tokenStoreTable = "IDN_OAUTH2_ACCESS_TOKEN";
		if (accessTokenStoreTable != null) {
			tokenStoreTable = accessTokenStoreTable;
		}

        return "SELECT ACCESS_TOKEN,AUTHZ_USER,TOKEN_SCOPE,CONSUMER_KEY," +
                "TIME_CREATED,VALIDITY_PERIOD FROM " + tokenStoreTable +
                " WHERE TOKEN_STATE='ACTIVE' ";
    }
	
	private String getScopeString(List<String> scopes) {
        return StringUtils.join(scopes, " ");
    }

	/* (??????) 2018.03.21
	public Map<Integer, APIKey> getAccessTokensByUser(String user, String loggedInUser)
	                                                                                   throws
                                                                                       AppManagementException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet getTokenRS = null;
		Map<Integer, APIKey> tokenDataMap = new HashMap<Integer, APIKey>();

		String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
				AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromUserId(user);
		}

        String getTokenSql = "SELECT ACCESS_TOKEN, AUTHZ_USER, TOKEN_SCOPE, CONSUMER_KEY, " +
                "TIME_CREATED, VALIDITY_PERIOD " +
                "FROM " + accessTokenStoreTable +
                " WHERE AUTHZ_USER = ? AND TOKEN_STATE = 'ACTIVE' ";
        try {
			connection = APIMgtDBUtil.getConnection();
			PreparedStatement getToken = connection.prepareStatement(getTokenSql);
			getToken.setString(1, user);
			getTokenRS = getToken.executeQuery();
			Integer i = 0;
			while (getTokenRS.next()) {
				String authorizedUser = getTokenRS.getString("AUTHZ_USER");
				if (AppManagerUtil.isLoggedInUserAuthorizedToRevokeToken(loggedInUser, authorizedUser)) {
					String accessToken = AppManagerUtil.decryptToken(getTokenRS.getString("ACCESS_TOKEN"));
					APIKey apiKey = new APIKey();
					apiKey.setAccessToken(accessToken);
					apiKey.setAuthUser(authorizedUser);
					apiKey.setTokenScope(getTokenRS.getString("TOKEN_SCOPE"));
					apiKey.setCreatedDate(getTokenRS.getTimestamp("TIME_CREATED").toString()
							.split("\\.")[0]);
					String consumerKey = getTokenRS.getString("CONSUMER_KEY");
					apiKey.setConsumerKey(AppManagerUtil.decryptToken(consumerKey));
					apiKey.setValidityPeriod(getTokenRS.getLong("VALIDITY_PERIOD"));
					tokenDataMap.put(i, apiKey);
					i++;
				}
			}
		} catch (SQLException e) {
			handleException("Failed to get access token data. ", e);
		} catch (CryptoException e) {
			handleException("Failed to get access token data. ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, getTokenRS);
		}
		return tokenDataMap;
	}

	public Map<Integer, APIKey> getAccessTokensByDate(String date, boolean latest,
	                                                  String loggedInUser)
	                                                                      throws
                                                                          AppManagementException {
		Map<Integer, APIKey> tokenDataMap = new HashMap<Integer, APIKey>();

        if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
                AppManagerUtil.checkUserNameAssertionEnabled()) {
            String[] keyStoreTables = AppManagerUtil.getAvailableKeyStoreTables();
            if (keyStoreTables != null) {
                for (String keyStoreTable : keyStoreTables) {
                    Map<Integer, APIKey> tokenDataMapTmp =
                            getAccessTokensByDate(date,
                                                  latest,
                                                  getTokenByDateSqls(keyStoreTable),
                                                  loggedInUser);
                    tokenDataMap.putAll(tokenDataMapTmp);
                }
            }
        } else {
            tokenDataMap =
                    getAccessTokensByDate(date, latest, getTokenByDateSqls(null),
                                          loggedInUser);
        }

		return tokenDataMap;
	}

	public Map<Integer, APIKey> getAccessTokensByDate(String date, boolean latest,
	                                                  String[] querySql, String loggedInUser)
	                                                                                         throws
                                                                                             AppManagementException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet getTokenRS = null;
		Map<Integer, APIKey> tokenDataMap = new HashMap<Integer, APIKey>();

		try {
			SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
			java.util.Date searchDate = fmt.parse(date);
			Date sqlDate = new Date(searchDate.getTime());
			connection = APIMgtDBUtil.getConnection();
			PreparedStatement getToken;
			if (latest) {
				getToken = connection.prepareStatement(querySql[0]);
			} else {
				getToken = connection.prepareStatement(querySql[1]);
			}
			getToken.setDate(1, sqlDate);

			getTokenRS = getToken.executeQuery();
			Integer i = 0;
			while (getTokenRS.next()) {
				String authorizedUser = getTokenRS.getString("AUTHZ_USER");
				if (AppManagerUtil.isLoggedInUserAuthorizedToRevokeToken(loggedInUser, authorizedUser)) {
					String accessToken = AppManagerUtil.decryptToken(getTokenRS.getString("ACCESS_TOKEN"));
					APIKey apiKey = new APIKey();
					apiKey.setAccessToken(accessToken);
					apiKey.setAuthUser(authorizedUser);
					apiKey.setTokenScope(getTokenRS.getString("TOKEN_SCOPE"));
					apiKey.setCreatedDate(getTokenRS.getTimestamp("TIME_CREATED").toString()
					                                .split("\\.")[0]);
					String consumerKey = getTokenRS.getString("CONSUMER_KEY");
					apiKey.setConsumerKey(AppManagerUtil.decryptToken(consumerKey));
					apiKey.setValidityPeriod(getTokenRS.getLong("VALIDITY_PERIOD"));
					tokenDataMap.put(i, apiKey);
					i++;
				}
			}
		} catch (SQLException e) {
			handleException("Failed to get access token data. ", e);
		} catch (ParseException e) {
			handleException("Failed to get access token data. ", e);
		} catch (CryptoException e) {
			handleException("Failed to get access token data. ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, getTokenRS);
		}
		return tokenDataMap;
	}
	*/
	
	public Map<Integer, APIKey> getAccessTokensByUser(String user, String loggedInUser) throws AppManagementException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;
        Map<Integer, APIKey> tokenDataMap = new HashMap<Integer, APIKey>();

        String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
        accessTokenStoreTable = getAccessTokenStoreTableNameOfUserId(user, accessTokenStoreTable);

        String getTokenSql = SQLConstants.GET_ACCESS_TOKEN_BY_USER_PREFIX + accessTokenStoreTable + SQLConstants
                .GET_ACCESS_TOKEN_BY_USER_SUFFIX;
        try {
            connection = APIMgtDBUtil.getConnection();
            ps = connection.prepareStatement(getTokenSql);
            ps.setString(1, user);
            result = ps.executeQuery();
            Integer i = 0;
            boolean accessTokenRowBreaker = false;
            while (accessTokenRowBreaker || result.next()) {
                accessTokenRowBreaker = false;
                String username = result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_AUTHORIZED_USER);
                String domainName = result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_USER_DOMAIN);
                String authorizedUserWithDomain = UserCoreUtil.addDomainToName(username, domainName);

                if (AppManagerUtil.isLoggedInUserAuthorizedToRevokeToken(loggedInUser, authorizedUserWithDomain)) {
                    String accessToken = AppManagerUtil.decryptToken(result.getString("ACCESS_TOKEN"));
                    APIKey apiKey = new APIKey();
                    apiKey.setAccessToken(accessToken);
                    apiKey.setAuthUser(authorizedUserWithDomain);
                    apiKey.setCreatedDate(result.getTimestamp("TIME_CREATED").toString().split("\\.")[0]);
                    String consumerKey = result.getString("CONSUMER_KEY");
                    apiKey.setConsumerKey(consumerKey);
                    apiKey.setValidityPeriod(result.getLong("VALIDITY_PERIOD"));
                    // Load all the rows to in memory and build the scope string
                    List<String> scopes = new ArrayList<String>();
                    String tokenString = result.getString("ACCESS_TOKEN");
                    do {
                        String currentRowTokenString = result.getString("ACCESS_TOKEN");
                        if (tokenString.equals(currentRowTokenString)) {
                            scopes.add(result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_TOKEN_SCOPE));
                        } else {
                            accessTokenRowBreaker = true;
                            break;
                        }
                    } while (result.next());
                    apiKey.setTokenScope(getScopeString(scopes));
                    tokenDataMap.put(i, apiKey);
                    i++;
                }
            }
        } catch (SQLException e) {
            handleException("Failed to get access token data. ", e);
        } catch (CryptoException e) {
            handleException("Failed to get access token data. ", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
        return tokenDataMap;
    }

    public Map<Integer, APIKey> getAccessTokensByDate(String date, boolean latest, String loggedInUser)
            throws AppManagementException {
        Map<Integer, APIKey> tokenDataMap = new HashMap<Integer, APIKey>();

        if (AppManagerUtil.checkAccessTokenPartitioningEnabled() && AppManagerUtil.checkUserNameAssertionEnabled()) {
            String[] keyStoreTables = AppManagerUtil.getAvailableKeyStoreTables();
            if (keyStoreTables != null) {
                for (String keyStoreTable : keyStoreTables) {
                    Map<Integer, APIKey> tokenDataMapTmp = getAccessTokensByDate(date, latest, getTokenByDateSqls
                            (keyStoreTable), loggedInUser);
                    tokenDataMap.putAll(tokenDataMapTmp);
                }
            }
        } else {
            tokenDataMap = getAccessTokensByDate(date, latest, getTokenByDateSqls(null), loggedInUser);
        }
        return tokenDataMap;
    }

    public Map<Integer, APIKey> getAccessTokensByDate(String date, boolean latest, String[] querySql,
                                                      String loggedInUser) throws AppManagementException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;
        Map<Integer, APIKey> tokenDataMap = new HashMap<Integer, APIKey>();

        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            java.util.Date searchDate = fmt.parse(date);
            Date sqlDate = new Date(searchDate.getTime());
            connection = APIMgtDBUtil.getConnection();
            if (latest) {
                ps = connection.prepareStatement(querySql[0]);
            } else {
                ps = connection.prepareStatement(querySql[1]);
            }
            ps.setDate(1, sqlDate);

            result = ps.executeQuery();
            Integer i = 0;
            boolean accessTokenRowBreaker = false;
            while (accessTokenRowBreaker || result.next()) {
                accessTokenRowBreaker = true;

                String username = result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_AUTHORIZED_USER);
                String domainName = result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_USER_DOMAIN);
                String authorizedUserWithDomain = UserCoreUtil.addDomainToName(username, domainName);

                if (AppManagerUtil.isLoggedInUserAuthorizedToRevokeToken(loggedInUser, authorizedUserWithDomain)) {
                    String accessToken = AppManagerUtil.decryptToken(result.getString("ACCESS_TOKEN"));
                    APIKey apiKey = new APIKey();
                    apiKey.setAccessToken(accessToken);
                    apiKey.setAuthUser(authorizedUserWithDomain);
                    apiKey.setCreatedDate(result.getTimestamp("TIME_CREATED").toString().split("\\.")[0]);
                    String consumerKey = result.getString("CONSUMER_KEY");
                    apiKey.setConsumerKey(consumerKey);
                    apiKey.setValidityPeriod(result.getLong("VALIDITY_PERIOD"));
                    // Load all the rows to in memory and build the scope string
                    List<String> scopes = new ArrayList<String>();
                    String tokenString = result.getString("ACCESS_TOKEN");
                    do {
                        String currentRowTokenString = result.getString("ACCESS_TOKEN");
                        if (tokenString.equals(currentRowTokenString)) {
                            scopes.add(result.getString(AppMConstants.IDENTITY_OAUTH2_FIELD_TOKEN_SCOPE));
                        } else {
                            accessTokenRowBreaker = true;
                            break;
                        }
                    } while (result.next());
                    apiKey.setTokenScope(getScopeString(scopes));
                    tokenDataMap.put(i, apiKey);
                    i++;
                }
            }
        } catch (SQLException e) {
            handleException("Failed to get access token data. ", e);
        } catch (ParseException e) {
            handleException("Failed to get access token data. ", e);
        } catch (CryptoException e) {
            handleException("Failed to get access token data. ", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
        return tokenDataMap;
    }

    
    private String getAccessTokenStoreTableNameOfUserId(String userId, String accessTokenStoreTable)
            throws AppManagementException {
        if (AppManagerUtil.checkAccessTokenPartitioningEnabled() && AppManagerUtil.checkUserNameAssertionEnabled()) {
            return AppManagerUtil.getAccessTokenStoreTableFromUserId(userId);
        }
        return accessTokenStoreTable;
    }

	public String[] getTokenByDateSqls(String accessTokenStoreTable) {
		String[] querySqlArr = new String[2];
		String tokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (accessTokenStoreTable != null) {
			tokenStoreTable = accessTokenStoreTable;
		}

        querySqlArr[0] = "SELECT ACCESS_TOKEN, AUTHZ_USER, TOKEN_SCOPE, CONSUMER_KEY, " +
                "TIME_CREATED, VALIDITY_PERIOD " +
                "FROM " + tokenStoreTable +
                " WHERE TOKEN_STATE ='ACTIVE' AND TIME_CREATED >= ? ";

        querySqlArr[1] = "SELECT ACCESS_TOKEN, AUTHZ_USER, TOKEN_SCOPE, CONSUMER_KEY," +
                "TIME_CREATED, VALIDITY_PERIOD " +
                "FROM " + tokenStoreTable +
                " WHERE TOKEN_STATE ='ACTIVE' AND TIME_CREATED <= ? ";

		return querySqlArr;
	}

	public void updateTierPermissions(String tierName, String permissionType, String roles,
	                                  int tenantId) throws AppManagementException {
		Connection conn = null;
		ResultSet rs = null;
		PreparedStatement ps = null;
		ResultSet resultSet = null;
		int tierPermissionId = -1;

		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
            String getTierPermissionQuery =
                    "SELECT TIER_PERMISSIONS_ID FROM APM_TIER_PERMISSIONS WHERE TIER = ? AND " +
                            "TENANT_ID = ?";

            ps = conn.prepareStatement(getTierPermissionQuery);
			ps.setString(1, tierName);
			ps.setInt(2, tenantId);
			resultSet = ps.executeQuery();
			if (resultSet.next()) {
				tierPermissionId = resultSet.getInt("TIER_PERMISSIONS_ID");
			}
			resultSet.close();
			ps.close();

			if (tierPermissionId == -1) {
                String query =
                        "INSERT INTO APM_TIER_PERMISSIONS (TIER, PERMISSIONS_TYPE, ROLES, " +
                                "TENANT_ID) " +
                                "VALUES(?, ?, ?, ?)";
                ps = conn.prepareStatement(query);
				ps.setString(1, tierName);
				ps.setString(2, permissionType);
				ps.setString(3, roles);
				ps.setInt(4, tenantId);
				ps.execute();
			} else {
                String query =
                        "UPDATE APM_TIER_PERMISSIONS SET TIER = ?, PERMISSIONS_TYPE = ?, ROLES = ? "
                                + "WHERE TIER_PERMISSIONS_ID = ? AND TENANT_ID = ?";
                ps = conn.prepareStatement(query);
				ps.setString(1, tierName);
				ps.setString(2, permissionType);
				ps.setString(3, roles);
				ps.setInt(4, tierPermissionId);
				ps.setInt(5, tenantId);
				ps.executeUpdate();
			}
			conn.commit();

		} catch (SQLException e) {
			handleException("Error in updating tier permissions: " + e.getMessage(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
	}

	public Set<TierPermissionDTO> getTierPermissions(int tenantId) throws AppManagementException {
		Connection conn = null;
		ResultSet rs = null;
		PreparedStatement ps = null;
		ResultSet resultSet = null;

		Set<TierPermissionDTO> tierPermissions = new HashSet<TierPermissionDTO>();

		try {
			conn = APIMgtDBUtil.getConnection();
            String getTierPermissionQuery =
                    "SELECT TIER, PERMISSIONS_TYPE, ROLES FROM APM_TIER_PERMISSIONS " +
                            "WHERE TENANT_ID = ?";
            ps = conn.prepareStatement(getTierPermissionQuery);
			ps.setInt(1, tenantId);
			resultSet = ps.executeQuery();
			while (resultSet.next()) {
				TierPermissionDTO tierPermission = new TierPermissionDTO();
				tierPermission.setTierName(resultSet.getString("TIER"));
				tierPermission.setPermissionType(resultSet.getString("PERMISSIONS_TYPE"));
				String roles = resultSet.getString("ROLES");
				if (roles != null && !roles.equals("")) {
					String roleList[] = roles.split(",");
					tierPermission.setRoles(roleList);
				}
				tierPermissions.add(tierPermission);
			}
			resultSet.close();
			ps.close();
		} catch (SQLException e) {
			handleException("Failed to get Tier permission information ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
		return tierPermissions;
	}

	public TierPermissionDTO getTierPermission(String tierName, int tenantId)
	                                                                         throws
                                                                             AppManagementException {
		Connection conn = null;
		ResultSet rs = null;
		PreparedStatement ps = null;
		ResultSet resultSet = null;

		TierPermissionDTO tierPermission = null;
		try {
			conn = APIMgtDBUtil.getConnection();
			String getTierPermissionQuery =
                    "SELECT PERMISSIONS_TYPE, ROLES  FROM APM_TIER_PERMISSIONS"
                            + " WHERE TIER = ? AND TENANT_ID = ?";
            ps = conn.prepareStatement(getTierPermissionQuery);
			ps.setString(1, tierName);
			ps.setInt(2, tenantId);
			resultSet = ps.executeQuery();
			while (resultSet.next()) {
				tierPermission = new TierPermissionDTO();
				tierPermission.setTierName(tierName);
				tierPermission.setPermissionType(resultSet.getString("PERMISSIONS_TYPE"));
				String roles = resultSet.getString("ROLES");
				if (roles != null) {
					String roleList[] = roles.split(",");
					tierPermission.setRoles(roleList);
				}
			}
			resultSet.close();
			ps.close();
		} catch (SQLException e) {
			handleException("Failed to get Tier permission information for Tier " + tierName, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
		return tierPermission;
	}

	/**
	 * This method returns the set of Subscribers for given provider
	 *
	 * @param providerName
	 *            name of the provider
	 * @return Set<Subscriber>
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to get subscribers for given provider
	 */
	public Set<Subscriber> getSubscribersOfProvider(String providerName)
	                                                                    throws
                                                                        AppManagementException {

		Set<Subscriber> subscribers = new HashSet<Subscriber>();
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet result = null;

		try {
			connection = APIMgtDBUtil.getConnection();

            String sqlQuery =
                    "SELECT SUBS.USER_ID AS USER_ID, "
                            + "SUBS.EMAIL_ADDRESS AS EMAIL_ADDRESS, "
                            + "SUBS.DATE_SUBSCRIBED AS DATE_SUBSCRIBED " +
                            "FROM APM_SUBSCRIBER SUBS, APM_APPLICATION APP, "
                            + "APM_SUBSCRIPTION SUB, APM_APP API "
                            + "WHERE SUB.APPLICATION_ID = APP.APPLICATION_ID "
                            + "AND SUBS. SUBSCRIBER_ID = APP.SUBSCRIBER_ID "
                            + "AND API.APP_ID = SUB.APP_ID "
                            + "AND API.APP_PROVIDER = ?";

			ps = connection.prepareStatement(sqlQuery);
			ps.setString(1, AppManagerUtil.replaceEmailDomainBack(providerName));
			result = ps.executeQuery();

            while (result.next()) {
                // Subscription table should have APP_VERSION AND APP_PROVIDER
                Subscriber subscriber =
                        new Subscriber(
                                result.getString(AppMConstants.SUBSCRIBER_FIELD_EMAIL_ADDRESS));
                subscriber.setName(result.getString(AppMConstants.SUBSCRIBER_FIELD_USER_ID));
                subscriber.setSubscribedDate(result.getDate(
                        AppMConstants.SUBSCRIBER_FIELD_DATE_SUBSCRIBED));
                subscribers.add(subscriber);
            }

		} catch (SQLException e) {
			handleException("Failed to subscribers for :" + providerName, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, result);
		}
		return subscribers;
    }

    public Set<Subscriber> getSubscribersOfAPI(APIIdentifier identifier)
	                                                                    throws
                                                                        AppManagementException {
        Set<Subscriber> subscribers = new HashSet<Subscriber>();
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;

        try {
            connection = APIMgtDBUtil.getConnection();
            String sqlQuery = "SELECT DISTINCT "
                              + "SB.USER_ID, SB.TENANT_ID, SB.EMAIL_ADDRESS, SB.DATE_SUBSCRIBED "
                              + "FROM APM_SUBSCRIBER SB, APM_SUBSCRIPTION SP, "
                              + "APM_APPLICATION APP, APM_APP API "
                              + "WHERE API.APP_PROVIDER = ? AND API.APP_NAME = ? "
                              + "AND API.APP_VERSION = ? "
                              + "AND SP.APPLICATION_ID = APP.APPLICATION_ID "
                              + "AND APP.SUBSCRIBER_ID = SB.SUBSCRIBER_ID "
                              + "AND API.APP_ID = SP.APP_ID";
            ps = connection.prepareStatement(sqlQuery);
            ps.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            ps.setString(2, identifier.getApiName());
            ps.setString(3, identifier.getVersion());
            result = ps.executeQuery();
            if (result == null) {
                return subscribers;
            }

            while (result.next()) {
                String username = result.getString(AppMConstants.SUBSCRIBER_FIELD_USER_ID);
                int tenantId = result.getInt(AppMConstants.SUBSCRIBER_FIELD_TENANT_ID);
                String emailAddress = result.getString(AppMConstants.SUBSCRIBER_FIELD_EMAIL_ADDRESS);
                java.util.Date subscribedDate = result.getTimestamp(AppMConstants.SUBSCRIBER_FIELD_DATE_SUBSCRIBED);
                Subscriber subscriber = new Subscriber(username);
                subscriber.setTenantId(tenantId);
                subscriber.setEmail(emailAddress);
                subscriber.setSubscribedDate(subscribedDate);
                subscribers.add(subscriber);
            }
        } catch (SQLException e) {
            handleException("Failed to get subscribers for :" + identifier.getApiName(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
		return subscribers;
    }

    public long getAPISubscriptionCountByAPI(APIIdentifier identifier)
            throws AppManagementException {

        String sqlQuery =
                "SELECT COUNT(SUB.SUBSCRIPTION_ID) AS SUB_ID "
                        + "FROM APM_SUBSCRIPTION SUB, APM_APP API "
                        + "WHERE API.APP_PROVIDER = ? AND API.APP_NAME = ? "
                        + "AND API.APP_VERSION = ? AND API.APP_ID = SUB.APP_ID";
        long subscriptions = 0;

        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;

        try {
            connection = APIMgtDBUtil.getConnection();

            ps = connection.prepareStatement(sqlQuery);
            ps.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            ps.setString(2, identifier.getApiName());
            ps.setString(3, identifier.getVersion());
            result = ps.executeQuery();
            if (result == null) {
                return subscriptions;
            }
            while (result.next()) {
                subscriptions = result.getLong("SUB_ID");
            }
        } catch (SQLException e) {
            handleException("Failed to get subscription count for WebApp", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
        return subscriptions;
    }


    /**
     * This method returns the subscription count of apps for given period.
     *
     * @param providerName     web apps
     * @param fromDate         From Date
     * @param toDate           To Date
     * @param isSubscriptionOn if any subscription(self or enterprise) model is on or off
     * @return subscription count of apps
     * @throws barley.appmgt.api.AppManagementException
     */
    public List<SubscriptionCount> getSubscriptionCountByApp(String providerName, String fromDate, String toDate,
                                                       int tenantId, boolean isSubscriptionOn)
            throws AppManagementException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;
        String sqlQuery;
        // (??????)
        // Map<String, Long> subscriptions = new TreeMap<String, Long>();
        List<SubscriptionCount> subscriptions = new ArrayList<>();

        try {
            connection = APIMgtDBUtil.getConnection();
            if ("__all_providers__".equals(providerName)) {
                if (isSubscriptionOn) {
                    sqlQuery = "SELECT API.APP_NAME, API.APP_VERSION, API.APP_PROVIDER, "
                            + "API.UUID AS UUID, COUNT(SUB.SUBSCRIPTION_ID) AS SUB_ID "
                            + "FROM APM_SUBSCRIPTION SUB, APM_APP API, APM_SUBSCRIBER SUBR, "
                            + "APM_APPLICATION APP WHERE API.APP_ID = SUB.APP_ID AND "
                            + "SUB.APPLICATION_ID=APP.APPLICATION_ID AND "
                            + "APP.SUBSCRIBER_ID=SUBR.SUBSCRIBER_ID AND SUBR.TENANT_ID = ? ";
                    if (fromDate != null && toDate != null) {
                        sqlQuery += " AND SUB.SUBSCRIPTION_TIME BETWEEN ";
                    }
                } else {
                    sqlQuery = "SELECT API.APP_NAME, API.APP_VERSION, API.APP_PROVIDER, " +
                            "API.UUID AS UUID, COUNT(DISTINCT HIT.USER_ID) AS SUB_ID " +
                            "FROM APM_APP API " +
                            "INNER JOIN APM_APP_HITS HIT ON API.UUID=HIT.UUID " +
                            "WHERE HIT.TENANT_ID = ? ";
                    if (fromDate != null && toDate != null) {
                        sqlQuery += " AND HIT.HIT_TIME BETWEEN ";
                    }
                }
                if (fromDate != null && toDate != null) {
                    if (!connection.getMetaData().getDriverName().contains("Oracle")) {
                        sqlQuery += "? AND ? ";
                    } else {
                        sqlQuery += "TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS') AND "
                                + "TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS') ";
                    }
                }
                sqlQuery += "GROUP BY API.APP_NAME,API.APP_PROVIDER,APP_VERSION,API.UUID";
                ps = connection.prepareStatement(sqlQuery);
                ps.setInt(1, tenantId);
                if (fromDate != null && toDate != null) {
                    ps.setString(2, fromDate);
                    ps.setString(3, toDate);
                }
            } else {
                if (isSubscriptionOn) {
                    sqlQuery = "SELECT API.APP_NAME,APP_VERSION, API.APP_PROVIDER, API.UUID "
                            + "AS UUID, COUNT(SUB.SUBSCRIPTION_ID) AS SUB_ID FROM "
                            + "APM_SUBSCRIPTION SUB, APM_APP API WHERE API.APP_PROVIDER = ? "
                            + "AND API.APP_ID=SUB.APP_ID AND SUB.SUBSCRIPTION_TIME BETWEEN ";
                } else {
                    sqlQuery = "SELECT API.APP_NAME, API.APP_VERSION, API.APP_PROVIDER, " +
                            "API.UUID AS UUID, COUNT(DISTINCT HIT.USER_ID) AS SUB_ID " +
                            "FROM APM_APP API " +
                            "INNER JOIN APM_APP_HITS HIT ON API.UUID=HIT.UUID " +
                            "WHERE API.APP_PROVIDER= ? " +
                            "AND HIT.HIT_TIME BETWEEN ";
                }

                if (!connection.getMetaData().getDriverName().contains("Oracle")) {
                    sqlQuery += "? AND ? ";
                } else {
                    sqlQuery += "TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS') AND "
                            + "TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS') ";
                }
                sqlQuery += "GROUP BY API.APP_NAME,APP_VERSION,API.APP_PROVIDER,API.UUID ";
                ps = connection.prepareStatement(sqlQuery);
                ps.setString(1, providerName);
                ps.setString(2, fromDate);
                ps.setString(3, toDate);
            }

            result = ps.executeQuery();

            if (result == null) {
                return subscriptions;
            }

            while (result.next()) {
                String appName = result.getString("APP_NAME");
                String appProvider = result.getString("APP_PROVIDER");
                String appVersion = result.getString("APP_VERSION");
                String appuuid = result.getString("uuid");
                long count = result.getLong("SUB_ID");
                // (??????) ????????? ??????
                //String key = appName + "/" + appVersion + "&" + appuuid;
                //subscriptions.put(key, count);
                SubscriptionCount subscriptionCount = new SubscriptionCount();
                subscriptionCount.setAppName(appName);
                subscriptionCount.setAppVersion(appVersion);
                subscriptionCount.setAppId(appuuid);
                subscriptionCount.setSubscriptionCount(count);
                subscriptionCount.setProviderName(appProvider);
                subscriptions.add(subscriptionCount);
            }
        } catch (SQLException e) {
            handleException("Failed to get subscriptionCount of apps for provider :" + providerName +
                    "for the period " + fromDate + "to" + toDate, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
        return subscriptions;
    }



    /**
     * This method returns subscribed web apps by users within given period
     * @param fromDate From Date
     * @param toDate To Date
     * @return List of apps subscribed by users.
     * @throws barley.appmgt.api.AppManagementException
     */
    public Map<String, List> getSubscribedAPPsByUsers(String fromDate, String toDate, int tenantId) throws
                                                                                                AppManagementException {

        Map<String, List> users = new HashMap<String, List>();
        List<Subscriber> subscribers;
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;

        try {
            connection = APIMgtDBUtil.getConnection();
            String sqlQuery = "SELECT SUBR.USER_ID AS USER_ID,API.APP_NAME AS API, API.APP_VERSION, API.APP_PROVIDER " +
                    "AS PROVIDER, SUB.SUBSCRIPTION_TIME as TIME  FROM APM_SUBSCRIBER SUBR, APM_APPLICATION APP, " +
                    "APM_SUBSCRIPTION SUB, APM_APP API WHERE SUB.APPLICATION_ID = APP.APPLICATION_ID AND " +
                    "SUBR.SUBSCRIBER_ID = APP.SUBSCRIBER_ID AND SUB.APP_ID = API.APP_ID AND SUBR.TENANT_ID = ? ";

            if (fromDate != null && toDate != null) {
                sqlQuery += addRangeCondition("SUB.SUBSCRIPTION_TIME", true, connection.getMetaData().getDriverName());
            }

            sqlQuery += "GROUP BY SUBR.USER_ID,API.APP_NAME,API.APP_VERSION, API.APP_PROVIDER,SUB.SUBSCRIPTION_TIME";

            ps = connection.prepareStatement(sqlQuery);
            ps.setInt(1, tenantId);
            if (fromDate != null && toDate != null) {
                ps.setString(2, fromDate);
                ps.setString(3, toDate);
            }
            result = ps.executeQuery();
            if (result == null) {
                return users;
            }

            while (result.next()) {
                String app = result.getString("API")+"/"+result.getString("APP_VERSION");
                if (users.containsKey(app)) {
                    subscribers = users.get(app);
                    Subscriber subscriber = new Subscriber(result.getString("USER_ID"));
                    subscriber.setSubscribedDate(result.getDate("TIME"));

                    subscribers.add(subscriber);
                    users.put(app, subscribers);
                } else {
                    subscribers = new ArrayList<Subscriber>();
                    Subscriber subscriber = new Subscriber(result.getString("USER_ID"));
                    subscriber.setSubscribedDate(result.getDate("TIME"));
                    subscribers.add(subscriber);
                    users.put(app, subscribers);
                }
            }
        } catch (SQLException e) {
            handleException("Failed to get subscribed apps by users for the period " + fromDate + "to " + toDate, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
        return users;
    }

	/**
	 * This method is used to update the subscriber
	 *
	 * @param identifier
	 *            APIIdentifier
	 * @param context
	 *            Context of the WebApp
	 * @param applicationId
	 *            Application id
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to update subscriber
	 */
	public void updateSubscriptions(APIIdentifier identifier, String context, int applicationId)
	                                                                                            throws
                                                                                                AppManagementException {
		addSubscription(identifier, Subscription.SUBSCRIPTION_TYPE_INDIVIDUAL, context, applicationId,
		                AppMConstants.SubscriptionStatus.UNBLOCKED, null);
	}

	/**
	 * This method is used to update the subscription
	 *
	 * @param identifier
	 *            APIIdentifier
	 * @param subStatus
	 *            Subscription Status[BLOCKED/UNBLOCKED]
	 * @param applicationId
	 *            Application id
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to update subscriber
	 */
	public void updateSubscription(APIIdentifier identifier, String subStatus, int applicationId)
	                                                                                             throws
                                                                                                 AppManagementException {

		Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;
		int apiId = -1;

		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
			String getApiQuery =
			                     "SELECT APP_ID FROM APM_APP API WHERE APP_PROVIDER = ? AND "
			                             + "APP_NAME = ? AND APP_VERSION = ?";
			ps = conn.prepareStatement(getApiQuery);
			ps.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
			ps.setString(2, identifier.getApiName());
			ps.setString(3, identifier.getVersion());
			resultSet = ps.executeQuery();
			if (resultSet.next()) {
				apiId = resultSet.getInt("APP_ID");
			}
			resultSet.close();
			ps.close();

			if (apiId == -1) {
				String msg = "Unable to get the WebApp ID for: " + identifier;
				log.error(msg);
				throw new AppManagementException(msg);
			}

			// This query to update the APM_SUBSCRIPTION table
			String sqlQuery =
                    "UPDATE APM_SUBSCRIPTION SET SUB_STATUS = ? WHERE APP_ID = ? AND " +
                            "APPLICATION_ID = ?";

			// Updating data to the APM_SUBSCRIPTION table
			ps = conn.prepareStatement(sqlQuery);
			ps.setString(1, subStatus);
			ps.setInt(2, apiId);
			ps.setInt(3, applicationId);
			ps.execute();

			// finally commit transaction
			conn.commit();

		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the add subscription ", e);
				}
			}
			handleException("Failed to update subscription data ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
	}

	public void updateSubscriptionStatus(int subscriptionId, String status)
	                                                                       throws
                                                                           AppManagementException {

		Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;

		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);

            // This query is to update the APM_SUBSCRIPTION table
            String sqlQuery =
                    "UPDATE APM_SUBSCRIPTION SET SUB_STATUS = ? WHERE SUBSCRIPTION_ID = ?";

			ps = conn.prepareStatement(sqlQuery);
			ps.setString(1, status);
			ps.setInt(2, subscriptionId);
			ps.execute();

			// Commit transaction
			conn.commit();

		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback subscription status update ", e);
				}
			}
			handleException("Failed to update subscription status ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
	}

	/**
	 *
	 * @param keyType
     * @param newAccessToken
     * @param validityPeriod
	 * @return
	 * @throws IdentityException
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public void updateRefreshedApplicationAccessToken(String keyType, String newAccessToken,
	                                                  long validityPeriod)
	                                                                      throws IdentityException,
                                                                                 AppManagementException {

		String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
		    AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromAccessToken(newAccessToken);
		}
        // Update Access Token
        String sqlUpdateNewAccessToken =
                "UPDATE " + accessTokenStoreTable +
                        " SET USER_TYPE = ?, VALIDITY_PERIOD = ? " +
                        " WHERE ACCESS_TOKEN = ? AND TOKEN_SCOPE = ? ";

		Connection connection = null;
		PreparedStatement prepStmt = null;
		try {
			connection = APIMgtDBUtil.getConnection();
			connection.setAutoCommit(false);
			prepStmt = connection.prepareStatement(sqlUpdateNewAccessToken);
			prepStmt.setString(1, AppMConstants.ACCESS_TOKEN_USER_TYPE_APPLICATION);
			if (validityPeriod < 0) {
				prepStmt.setLong(2, Long.MAX_VALUE);
			} else {
				prepStmt.setLong(2, validityPeriod * 1000);
			}
			prepStmt.setString(3, AppManagerUtil.encryptToken(newAccessToken));
			prepStmt.setString(4, keyType);

			prepStmt.execute();
			prepStmt.close();

			connection.commit();

		} catch (SQLException e) {
			log.error(e.getMessage(), e);
			if (connection != null) {
				try {
					connection.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the add access token ", e);
				}
			}
		} catch (CryptoException e) {
			log.error(e.getMessage(), e);
			if (connection != null) {
				try {
					connection.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the add access token ", e);
				}
			}
		} finally {
			IdentityDatabaseUtil.closeAllConnections(connection, null, prepStmt);
		}

	}

	/**
	 * @param apiIdentifier
	 *            APIIdentifier
	 * @param userId
	 *            User Id
	 * @return true if user subscribed for given APIIdentifier
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to check subscribed or not
	 */
	public boolean isSubscribed(APIIdentifier apiIdentifier, String userId)
	                                                                       throws
                                                                           AppManagementException {
		boolean isSubscribed = false;
		// identify loggedinuser
		String loginUserName = getLoginUserName(userId);

		String apiId =
		               apiIdentifier.getProviderName() + "_" + apiIdentifier.getApiName() + "_" +
		                       apiIdentifier.getVersion();

		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
        String sqlQuery =
                "SELECT SUBS.TIER_ID, API.APP_PROVIDER, API.APP_NAME, API.APP_VERSION, SUBS" +
                        ".LAST_ACCESSED , SUBS.APPLICATION_ID " +
                        "FROM APM_SUBSCRIPTION SUBS, APM_SUBSCRIBER SUB, APM_APPLICATION  APP, " +
                        "APM_APP API " +
                        "WHERE API .APP_PROVIDER  = ? AND API.APP_NAME = ? AND API.APP_VERSION = " +
                        "? AND SUB.USER_ID = ? AND SUB.TENANT_ID = ? AND APP.SUBSCRIBER_ID = SUB" +
                        ".SUBSCRIBER_ID AND API.APP_ID = SUBS.APP_ID";

		try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(sqlQuery);
			ps.setString(1, AppManagerUtil.replaceEmailDomainBack(apiIdentifier.getProviderName()));
			ps.setString(2, apiIdentifier.getApiName());
			ps.setString(3, apiIdentifier.getVersion());
			ps.setString(4, loginUserName);
			int tenantId;
			try {
				tenantId = IdentityTenantUtil.getTenantIdOfUser(loginUserName);
			} catch (IdentityRuntimeException e) {
				String msg = "Failed to get tenant id of user : " + loginUserName;
				log.error(msg, e);
				throw new AppManagementException(msg, e);
			}
			ps.setInt(5, tenantId);

			rs = ps.executeQuery();

			if (rs.next()) {
				isSubscribed = true;
			}
		} catch (SQLException e) {
			handleException("Error while checking if user has subscribed to the WebApp ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return isSubscribed;
	}

	/**
	 * @param providerName
	 *            Name of the provider
	 * @return UserApplicationAPIUsage of given provider
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to get
	 *             UserApplicationAPIUsage for given provider
	 */
	public UserApplicationAPIUsage[] getAllAPIUsageByProvider(String providerName)
	                                                                              throws
                                                                                  AppManagementException {

		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet result = null;

		try {
			connection = APIMgtDBUtil.getConnection();

			String sqlQuery =
			                  "SELECT " + "   SUBS.SUBSCRIPTION_ID AS SUBSCRIPTION_ID, "
			                          + "   SUBS.APPLICATION_ID AS APPLICATION_ID, "
			                          + "   SUBS.SUB_STATUS AS SUB_STATUS, "
			                          + "   SUBS.TIER_ID AS TIER_ID, "
			                          + "   API.APP_PROVIDER AS APP_PROVIDER, "
			                          + "   API.APP_NAME AS APP_NAME, "
			                          + "   API.APP_VERSION AS APP_VERSION, "
			                          + "   SUBS.LAST_ACCESSED AS LAST_ACCESSED, "
			                          + "   SUB.USER_ID AS USER_ID, " + "   APP.NAME AS APPNAME "
			                          + "FROM " + "   APM_SUBSCRIPTION SUBS, "
			                          + "   APM_APPLICATION APP, " + "   APM_SUBSCRIBER SUB, "
			                          + "   APM_APP API " + "WHERE "
			                          + "   SUBS.APPLICATION_ID = APP.APPLICATION_ID "
			                          + "   AND APP.SUBSCRIBER_ID = SUB.SUBSCRIBER_ID "
			                          + "   AND API.APP_PROVIDER = ? "
			                          + "   AND API.APP_ID = SUBS.APP_ID " + "ORDER BY "
			                          + "   APP.NAME";

			ps = connection.prepareStatement(sqlQuery);
			ps.setString(1, AppManagerUtil.replaceEmailDomainBack(providerName));
			result = ps.executeQuery();

			Map<String, UserApplicationAPIUsage> userApplicationUsages =
			                                                             new TreeMap<String, UserApplicationAPIUsage>();
			while (result.next()) {
				int subId = result.getInt("SUBSCRIPTION_ID");
				String userId = result.getString("USER_ID");
				String application = result.getString("APPNAME");
				int appId = result.getInt("APPLICATION_ID");
				String subStatus = result.getString("SUB_STATUS");
				String key = userId + "::" + application;
				UserApplicationAPIUsage usage = userApplicationUsages.get(key);
				if (usage == null) {
					usage = new UserApplicationAPIUsage();
					usage.setUserId(userId);
					usage.setApplicationName(application);
					usage.setAppId(appId);
					userApplicationUsages.put(key, usage);
				}
				APIIdentifier apiId =
				                      new APIIdentifier(result.getString("APP_PROVIDER"),
				                                        result.getString("APP_NAME"),
				                                        result.getString("APP_VERSION"));
				SubscribedAPI apiSubscription = new SubscribedAPI(new Subscriber(userId), apiId);
				apiSubscription.setSubStatus(subStatus);
				usage.addApiSubscriptions(apiSubscription);

			}
			return userApplicationUsages.values()
			                            .toArray(new UserApplicationAPIUsage[userApplicationUsages.size()]);

		} catch (SQLException e) {
			handleException("Failed to find WebApp Usage for :" + providerName, e);
			return null;
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, result);
		}
	}

	public String[] addOAuthConsumer(String username, int tenantId, String appName,
	                                 String callbackUrl) throws IdentityOAuthAdminException,
                                                             AppManagementException {
		Connection connection = null;
		PreparedStatement prepStmt = null;
		// identify loggedinuser
		String loginUserName = getLoginUserName(username);
		String sqlStmt =
				"INSERT INTO IDN_OAUTH_CONSUMER_APPS "
						+ "(CONSUMER_KEY, CONSUMER_SECRET, USERNAME, TENANT_ID, OAUTH_VERSION, APP_NAME, CALLBACK_URL) VALUES (?,?,?,?,?,?, ?) ";
		String consumerKey;
		String consumerSecret = OAuthUtil.getRandomNumber();

		do {
			consumerKey = OAuthUtil.getRandomNumber();
		} while (isDuplicateConsumer(consumerKey));

		try {

			consumerKey = AppManagerUtil.encryptToken(consumerKey);
			consumerSecret = AppManagerUtil.encryptToken(consumerSecret);

			connection = APIMgtDBUtil.getConnection();
			connection.setAutoCommit(false);
			prepStmt = connection.prepareStatement(sqlStmt);
			prepStmt.setString(1, consumerKey);
			prepStmt.setString(2, consumerSecret);
			prepStmt.setString(3, loginUserName.toLowerCase());
			prepStmt.setInt(4, tenantId);
			prepStmt.setString(5, OAuthConstants.OAuthVersions.VERSION_2);
			prepStmt.setString(6, appName);
			prepStmt.setString(7, callbackUrl);
			prepStmt.execute();

			connection.commit();

		} catch (SQLException e) {
			handleException("Error when adding a new OAuth consumer.", e);
		} catch (CryptoException e) {
			handleException("Error while attempting to encrypt consumer-key, consumer-secret.", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, null);
		}
		try {
			return new String[] { AppManagerUtil.decryptToken(consumerKey),
					AppManagerUtil.decryptToken(consumerSecret) };
		} catch (CryptoException e) {
			handleException("Error while decrypting consumer-key, consumer-secret", e);
		}
		return null;
	}

	private void updateOAuthConsumerApp(String appName, String callbackUrl)
	                                                                       throws IdentityOAuthAdminException,
                                                                                  AppManagementException {
		Connection connection = null;
		PreparedStatement prepStmt = null;
		String sqlStmt =
		                 "UPDATE IDN_OAUTH_CONSUMER_APPS "
		                         + "SET CALLBACK_URL = ? WHERE APP_NAME = ?";
		try {
			connection = APIMgtDBUtil.getConnection();
			connection.setAutoCommit(false);
			prepStmt = connection.prepareStatement(sqlStmt);
			prepStmt.setString(1, callbackUrl);
			prepStmt.setString(2, appName);
			prepStmt.execute();
			connection.commit();
		} catch (SQLException e) {
			handleException("Error when updating OAuth consumer App for " + appName, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, null);
		}
	}

	private boolean isDuplicateConsumer(String consumerKey) throws AppManagementException {
		Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rSet = null;
        String sqlQuery = "SELECT APP_NAME FROM IDN_OAUTH_CONSUMER_APPS WHERE CONSUMER_KEY = ?";
        boolean isDuplicateConsumer = false;
		try {
			consumerKey = AppManagerUtil.encryptToken(consumerKey);
			connection = APIMgtDBUtil.getConnection();
			prepStmt = connection.prepareStatement(sqlQuery);
			prepStmt.setString(1, consumerKey);

			rSet = prepStmt.executeQuery();
			if (rSet.next()) {
				isDuplicateConsumer = true;
			}
		} catch (SQLException e) {
			handleException("Error when reading the application information from"
					+ " the persistence store.", e);
		} catch (CryptoException e) {
			handleException("Error while encrypting consumer-key", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rSet);
		}
		return isDuplicateConsumer;
	}

	public int addApplication(Application application, String userId) throws
                                                                      AppManagementException {
		Connection conn = null;
		int applicationId = 0;
		String loginUserName = getLoginUserName(userId);
		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
			applicationId = addApplication(application, loginUserName, conn);
			conn.commit();
		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the add Application ", e);
				}
			}
			handleException("Failed to add Application", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(null, conn, null);
		}
		return applicationId;
	}

	/**
	 * @param application
	 *            Application
	 * @param userId
	 *            User Id
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to add Application
	 */
	public int addApplication(Application application, String userId, Connection conn)
	                                                                                  throws
                                                                                      AppManagementException,
	                                                                                  SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		int applicationId = 0;
		try {
			int tenantId;

			try {
				tenantId = IdentityTenantUtil.getTenantIdOfUser(userId);
			} catch (IdentityRuntimeException e) {
				String msg = "Failed to get tenant id of user : " + userId;
				log.error(msg, e);
				throw new AppManagementException(msg, e);
			}
			// Get subscriber Id
			Subscriber subscriber = getSubscriber(userId, tenantId, conn);
			if (subscriber == null) {
				String msg = "Could not load Subscriber records for: " + userId;
				log.error(msg);
				throw new AppManagementException(msg);
			}
			// This query to update the APM_APPLICATION table
			String sqlQuery =
			                  "INSERT "
			                          + "INTO APM_APPLICATION (NAME, SUBSCRIBER_ID, APPLICATION_TIER, CALLBACK_URL, DESCRIPTION, APPLICATION_STATUS)"
			                          + " VALUES (?,?,?,?,?,?)";
			// Adding data to the APM_APPLICATION table
			// ps = conn.prepareStatement(sqlQuery);
			ps = conn.prepareStatement(sqlQuery, new String[] { "APPLICATION_ID" });
			if (conn.getMetaData().getDriverName().contains("PostgreSQL")) {
				ps = conn.prepareStatement(sqlQuery, new String[] { "application_id" });
			}

			ps.setString(1, application.getName());
			ps.setInt(2, subscriber.getId());
			ps.setString(3, application.getTier());
			ps.setString(4, application.getCallbackUrl());
			ps.setString(5, application.getDescription());

			if (application.getName().equals(AppMConstants.DEFAULT_APPLICATION_NAME)) {
				ps.setString(6, AppMConstants.ApplicationStatus.APPLICATION_APPROVED);
			} else {
				ps.setString(6, AppMConstants.ApplicationStatus.APPLICATION_CREATED);
			}
			ps.executeUpdate();
			rs = ps.getGeneratedKeys();
			while (rs.next()) {
				applicationId = Integer.parseInt(rs.getString(1));
			}

			ps.close();
		} catch (SQLException e) {
			handleException("Failed to add Application", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, null, rs);
		}
		return applicationId;

	}

	public void updateApplication(Application application) throws AppManagementException {
		Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;

		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);

			// This query to update the APM_APPLICATION table
			String sqlQuery =
					"UPDATE " + "APM_APPLICATION" + " SET NAME = ? "
							+ ", APPLICATION_TIER = ? " + ", CALLBACK_URL = ? "
							+ ", DESCRIPTION = ? " + "WHERE" + " APPLICATION_ID = ?";
			// Adding data to the APM_APPLICATION table
			ps = conn.prepareStatement(sqlQuery);
			ps.setString(1, application.getName());
			ps.setString(2, application.getTier());
			ps.setString(3, application.getCallbackUrl());
			ps.setString(4, application.getDescription());
			ps.setInt(5, application.getId());

			ps.executeUpdate();
			ps.close();
			// finally commit transaction
			conn.commit();

			updateOAuthConsumerApp(application.getName(), application.getCallbackUrl());

		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the update Application ", e);
				}
			}
			handleException("Failed to update Application", e);
		} catch (IdentityOAuthAdminException e) {
			handleException("Failed to update OAuth Consumer Application", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
	}

	/**
	 * Update the status of the Application creation process
	 *
	 * @param applicationId
	 * @param status
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public void updateApplicationStatus(int applicationId, String status)
	                                                                     throws
                                                                         AppManagementException {
		Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;

		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);

			String updateSqlQuery =
					"UPDATE " + " APM_APPLICATION" + " SET APPLICATION_STATUS = ? "
							+ "WHERE" + " APPLICATION_ID = ?";

			ps = conn.prepareStatement(updateSqlQuery);
			ps.setString(1, status);
			ps.setInt(2, applicationId);

			ps.executeUpdate();
			ps.close();

			conn.commit();

		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the update Application ", e);
				}
			}
			handleException("Failed to update Application", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
	}
	
	/**
     * Deletes an Application along with subscriptions, keys and registration data
     *
     * @param application Application object to be deleted from the database which has the application Id
     * @throws APIManagementException
     */
    public void deleteApplication(Application application) throws AppManagementException {
        Connection connection = null;
        PreparedStatement deleteMappingQuery = null;
        PreparedStatement deleteSubscription = null;
        PreparedStatement deleteApp = null;
        
        String deleteSubscriptionsQuery = SQLConstants.REMOVE_APPLICATION_FROM_SUBSCRIPTIONS_SQL;        
        String deleteApplicationQuery = SQLConstants.REMOVE_APPLICATION_FROM_APPLICATIONS_SQL;
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            
            // ?????? ?????? 
            deleteSubscription = connection.prepareStatement(deleteSubscriptionsQuery);
            deleteSubscription.setInt(1, application.getId());
            deleteSubscription.execute();

            if (log.isDebugEnabled()) {
                log.debug("Subscription details are deleted successfully for Application - " + application.getName());
            }

            // ?????????????????? ?????? 
            deleteApp = connection.prepareStatement(deleteApplicationQuery);
            deleteApp.setInt(1, application.getId());
            deleteApp.execute();

            if (log.isDebugEnabled()) {
                log.debug("Application " + application.getName() + " is deleted successfully.");
            }

            connection.commit();
            
        } catch (SQLException e) {
            handleException("Error while removing application details from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(deleteApp, null, null);
            APIMgtDBUtil.closeAllConnections(deleteMappingQuery, null, null);
            APIMgtDBUtil.closeAllConnections(deleteSubscription, null, null);
            APIMgtDBUtil.closeAllConnections(deleteApp, null, null);

        }
    }

	/**
	 * get the status of the Application creation process
	 *
	 * @param appName
     * @param userId
	 * @return
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public String getApplicationStatus(String appName, String userId) throws
                                                                      AppManagementException {
		Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;
		String status = null;
		int applicationId = getApplicationId(appName, userId);
		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
            String sqlQuery = "SELECT APPLICATION_STATUS FROM APM_APPLICATION WHERE APPLICATION_ID = ?";

			ps = conn.prepareStatement(sqlQuery);
			ps.setInt(1, applicationId);
			resultSet = ps.executeQuery();
			while (resultSet.next()) {
				status = resultSet.getString("APPLICATION_STATUS");
			}
			ps.close();
			conn.commit();
		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the update Application ", e);
				}
			}
			handleException("Failed to update Application", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
		return status;
	}

	/**
	 * @param username
	 *            Subscriber
	 * @return ApplicationId for given appname.
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to get Applications for given subscriber.
	 */
	public int getApplicationId(String appName, String username) throws AppManagementException {
		if (username == null) {
			return 0;
		}
		Subscriber subscriber = getSubscriber(username);

		Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		int appId = 0;

		String sqlQuery = "SELECT " + "   APPLICATION_ID " +

				"FROM " + "   APM_APPLICATION " + "WHERE " + "   SUBSCRIBER_ID  = ? AND  NAME= ?";

		try {
			connection = APIMgtDBUtil.getConnection();
			prepStmt = connection.prepareStatement(sqlQuery);
			prepStmt.setInt(1, subscriber.getId());
			prepStmt.setString(2, appName);
			rs = prepStmt.executeQuery();

			while (rs.next()) {
				appId = rs.getInt("APPLICATION_ID");
			}

		} catch (SQLException e) {
			handleException("Error when getting the application id from"
					+ " the persistence store.", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}
		return appId;
	}

	/**
	 * Find the name of the application by Id
	 *
	 * @param applicationId
	 *            - applicatoin id
	 * @return - application name
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public String getApplicationNameFromId(int applicationId) throws AppManagementException {

		Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		String appName = null;

		String sqlQuery = "SELECT NAME " + "FROM APM_APPLICATION " + "WHERE " + "APPLICATION_ID = ?";

		try {
			connection = APIMgtDBUtil.getConnection();
			prepStmt = connection.prepareStatement(sqlQuery);
			prepStmt.setInt(1, applicationId);
			rs = prepStmt.executeQuery();

			while (rs.next()) {
				appName = rs.getString("NAME");
			}

		} catch (SQLException e) {
			handleException("Error when getting the application name for id " + applicationId, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}
		return appName;
	}

	/**
     * @param subscriber Subscriber
     * @return Applications for given subscriber.
     * @throws barley.appmgt.api.AppManagementException if failed to get Applications for given subscriber.
     */
    //ToDo: This method is added back to avoid broken UI. But we need to refactor this method since
    // AppM only using default Application
    public Application[] getApplications(Subscriber subscriber) throws AppManagementException {
        if (subscriber == null) {
            return null;
        }
        //if subscribed user used email find the ordinal  username
        String subscribedUser = getLoginUserName(subscriber.getName());
        subscriber.setName(subscribedUser);

        Connection connection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        Application[] applications = null;

        String sqlQuery = "SELECT " +
                          "   APPLICATION_ID " +
                          "   ,NAME" +
                          "   ,APPLICATION_TIER" +
                          "   ,SUBSCRIBER_ID  " +
                          "   ,CALLBACK_URL  " +
                          "   ,DESCRIPTION  " +
                          "FROM " +
                          "   APM_APPLICATION " +
                          "WHERE " +
                          "   SUBSCRIBER_ID  = ?";

        try {
            int tenantId;
            connection = APIMgtDBUtil.getConnection();
            try {
                tenantId = IdentityTenantUtil.getTenantIdOfUser(subscriber.getName());
            } catch (IdentityRuntimeException e) {
                String msg = "Failed to get tenant id of user : " + subscriber.getName();
                log.error(msg, e);
                throw new AppManagementException(msg, e);
            }

            //getSubscriberId
            if (subscriber.getId() == 0) {
                Subscriber subs;
                subs = getSubscriber(subscriber.getName(), tenantId, connection);
                if (subs == null) {
                    return null;
                } else {
                    subscriber = subs;
                }
            }

            prepStmt = connection.prepareStatement(sqlQuery);
            prepStmt.setInt(1, subscriber.getId());
            rs = prepStmt.executeQuery();

            ArrayList<Application> applicationsList = new ArrayList<Application>();
            //  String tenantAwareUserId = MultitenantUtils.getTenantAwareUsername(subscriber.getName());
            String tenantAwareUserId = subscriber.getName();
            Application application;
            while (rs.next()) {
                application = new Application(rs.getString("NAME"), subscriber);
                application.setId(rs.getInt("APPLICATION_ID"));
                application.setTier(rs.getString("APPLICATION_TIER"));
                application.setCallbackUrl(rs.getString("CALLBACK_URL"));
                application.setDescription(rs.getString("DESCRIPTION"));
                applicationsList.add(application);
            }
            Collections.sort(applicationsList, new Comparator<Application>() {
                public int compare(Application o1, Application o2) {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });
            applications = applicationsList.toArray(new Application[applicationsList.size()]);

        } catch (SQLException e) {
            handleException("Error when reading the application information from" +
                            " the persistence store.", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
        }
        return applications;
    }
    
    public Application getApplicationByName(String applicationName, String userId)
            throws AppManagementException {
        //mysql> select APP.APPLICATION_ID, APP.NAME, APP.SUBSCRIBER_ID,APP.APPLICATION_TIER,APP.CALLBACK_URL,APP
        // .DESCRIPTION,
        // APP.APPLICATION_STATUS from AM_SUBSCRIBER as SUB,AM_APPLICATION as APP
        // where SUB.user_id='admin' AND APP.name='DefaultApplication' AND SUB.SUBSCRIBER_ID=APP.SUBSCRIBER_ID;
        Connection connection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        Application application = null;
        try {
            connection = APIMgtDBUtil.getConnection();

            String query = SQLConstants.GET_APPLICATION_BY_NAME_PREFIX;
            String whereClause = "  WHERE SUB.USER_ID =? AND APP.NAME=? AND SUB.SUBSCRIBER_ID=APP.SUBSCRIBER_ID";
            query = query + whereClause;

            prepStmt = connection.prepareStatement(query);

            prepStmt.setString(1, userId);
            prepStmt.setString(2, applicationName);

            rs = prepStmt.executeQuery();
            while (rs.next()) {
                String subscriberId = rs.getString("SUBSCRIBER_ID");
                String subscriberName = rs.getString("USER_ID");

                Subscriber subscriber = new Subscriber(subscriberName);
                subscriber.setId(Integer.parseInt(subscriberId));
                application = new Application(applicationName, subscriber);

                application.setDescription(rs.getString("DESCRIPTION"));
                application.setStatus(rs.getString("APPLICATION_STATUS"));
                application.setCallbackUrl(rs.getString("CALLBACK_URL"));
                application.setId(rs.getInt("APPLICATION_ID"));
                application.setTier(rs.getString("APPLICATION_TIER"));
            }
        } catch (SQLException e) {
            handleException("Error while obtaining details of the Application : " + applicationName, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
        }
        return application;
    }

	/**
	 * returns a subscriber record for given username,tenant Id
	 *
	 * @param username
	 *            UserName
	 * @param tenantId
	 *            Tenant Id
	 * @param connection
	 * @return Subscriber
	 * @throws barley.appmgt.api.AppManagementException
	 *             if failed to get subscriber
	 */
	private Subscriber getSubscriber(String username, int tenantId, Connection connection)
	                                                                                      throws
                                                                                          AppManagementException {
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		Subscriber subscriber = null;
		String sqlQuery =
		                  "SELECT " + "   SUB.SUBSCRIBER_ID AS SUBSCRIBER_ID"
		                          + "   ,SUB.USER_ID AS USER_ID "
		                          + "   ,SUB.TENANT_ID AS TENANT_ID"
		                          + "   ,SUB.EMAIL_ADDRESS AS EMAIL_ADDRESS"
		                          + "   ,SUB.DATE_SUBSCRIBED AS DATE_SUBSCRIBED " + "FROM "
		                          + "   APM_SUBSCRIBER SUB " + "WHERE " + "SUB.USER_ID = ? "
		                          + "AND SUB.TENANT_ID = ?";

		try {
			prepStmt = connection.prepareStatement(sqlQuery);
			prepStmt.setString(1, username);
			prepStmt.setInt(2, tenantId);
			rs = prepStmt.executeQuery();

			if (rs.next()) {
				subscriber = new Subscriber(rs.getString("USER_ID"));
				subscriber.setEmail(rs.getString("EMAIL_ADDRESS"));
				subscriber.setId(rs.getInt("SUBSCRIBER_ID"));
				subscriber.setSubscribedDate(rs.getDate("DATE_SUBSCRIBED"));
				subscriber.setTenantId(rs.getInt("TENANT_ID"));
				return subscriber;
			}
		} catch (SQLException e) {
			handleException("Error when reading the application information from"
			                + " the persistence store.", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, null, rs);
		}
		return subscriber;
	}

	public void recordAPILifeCycleEvent(APIIdentifier identifier, APIStatus oldStatus,
			APIStatus newStatus, String userId)
	                                                                       throws
                                                                           AppManagementException {
		Connection conn = null;
		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
			recordAPILifeCycleEvent(identifier, oldStatus.toString(), newStatus.toString(), userId, conn);
			conn.commit();
		} catch (SQLException e) {
			handleException("Failed to record WebApp state change", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(null, conn, null);
		}
	}

	public void recordAPILifeCycleEvent(APIIdentifier identifier, String oldStatus,
	                                    String newStatus, String userId, Connection conn)
	                                                                                        throws
                                                                                            AppManagementException {
		// Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;

		int tenantId;
		int apiId = -1;
		try {
			tenantId = IdentityTenantUtil.getTenantIdOfUser(userId);
		} catch (IdentityRuntimeException e) {
			String msg = "Failed to get tenant id of user : " + userId;
			log.error(msg, e);
			throw new AppManagementException(msg, e);
		}

		if (oldStatus == null && !newStatus.equals(APIStatus.CREATED.toString())) {
			String msg = "Invalid old and new state combination";
			log.error(msg);
			throw new AppManagementException(msg);
		} else if (oldStatus != null && oldStatus.equals(newStatus)) {
			String msg = "No measurable differences in WebApp state";
			log.error(msg);
			throw new AppManagementException(msg);
		}

		String getAPIQuery =
		                     "SELECT " + "API.APP_ID FROM APM_APP API" + " WHERE "
		                             + "API.APP_PROVIDER = ?" + " AND API.APP_NAME = ?"
		                             + " AND API.APP_VERSION = ?";

		String sqlQuery =
		                  "INSERT "
		                          + "INTO APM_APP_LC_EVENT (APP_ID, PREVIOUS_STATE, NEW_STATE, USER_ID, TENANT_ID, EVENT_DATE)"
		                          + " VALUES (?,?,?,?,?,?)";

		try {
			// conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(getAPIQuery);
			ps.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
			ps.setString(2, identifier.getApiName());
			ps.setString(3, identifier.getVersion());
			resultSet = ps.executeQuery();
			if (resultSet.next()) {
				apiId = resultSet.getInt("APP_ID");
			}
			resultSet.close();
			ps.close();
			if (apiId == -1) {
				String msg = "Unable to find the WebApp: " + identifier + " in the database";
				log.error(msg);
				throw new AppManagementException(msg);
			}

			ps = conn.prepareStatement(sqlQuery);
			ps.setInt(1, apiId);
			if (oldStatus != null) {
				ps.setString(2, oldStatus);
			} else {
				ps.setNull(2, Types.VARCHAR);
			}
			// (??????) 2018.03.13 - enum ???????????? ??????. value??? ???????????? ???????????? ????????? ??????.
//			ps.setString(3, newStatus.getStatus());
			ps.setString(3, newStatus);
			ps.setString(4, userId);
			ps.setInt(5, tenantId);
			ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));

			ps.executeUpdate();

			// finally commit transaction
//			conn.commit();

		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the API state change record", e);
				}
			}
			handleException("Failed to record API state change", e);
		} finally {
			// APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
	}

	public List<LifeCycleEvent> getLifeCycleEvents(APIIdentifier apiId)
	                                                                   throws
                                                                       AppManagementException {
		Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		String sqlQuery =
		                  "SELECT" + " LC.APP_ID AS APP_ID,"
		                          + " LC.PREVIOUS_STATE AS PREVIOUS_STATE,"
		                          + " LC.NEW_STATE AS NEW_STATE," + " LC.USER_ID AS USER_ID,"
		                          + " LC.EVENT_DATE AS EVENT_DATE " + "FROM"
		                          + " APM_APP_LC_EVENT LC, " + " APM_APP API " + "WHERE"
		                          + " API.APP_PROVIDER = ?" + " AND API.APP_NAME = ?"
		                          + " AND API.APP_VERSION = ?" + " AND API.APP_ID = LC.APP_ID";

		List<LifeCycleEvent> events = new ArrayList<LifeCycleEvent>();

		try {
			connection = APIMgtDBUtil.getConnection();
			prepStmt = connection.prepareStatement(sqlQuery);
			prepStmt.setString(1, AppManagerUtil.replaceEmailDomainBack(apiId.getProviderName()));
			prepStmt.setString(2, apiId.getApiName());
			prepStmt.setString(3, apiId.getVersion());
			rs = prepStmt.executeQuery();

			while (rs.next()) {
				LifeCycleEvent event = new LifeCycleEvent();
				event.setApi(apiId);
				String oldState = rs.getString("PREVIOUS_STATE");
				//event.setOldStatus(oldState != null ? APIStatus.valueOf(oldState) : null);
				event.setOldStatus(oldState != null ? oldState : null);
				//event.setNewStatus(APIStatus.valueOf(rs.getString("NEW_STATE")));
				event.setNewStatus(rs.getString("NEW_STATE"));
				event.setUserId(rs.getString("USER_ID"));
				event.setDate(rs.getTimestamp("EVENT_DATE"));
				events.add(event);
			}

			Collections.sort(events, new Comparator<LifeCycleEvent>() {
				public int compare(LifeCycleEvent o1, LifeCycleEvent o2) {
					return o1.getDate().compareTo(o2.getDate());
				}
			});
		} catch (SQLException e) {
			handleException("Error when executing the SQL : " + sqlQuery, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}
		return events;
	}

    public void addWebApp(WebApp app) throws AppManagementException {
        Connection connection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        //String businessOwnerName = app.getBusinessOwner();
        
        // (??????) CREATED_BY,CREATED_TIME, CATEGORY, THUMBNAIL_URL, DESCRIPTION ?????? ??????
//        String query = "INSERT INTO APM_APP(APP_PROVIDER, TENANT_ID, APP_NAME, APP_VERSION, CONTEXT, TRACKING_CODE, " +
//                        "VISIBLE_ROLES, UUID, SAML2_SSO_ISSUER, LOG_OUT_URL,APP_ALLOW_ANONYMOUS, APP_ENDPOINT, TREAT_AS_SITE) " +
//                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String query = "INSERT INTO APM_APP(APP_PROVIDER, TENANT_ID, APP_NAME, APP_VERSION, CONTEXT, TRACKING_CODE, " +
                "VISIBLE_ROLES, UUID, SAML2_SSO_ISSUER, LOG_OUT_URL,APP_ALLOW_ANONYMOUS, APP_ENDPOINT, TREAT_AS_SITE, " + 
        		"CREATED_BY, CREATED_TIME, CATEGORY, THUMBNAIL_URL, DESCRIPTION, TITLE) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try {
            String gatewayURLs = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().
                    getAPIManagerConfiguration().getFirstProperty(GATEWAY_URL);
            String[] urlArray = gatewayURLs.split(",");
            String prodURL = urlArray[0];
            String logoutURL = app.getLogoutURL();
            if (logoutURL != null && !"".equals(logoutURL.trim())) {
                logoutURL = prodURL.concat(app.getContext()).concat("/" + app.getId().getVersion() + "/").concat(logoutURL);
            }

            int tenantId;
            String tenantDomain = MultitenantUtils.getTenantDomain(AppManagerUtil.replaceEmailDomainBack(app.getId().getProviderName()));
            try {
                tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(tenantDomain);
            } catch (UserStoreException e) {
                throw new AppManagementException("Error in retrieving Tenant Information while adding app :"
                        + app.getId().getApiName(), e);
            }

            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            prepStmt = connection.prepareStatement(query, new String[]{"APP_ID"});
            prepStmt.setString(1, AppManagerUtil.replaceEmailDomainBack(app.getId().getProviderName()));
            prepStmt.setInt(2, tenantId);
            prepStmt.setString(3, app.getId().getApiName());
            prepStmt.setString(4, app.getId().getVersion());
            prepStmt.setString(5, app.getContext());
            prepStmt.setString(6, app.getTrackingCode());
            prepStmt.setString(7, app.getVisibleRoles());
            prepStmt.setString(8, app.getUUID());
            prepStmt.setString(9, app.getSaml2SsoIssuer());
            prepStmt.setString(10, logoutURL);
            prepStmt.setBoolean(11, app.getAllowAnonymous());
            prepStmt.setString(12, app.getUrl());
            prepStmt.setBoolean(13, Boolean.parseBoolean(app.getTreatAsASite()));
            
            // (??????) ?????? ?????? 
            prepStmt.setString(14, AppManagerUtil.replaceEmailDomainBack(app.getId().getProviderName()));
            prepStmt.setTimestamp(15, new Timestamp(System.currentTimeMillis()));
            
            // (??????) 2019.09.26 - ????????????, ?????????, ?????? ?????? ?????? 
            prepStmt.setString(16, app.getCategory());
            prepStmt.setString(17, app.getThumbnailUrl());
            prepStmt.setString(18, app.getDescription());
            
            // (??????) 2019.10.21 - ????????? ?????? ??????
            prepStmt.setString(19, app.getTitle());

            prepStmt.execute();

            rs = prepStmt.getGeneratedKeys();
            int webAppId = -1;
            if (rs.next()) {
                webAppId = rs.getInt(1);
            }
            // apm_app_url_mapping ???????????? ????????????. 
            addURLTemplates(webAppId, app, connection);
            //Set default versioning details
            saveDefaultVersionDetails(app, connection);

            // ?????????????????? ???????????? ????????????.
            recordAPILifeCycleEvent(app.getId(), null, APIStatus.CREATED.toString(),
                    AppManagerUtil.replaceEmailDomainBack(app.getId().getProviderName()),
                    connection);
            
            // APM_APP_XACML_PARTIAL_MAPPINGS ????????? ?????? 
            if (app.getPolicyPartials() != null && !app.getPolicyPartials().isEmpty()) {
                JSONArray policyPartialIdList = (JSONArray) JSONValue.parse(app.getPolicyPartials());
                saveApplicationPolicyPartialsMappings(connection, webAppId, policyPartialIdList.toArray());
            }

            //save policy groups app wise
            // APM_POLICY_GROUP_MAPPING ????????? ?????? 
            if (app.getPolicyGroups() != null && !app.getPolicyGroups().isEmpty()) {
                JSONArray policyGroupIdList = (JSONArray) JSONValue.parse(app.getPolicyGroups());
                saveApplicationPolicyGroupsMappings(connection, webAppId, policyGroupIdList.toArray());
            }

            //save java policies app wise
            // APM_APP_JAVA_POLICY_MAPPING ????????? ?????? 
            if (app.getJavaPolicies() != null && !app.getJavaPolicies().isEmpty()) {
                JSONArray javaPolicyIdList = (JSONArray) JSONValue.parse(app.getJavaPolicies());
                saveJavaPolicyMappings(connection, webAppId, javaPolicyIdList.toArray());
            }

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback when adding the WebApp: " + app.getId() + " to the database", e);
                }
            }
            handleException("Error while adding the WebApp: " + app.getId() + " to the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
        }
    }

	/**
	 * Persists WorkflowDTO to Database
	 *
	 * @param workflow
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public void addWorkflowEntry(WorkflowDTO workflow) throws AppManagementException {
		Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;

		String query =
		               "INSERT INTO APM_WORKFLOWS (WF_REFERENCE, WF_TYPE, WF_STATUS, WF_CREATED_TIME, "
		                       + "WF_STATUS_DESC, TENANT_ID, TENANT_DOMAIN, WF_EXTERNAL_REFERENCE ) VALUES (?,?,?,?,?,?,?,?)";
		try {
			Timestamp cratedDateStamp = new Timestamp(workflow.getCreatedTime());

			connection = APIMgtDBUtil.getConnection();
			connection.setAutoCommit(false);
			prepStmt = connection.prepareStatement(query);
			prepStmt.setString(1, workflow.getWorkflowReference());
			prepStmt.setString(2, workflow.getWorkflowType());
			prepStmt.setString(3, workflow.getStatus().toString());
			prepStmt.setTimestamp(4, cratedDateStamp);
			prepStmt.setString(5, workflow.getWorkflowDescription());
			prepStmt.setInt(6, workflow.getTenantId());
			prepStmt.setString(7, workflow.getTenantDomain());
			prepStmt.setString(8, workflow.getExternalWorkflowReference());

			prepStmt.execute();

			connection.commit();
		} catch (SQLException e) {
			handleException("Error while adding Workflow : " +
			                        workflow.getExternalWorkflowReference() + " to the database", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}
	}

	public void updateWorkflowStatus(WorkflowDTO workflowDTO) throws AppManagementException {
		Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;

		String query =
		               "UPDATE APM_WORKFLOWS SET WF_STATUS = ?, WF_STATUS_DESC = ?, WF_UPDATED_TIME = ? "
		                       + "WHERE WF_EXTERNAL_REFERENCE = ?";
		try {
			Timestamp updatedTimeStamp = new Timestamp(System.currentTimeMillis());

			connection = APIMgtDBUtil.getConnection();
			connection.setAutoCommit(false);
			prepStmt = connection.prepareStatement(query);
			prepStmt.setString(1, workflowDTO.getStatus().toString());
			prepStmt.setString(2, workflowDTO.getWorkflowDescription());
			prepStmt.setTimestamp(3, updatedTimeStamp);
			prepStmt.setString(4, workflowDTO.getExternalWorkflowReference());

			prepStmt.execute();

			connection.commit();
		} catch (SQLException e) {
			handleException("Error while updating Workflow Status of workflow " +
			                        workflowDTO.getExternalWorkflowReference(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}
	}

	/**
     * Returns the latest workflow object for a given workflow reference.
     *
     * @param workflowReference
     * @return
     * @throws barley.appmgt.api.AppManagementException
     */
    public WorkflowDTO retrieveLatestWorkflowByReference(String workflowReference) throws
                                                                                   AppManagementException {
        Connection connection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        WorkflowDTO workflowDTO = null;
        String query;

        try {
            connection = APIMgtDBUtil.getConnection();

            String driverName = connection.getMetaData().getDriverName();
            if (driverName.contains(oracleDriverName)) {
                query = "SELECT WF_STATUS, WF_EXTERNAL_REFERENCE, WF_CREATED_TIME, WF_REFERENCE, TENANT_DOMAIN, " +
                        "TENANT_ID, WF_TYPE, WF_STATUS_DESC " +
                        "FROM APM_WORKFLOWS " +
                        "WHERE WF_REFERENCE = ? AND ROWNUM <= 1 " +
                        "ORDER BY WF_CREATED_TIME ";
            } else if (driverName.contains(msSQLDriverName) || driverName.contains(microsoftDriverName)) {
                query = "SELECT TOP 1 WF_STATUS, WF_EXTERNAL_REFERENCE, WF_CREATED_TIME, WF_REFERENCE, TENANT_DOMAIN, " +
                        "TENANT_ID, WF_TYPE, WF_STATUS_DESC " +
                        "FROM APM_WORKFLOWS " +
                        "WHERE WF_REFERENCE = ? " +
                        "ORDER BY WF_CREATED_TIME";
            } else {
                query = "SELECT WF_STATUS, WF_EXTERNAL_REFERENCE, WF_CREATED_TIME, WF_REFERENCE, TENANT_DOMAIN, " +
                        "TENANT_ID, WF_TYPE, WF_STATUS_DESC " +
                        "FROM APM_WORKFLOWS " +
                        "WHERE WF_REFERENCE = ? " +
                        "ORDER BY WF_CREATED_TIME LIMIT 1";
            }

            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, workflowReference);

            rs = prepStmt.executeQuery();

            while (rs.next()) {
                workflowDTO = new WorkflowDTO();
                workflowDTO.setStatus(WorkflowStatus.valueOf(rs.getString("WF_STATUS")));
                workflowDTO.setExternalWorkflowReference(rs.getString("WF_EXTERNAL_REFERENCE"));
                workflowDTO.setCreatedTime(rs.getTimestamp("WF_CREATED_TIME").getTime());
                workflowDTO.setWorkflowReference(rs.getString("WF_REFERENCE"));
                workflowDTO.setTenantDomain(rs.getString("TENANT_DOMAIN"));
                workflowDTO.setTenantId(rs.getInt("TENANT_ID"));
                workflowDTO.setWorkflowType(rs.getString("WF_TYPE"));
                workflowDTO.setWorkflowDescription(rs.getString("WF_STATUS_DESC"));
            }

        } catch (SQLException e) {
            handleException("Error while retrieving workflow details for " + workflowReference, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
        }

        return workflowDTO;
    }

	/**
	 * Returns a workflow object for a given external workflow reference.
	 *
	 * @param workflowReference
	 * @return
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public WorkflowDTO retrieveWorkflow(String workflowReference) throws AppManagementException {
		Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		WorkflowDTO workflowDTO = null;

        String query = "SELECT WF_STATUS, WF_EXTERNAL_REFERENCE, WF_CREATED_TIME, WF_REFERENCE, TENANT_DOMAIN, " +
                "TENANT_ID, WF_TYPE, WF_STATUS_DESC " +
                "FROM APM_WORKFLOWS " +
                "WHERE WF_EXTERNAL_REFERENCE = ?";
        try {
			connection = APIMgtDBUtil.getConnection();
			prepStmt = connection.prepareStatement(query);
			prepStmt.setString(1, workflowReference);

			rs = prepStmt.executeQuery();

			while (rs.next()) {
				workflowDTO = new WorkflowDTO();
				workflowDTO.setStatus(WorkflowStatus.valueOf(rs.getString("WF_STATUS")));
				workflowDTO.setExternalWorkflowReference(rs.getString("WF_EXTERNAL_REFERENCE"));
				workflowDTO.setCreatedTime(rs.getTimestamp("WF_CREATED_TIME").getTime());
				workflowDTO.setWorkflowReference(rs.getString("WF_REFERENCE"));
				workflowDTO.setTenantDomain(rs.getString("TENANT_DOMAIN"));
				workflowDTO.setTenantId(rs.getInt("TENANT_ID"));
				workflowDTO.setWorkflowType(rs.getString("WF_TYPE"));
				workflowDTO.setWorkflowDescription(rs.getString("WF_STATUS_DESC"));
			}
		} catch (SQLException e) {
			handleException("Error while retrieving workflow details for " + workflowReference, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}

		return workflowDTO;
	}

	/**
	 * Adds URI templates define for an Webapp
	 *
	 * @param apiId webapp id
	 * @param api webapp object
	 * @param connection db connection
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public void addURLTemplates(int apiId, WebApp api, Connection connection) throws
			AppManagementException {
		if (apiId == -1) {
			// application addition has failed
			return;
		}
		PreparedStatement prepStmt = null;
		PreparedStatement statementToAddPolicyMappings = null;

        String query = "INSERT INTO APM_APP_URL_MAPPING (APP_ID, HTTP_METHOD, URL_PATTERN, POLICY_GRP_ID) " +
                "VALUES (?,?,?,?)";

        if (log.isDebugEnabled()) {
            log.debug("Inserting Application's URL Mappings and Entitlement policy partial mappings against AppId -"
                    + apiId);
        }

		try {
			prepStmt = connection.prepareStatement(query);
			Iterator<URITemplate> uriTemplateIterator = api.getUriTemplates().iterator();

			while (uriTemplateIterator.hasNext()) {
                URITemplate uriTemplate = uriTemplateIterator.next();

				prepStmt.setInt(1, apiId);
				prepStmt.setString(2, uriTemplate.getHTTPVerb());
				prepStmt.setString(3, uriTemplate.getUriTemplate());
				prepStmt.setInt(4, uriTemplate.getPolicyGroupId());
				prepStmt.executeUpdate();
			}
		} catch (SQLException e) {
			handleException("Error while adding URL template(s) to the database for WebApp : " +
					api.getId(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, null, null);
		}
	}

    /**
     * Insert or Update default version details
     *
     * @param app
     * @param connection
     * @throws AppManagementException
     * @throws SQLException
     */
    private void saveDefaultVersionDetails(WebApp app, Connection connection)
            throws AppManagementException, SQLException {
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        int recordCount = 0;

        String sqlQuery =
                "SELECT COUNT(*) AS ROWCOUNT FROM APM_APP_DEFAULT_VERSION WHERE APP_NAME=? AND APP_PROVIDER=? AND " +
                        "TENANT_ID=? ";

        try {
            int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);

            prepStmt = connection.prepareStatement(sqlQuery);
            prepStmt.setString(1, app.getId().getApiName());
            prepStmt.setString(2, app.getId().getProviderName());
            prepStmt.setInt(3, tenantId);
            rs = prepStmt.executeQuery();

            if (rs.next()) {
                recordCount = rs.getInt("ROWCOUNT");
            }

            if (recordCount == 0) {
                //if this is the default version and there are no existing records, create a new one
                if (app.isDefaultVersion()) {
                    addDefaultVersionDetails(app, connection);
                }
            } else {
                //if there is an existing record and if this is the latest default, update the status
                if (app.isDefaultVersion()) {
                    updateDefaultVersionDetails(app, connection);
                } else {
                    //If this is an existing record but if this is not the latest default, check if this is the
                    // previous default version
                    String existingDefaultVersion = getDefaultVersion(app.getId().getApiName(), app.getId().getProviderName(),
                                                            AppDefaultVersion.APP_IS_ANY_LIFECYCLE_STATE, connection);
                    if (existingDefaultVersion.equals(app.getId().getVersion())) {
                        //if this is the ex default version, delete the entry
                        deleteDefaultVersionDetails(app.getId(), connection);
                    }
                }
            }
        } catch (SQLException e) {
            /* In the code it is using a single SQL connection passed from the parent function so the error is logged
             here and throwing the SQLException so the connection will be disposed by the parent function. */
            log.error("Error when getting the default version record count for Application: " +
                              app.getId().getApiName(), e);
            throw e;
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, null, rs);
        }
    }

    /**
     * Insert default version details
     *
     * @param app
     * @param connection
     * @throws AppManagementException
     * @throws SQLException
     */
    private void addDefaultVersionDetails(WebApp app, Connection connection) throws
                                                                             AppManagementException, SQLException {
        PreparedStatement prepStmt = null;
        String query =
                "INSERT INTO APM_APP_DEFAULT_VERSION  (APP_NAME, APP_PROVIDER, DEFAULT_APP_VERSION, " +
                        "PUBLISHED_DEFAULT_APP_VERSION, TENANT_ID) VALUES (?,?,?,?,?)";

        if (log.isDebugEnabled()) {
            log.debug("Inserting default version details for AppId -" + app.getId().getApplicationId());
        }

        try {
            int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);

            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, app.getId().getApiName());
            prepStmt.setString(2, app.getId().getProviderName());
            prepStmt.setString(3, app.getId().getVersion());
            if (app.getStatus() == APIStatus.PUBLISHED) {
                prepStmt.setString(4, app.getId().getVersion());
            } else {
                prepStmt.setString(4, null);
            }
            prepStmt.setInt(5, tenantId);
            prepStmt.executeUpdate();
        } catch (SQLException e) {
             /* In the code it is using a single SQL connection passed from the parent function so the error is logged
             here and throwing the SQLException so the connection will be disposed by the parent function. */
            log.error("Error while inserting default version details for WebApp : " +
                              app.getId(), e);
            throw e;
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, null, null);
        }
    }


    /**
     * Insert default version details
     *
     * @param app
     * @param connection
     * @throws AppManagementException
     * @throws SQLException
     */
    private void updateDefaultVersionDetails(WebApp app, Connection connection) throws
                                                                                AppManagementException, SQLException {
        PreparedStatement prepStmt = null;
        String query;
        try {
            int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);

            if (app.getStatus() == APIStatus.PUBLISHED && app.isDefaultVersion()) {
                query = "UPDATE APM_APP_DEFAULT_VERSION SET DEFAULT_APP_VERSION=?, PUBLISHED_DEFAULT_APP_VERSION=? " +
                        "WHERE APP_NAME=? AND APP_PROVIDER=? AND TENANT_ID=? ";
                prepStmt = connection.prepareStatement(query);
                prepStmt.setString(1, app.getId().getVersion());
                prepStmt.setString(2, app.getId().getVersion());
                prepStmt.setString(3, app.getId().getApiName());
                prepStmt.setString(4, app.getId().getProviderName());
                prepStmt.setInt(5, tenantId);
            } else {
                query =
                        "UPDATE APM_APP_DEFAULT_VERSION SET DEFAULT_APP_VERSION=? WHERE APP_NAME=? AND APP_PROVIDER=?" +
                                " AND TENANT_ID=? ";
                prepStmt = connection.prepareStatement(query);
                prepStmt.setString(1, app.getId().getVersion());
                prepStmt.setString(2, app.getId().getApiName());
                prepStmt.setString(3, app.getId().getProviderName());
                prepStmt.setInt(4, tenantId);
            }
            prepStmt.executeUpdate();
        } catch (SQLException e) {
              /* In the code it is using a single SQL connection passed from the parent function so the error is logged
             here and throwing the SQLException so the connection will be disposed by the parent function. */
            log.error("Error while updating default version details for WebApp : " +
                              app.getId(), e);
            throw e;
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, null, null);
        }
    }

    /**
     * Delete default version details
     *
     * @param apiIdentifier APIIdentifier class
     * @param connection
     * @throws AppManagementException
     * @throws SQLException
     */
    private void deleteDefaultVersionDetails(APIIdentifier apiIdentifier, Connection connection) throws
                                                                                                 AppManagementException,
                                                                                                 SQLException {
        PreparedStatement prepStmt = null;
        String query;
        try {
            int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);
            query = "DELETE FROM APM_APP_DEFAULT_VERSION WHERE APP_NAME=? AND APP_PROVIDER=? AND TENANT_ID=? ";
            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, apiIdentifier.getApiName());
            prepStmt.setString(2, apiIdentifier.getProviderName());
            prepStmt.setInt(3, tenantId);
            prepStmt.executeUpdate();
        } catch (SQLException e) {
              /* In the code it is using a single SQL connection passed from the parent function so the error is logged
             here and throwing the SQLException so the connection will be disposed by the parent function. */
            log.error("Error while deleting default version details for WebApp : " +
                              apiIdentifier.getApiName(), e);
            throw e;
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, null, null);
        }
    }


    /**
	 * update URI templates define for an API
	 *
	 * @param api
	 * @param connection
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public void updateURLTemplates(WebApp api, Connection connection) throws
                                                                      AppManagementException {
		int apiId = getAPIID(api.getId(), connection);
		if (apiId == -1) {
			// application addition has failed
			return;
		}
		PreparedStatement prepStmt = null;
		String deleteOldMappingsQuery = "DELETE FROM APM_APP_URL_MAPPING WHERE APP_ID = ?";
		try {
			prepStmt = connection.prepareStatement(deleteOldMappingsQuery);
			prepStmt.setInt(1, apiId);
			prepStmt.execute();
		} catch (SQLException e) {
			handleException("Error while deleting URL template(s) for WebApp : " +
			                api.getId().toString(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, null, null);
		}
		addURLTemplates(apiId, api, connection);
    }

	/**
	 * returns all URL templates define for all active(PUBLISHED) APIs.
	 */
	public static ArrayList<URITemplate> getAllURITemplates(String apiContext, String version)
			throws AppManagementException {
		Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		ArrayList<URITemplate> uriTemplates = new ArrayList<URITemplate>();
		// POLICY_GRP_ID ??????
        String query = " SELECT AUM.HTTP_METHOD, AUTH_SCHEME, URL_PATTERN, THROTTLING_TIER, SKIP_THROTTLING, USER_ROLES, AUM.POLICY_GRP_ID "
                + " FROM APM_APP_URL_MAPPING AUM "
                + " LEFT JOIN APM_APP API ON API.APP_ID=AUM.APP_ID "
                + " LEFT JOIN APM_POLICY_GROUP POLICY ON AUM.POLICY_GRP_ID= POLICY.POLICY_GRP_ID "
                + " WHERE API.CONTEXT= ? AND API.APP_VERSION = ? "
                + " ORDER BY URL_MAPPING_ID ";

		try {
			connection = APIMgtDBUtil.getConnection();
			prepStmt = connection.prepareStatement(query);
			prepStmt.setString(1, apiContext);
			prepStmt.setString(2, version);
			rs = prepStmt.executeQuery();

			while (rs.next()) {
                URITemplate uriTemplate = new URITemplate();
				uriTemplate.setHTTPVerb(rs.getString("HTTP_METHOD"));
				uriTemplate.setAuthType(rs.getString("AUTH_SCHEME"));
				uriTemplate.setUriTemplate(rs.getString("URL_PATTERN"));
				uriTemplate.setThrottlingTier(rs.getString("THROTTLING_TIER"));
				uriTemplate.setSkipThrottling(rs.getBoolean("SKIP_THROTTLING"));
				uriTemplate.setUserRoles(rs.getString("USER_ROLES"));
				// POLICY_GRP_ID ??????
				uriTemplate.setPolicyGroupId(rs.getInt("POLICY_GRP_ID"));
				uriTemplates.add(uriTemplate);
			}
		} catch (SQLException e) {
            handleException("Error while fetching URL Templates. Api Context - "
                    + apiContext + ", Version - " + version, e);
        } finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}
		return uriTemplates;
	}

	/**
	 * Returns entitlement policy template contexts of a given Application
	 *
	 * @param appIdentifier application identifier
	 * @return list of entitlement policy contexts
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public List<XACMLPolicyTemplateContext> getEntitlementPolicyTemplateContexts(APIIdentifier appIdentifier)
			throws AppManagementException {

		List<XACMLPolicyTemplateContext> contexts = new ArrayList<XACMLPolicyTemplateContext>();

        String query = "SELECT DISTINCT "
                + "APP.APP_ID AS APP_ID, APP.UUID AS APP_UUID, POLICY_GROUP.POLICY_GRP_ID AS POLICY_GRP_ID,"
                + "ENTITLEMENT_POLICY.ENTITLEMENT_POLICY_PARTIAL_ID AS RULE_ID, ENTITLEMENT_POLICY.CONTENT AS RULE_CONTENT "
                + "FROM "
                + "APM_APP APP, "
                + "APM_POLICY_GROUP POLICY_GROUP, "
                + "APM_POLICY_GROUP_MAPPING APP_GROUP, "
                + "APM_ENTITLEMENT_POLICY_PARTIAL ENTITLEMENT_POLICY, "
                + "APM_POLICY_GRP_PARTIAL_MAPPING GROUP_RULE "
                + "WHERE APP.APP_ID = "
                + "(SELECT APP_ID FROM APM_APP WHERE APP_PROVIDER = ? AND APP_NAME = ? AND APP_VERSION = ? ) "
                + "AND APP_GROUP.APP_ID = APP.APP_ID "
                + "AND APP_GROUP.POLICY_GRP_ID = POLICY_GROUP.POLICY_GRP_ID "
                + "AND GROUP_RULE.POLICY_GRP_ID = POLICY_GROUP.POLICY_GRP_ID "
                + "AND GROUP_RULE.POLICY_PARTIAL_ID = ENTITLEMENT_POLICY.ENTITLEMENT_POLICY_PARTIAL_ID";

		Connection connection = null;
		PreparedStatement preparedStatement = null;
		ResultSet resultSet = null;

		if (log.isDebugEnabled()) {
			log.debug("Searching for entitlement policy contexts of the application : " + appIdentifier.getApiName());
		}

		try {
            connection = APIMgtDBUtil.getConnection();
            String providerName = AppManagerUtil.replaceEmailDomainBack(appIdentifier.getProviderName());
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, providerName);
            preparedStatement.setString(2, appIdentifier.getApiName());
			preparedStatement.setString(3, appIdentifier.getVersion());

			resultSet = preparedStatement.executeQuery();

			while (resultSet.next()) {
                XACMLPolicyTemplateContext context = new XACMLPolicyTemplateContext();
				context.setAppId(resultSet.getInt("APP_ID"));
				context.setAppUuid(resultSet.getString("APP_UUID"));
				context.setPolicyGroupId(resultSet.getInt("POLICY_GRP_ID"));
				context.setRuleId(resultSet.getInt("RULE_ID"));
				context.setRuleContent(resultSet.getString("RULE_CONTENT"));
				contexts.add(context);
			}

		} catch (SQLException e) {
			handleException("Error while fetching entitlement policy template contexts for webapp : " +
					appIdentifier.getApiName(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(preparedStatement, connection, resultSet);
		}
		return contexts;
	}

	public void updateWebApp(WebApp api, String authorizedAdminCookie) throws AppManagementException {
		Connection connection = null;
		PreparedStatement prepStmt = null;
        ResultSet rs = null;
        
        // (??????) CATEGORY, THUMBNAIL_URL, DESCRIPTION ?????? ?????? 
//        String query = "UPDATE APM_APP " +
//                    " SET CONTEXT = ?, LOG_OUT_URL  = ?, APP_ALLOW_ANONYMOUS = ?, APP_ENDPOINT = ? ,TREAT_AS_SITE = ? ," +
//                    " VISIBLE_ROLES = ? WHERE APP_PROVIDER = ? AND APP_NAME = ? AND APP_VERSION = ? ";
        String query = "UPDATE APM_APP " +
                " SET CONTEXT = ?, LOG_OUT_URL  = ?, APP_ALLOW_ANONYMOUS = ?, APP_ENDPOINT = ? ,TREAT_AS_SITE = ? , VISIBLE_ROLES = ?, " +
                "     UPDATED_BY = ?," +
                "     UPDATED_TIME = ?, " +
                "     CATEGORY = ?, " +
                "     THUMBNAIL_URL = ?, " +
                "     DESCRIPTION = ?, " +
                "     TITLE = ? " +
                "WHERE APP_PROVIDER = ? AND APP_NAME = ? AND APP_VERSION = ? ";

		String gatewayURLs = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().
				getAPIManagerConfiguration().getFirstProperty(GATEWAY_URL);

		String[] urlArray = gatewayURLs.split(",");
		String prodURL = urlArray[0];
		String logoutURL =  api.getLogoutURL();
        String logoutURLStart = prodURL.concat(api.getContext()).concat("/"+api.getId().getVersion());
        if(logoutURL.endsWith("/")){
            logoutURL = logoutURLStart.concat(logoutURL);
        }else{
            logoutURL = logoutURLStart.concat("/" + logoutURL);
        }

		try {
			connection = APIMgtDBUtil.getConnection();
			connection.setAutoCommit(false);

            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, api.getContext());
            prepStmt.setString(2, logoutURL);
            prepStmt.setBoolean(3, api.getAllowAnonymous());
            prepStmt.setString(4, api.getUrl());
            prepStmt.setBoolean(5, Boolean.parseBoolean(api.getTreatAsASite()));
            prepStmt.setString(6, api.getVisibleRoles());
            
            // (??????) ????????????, ????????? ?????? 
            prepStmt.setString(7, AppManagerUtil.replaceEmailDomainBack(api.getId().getProviderName()));
            prepStmt.setTimestamp(8, new Timestamp(System.currentTimeMillis()));
            
            // (??????) 2019.09.26 - ????????????, ?????????, ?????? ?????? ?????? 
            prepStmt.setString(9, api.getCategory());
            prepStmt.setString(10, api.getThumbnailUrl());
            prepStmt.setString(11, api.getDescription());
            
            // (??????) 2019.09.26 - ????????? ?????? ?????? 
            prepStmt.setString(12, api.getTitle());
            
            prepStmt.setString(13, AppManagerUtil.replaceEmailDomainBack(api.getId().getProviderName()));
            prepStmt.setString(14, api.getId().getApiName());
            prepStmt.setString(15, api.getId().getVersion());
            prepStmt.execute();

			int webAppId = getWebAppIdFromUUID(api.getUUID(), connection);

			//Update and persist policy partial info
			if(api.getPolicyPartials() != null) {
				JSONArray policyPartialIdList = (JSONArray) JSONValue.parse(api.getPolicyPartials());

				//Remove existing updated entitlement policies from IDP
                removeApplicationsEntitlementPolicies(webAppId, connection, authorizedAdminCookie);
            }

            if (api.getPolicyGroups() != null && !api.getPolicyGroups().isEmpty()) {
                JSONArray policyGroupIdList = (JSONArray) JSONValue.parse(api.getPolicyGroups());
                updatePolicyGroups(webAppId, policyGroupIdList.toArray(), connection);
            }

			//update java policy handlers
			if (api.getJavaPolicies() != null && !api.getJavaPolicies().isEmpty()) {
				JSONArray javaPolicyIdList = (JSONArray) JSONValue.parse(api.getJavaPolicies());
				updateJavaPolicies(webAppId, javaPolicyIdList.toArray(), connection);
            }

            updateURLTemplates(api, connection);

            //if selected as default version save entry
            saveDefaultVersionDetails(api, connection);

            connection.commit();

		} catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback when updating the WebApp" + api.getId() + " in the database", e);
                }
            }
            handleException("Error while updating the WebApp: " + api.getId() + " in the database", e);
        } finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}
	}

	private void updatePolicyGroups(int applicationId, Object[] policyPartialList,
									Connection connection) throws
			AppManagementException {

		PreparedStatement ps = null;
		String query = "DELETE FROM APM_POLICY_GROUP_MAPPING WHERE APP_ID=? ";

		try {
			if (log.isDebugEnabled()) {
				log.debug("Updating policy group mappings with webapp id : " + applicationId);
			}
			ps = connection.prepareStatement(query);
			ps.setInt(1, applicationId);
			ps.executeUpdate();

			//Save application's policy partial mapping data
			saveApplicationPolicyGroupsMappings(connection, applicationId, policyPartialList);

		} catch (SQLException e) {
			handleException("Error while deleting XACML policy partial mappings for webapp : " +
					applicationId, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, null, null);
		}
	}

    /**
     * Update Java policies
     *
     * @param applicationId
     * @param javaPolicyList
     * @param connection
     * @throws java.sql.SQLException on error
     */
    private void updateJavaPolicies(int applicationId, Object[] javaPolicyList,
                                    Connection connection) throws
            SQLException {
        PreparedStatement ps = null;
        String query = "DELETE FROM APM_APP_JAVA_POLICY_MAPPING WHERE APP_ID = ? ";

        try {
            if (log.isDebugEnabled()) {
                log.debug("Updating Java Policies mappings with webapp id : " + applicationId);
            }
            ps = connection.prepareStatement(query);
            ps.setInt(1, applicationId);
            ps.executeUpdate();

            //Save application's policy partial mapping data
            saveJavaPolicyMappings(connection, applicationId, javaPolicyList);
        } catch (SQLException e) {
            /* In the code im using a single SQL connection passed from the parent function so I'm logging the error here
            and throwing the SQLException so the connection will be disposed by the parent function. */
            log.error("Error while deleting XACML policy partial mappings for webapp : " +
                    applicationId, e);
            throw e;
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, null, null);
        }
    }

    public static String getSubscriptionType(int subsID)  throws AppManagementException {
        Connection connection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String subscriptionType = null;

        String getAPIQuery =
                            "SELECT " + "SUBS.SUBSCRIPTION_TYPE FROM  APM_SUBSCRIPTION SUBS" + " WHERE "
                                       + "SUBS.SUBSCRIPTION_ID = ?";

        try{
            connection = APIMgtDBUtil.getConnection();

            prepStmt = connection.prepareStatement(getAPIQuery);
            prepStmt.setInt(1, subsID );
            rs = prepStmt.executeQuery();

            if (rs.next()) {
                subscriptionType = rs.getString("SUBSCRIPTION_TYPE");
            }


            if (subscriptionType == null) {
                String msg = "Unable to find the WebApp:  in the database";
                log.error(msg);
                throw new AppManagementException(msg);
            }

        }  catch (SQLException e) {
            handleException("Error while locating WebApp:  from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, null, rs);
        }

        return subscriptionType;
    }
	public static int getAPIID(APIIdentifier apiId, Connection connection)
	                                                                      throws
                                                                          AppManagementException {
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		int id = -1;
		/* (??????) ??????????????? ?????? 
		String getAPIQuery =
		                     "SELECT " + "API.APP_ID FROM APM_APP API" + " WHERE "
		                             + "API.APP_PROVIDER = ?" + " AND API.APP_NAME = ?"
		                             + " AND API.APP_VERSION = ?";
		*/
		String getAPIQuery = SQLConstants.GET_APP_ID_SQL;
		try {
			if(connection == null) {
				connection = APIMgtDBUtil.getConnection();
			}
			
			prepStmt = connection.prepareStatement(getAPIQuery);
			prepStmt.setString(1, AppManagerUtil.replaceEmailDomainBack(apiId.getProviderName()));
			prepStmt.setString(2, apiId.getApiName());
			prepStmt.setString(3, apiId.getVersion());
			rs = prepStmt.executeQuery();
			if (rs.next()) {
				id = rs.getInt("APP_ID");
			}
			if (id == -1) {
				String msg = "Unable to find the WebApp: " + apiId + " in the database";
				log.error(msg);
				throw new AppManagementException(msg);
			}
		} catch (SQLException e) {
			handleException("Error while locating WebApp: " + apiId + " from the database", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, null, rs);
		}
		return id;
	}

	public void deleteAPI(APIIdentifier apiId, String authorizedAdminCookie) throws AppManagementException {
		Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		int id = -1;

		String deleteLCEventQuery = "DELETE FROM APM_APP_LC_EVENT WHERE APP_ID=? ";
		String deleteSubscriptionQuery = "DELETE FROM APM_SUBSCRIPTION WHERE APP_ID=?";
		String deleteConsumerQuery = "DELETE FROM APM_APP_CONSUMER_APPS WHERE SAML2_SSO_ISSUER=?";
		String deleteAPIQuery = "DELETE FROM APM_APP WHERE APP_PROVIDER=? AND APP_NAME=? AND APP_VERSION=? ";

		try {
			connection = APIMgtDBUtil.getConnection();
			connection.setAutoCommit(false);
			id = getAPIID(apiId, connection);

            //Remove webapp url mapping related entitlement policies from IDP
            removeApplicationsEntitlementPolicies(id, connection, authorizedAdminCookie);

			prepStmt = connection.prepareStatement(deleteSubscriptionQuery);
			prepStmt.setInt(1, id);
			prepStmt.execute();
			prepStmt.close();

			prepStmt = connection.prepareStatement(deleteLCEventQuery);
			prepStmt.setInt(1, id);
			prepStmt.execute();
			prepStmt.close();

			prepStmt = connection.prepareStatement(deleteConsumerQuery);
			prepStmt.setString(1, apiId.getApiName() + "-" + apiId.getVersion());
			prepStmt.execute();
			prepStmt.close();

			prepStmt = connection.prepareStatement(deleteAPIQuery);
			prepStmt.setString(1, AppManagerUtil.replaceEmailDomainBack(apiId.getProviderName()));
			prepStmt.setString(2, apiId.getApiName());
			prepStmt.setString(3, apiId.getVersion());
			prepStmt.execute();
			prepStmt.close();

            deleteDefaultVersionDetails(apiId, connection);
			connection.commit();
		} catch (SQLException e) {
			handleException("Error while removing the WebApp: " + apiId + " from the database", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}
	}

	/**
	 * Change access token status in to revoked in database level.
	 *
	 * @param key
	 *            API Key to be revoked
	 * @throws barley.appmgt.api.AppManagementException
	 *             on error in revoking access token
	 */
	public void revokeAccessToken(String key) throws AppManagementException {

		String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
				AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromAccessToken(key);
		}
		Connection conn = null;
		ResultSet rs = null;
		PreparedStatement ps = null;
		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
			String query =
					"UPDATE " + accessTokenStoreTable +
							" SET TOKEN_STATE='REVOKED' WHERE ACCESS_TOKEN= ? ";
			ps = conn.prepareStatement(query);
			ps.setString(1, AppManagerUtil.encryptToken(key));
			ps.execute();
			conn.commit();
		} catch (SQLException e) {
			handleException("Error in revoking access token: " + e.getMessage(), e);
		} catch (CryptoException e) {
			handleException("Error in revoking access token: " + e.getMessage(), e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
	}

	/**
	 * Get all applications associated with given tier
	 *
	 * @param tier
	 *            String tier name
	 * @return Application object array associated with tier
	 * @throws barley.appmgt.api.AppManagementException
	 *             on error in getting applications array
	 */
	public Application[] getApplicationsByTier(String tier) throws AppManagementException {
		if (tier == null) {
			return null;
		}
		Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;
		Application[] applications = null;

		String sqlQuery =
				"SELECT DISTINCT AMS.APPLICATION_ID,NAME,SUBSCRIBER_ID FROM APM_SUBSCRIPTION AMS,APM_APPLICATION AMA "
						+ "WHERE TIER_ID=? "
						+ "AND AMS.APPLICATION_ID=AMA.APPLICATION_ID";

		try {
			connection = APIMgtDBUtil.getConnection();
			prepStmt = connection.prepareStatement(sqlQuery);
			prepStmt.setString(1, tier);
			rs = prepStmt.executeQuery();
			ArrayList<Application> applicationsList = new ArrayList<Application>();
			Application application;
			while (rs.next()) {
				application =
						new Application(rs.getString("NAME"),
								getSubscriber(rs.getString("SUBSCRIBER_ID")));
				application.setId(rs.getInt("APPLICATION_ID"));
			}
			Collections.sort(applicationsList, new Comparator<Application>() {
				public int compare(Application o1, Application o2) {
					return o1.getName().compareToIgnoreCase(o2.getName());
				}
			});
			applications = applicationsList.toArray(new Application[applicationsList.size()]);

		} catch (SQLException e) {
			handleException("Error when reading the application information from"
					+ " the persistence store.", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}
		return applications;
	}

	private static void handleException(String msg, Throwable t) throws AppManagementException {
		log.error(msg, t);
		throw new AppManagementException(msg, t);
	}

    public static List<URLMapping> getURITemplatesPerAPIAsString(APIIdentifier identifier)
            throws AppManagementException {
        Connection conn = null;
		ResultSet resultSet = null;
		PreparedStatement ps = null;
		List<URLMapping> urlMappings = new ArrayList<URLMapping>();
		try {
			conn = APIMgtDBUtil.getConnection();

			// POLICY_GRP_ID ?????? ?????? 
            String sqlQuery = "SELECT URL_PATTERN, HTTP_METHOD, AUTH_SCHEME, THROTTLING_TIER, USER_ROLES, MAP.POLICY_GRP_ID, POLICY.NAME "
                    + "FROM  APM_APP_URL_MAPPING MAP "
                    + "LEFT JOIN APM_APP  APP ON MAP.APP_ID = APP.APP_ID "
                    + "LEFT JOIN APM_POLICY_GROUP POLICY ON MAP.POLICY_GRP_ID=POLICY.POLICY_GRP_ID "
                    + "WHERE APP_PROVIDER = ? AND APP_NAME = ? AND APP_VERSION = ? "
                    + "ORDER BY URL_MAPPING_ID ASC ";

			ps = conn.prepareStatement(sqlQuery);
			byte count = 0;
			ps.setString(++count, identifier.getProviderName());
			ps.setString(++count, identifier.getApiName());
			ps.setString(++count, identifier.getVersion());

			resultSet = ps.executeQuery();
			while (resultSet.next()) {
				URLMapping mapping = new URLMapping();
				urlMappings.add(mapping);
				mapping.setUrlPattern(resultSet.getString("URL_PATTERN"));
				mapping.setHttpMethod(resultSet.getString("HTTP_METHOD"));
				mapping.setAuthScheme(resultSet.getString("AUTH_SCHEME"));
				mapping.setThrottlingTier(resultSet.getString("THROTTLING_TIER"));
				mapping.setUserRoles(resultSet.getString("USER_ROLES"));
				// POLICY_GRP_ID ??????
				mapping.setPolicyGroupId(resultSet.getInt("POLICY_GRP_ID"));
				mapping.setPolicyGroupName(resultSet.getString("NAME"));
			}
		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
                    log.error("Failed to rollback when adding subscription for Application - "
                            + identifier.getApiName(), e);
                }
			}
			handleException("Failed to add subscriber data ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
		}
		return urlMappings;
	}

    public static String findConsumerKeyFromAccessToken(String accessToken)
            throws AppManagementException {
        String accessTokenStoreTable = AppMConstants.ACCESS_TOKEN_STORE_TABLE;
		if (AppManagerUtil.checkAccessTokenPartitioningEnabled() &&
		    AppManagerUtil.checkUserNameAssertionEnabled()) {
			accessTokenStoreTable = AppManagerUtil.getAccessTokenStoreTableFromAccessToken(accessToken);
		}
		Connection connection = null;
		PreparedStatement smt = null;
		ResultSet rs = null;
		String consumerKey = null;
		try {
			String getConsumerKeySql =
			                           "SELECT CONSUMER_KEY " + " FROM " + accessTokenStoreTable +
			                                   " WHERE ACCESS_TOKEN=?";
			connection = APIMgtDBUtil.getConnection();
			smt = connection.prepareStatement(getConsumerKeySql);
			smt.setString(1, AppManagerUtil.encryptToken(accessToken));
			rs = smt.executeQuery();
			while (rs.next()) {
				consumerKey = rs.getString(1);
			}
			if (consumerKey != null) {
				consumerKey = AppManagerUtil.decryptToken(consumerKey);
			}
		} catch (SQLException e) {
			handleException("Error while getting authorized domians.", e);
		} catch (CryptoException e) {
			handleException("Error while getting authorized domians.", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(smt, connection, rs);
		}
		return consumerKey;
	}


	public static boolean isContextExist(String context) {
		Connection connection = null;
		ResultSet resultSet = null;
		PreparedStatement prepStmt = null;

		String sql = "SELECT CONTEXT FROM APM_APP" + " WHERE CONTEXT= ?";
		try {
			connection = APIMgtDBUtil.getConnection();
			prepStmt = connection.prepareStatement(sql);
			prepStmt.setString(1, context);
			resultSet = prepStmt.executeQuery();

			while (resultSet.next()) {
				if (resultSet.getString(1) != null) {
					return true;
				}
			}
		} catch (SQLException e) {
			log.error("Failed to retrieve the WebApp Context ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, resultSet);
		}
		return false;
	}

	public static List<String> getAllAvailableContexts() {
		List<String> contexts = new ArrayList<String>();
		Connection connection = null;
		ResultSet resultSet = null;
		PreparedStatement prepStmt = null;

		String sql = "SELECT CONTEXT FROM APM_APP";
		try {
			connection = APIMgtDBUtil.getConnection();
			prepStmt = connection.prepareStatement(sql);
			resultSet = prepStmt.executeQuery();

			while (resultSet.next()) {
				contexts.add(resultSet.getString("CONTEXT"));
			}
		} catch (SQLException e) {
			log.error("Failed to retrieve the WebApp Context ", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, resultSet);
		}
		return contexts;
	}

    /**
     * Save the entitlement policy partial
     *
     * @param policyPartialName policy partial name
     * @param policyPartial     policy partial content
     * @param isSharedPartial   is policy partial shared
     * @param policyAuthor      author of the policy partial
     * @param tenantId          logged users tenant Id
     * @return policy partial id
     * @throws barley.appmgt.api.AppManagementException
     */
    public int saveEntitlementPolicyPartial(String policyPartialName, String policyPartial, boolean isSharedPartial,
											String policyAuthor,String policyPartialDesc,int tenantId) throws AppManagementException {

		Connection connection = null;
		PreparedStatement statementToInsertRecord = null;

		int policyPartialId = -1;

		try {

			if (log.isDebugEnabled()) {
				log.debug("Saves Entitlement Policy Partial with name : " +
						policyPartialName + " from author : " + policyAuthor);
			}
			connection = APIMgtDBUtil.getConnection();
			connection.setAutoCommit(false);
			String queryToInsertRecord = "INSERT INTO "
					+ "APM_ENTITLEMENT_POLICY_PARTIAL(NAME,CONTENT,SHARED,AUTHOR,DESCRIPTION,TENANT_ID)"
					+ " VALUES (?,?,?,?,?,?)";

			statementToInsertRecord = connection.prepareStatement(queryToInsertRecord, new String[]{"ENTITLEMENT_POLICY_PARTIAL_ID"});
			statementToInsertRecord.setString(1, policyPartialName);
			statementToInsertRecord.setString(2, policyPartial);
			statementToInsertRecord.setBoolean(3, isSharedPartial);
			statementToInsertRecord.setString(4, policyAuthor);
			statementToInsertRecord.setString(5, policyPartialDesc);
            statementToInsertRecord.setInt(6, tenantId);

			statementToInsertRecord.executeUpdate();

			ResultSet rs = statementToInsertRecord.getGeneratedKeys();
			while (rs.next()) {
				policyPartialId = Integer.parseInt(rs.getString(1));
			}
			rs.close();

			// Finally commit transaction.
			connection.commit();

		} catch (SQLException e) {
			if (connection != null) {
				try {
					connection.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the add entitlement policy partial with name : " +
							policyPartialName + " from author : " + policyAuthor, e1);
				}
			}
			handleException("Failed to add entitlement policy partial with name : " + policyPartialName +
					" from author : " + policyAuthor, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(statementToInsertRecord, connection, null);
		}
		return policyPartialId;
	}

    /**
     * Update existing policy partial
     * @param policyPartialId
     * @param policyPartial
     * @param author
     * @param isShared
	 * @param policyPartialDesc
     * @return
     * @throws barley.appmgt.api.AppManagementException
     */
	public boolean updateEntitlementPolicyPartial(int policyPartialId, String policyPartial
			, String author, boolean isShared, String policyPartialDesc) throws AppManagementException {

        Connection connection = null;
        PreparedStatement prepStmt = null;
        String queryToUpdatePolicyPartial = "UPDATE APM_ENTITLEMENT_POLICY_PARTIAL SET CONTENT=? ,SHARED=? ,DESCRIPTION=?" +
                " WHERE ENTITLEMENT_POLICY_PARTIAL_ID = ? ";

        ResultSet rs = null;
        String partialAuthor = null;
        boolean isSuccess = false;

        try {
			if (log.isDebugEnabled()) {
				log.debug("Updating Entitlement Policy Partial with id : " + policyPartial);
			}

            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            partialAuthor = this.getPolicyPartialAuthor(policyPartialId,connection);


            if (partialAuthor != null && partialAuthor.equals(author)) {
                prepStmt = connection.prepareStatement(queryToUpdatePolicyPartial);
                prepStmt.setString(1, policyPartial);
                prepStmt.setBoolean(2, isShared);
				prepStmt.setString(3, policyPartialDesc);
				prepStmt.setInt(4, policyPartialId);
                prepStmt.executeUpdate();
                isSuccess = true;
            }
            // Finally commit transaction.
            connection.commit();

		} catch (SQLException e) {
			if (connection != null) {
				try {
					connection.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the update of entitlement policy partial with id : " + policyPartial, e1);
				}
			}
			handleException("Failed to update to entitlement policy partial with id : " + policyPartial, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, null);

		}
		return isSuccess;
	}


   /**
     * This method returns the author of a policy partial
     * @param policyPartialId policy partial id
     * @param connection
     * @return other of policy partial
     * @throws barley.appmgt.api.AppManagementException
     */
    private String getPolicyPartialAuthor(int policyPartialId, Connection connection) throws
                                                                                      AppManagementException {

        PreparedStatement prepStmt = null;
        String author = null;
        ResultSet rs = null;
        String queryToGetPartialAuthor = "SELECT AUTHOR FROM APM_ENTITLEMENT_POLICY_PARTIAL  " +
                "WHERE ENTITLEMENT_POLICY_PARTIAL_ID  = ?";


        try {
            prepStmt = connection.prepareStatement(queryToGetPartialAuthor);
            prepStmt.setInt(1,policyPartialId);

            rs = prepStmt.executeQuery();
            while (rs.next()) {
                author = rs.getString("AUTHOR");
            }

        } catch (SQLException e) {
            handleException("Error while retrieving author of the policy parital with policy id : " +
                    policyPartialId, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, null, rs);
        }
        return author;
    }
	/**
	 * Get policy partial from policy partial id
	 *
	 * @param policyPartialId policy partial id
	 * @return policy partial content
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public EntitlementPolicyPartial getPolicyPartial(int policyPartialId) throws
                                                                          AppManagementException {

        Connection connection = null;
        PreparedStatement statementToGetPolicyPartial = null;
        EntitlementPolicyPartial entitlementPolicyPartial = null;
        ResultSet rs = null;
        String queryToGetPolicyPartial =
                "SELECT NAME, CONTENT, SHARED, DESCRIPTION, AUTHOR " +
                        "FROM APM_ENTITLEMENT_POLICY_PARTIAL " +
                        "WHERE ENTITLEMENT_POLICY_PARTIAL_ID = ?";

        try {

            if (log.isDebugEnabled()) {
                log.debug("Retrieving policy content of policy partial with id : " + policyPartialId);
            }

            connection = APIMgtDBUtil.getConnection();
            statementToGetPolicyPartial = connection.prepareStatement(queryToGetPolicyPartial);
            statementToGetPolicyPartial.setInt(1, policyPartialId);

            rs = statementToGetPolicyPartial.executeQuery();
            while (rs.next()) {
                entitlementPolicyPartial = new EntitlementPolicyPartial();
                entitlementPolicyPartial.setPolicyPartialName(rs.getString("NAME"));
                entitlementPolicyPartial.setPolicyPartialContent(rs.getString("CONTENT"));
                entitlementPolicyPartial.setAuthor(rs.getString("AUTHOR"));
                entitlementPolicyPartial.setDescription(rs.getString("DESCRIPTION"));
                entitlementPolicyPartial.setShared(rs.getBoolean("SHARED"));
            }

		} catch (SQLException e) {
			handleException("Failed to retrieve application entitlement policy partial with id : " +
					policyPartialId, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(statementToGetPolicyPartial, connection, rs);
		}
		return entitlementPolicyPartial;
	}

	/**
	 * Get webapp id with reference to a given webapp UUID
	 *
	 * @param uuid UUID of the given web application
	 * @return webapp id
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public int getWebAppId(String uuid) throws AppManagementException {

		Connection connection = null;
		int webAppId = -1;

		try {
			if (log.isDebugEnabled()) {
				log.debug("Retrieving id of webapp with uuid : " + uuid);
			}
			connection = APIMgtDBUtil.getConnection();
			webAppId = getWebAppIdFromUUID(uuid, connection);

		} catch (SQLException e) {
			handleException("Failed to retrieve ID for application with uuid : " + uuid, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(null, connection, null);
		}
		return webAppId;
	}

	/**
	 * Get the apps which use the given policy partial
	 *
	 * @param policyPartialId Policy Partial Id
	 * @return apps' name
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public List<APIIdentifier> getAssociatedApps(int policyPartialId) throws AppManagementException {
		Connection connection = null;
		PreparedStatement statementToGetAppsName = null;
		List<APIIdentifier> apiIdentifiers = new ArrayList<APIIdentifier>();
		ResultSet rs = null;

		String queryToGetAppsName = "SELECT DISTINCT APP.APP_NAME, APP.APP_PROVIDER, APP.APP_VERSION" +
				" FROM APM_POLICY_GRP_PARTIAL_MAPPING ENT " +
				" INNER JOIN APM_APP_URL_MAPPING URL ON URL.POLICY_GRP_ID=ENT.POLICY_GRP_ID " +
				" LEFT JOIN APM_APP APP ON APP.APP_ID=URL.APP_ID " +
				" WHERE ENT.POLICY_PARTIAL_ID = ? ";

		try {
			connection = APIMgtDBUtil.getConnection();
			statementToGetAppsName = connection.prepareStatement(queryToGetAppsName);
            statementToGetAppsName.setInt(1, policyPartialId);
            rs = statementToGetAppsName.executeQuery();

			APIIdentifier apiIdentifier = null;
			while (rs.next()) {
				String providerName = rs.getString("APP_PROVIDER");
				String apiName = rs.getString("APP_NAME");
				String version = rs.getString("APP_VERSION");
				apiIdentifier = new APIIdentifier(providerName, apiName, version);
				apiIdentifiers.add(apiIdentifier);
			}
		} catch (SQLException e) {
            handleException("Failed to retrieve apps associated with Policy Partial Id:" + policyPartialId, e);
        } finally {
			APIMgtDBUtil.closeAllConnections(statementToGetAppsName, connection, rs);
		}
		return apiIdentifiers;
	}

    /**
     * Get the list of entitlement policy partial which are shared
     *
     * @param tenantId logged users tenant Id
     * @return list of policy partial
     * @throws barley.appmgt.api.AppManagementException
     */
    public List<EntitlementPolicyPartial> getSharedEntitlementPolicyPartialsList(int tenantId) throws
                                                                                   AppManagementException {

        Connection connection = null;
        PreparedStatement statementToGetPolicyPartialList = null;
        List<EntitlementPolicyPartial> entitlementPolicyPartialList = new ArrayList<EntitlementPolicyPartial>();
        ResultSet rs = null;
        boolean isShared = true;

        String queryToGetPolicyPartial = "SELECT ENTITLEMENT_POLICY_PARTIAL_ID, NAME, CONTENT, SHARED, AUTHOR, " +
                "DESCRIPTION " +
                "FROM APM_ENTITLEMENT_POLICY_PARTIAL WHERE SHARED = ? " +
                "AND TENANT_ID = ? ";

        try {
            connection = APIMgtDBUtil.getConnection();
            statementToGetPolicyPartialList = connection.prepareStatement(queryToGetPolicyPartial);
            statementToGetPolicyPartialList.setBoolean(1, isShared);
            statementToGetPolicyPartialList.setInt(2, tenantId);

            rs = statementToGetPolicyPartialList.executeQuery();

            while (rs.next()) {
                EntitlementPolicyPartial policyPartial = new EntitlementPolicyPartial();
                policyPartial.setPolicyPartialId(rs.getInt("ENTITLEMENT_POLICY_PARTIAL_ID"));
                policyPartial.setPolicyPartialName(rs.getString("NAME"));

                // If the content cannot be parsed skip that policy partial.
                String ruleCondition = exctractConditionFromPolicyPartialContent(rs.getString("CONTENT"));
                if(ruleCondition == null){
                	log.error(String.format("Can't read content for the policy partial '%s'.", policyPartial.getPolicyPartialName()));
                	continue;
                }else{
                	policyPartial.setPolicyPartialContent(ruleCondition);
                }

                String ruleEffect = extractEffectFromPolicyPartialContent(rs.getString("CONTENT"));

                // No need to handle parsing errors at this point since they are captured in the previous block.
                if(ruleEffect != null){
                	policyPartial.setRuleEffect(ruleEffect);
                }

                policyPartial.setShared(rs.getBoolean("SHARED"));
                policyPartial.setAuthor(rs.getString("AUTHOR"));
				policyPartial.setDescription(rs.getString("DESCRIPTION"));
                entitlementPolicyPartialList.add(policyPartial);
            }

        } catch (SQLException e) {
            handleException("Failed to retrieve shared entitlement policy partials.", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToGetPolicyPartialList, connection, rs);
        }
        return entitlementPolicyPartialList;
    }

	/**
	 * Delete entitlement policy partial
	 *
	 * @param policyPartialId policy partial id
	 * @param author          author of the policy partial
	 * @return true if success else false
	 * @throws barley.appmgt.api.AppManagementException
	 */
	public boolean deletePolicyPartial(int policyPartialId, String author) throws
                                                                           AppManagementException {

        Connection connection = null;
        PreparedStatement prepStmt = null;
        String queryToDeletePolicyPartial =
                "DELETE FROM APM_ENTITLEMENT_POLICY_PARTIAL  WHERE ENTITLEMENT_POLICY_PARTIAL_ID= ?";
        ResultSet rs = null;
        String partialAuthor = null;
        boolean isSuccess = false;

        try {

			if(log.isDebugEnabled()){
				log.debug("Deleting policy partial with partial id : " + policyPartialId);
			}

            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);

            partialAuthor = this.getPolicyPartialAuthor(policyPartialId,connection);

            //Only author of the policy partial can delete
            if (partialAuthor != null && partialAuthor.equals(author)) {
                prepStmt = connection.prepareStatement(queryToDeletePolicyPartial);
                prepStmt.setInt(1, policyPartialId);
                prepStmt.execute();
                isSuccess = true;
            }
			// Finally commit transaction.
			connection.commit();

		} catch (SQLException e) {
			if (connection != null) {
				try {
					connection.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback the deletion of entitlement policy partial with id : " +
							policyPartialId, e1);
				}
			}
			handleException("Failed to delete entitlement policy partial with partial id : " + policyPartialId, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, null);
		}
		return isSuccess;
	}

    private String exctractConditionFromPolicyPartialContent(String policyPartialContent){

    	try {
			StAXOMBuilder builder = new StAXOMBuilder(new ByteArrayInputStream(policyPartialContent.getBytes()));
			OMElement conditionNode = (OMElement) builder.getDocumentElement().getChildrenWithLocalName("Condition").next();

			return conditionNode.toString();

		} catch (XMLStreamException e) {
			log.error("Can't extract the 'Condition' node from the 'Rule' node.", e);
			return null;
		}
    }

	private String extractEffectFromPolicyPartialContent(String policyPartialContent) {

		try {
			StAXOMBuilder builder = new StAXOMBuilder(new ByteArrayInputStream(policyPartialContent.getBytes()));
			String effect = builder.getDocumentElement().getAttributeValue(new QName("Effect"));

			return effect;

		} catch (XMLStreamException e) {
			log.error("Can't extract the 'Effect' attribute value from the 'Rule' node.", e);
			return null;
		}

	}

    /**
     * Remove existing updated entitlement policies from IDP
     *
     * @param applicationId   Applicatoin id
     * @param authorizedAdminCookie Autherized cookie to access IDP admin services
     * @param connection      DB Connection
     * @throws barley.appmgt.api.AppManagementException
     */
    private void removeApplicationsEntitlementPolicies(int applicationId, Connection connection, String authorizedAdminCookie)
            throws
            AppManagementException {
        PreparedStatement statementToRetrievePolicyIds = null;
		ResultSet rs = null;

        String queryToGetPolicyIdList = "SELECT POLICY_ID " +
                "FROM APM_POLICY_GRP_PARTIAL_MAPPING ENT " +
                "INNER JOIN APM_APP_URL_MAPPING MAP ON MAP.POLICY_GRP_ID = ENT.POLICY_GRP_ID AND  MAP.APP_ID= ? ";

		try {
			statementToRetrievePolicyIds = connection.prepareStatement(queryToGetPolicyIdList);
			statementToRetrievePolicyIds.setInt(1, applicationId);
			rs = statementToRetrievePolicyIds.executeQuery();

			//Define Entitlement Service
			AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
					getAPIManagerConfigurationService().getAPIManagerConfiguration();


            EntitlementService entitlementService = null;
            int count = 0;
            while (rs.next()) {
                if (count == 0) {
                    entitlementService = EntitlementServiceFactory.getEntitlementService(config, authorizedAdminCookie);
                }
                String policyId = rs.getString("POLICY_ID");
                //If policyId is not null, remove the Entitlement policy with reference to policy id
                if (policyId != null) {
                    entitlementService.removePolicy(policyId);
                }
                count++;
            }
            rs.close();
        } catch (SQLException e) {
			handleException("Error while retrieving URL XACML policy ids for WebApp : " +
					applicationId, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(statementToRetrievePolicyIds, null, null);
		}
	}

    /**
     * Persists the application's entitlement policy partials in database
     *
     * @param applicationId application id
     * @param partialIds    policy partial ids
     * @throws barley.appmgt.api.AppManagementException
     */
    public void saveApplicationPolicyPartialsMappings(Connection connection, int applicationId, Object[] partialIds)
            throws AppManagementException {

        PreparedStatement preparedStatement = null;
        String queryToInsertRecord = "INSERT INTO "
                + "APM_APP_XACML_PARTIAL_MAPPINGS(APP_ID,PARTIAL_ID)"
                + " VALUES (?,?)";

        try {
            preparedStatement = connection.prepareStatement(queryToInsertRecord);

            for (Object partialId : partialIds) {
                preparedStatement.setInt(1, applicationId);
                preparedStatement.setInt(2, Integer.parseInt(partialId.toString()));
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();

        } catch (SQLException e) {
            handleException("Error while persisting application-policy partial mappings of webapp with id :  " +
                    applicationId, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    /**
     * Update URLMapping - Entittlement policy patial mappings
     *
     * @param xacmlPolicyTemplateContexts xacml poilicy partial template contexts
     * @throws barley.appmgt.api.AppManagementException
     */
    public void updateURLEntitlementPolicyPartialMappings(List<XACMLPolicyTemplateContext> xacmlPolicyTemplateContexts)
            throws AppManagementException {

        String query = "UPDATE APM_POLICY_GRP_PARTIAL_MAPPING SET POLICY_ID = ? " +
                "WHERE POLICY_GRP_ID = ? " +
                "AND POLICY_PARTIAL_ID = ?";

        Connection connection = null;
        PreparedStatement preparedStatement = null;

        try {
            connection = APIMgtDBUtil.getConnection();
            preparedStatement = connection.prepareStatement(query);

            for (XACMLPolicyTemplateContext context : xacmlPolicyTemplateContexts) {
                preparedStatement.setString(1, context.getPolicyId());
                preparedStatement.setInt(2, context.getPolicyGroupId());
                preparedStatement.setInt(3, context.getRuleId());
                preparedStatement.addBatch();
            }

            preparedStatement.executeBatch();

            // Finally commit transaction.
            connection.commit();

        } catch (SQLException e) {
            handleException("Failed to update URL - Entitlement Policy Partial mappings", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, null);
        }
    }


    private static class SubscriptionInfo {
		private int subscriptionId;
		private String tierId;
		private String context;
		private int applicationId;
		private String accessToken;
		private String tokenType;
	}

	/**
	 * Identify whether the loggedin user used his ordinal username or email
	 *
	 * @param userId
	 * @return
	 */
	private boolean isUserLoggedInEmail(String userId) {

		if (userId.contains("@")) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Identify whether the loggedin user used his Primary Login name or
	 * Secondary login name
	 *
	 * @param userId
	 * @return
	 */
	private boolean isSecondaryLogin(String userId) {

		Map<String, Map<String, String>> loginConfiguration =
		                                                      ServiceReferenceHolder.getInstance()
		                                                                            .getAPIManagerConfigurationService()
		                                                                            .getAPIManagerConfiguration()
		                                                                            .getLoginConfiguration();
		if (loginConfiguration.get(EMAIL_LOGIN) != null) {
			Map<String, String> emailConf = loginConfiguration.get(EMAIL_LOGIN);
			if ("true".equalsIgnoreCase(emailConf.get(PRIMARY_LOGIN))) {
				if (isUserLoggedInEmail(userId)) {
					return false;
				} else {
					return true;
				}
			}
			if ("false".equalsIgnoreCase(emailConf.get(PRIMARY_LOGIN))) {
				if (isUserLoggedInEmail(userId)) {
					return true;
				} else {
					return false;
				}
			}

		}
		if (loginConfiguration.get(USERID_LOGIN) != null) {
			Map<String, String> userIdConf = loginConfiguration.get(USERID_LOGIN);
			if ("true".equalsIgnoreCase(userIdConf.get(PRIMARY_LOGIN))) {
				if (isUserLoggedInEmail(userId)) {
					return true;
				} else {
					return false;
				}
			}
			if ("false".equalsIgnoreCase(userIdConf.get(PRIMARY_LOGIN))) {
				if (isUserLoggedInEmail(userId)) {
					return false;
				} else {
					return true;
				}
			}

		}
		return false;
	}

	/**
	 * Get the primaryLogin name using secondary login name. Primary secondary
	 * Configuration is provided in the identitiy.xml. In the userstore, it is
	 * users responsibility TO MAINTAIN THE SECONDARY LOGIN NAME AS UNIQUE for
	 * each and every users. If it is not unique, we will pick the very first
	 * entry from the userlist.
	 *
	 * @param login
	 * @return
	 */
	private String getPrimaryloginFromSecondary(String login) {
		Map<String, Map<String, String>> loginConfiguration =
		                                                      ServiceReferenceHolder.getInstance()
		                                                                            .getAPIManagerConfigurationService()
		                                                                            .getAPIManagerConfiguration()
		                                                                            .getLoginConfiguration();
		String claimURI = null, username = null;
		if (isUserLoggedInEmail(login)) {
			Map<String, String> emailConf = loginConfiguration.get(EMAIL_LOGIN);
			claimURI = emailConf.get(CLAIM_URI);
		} else {
			Map<String, String> userIdConf = loginConfiguration.get(USERID_LOGIN);
			claimURI = userIdConf.get(CLAIM_URI);
		}

		try {
			RemoteUserManagerClient rmUserlient = new RemoteUserManagerClient(login);
			String user[] = rmUserlient.getUserList(claimURI, login);
			if (user.length > 0) {
				username = user[0].toString();
			}
		} catch (Exception e) {
			log.error("Error while retriivng the primaryLogin name using seconadry loginanme : " +
			          login, e);
		}
		return username;
	}

	/**
	 * identify the login username is primary or secondary
	 *
	 * @param userID
	 * @return
	 */
	private String getLoginUserName(String userID) {
		String primaryLogin = userID;
		if (isSecondaryLogin(userID)) {
			primaryLogin = getPrimaryloginFromSecondary(userID);
		}
		return primaryLogin;
	}

	private long getApplicationAccessTokenValidityPeriod() {
		return OAuthServerConfiguration.getInstance()
				.getApplicationAccessTokenValidityPeriodInSeconds();

	}

    /**
     * returns the application related data against the given application
     *
     * @param appContext
     * @param appVersion
     * @param consumer
     * @param authenticatedIDPs
     * @return application related data
     * @throws AppManagementException
     */
    public APIKeyValidationInfoDTO getApplicationData(String appContext, String appVersion, String consumer,
                                                      AuthenticatedIDP[] authenticatedIDPs)
            throws AppManagementException {
        Connection connection = null;
        APIKeyValidationInfoDTO info = null;
        try {
            connection = APIMgtDBUtil.getConnection();
            boolean hasValidSubscription = false;
            boolean isSelfSubscriptionEnabled = ServiceReferenceHolder.getInstance()
                    .getAPIManagerConfigurationService()
                    .getAPIManagerConfiguration().isSelfSubscriptionEnabled();
            boolean isEnterpriseSubscriptionEnabled = ServiceReferenceHolder.getInstance()
                    .getAPIManagerConfigurationService()
                    .getAPIManagerConfiguration().isEnterpriseSubscriptionEnabled();
            Subscription subscription = null;

            if (isSelfSubscriptionEnabled) {
                subscription = getIndividualSubscription(consumer, appContext, appVersion, connection);
                // If there is an individual subscription proceed with it.
                if (subscription != null) {
                    hasValidSubscription = true;
                } else if (isEnterpriseSubscriptionEnabled) {
                    if (authenticatedIDPs != null) {
                        subscription = getEnterpriseSubscription(appContext, appVersion, connection);
                        // If there is an enterprise subscription for this app and the authenticated IDP is a trusted
                        // IDP, proceed.
                        if (subscription != null && subscription.isTrustedIdp(authenticatedIDPs)) {
                            hasValidSubscription = true;
                        }
                    }
                }
            } else if (!isSelfSubscriptionEnabled) {
                if (isEnterpriseSubscriptionEnabled) {
                    if (authenticatedIDPs != null) {
                        subscription = getEnterpriseSubscription(appContext, appVersion, connection);
                        // If there is an enterprise subscription for this app and the authenticated IDP is a trusted
                        // IDP, proceed.
                        if (subscription != null && subscription.isTrustedIdp(authenticatedIDPs)) {
                            hasValidSubscription = true;
                        }
                    }
                } else {
                    hasValidSubscription = true;
                }
            }

            // If there is no either an individual subscription or an enterprise subscription, don't authorize;
            info = new APIKeyValidationInfoDTO();

            if (!hasValidSubscription) {
                info.setValidationStatus(AppMConstants.API_AUTH_FORBIDDEN);
                info.setAuthorized(false);
                return info;
            }

            // Get App info from the database.
            WebApp webApp = getWebApp(appContext, appVersion, connection);

            info.setApiName(webApp.getId().getApiName());
            info.setApiVersion(webApp.getId().getVersion());
            info.setApiPublisher(webApp.getId().getProviderName());
            info.setTier(AppMConstants.UNLIMITED_TIER);
            info.setContext(appContext);
            info.setLogoutURL(webApp.getLogoutURL());

            if(isEnterpriseSubscriptionEnabled) {
                // Set trusted IDPs.
                info.setTrustedIdp(JSONValue.toJSONString(subscription.getTrustedIdps()));
            }

            info.setAuthorized(true);
            if (isSelfSubscriptionEnabled || isEnterpriseSubscriptionEnabled) {
                // Validate the subscription status.
                String subscriptionStatus = subscription.getSubscriptionStatus();

                if (subscriptionStatus.equals(AppMConstants.SubscriptionStatus.BLOCKED)) {
                    info.setValidationStatus(AppMConstants.KeyValidationStatus.API_BLOCKED);
                    info.setAuthorized(false);
                } else if (AppMConstants.SubscriptionStatus.ON_HOLD.equals(subscriptionStatus) ||
                        AppMConstants.SubscriptionStatus.REJECTED.equals(subscriptionStatus)) {
                    info.setValidationStatus(AppMConstants.KeyValidationStatus.SUBSCRIPTION_INACTIVE);
                    info.setAuthorized(false);
                }
            }

        } catch (SQLException e) {
            handleException("Error while getting WebApp data for : " + appContext + "-" +
                                    appVersion, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null, connection, null);
        }
        return info;
    }

	/**
	 * Returns the web application id of a given web application uuid
	 *
	 * @param webAppUUID webapplication id
	 * @param connection SQL Connection to the data store
	 * @return web application unique id
	 * @throws barley.appmgt.api.AppManagementException
	 */
	private int getWebAppIdFromUUID(String webAppUUID, Connection connection) throws
                                                                              AppManagementException {

		PreparedStatement statementToGetWebAppId = null;
		int webAppId = -1;
		ResultSet rs = null;
		String queryToGetWebAppId = "SELECT APP_ID" +
				" FROM APM_APP " +
				" WHERE UUID = ?";

		try {
			statementToGetWebAppId = connection.prepareStatement(queryToGetWebAppId);
			statementToGetWebAppId.setString(1, webAppUUID);

			rs = statementToGetWebAppId.executeQuery();
			while (rs.next()) {

				webAppId = rs.getInt("APP_ID");
			}

		} catch (SQLException e) {
			handleException("Error while retrieving id of the webapp with uuid : " +
					webAppUUID, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(statementToGetWebAppId, null, rs);
		}
		return webAppId;
	}

    /**
     * Returns the individual subscription for the given user ID, app context and app version.
     * @param userId User Id
     * @param appContext App context of the URL
     * @param appVersion App version
     * @param connection SQL Connection to the data store
     * @return The subscription if there is one, null otherwise.
     */
    private Subscription getIndividualSubscription(String userId, String appContext, String appVersion, Connection connection){

        Subscription subscription = null;

        String queryToFindIndividualSubscription = "SELECT " +
                                                    "SUB2.SUBSCRIPTION_ID AS SUBSCRIPTION_ID," +
                                                    "SUB2.APP_ID AS APP_ID," +
                                                    "SUB2.APPLICATION_ID AS APPLICATION_ID," +
                                                    "SUB2.SUB_STATUS AS SUB_STATUS," +
                                                    "SUB2.TIER_ID AS TIER_ID," +
                                                    "SUB2.TRUSTED_IDP AS TRUSTED_IDPS " +
                                                    "FROM " +
                                                    "APM_SUBSCRIBER SUB1 INNER JOIN APM_APPLICATION APP " +
                                                    "ON SUB1.SUBSCRIBER_ID = APP.SUBSCRIBER_ID " +
                                                    "INNER JOIN APM_SUBSCRIPTION SUB2 " +
                                                    "ON SUB2.APPLICATION_ID = APP.APPLICATION_ID " +
                                                    "INNER JOIN APM_APP API " +
                                                    "ON SUB2.APP_ID = API.APP_ID " +
                                                    "WHERE " +
                                                    "SUB2.SUBSCRIPTION_TYPE = ? " +
                                                    "AND SUB1.USER_ID = ? " +
                                                    "AND API.CONTEXT = ? " +
                                                    "AND API.APP_VERSION = ?";

        PreparedStatement statementToFindIndividualSubscriptions = null;
        ResultSet individualSubscriptionResult = null;

        try{
            statementToFindIndividualSubscriptions = connection.prepareStatement(queryToFindIndividualSubscription);

            byte parameterIndex = 0;
            statementToFindIndividualSubscriptions.setString(++parameterIndex, Subscription.SUBSCRIPTION_TYPE_INDIVIDUAL);
            statementToFindIndividualSubscriptions.setString(++parameterIndex, userId);
            statementToFindIndividualSubscriptions.setString(++parameterIndex, appContext);
            statementToFindIndividualSubscriptions.setString(++parameterIndex, appVersion);

            individualSubscriptionResult = statementToFindIndividualSubscriptions.executeQuery();

            while(individualSubscriptionResult.next()){
                subscription = new Subscription();
                subscription.setSubscriptionId(individualSubscriptionResult.getInt("SUBSCRIPTION_ID"));
                subscription.setSubscriptionType(Subscription.SUBSCRIPTION_TYPE_INDIVIDUAL);
                subscription.setWebAppId(individualSubscriptionResult.getInt("APP_ID"));
                subscription.setApplicationId(individualSubscriptionResult.getInt("APPLICATION_ID"));
                subscription.setSubscriptionStatus(individualSubscriptionResult.getString("SUB_STATUS"));
                subscription.setTierId(individualSubscriptionResult.getString("TIER_ID"));

                // Set trusted IDPs.
                String trustedIdpsJson = individualSubscriptionResult.getString("TRUSTED_IDPS");
                Object decodedJson = null;
                if (trustedIdpsJson != null) {
                    decodedJson = JSONValue.parse(trustedIdpsJson);
                }

                if(decodedJson != null){
                    for(Object item : (JSONArray)decodedJson){
                        subscription.addTrustedIdp(item.toString());
                    }
                }
            }

        }catch (SQLException exception){
            log.error(String.format("Error while getting the individual subscription for user : '%s' , " +
                                                                        "context : '%s' , version : '%s' ",
                                                                            userId, appContext, appVersion), exception);
            return null;
        }finally {
            APIMgtDBUtil.closeAllConnections(statementToFindIndividualSubscriptions, null, individualSubscriptionResult);
        }

        return subscription;
    }

    /**
     * Returns the enterprise subscription for the given app context and the app version.
     * @param appContext App context of the URL
     * @param appVersion App version
     * @param connection SQL Connection to the data store
     * @return The enterprise subscription if there is one. Null otherwise.
     */
    public Subscription getEnterpriseSubscription(String appContext, String appVersion, Connection connection){

        Subscription subscription = null;

        String queryToFindEnterpriseSubscription = "SELECT " +
                                                    "SUB.SUBSCRIPTION_ID AS SUBSCRIPTION_ID," +
                                                    "SUB.APP_ID AS APP_ID," +
                                                    "SUB.APPLICATION_ID AS APPLICATION_ID," +
                                                    "SUB.SUB_STATUS AS SUBSCRIPTION_STATUS," +
                                                    "SUB.TIER_ID AS TIER_ID," +
                                                    "SUB.TRUSTED_IDP AS TRUSTED_IDPS " +
                                                    "FROM " +
                                                    "APM_APPLICATION APP INNER JOIN APM_SUBSCRIPTION SUB " +
                                                    "ON SUB.APPLICATION_ID = APP.APPLICATION_ID " +
                                                    "INNER JOIN APM_APP API " +
                                                    "ON SUB.APP_ID = API.APP_ID " +
                                                    "WHERE " +
                                                    "SUB.SUBSCRIPTION_TYPE = ? " +
                                                    "AND API.CONTEXT = ? " +
                                                    "AND API.APP_VERSION = ?";

        PreparedStatement statementToFindEnterpriseSubscriptions = null;
        ResultSet enterpriseSubscriptionResult = null;

        try{
            statementToFindEnterpriseSubscriptions = connection.prepareStatement(queryToFindEnterpriseSubscription);

            byte parameterIndex = 0;
            statementToFindEnterpriseSubscriptions.setString(++parameterIndex, Subscription.SUBSCRIPTION_TYPE_ENTERPRISE);
            statementToFindEnterpriseSubscriptions.setString(++parameterIndex, appContext);
            statementToFindEnterpriseSubscriptions.setString(++parameterIndex, appVersion);

            enterpriseSubscriptionResult = statementToFindEnterpriseSubscriptions.executeQuery();

            while(enterpriseSubscriptionResult.next()){

                subscription = new Subscription();
                subscription.setSubscriptionId(enterpriseSubscriptionResult.getInt("SUBSCRIPTION_ID"));
                subscription.setSubscriptionType(Subscription.SUBSCRIPTION_TYPE_ENTERPRISE);
                subscription.setWebAppId(enterpriseSubscriptionResult.getInt("APP_ID"));
                subscription.setApplicationId(enterpriseSubscriptionResult.getInt("APPLICATION_ID"));
                subscription.setSubscriptionStatus(enterpriseSubscriptionResult.getString("SUBSCRIPTION_STATUS"));
                subscription.setTierId(enterpriseSubscriptionResult.getString("TIER_ID"));

                // Set trusted IDPs.
                String trustedIdpsJson = enterpriseSubscriptionResult.getString("TRUSTED_IDPS");
                Object  decodedJson = JSONValue.parse(trustedIdpsJson);
                if(decodedJson != null){
                    for(Object item : (JSONArray)decodedJson){
                        subscription.addTrustedIdp(item.toString());
                    }
                }
            }
        }catch (SQLException exception){
            log.error(String.format("Error while getting the individual subscription for context : '%s' ," +
                                                                                                   " version : '%s' ",
                                                                                    appContext, appVersion), exception);
            return null;
        }finally {
            APIMgtDBUtil.closeAllConnections(statementToFindEnterpriseSubscriptions, null, enterpriseSubscriptionResult);
        }

        return subscription;
    }

    /**
     * This method returns basic webapp details(provider,name,logout url) for given context and version of the
     * webapp
     *
     * @param context    Context of the WebApp
     * @param version    Version of the WebApp
     * @param connection SQL Connection to the data store
     * @return WebApp
     */
    private WebApp getWebApp(String context, String version, Connection connection) throws AppManagementException {

        WebApp webapp = null;
        PreparedStatement statementToGetAppInfo = null;
        ResultSet appInfoResult = null;

        String queryToGetAppInfo = "SELECT " +
                "APP.APP_PROVIDER AS APP_PROVIDER, " +
                "APP.APP_NAME AS APP_NAME, " +
                "APP.LOG_OUT_URL AS LOGOUT_URL " +
                "FROM " +
                "APM_APP APP " +
                "WHERE " +
                "APP.CONTEXT = ? " +
                "AND APP.APP_VERSION = ?";

        try {
            statementToGetAppInfo = connection.prepareStatement(queryToGetAppInfo);
            statementToGetAppInfo.setString(1, context);
            statementToGetAppInfo.setString(2, version);
            appInfoResult = statementToGetAppInfo.executeQuery();

            while (appInfoResult.next()) {
                String apiProvider = appInfoResult.getString("APP_PROVIDER");
                String apiName = appInfoResult.getString("APP_NAME");
                String apiVersion = version;
                APIIdentifier identifier = new APIIdentifier(apiProvider, apiName, apiVersion);
                webapp = new WebApp(identifier);

                String logoutUrl = appInfoResult.getString("LOGOUT_URL");
                webapp.setLogoutURL(logoutUrl);
            }

        } catch (SQLException exception) {
            String errorMessage = String.format("Error while getting app for the app context :" +
                                                        " '%s' and version :'%s'", context, version);
            handleException(errorMessage, exception);
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToGetAppInfo, null, appInfoResult);
        }
        return webapp;
    }


    /**
     * Get the all web apps with provider, name ,context and version
     *
     * @return web apps
     * @throws barley.appmgt.api.AppManagementException
     */
    public List<WebApp> getAllWebApps() throws AppManagementException {
        WebApp webApp = null;
        Connection connection = null;
        PreparedStatement statementToGetAppInfo = null;
        ResultSet appInfoResult = null;
        List<WebApp> webApps = new ArrayList<WebApp>();
        String queryToGetAppInfo = "SELECT " +
                "APP.APP_PROVIDER AS APP_PROVIDER, " +
                "APP.APP_NAME AS APP_NAME, " +
                "APP.APP_VERSION AS APP_VERSION, " +
                "APP.CONTEXT AS CONTEXT, " +
                "APP.APP_ENDPOINT AS APP_ENDPOINT, " +
                "COALESCE(DEF.DEFAULT_APP_VERSION,'') AS DEFAULT_APP_VERSION " +
                "FROM " +
                "APM_APP APP " +
                "LEFT JOIN APM_APP_DEFAULT_VERSION DEF " +
                "ON DEF.APP_NAME = APP.APP_NAME AND DEF.APP_PROVIDER = APP.APP_PROVIDER ";

        try {
            connection = APIMgtDBUtil.getConnection();
            statementToGetAppInfo = connection.prepareStatement(queryToGetAppInfo);
            appInfoResult = statementToGetAppInfo.executeQuery();

            APIIdentifier identifier = null;
            while (appInfoResult.next()) {
                identifier = new APIIdentifier(
                        appInfoResult.getString("APP_PROVIDER"),
                        appInfoResult.getString("APP_NAME"),
                        appInfoResult.getString("APP_VERSION"));

                webApp = new WebApp(identifier);
                webApp.setContext(appInfoResult.getString("CONTEXT"));
                webApp.setUrl(appInfoResult.getString("APP_ENDPOINT"));
                webApp.setDefaultVersion(appInfoResult.getString("DEFAULT_APP_VERSION").equals(appInfoResult.getString(
                        "APP_VERSION")));
                webApps.add(webApp);
            }

        } catch (SQLException e) {
            handleException("Error while getting all webapps", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToGetAppInfo, connection, appInfoResult);
        }

        return webApps;
    }


    /**
     * Get the all web apps with provider, name ,context and version
     *
     * @return web apps
     * @throws barley.appmgt.api.AppManagementException
     *
     */
    public List<WebApp> getAllWebApps(String tenantDomain) throws AppManagementException {
        WebApp webApp = null;
        Connection connection = null;
        PreparedStatement statementToGetAppInfo = null;
        ResultSet appInfoResult = null;
        List<WebApp> webApps = new ArrayList<WebApp>();
        String queryToGetAppInfo = "SELECT " +
                                   "APP.APP_PROVIDER AS APP_PROVIDER, " +
                                   "APP.APP_NAME AS APP_NAME, " +
                                   "APP.APP_VERSION AS APP_VERSION, " +
                                   "APP.CONTEXT AS CONTEXT " +
                                   "FROM " +
                                   "APM_APP APP " +
                                   "WHERE TENANT_ID = ?";

        try {
            connection = APIMgtDBUtil.getConnection();
            statementToGetAppInfo = connection.prepareStatement(queryToGetAppInfo);
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().
                    getTenantManager().getTenantId(tenantDomain);
            statementToGetAppInfo.setInt(1, tenantId);
            appInfoResult = statementToGetAppInfo.executeQuery();

            APIIdentifier identifier = null;
            while (appInfoResult.next()) {
                identifier = new APIIdentifier(
                        appInfoResult.getString("APP_PROVIDER"),
                        appInfoResult.getString("APP_NAME"),
                        appInfoResult.getString("APP_VERSION"));
                webApp = new WebApp(identifier);
                webApp.setContext(appInfoResult.getString("CONTEXT"));
                webApps.add(webApp);
            }

        } catch (SQLException e) {
            handleException("Error while getting all webapps from tenant " + tenantDomain, e);
        } catch (UserStoreException e) {
            handleException("Could not load tenant registry. Error while getting tenant id from tenant domain " +
                            tenantDomain, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statementToGetAppInfo, connection, appInfoResult);
        }

        return webApps;
    }

    public void addOAuthAPIAccessInfo(WebApp webApp, int tenantId) throws AppManagementException {
        Connection connection = null;
		PreparedStatement prepStmt = null;
		ResultSet rs = null;

		String query = "INSERT INTO APM_APP_CONSUMER_APPS (SAML2_SSO_ISSUER, APP_CONSUMER_KEY, API_TOKEN_ENDPOINT, " +
                       "API_CONSUMER_KEY, API_CONSUMER_SECRET, APP_NAME) VALUES (?,?,?,?,?,?)";

        //This need to be changed
        String getAppConsumerKeyQuery = "SELECT" + " CONSUMER_KEY " + " FROM"
                                        + " IDN_OAUTH_CONSUMER_APPS " + " WHERE"
                                        + " APP_NAME = ? AND TENANT_ID = ?";

		try {
			connection = APIMgtDBUtil.getConnection();
			connection.setAutoCommit(false);
            prepStmt = connection.prepareStatement(getAppConsumerKeyQuery);
			prepStmt.setString(1, webApp.getId().getApiName());
			prepStmt.setInt(2, tenantId);

			rs = prepStmt.executeQuery();
            String appConsumerKey = null;
			while (rs.next()) {
				appConsumerKey = rs.getString("CONSUMER_KEY");
			}
            prepStmt.close();

            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, webApp.getSaml2SsoIssuer());
			prepStmt.setString(2, appConsumerKey);
			prepStmt.setString(3, webApp.getTokenEndpoint());
			prepStmt.setString(4, webApp.getApiConsumerKey());
			prepStmt.setString(5, webApp.getApiConsumerSecret());
			prepStmt.setString(6, webApp.getApiName());

			prepStmt.execute();

			connection.commit();
		} catch (SQLException e) {
			handleException("Error while adding OAuth API configs to the database", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}
    }

    public void updateOAuthAPIAccessInfo(WebApp webApp,  int tenantId) throws AppManagementException {
        Connection connection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        String getAppConsumerKeyQuery = "SELECT" + " CONSUMER_KEY " + " FROM"
                + " IDN_OAUTH_CONSUMER_APPS " + " WHERE"
                + " APP_NAME = ? AND TENANT_ID = ?";


        // Remove entry from APM_SUBSCRIPTION table
        String query = "DELETE FROM APM_APP_CONSUMER_APPS WHERE APP_CONSUMER_KEY = ?";

        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            prepStmt = connection.prepareStatement(getAppConsumerKeyQuery);
            prepStmt.setString(1, webApp.getId().getApiName());
            prepStmt.setInt(2, tenantId);

            rs = prepStmt.executeQuery();
            String appConsumerKey = null;
            while (rs.next()) {
                appConsumerKey = rs.getString("CONSUMER_KEY");
            }
            prepStmt.close();

            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, appConsumerKey);


            prepStmt.executeUpdate();

            connection.commit();
        } catch (SQLException e) {
            handleException("Error while adding OAuth API configs to the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
        }
    }

    public static Map<String,String> getRegisteredAPIs(String webAppConsumerKey) throws
                                                                                 AppManagementException {
        Connection connection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        Map<String,String> registeredAPIs = new HashMap<String,String>();

        String query = "SELECT" + " API_CONSUMER_KEY, API_CONSUMER_SECRET, APP_NAME, API_TOKEN_ENDPOINT " + " FROM"
                       + " APM_APP_CONSUMER_APPS " + " WHERE"
                       + " APP_CONSUMER_KEY = ?";

		try {
			connection = APIMgtDBUtil.getConnection();
            prepStmt = connection.prepareStatement(query);
			prepStmt.setString(1, webAppConsumerKey);

			rs = prepStmt.executeQuery();
            String apiConsumerKey;
            String apiConSecret;
            String apiName;
            String tokenEp;
			while (rs.next()) {
				apiConsumerKey = rs.getString("API_CONSUMER_KEY");
                apiConSecret = rs.getString("API_CONSUMER_SECRET");
                apiName = rs.getString("APP_NAME");
                tokenEp = rs.getString("API_TOKEN_ENDPOINT");

                registeredAPIs.put(apiName, apiConsumerKey + "," + apiConSecret + "," + tokenEp );
			}
		} catch (SQLException e) {
			handleException("Error while adding OAuth API configs to the database", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}

        return registeredAPIs;
    }

    public static String getSAML2SSOIssuerByAppConsumerKey(String webAppConsumerKey)
            throws AppManagementException {

        Connection con = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        String query = "SELECT SAML2_SSO_ISSUER " +
                       "FROM APM_APP_CONSUMER_APPS " +
                       "WHERE APP_CONSUMER_KEY=?";

        String saml2SsoIssuer = null;

        try {
            con = APIMgtDBUtil.getConnection();
            prepStmt = con.prepareStatement(query);
            prepStmt.setString(1, webAppConsumerKey);
            rs = prepStmt.executeQuery();

            while(rs.next()) {
                saml2SsoIssuer = rs.getString("SAML2_SSO_ISSUER");
            }
        } catch (SQLException e) {
            handleException("Error while getting SAML2_SSO_ISSUER for webAppConsumerKey = " + webAppConsumerKey, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, con, rs);
        }

        return saml2SsoIssuer;
    }

    public static boolean webAppKeyPairExist(String consumerKey, String consumerSecret) throws
                                                                                        AppManagementException {
        Connection connection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        Map<String,String> registeredAPIs = new HashMap<String,String>();

        String query = "SELECT" + " CONSUMER_KEY, CONSUMER_SECRET   " + " FROM"
                       + " IDN_OAUTH_CONSUMER_APPS " + " WHERE"
                       + " CONSUMER_KEY = ? "
                       + " AND CONSUMER_SECRET = ?";

		try {
			connection = APIMgtDBUtil.getConnection();
            prepStmt = connection.prepareStatement(query);
			prepStmt.setString(1, consumerKey);
            prepStmt.setString(2, consumerSecret);

			rs = prepStmt.executeQuery();
			if (rs.next()) {
				return true;
			}
		} catch (SQLException e) {
			handleException("Error while adding OAuth API configs to the database", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
		}

        return false;
    }

    public static List<String> getApplicationKeyPair(String appName, String webappProvider)
            throws AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String consumerKey;
        String consumerSecret;
        List<String> keys = new ArrayList<String>();
        int tenantId;

        String query = "SELECT CONSUMER_KEY,CONSUMER_SECRET " +
                       " FROM IDN_OAUTH_CONSUMER_APPS " +
                       " WHERE APP_NAME = ? AND TENANT_ID = ?";

        try {
            conn = APIMgtDBUtil.getConnection();
            ps = conn.prepareStatement(query);
            tenantId = IdentityTenantUtil.getTenantIdOfUser(webappProvider);
            ps.setString(1, appName);
            ps.setInt(2, tenantId);
            rs = ps.executeQuery();
            if (rs.next()) {
                consumerKey = rs.getString("CONSUMER_KEY");
                consumerSecret = rs.getString("CONSUMER_SECRET");
                keys.add(consumerKey);
                keys.add(consumerSecret);
            }
        } catch (SQLException e) {
            handleException("Error when executing the SQL ", e);
        } catch (IdentityRuntimeException e) {
            handleException("Error while getting tenantId of user", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }

        return keys;
    }

    private boolean isAPIPublished(WebApp api) throws AppManagementException {
        try {
            String tenantDomain = null;
            if (api.getId().getProviderName().contains("AT")) {
                String provider = api.getId().getProviderName().replace("-AT-", "@");
                tenantDomain = MultitenantUtils.getTenantDomain( provider);
            }
            APIGatewayManager gatewayManager = APIGatewayManager.getInstance();
            return gatewayManager.isAPIPublished(api, tenantDomain);
        } catch (Exception e) {
            handleException("Error while checking WebApp status", e);
        }
        return false;
    }

    /**
     * Save store wise hits.
     *
     * @param webAppUUID Application UUID
     * @param userId     User Id
     * @param tenantId   Tenant Id
     * @param appName    App Name
     * @param appVersion App Version
     * @param context    Context
     * @param timeStamp  Hit timestamp
     */
    public static void saveStoreHits(String webAppUUID, String userId, Integer tenantId,
                                     String appName, String appVersion, String context, Long timeStamp)
            throws AppManagementException {
        Connection conn = null;
        try {
            conn = APIMgtDBUtil.getUiActivityDBConnection();
            insertStoreHits(webAppUUID, userId, tenantId, appName, appVersion, context, timeStamp, conn);
            conn.commit();
        } catch (SQLException e) {
            String webAppInfo =
                    "(Webapp UUID: " + webAppUUID + ", UserId : " + userId + ", TenantId : " +
                            tenantId + ")";
            handleException("Error in saving Store hits: " + webAppInfo + " : " + e.getMessage(),
                            e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null, conn, null);
        }
    }

    /**
     * Insert store wise hits.
     *
     * @param webAppUUID Application UUID
     * @param userId     User Id
     * @param tenantId   Tenant Id
     * @param appName    App Name
     * @param appVersion App Version
     * @param context    Context
     * @param timeStamp  Hit timestamp
     */
    public static void insertStoreHits(String webAppUUID, String userId, Integer tenantId, String appName,
                                       String appVersion, String context, Long timeStamp, Connection conn)
            throws AppManagementException {
        ResultSet rs = null;
        PreparedStatement ps = null;
        try {
            String query = " INSERT INTO APM_APP_HITS (UUID,APP_NAME,VERSION,CONTEXT,USER_ID, " +
                    "TENANT_ID,HIT_TIME) VALUES(?,?,?,?,?,?,?) ";
            ps = conn.prepareStatement(query);
            ps.setString(1, webAppUUID);
            ps.setString(2, appName);
            ps.setString(3, appVersion);
            ps.setString(4,context);
            ps.setString(5, userId);
            ps.setInt(6, tenantId);
            ps.setTimestamp(7, new Timestamp(timeStamp));
            ps.executeUpdate();
            if (log.isDebugEnabled()) {
                log.debug("Record relevant to webapp id '" + webAppUUID + "' saved successfully");
            }
        } catch (SQLException e) {
            String webAppInfo = "(Web app UUID: " + webAppUUID + ", UserId : " + userId + ", " +
                    "TenantId : " + tenantId + ")";
            handleException("Error occurred while updating Store hits: " + webAppInfo + " : "
                                    + e.getMessage(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, null, rs);
        }
    }

    /**
     * Get Apps by app hit count.
     * @param userId      user Id.
     * @param startIndex  pagination start index.
     * @param pageSize    No of elements per page.
     * @return JSONArray with sorted UUIDs.
     */
    public static JSONArray getAppsByHitCount(String userId, Integer startIndex, Integer pageSize)
            throws AppManagementException {
        Connection conn;
        JSONArray jsonResultArr = null;

        // Contains input parameter values for logging purposes
        StringBuilder builderDataContext = new StringBuilder();
        builderDataContext.append("(userId:").append(userId)
                .append(", startIndex:").append(startIndex)
                .append(", pageSize:").append(pageSize).append(")");

        try {
            //get the connection for the AM data source
            conn = APIMgtDBUtil.getConnection();
            List<String> uuidsList;
            if (AppManagerUtil.isUIActivityDASPublishEnabled()) {
                uuidsList = getAppHitStatsFromBamDBAndAppmDB(conn, userId, startIndex, pageSize,
                                                             builderDataContext);
            } else {
                uuidsList = getAppHitStatsFromAppmDBOnly(conn, userId, startIndex, pageSize,
                                                         builderDataContext);
            }
            jsonResultArr = new JSONArray();
            for (int i = 0; i < uuidsList.size(); i++) {
                JSONObject uuidObject = new JSONObject();
                uuidObject.put("UUID", uuidsList.get(i));
                jsonResultArr.add(uuidObject);
            }
        } catch (SQLException e) {
            throw new AppManagementException(
                    "SQL Exception is occurred while fetching the store hit sorted data : "
                            + builderDataContext.toString() + " : "
                            + e.getMessage(), e);
        }
        return jsonResultArr;
    }

    /**
     * Get app hits when ui data activity data source hasn't set to DAS data source.
     * @param conn Connection.
     * @param userId user Id.
     * @param startIndex pagination start index.
     * @param pageSize No of elements per page.
     * @param builderDataContext builder with data context.
     * @return a list of UUIDs.
     * @throws AppManagementException
     */
    private static List<String> getAppHitStatsFromAppmDBOnly(Connection conn, String userId,
                                                             int startIndex, int pageSize,
                                                             StringBuilder builderDataContext)
            throws AppManagementException {
        ResultSet rs = null;
        PreparedStatement ps = null;
        List<String> uuidsList = new ArrayList<String>();
        try {
            String query  = "SELECT * FROM (SELECT HIT.UUID ,COUNT(*) AS HIT_COUNT,UPPER(APP_NAME) "
                    + "AS APP_NAME, HIT.CONTEXT FROM APM_APP_HITS  HIT "
                    + "WHERE HIT.USER_ID=? GROUP BY HIT.UUID "
                    + "UNION ALL "
                    + "SELECT UUID ,0 AS HIT_COUNT, UPPER(APP_NAME) AS APP_NAME, CONTEXT FROM APM_APP "
                    + "WHERE UUID NOT IN (SELECT UUID FROM APM_APP_HITS  WHERE USER_ID=? )) A "
                    + "ORDER BY HIT_COUNT DESC,APP_NAME ASC LIMIT ? , ?";

            if (conn.getMetaData().getDriverName().contains("Oracle")) {
                query = "SELECT * FROM (SELECT HIT.UUID ,COUNT(*) AS HIT_COUNT,UPPER(APP_NAME) "
                        + "AS APP_NAME, HIT.CONTEXT FROM APM_APP_HITS HIT "
                        + "WHERE HIT.USER_ID=? "
                        + "GROUP BY HIT.UUID, HIT.APP_NAME, HIT.VERSION, HIT.CONTEXT UNION ALL "
                        + "SELECT UUID ,0 AS HIT_COUNT, UPPER(APP_NAME) AS APP_NAME, CONTEXT FROM APM_APP "
                        + "WHERE UUID NOT IN (SELECT UUID FROM APM_APP_HITS WHERE USER_ID=? )) A  "
                        + "WHERE ROWNUM >= ? AND ROWNUM <= ? "
                        + "ORDER BY HIT_COUNT DESC,APP_NAME ASC ";
            }

            ps = conn.prepareStatement(query);
            ps.setString(1, userId);
            ps.setString(2, userId);
            ps.setInt(3, startIndex);
            ps.setInt(4, pageSize);
            rs = ps.executeQuery();
            if (rs.isFirst()) {
                uuidsList = new ArrayList<String>();
            }
            while (rs.next()) {
                uuidsList.add(rs.getString("UUID"));
            }
        } catch (SQLException e) {
            throw new AppManagementException(
                    "SQL Exception is occurred while fetching the store hit sorted data from " +
                            "App Manager Database: " + builderDataContext.toString() + " : "
                            + e.getMessage(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return uuidsList;
    }

    /**
     * Get app hits when ui data activity data source has set to DAS data source.
     * @param appMCon Connection to App Manager database.
     * @param userId user Id.
     * @param pageSize No of elements per page.
     * @param pageSize No of elements per page.
     * @param builderDataContext builder with data context.
     * @return a list of UUIDs.
     * @throws AppManagementException
     */
    private static List<String> getAppHitStatsFromBamDBAndAppmDB(Connection appMCon, String userId,
                                                                 int startIndex, int pageSize,
                                                                 StringBuilder builderDataContext)
            throws AppManagementException {
        List<String> uuidsList = new ArrayList<String>();
        ResultSet bamResultSet = null;
        PreparedStatement bamPs = null;
        ResultSet appmResultSet = null;
        PreparedStatement appmPs = null;
        Connection bamConn = null;
        try {
            //get the connection for the UI Activity Publish data source
            bamConn = APIMgtDBUtil.getUiActivityDBConnection();

            String uuidRetrivealBamQuery = getAppUuidsFromBamDb(bamConn);
            bamPs = bamConn.prepareStatement(uuidRetrivealBamQuery);
            bamPs.setString(1, userId);
            bamPs.setInt(2, startIndex);
            bamPs.setInt(3, pageSize);
            bamResultSet = bamPs.executeQuery();
            while (bamResultSet.next()) {
                String uuid = bamResultSet.getString("UUID");
                uuidsList.add(uuid);
            }
            String uuidRetrievalAppmQuery = getAppUuidsFromAppmDb(uuidsList, appMCon);
            appmPs = appMCon.prepareStatement(uuidRetrievalAppmQuery);
            for (int j = 0; j < uuidsList.size(); j++) {
                appmPs.setString(j + 1, uuidsList.get(j));
            }
            appmPs.setInt(uuidsList.size() + 1, startIndex);
            appmPs.setInt(uuidsList.size() + 2, pageSize);
            appmResultSet = appmPs.executeQuery();
            while (appmResultSet.next()) {
                uuidsList.add(appmResultSet.getString("UUID"));
            }
        } catch (SQLException e) {
            throw new AppManagementException(
                    "SQL Exception is occurred while fetching the store hit sorted data from " +
                            "App Manager Database and Bam database: "
                            + builderDataContext.toString() + " : " + e.getMessage(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(bamPs, bamConn, bamResultSet);
            APIMgtDBUtil.closeAllConnections(appmPs, appMCon, appmResultSet);
        }
        return uuidsList;
    }

    private static String getAppUuidsFromBamDb(Connection bamConn)
            throws  AppManagementException {
        String uuidRetrivealBamQuery;
        try {
            uuidRetrivealBamQuery = "SELECT UUID, COUNT(*) AS HIT_COUNT, UPPER(APP_NAME) AS APP_NAME,  "
                    + "CONTEXT FROM APM_APP_HITS WHERE USER_ID= ? GROUP BY UUID "
                    + "ORDER BY HIT_COUNT DESC,APP_NAME ASC LIMIT ? , ?";

            if (bamConn.getMetaData().getDriverName().contains("Oracle")) {
                uuidRetrivealBamQuery = "SELECT * FROM(SELECT UUID, COUNT(*) AS HIT_COUNT, "
                        + "UPPER(APP_NAME) AS APP_NAME, CONTEXT FROM APM_APP_HITS WHERE USER_ID=? "
                        + "GROUP BY UUID,APP_NAME,VERSION "
                        + "ORDER BY HIT_COUNT DESC,APP_NAME ASC) WHERE ROWNUM >= ? AND ROWNUM <= ?";
            }
        } catch (SQLException ex) {
            throw new AppManagementException(
                    "SQL Exception is occurred while reading driver name of DAS database connection "
                            + " : " + ex.getMessage(), ex);
        }
        return uuidRetrivealBamQuery;
    }

    private static String getAppUuidsFromAppmDb(List<String> uuidList, Connection appmConn)
            throws AppManagementException {

        StringBuilder uuidRetrievalAppmQuery = new StringBuilder();
        String uuidRetrievalQuery;
        try {
            if (uuidList.size() > 0) {
                uuidRetrievalAppmQuery.append("SELECT UUID, UPPER(APP_NAME) AS APP_NAME, CONTEXT FROM "
                                                      + "APM_APP WHERE UUID NOT IN (");
                for (int i = 0; i < uuidList.size(); i++) {
                    uuidRetrievalAppmQuery.append("?,");
                }
                uuidRetrievalQuery = uuidRetrievalAppmQuery.substring(0, uuidRetrievalAppmQuery.length() - 1);

                if (appmConn.getMetaData().getDriverName().contains("Oracle")) {
                    uuidRetrievalQuery = "SELECT * FROM (" + uuidRetrievalQuery
                            + ") ORDER BY APP_NAME ASC) WHERE ROWNUM >= ? AND ROWNUM <= ? ";
                } else {
                    uuidRetrievalQuery += ") ORDER BY APP_NAME ASC LIMIT ? , ?";
                }
            } else {
                if (appmConn.getMetaData().getDriverName().contains("Oracle")) {
                    uuidRetrievalQuery =
                            "SELECT * FROM (SELECT UUID, UPPER(APP_NAME) AS APP_NAME, CONTEXT " +
                                    "FROM APM_APP ORDER BY APP_NAME ASC) WHERE ROWNUM >= ? AND ROWNUM <= ? ";
                } else {
                    uuidRetrievalQuery =
                            "SELECT UUID, UPPER(APP_NAME) AS APP_NAME, CONTEXT FROM APM_APP ORDER BY APP_NAME ASC " +
                                    "LIMIT ? , ?";
                }
            }
        } catch (SQLException ex) {
            throw new AppManagementException(
                    "SQL Exception is occurred while reading driver name of App Manager database " +
                            "connection : " + ex.getMessage(), ex);
        }
        return uuidRetrievalQuery;
    }

    /**
	 * Save policy groups
	 *
	 * @param policyGroupName    :policy group name
	 * @param throttlingTier     : throttling Tier
	 * @param userRoles          : user roles
	 * @param isAnonymousAllowed : is anonymous access allowed to URL pattern
	 * @param objPartialMappings : Object which contains XACML policy partial details arrays
	 * @param policyGroupDesc    :Policy group Desciption
	 * @return : last saved policy group id
	 * @throws AppManagementException if any an error found while saving data to DB
	 */
    public static Integer savePolicyGroup(String policyGroupName, String throttlingTier,
                                          String userRoles, String isAnonymousAllowed,
                                          Object[] objPartialMappings, String policyGroupDesc)
            throws AppManagementException {
        PreparedStatement ps = null;
        Connection conn = null;
        ResultSet rs = null;
        String query = "INSERT INTO APM_POLICY_GROUP(NAME,THROTTLING_TIER,USER_ROLES,URL_ALLOW_ANONYMOUS,DESCRIPTION) "
                + "VALUES(?,?,?,?,?) ";
        int policyGroupId = -1;
		try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
			ps = conn.prepareStatement(query, new String[]{"POLICY_GRP_ID"});
			ps.setString(1, policyGroupName);
			ps.setString(2, throttlingTier);
			ps.setString(3, userRoles);
			ps.setBoolean(4, Boolean.parseBoolean(isAnonymousAllowed));
			ps.setString(5, policyGroupDesc);
			ps.executeUpdate();
			rs = ps.getGeneratedKeys();
			if (rs.next()) {
				policyGroupId = Integer.parseInt(rs.getString(1));
			}
            // save partials mapped to policy group
            if (objPartialMappings != null) {
                if (objPartialMappings.length > 0) {
                    savePolicyPartialMappings(policyGroupId, objPartialMappings, conn);
                }
            }

			conn.commit();
			if (log.isDebugEnabled()) {
				StringBuilder strDataContext = new StringBuilder();
                strDataContext.append("(policyGroupName:").append(policyGroupName)
                        .append(", throttlingTier:").append(throttlingTier)
                        .append(", userRoles:").append(userRoles)
                        .append(", isAnonymousAllowed:").append(isAnonymousAllowed)
                        .append(", Partial Mappings:").append(objPartialMappings)
                        .append(")");
                log.debug("Record saved successfully." + strDataContext.toString());
			}
		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
                    log.error("Failed to rollback while saving the policy group - " + policyGroupId, e);
                }
			}
            StringBuilder strDataContext = new StringBuilder();
            strDataContext.append("(policyGroupName:").append(policyGroupName)
                    .append(", throttlingTier:").append(throttlingTier)
                    .append(", userRoles:").append(userRoles)
                    .append(", isAnonymousAllowed:").append(isAnonymousAllowed)
                    .append(", Partial Mappings:").append(objPartialMappings)
                    .append(")");

            handleException("SQL Error while executing the query to save Policy Group : " + query +
                    " : " + strDataContext.toString(), e);
        } finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return policyGroupId;
	}


    /**
     * Update policy groups
     *
     * @param policyGroupName    Policy group name
     * @param throttlingTier     Throttling Tier
     * @param userRoles          User roles
     * @param isAnonymousAllowed Is anonymous access allowed to URL pattern
     * @param policyGroupId      Policy group id
     * @param policyGroupDesc    Policy group Description
     * @param authorizedAdminCookie    Authorized cookie to access IDP admin services
     * @return Last saved policy group id
     * @throws AppManagementException if any an error found while saving data to DB
     */
    public static void updatePolicyGroup(String policyGroupName, String throttlingTier,
                                         String userRoles, String isAnonymousAllowed,
                                         int policyGroupId, Object[] objPartialMappings, String policyGroupDesc,
                                         String authorizedAdminCookie) throws AppManagementException {
        PreparedStatement ps = null;
		Connection conn = null;
        String query = "UPDATE APM_POLICY_GROUP " +
                "SET NAME = ?, THROTTLING_TIER = ?, USER_ROLES = ?, URL_ALLOW_ANONYMOUS = ?, DESCRIPTION = ? " +
                "WHERE POLICY_GRP_ID = ? ";
        try {
			conn = APIMgtDBUtil.getConnection();
			conn.setAutoCommit(false);
			ps = conn.prepareStatement(query);
			ps.setString(1, policyGroupName);
			ps.setString(2, throttlingTier);
			ps.setString(3, userRoles);
			ps.setBoolean(4, Boolean.parseBoolean(isAnonymousAllowed));
			ps.setString(5, policyGroupDesc);
			ps.setInt(6, policyGroupId);
			ps.executeUpdate();

            //delete XACML Policies from Entitlement Service
            deleteXACMLPoliciesFromEntitlementService(policyGroupId, conn, authorizedAdminCookie);

			//delete partials mapped to group id
			deletePolicyPartialMappings(policyGroupId, conn);

            //insert new partial mappings
            if (objPartialMappings != null) {
                if (objPartialMappings.length > 0) {
                    savePolicyPartialMappings(policyGroupId, objPartialMappings, conn);
                }
            }

			conn.commit();

			if (log.isDebugEnabled()) {
				StringBuilder strDataContext=new StringBuilder();
                strDataContext.append("(policyGroupName:").append(policyGroupName)
                        .append(", throttlingTier:").append(throttlingTier)
                        .append(", userRoles:").append(userRoles)
                        .append(", isAnonymousAllowed:").append(isAnonymousAllowed)
                        .append(", Partial Mappings:").append(objPartialMappings)
                        .append(")");
				log.debug("Record updated successfully." + strDataContext.toString());
			}
		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException e1) {
					log.error("Failed to rollback while updating the policy group", e);
				}
			}
			StringBuilder strDataContext=new StringBuilder();
            strDataContext.append("(policyGroupName:").append(policyGroupName)
                    .append(", throttlingTier:").append(throttlingTier)
                    .append(", userRoles:").append(userRoles)
                    .append(", isAnonymousAllowed:").append(isAnonymousAllowed)
                    .append(", Partial Mappings:").append(objPartialMappings)
                    .append(")");

            handleException("SQL Error while executing the query to update Policy Group : " + query + " : " +
                    strDataContext.toString(), e);
        } finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, null);
		}
	}
    
    // (??????) 2019.11.04
    public static boolean isExistsPolicyGroup(String policyGroupName)
			throws AppManagementException {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		boolean result = false;
		
		String query =
				"SELECT POLICY_GRP_ID "
						+ "FROM APM_POLICY_GROUP "
						+ "WHERE NAME = ? ";
		try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(query);
			ps.setString(1, policyGroupName);
			rs = ps.executeQuery();
			if (rs.next()) {
				result = true;
			}
		} catch (SQLException e) {
			handleException("SQL Error while executing the query to get policy group " + 
											query + " : (policyGroupName:" + policyGroupName + ")", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return result;
	}
    
    /**
	 * save applications wise policy groups
	 *
	 * @param connection     : SQL connection
	 * @param applicationId  : application id
	 * @param policyGroupIds : policy groups id list
	 * @throws AppManagementException if any an error found while saving data to DB
	 */
	public void saveApplicationPolicyGroupsMappings(Connection connection, int applicationId, Object[] policyGroupIds)
            throws AppManagementException {
        PreparedStatement preparedStatement = null;
        String query = "INSERT INTO APM_POLICY_GROUP_MAPPING(APP_ID, POLICY_GRP_ID) VALUES(?,?)";
        try {
			preparedStatement = connection.prepareStatement(query);

			for (Object policyGroupId : policyGroupIds) {
				preparedStatement.setInt(1, applicationId);
				preparedStatement.setInt(2, Integer.parseInt(policyGroupId.toString()));
				preparedStatement.addBatch();
			}
			preparedStatement.executeBatch();
		} catch (SQLException e) {
            String strDataContext = "(applicationId:" + applicationId + ", policyGroupIds:" + policyGroupIds + ")";
            handleException("SQL Error while executing the query to save Policy Group mappings  : " + query + " : " +
                    strDataContext, e);
        } finally {
			APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
		}
	}

	/**
	 * method used to get Applications wise Policy Groups
	 *
	 * @param appId : Application Id
	 * @return : list of object EntitlementPolicyGroup which contains Policy Group details
	 * @throws AppManagementException on error
	 */
    public List<EntitlementPolicyGroup> getPolicyGroupListByApplication(int appId)
            throws AppManagementException {

		Connection connection = null;
		PreparedStatement ps = null;
		List<EntitlementPolicyGroup> entitlementPolicyGroupList =
				new ArrayList<EntitlementPolicyGroup>();
		ResultSet rs = null;

        String query = "SELECT POLICY_GRP_ID, NAME, THROTTLING_TIER, USER_ROLES, "
                + "URL_ALLOW_ANONYMOUS, DESCRIPTION FROM APM_POLICY_GROUP "
                + "WHERE POLICY_GRP_ID IN (SELECT POLICY_GRP_ID FROM APM_POLICY_GROUP_MAPPING WHERE APP_ID=?) ";
        try {
			connection = APIMgtDBUtil.getConnection();
			ps = connection.prepareStatement(query);
			ps.setInt(1, appId);
			rs = ps.executeQuery();

			while (rs.next()) {
				EntitlementPolicyGroup policyGroup = new EntitlementPolicyGroup();
				policyGroup.setPolicyGroupId(rs.getInt("POLICY_GRP_ID"));
				policyGroup.setPolicyGroupName(rs.getString("NAME"));
				policyGroup.setThrottlingTier(rs.getString("THROTTLING_TIER"));
				policyGroup.setUserRoles(rs.getString("USER_ROLES"));
				policyGroup.setAllowAnonymous(rs.getBoolean("URL_ALLOW_ANONYMOUS"));
				policyGroup.setPolicyPartials(getEntitledPartialListForPolicyGroup(rs.getInt("POLICY_GRP_ID"),
						connection));
				policyGroup.setPolicyDescription(rs.getString("DESCRIPTION"));
				entitlementPolicyGroupList.add(policyGroup);
			}

		} catch (SQLException e) {
            handleException("SQL Error while executing the query to fetch Application wise policy Group list : " +
                    query + " : (Application Id" + appId + ")", e);
        } finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, rs);
		}
		return entitlementPolicyGroupList;

	}
    
 // (??????) 2019.11.04
    public List<EntitlementPolicyGroup> getPolicyGroupList() throws AppManagementException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		List<EntitlementPolicyGroup> entitlementPolicyGroupList =
				new ArrayList<EntitlementPolicyGroup>();
		
		String query =
				"SELECT POLICY_GRP_ID, NAME, THROTTLING_TIER, USER_ROLES, "  
					+ " URL_ALLOW_ANONYMOUS, DESCRIPTION " 
					+ " FROM APM_POLICY_GROUP "
					+ " ORDER BY POLICY_GRP_ID ";
		try {
			connection = APIMgtDBUtil.getConnection();
			ps = connection.prepareStatement(query);
			rs = ps.executeQuery();

			while (rs.next()) {
				EntitlementPolicyGroup policyGroup = new EntitlementPolicyGroup();
				policyGroup.setPolicyGroupId(rs.getInt("POLICY_GRP_ID"));
				policyGroup.setPolicyGroupName(rs.getString("NAME"));
				policyGroup.setThrottlingTier(rs.getString("THROTTLING_TIER"));
				policyGroup.setUserRoles(rs.getString("USER_ROLES"));
				policyGroup.setAllowAnonymous(rs.getBoolean("URL_ALLOW_ANONYMOUS"));
				policyGroup.setPolicyPartials(getEntitledPartialListForPolicyGroup(rs.getInt("POLICY_GRP_ID"),
						connection));
				policyGroup.setPolicyDescription(rs.getString("DESCRIPTION"));
				entitlementPolicyGroupList.add(policyGroup);
			}

		} catch (SQLException e) {
            handleException("SQL Error while executing the query to fetch Application wise policy Group list ", e);
        } finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, rs);
		}
		return entitlementPolicyGroupList;
	}


	/**
	 * Get Policy partial details related to Policy Group
	 * @param policyGroupId Policy Group Id
	 * @param connection sql connection
	 * @return array of policy partial details objects
	 * @throws AppManagementException on error
	 */
	private JSONArray getEntitledPartialListForPolicyGroup(Integer policyGroupId, Connection connection) throws
			AppManagementException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		JSONArray arrPartials = new JSONArray();
		String query = "SELECT POLICY_PARTIAL_ID, EFFECT, POLICY_ID " +
				"FROM APM_POLICY_GRP_PARTIAL_MAPPING WHERE POLICY_GRP_ID = ? ";
		try {
			ps = connection.prepareStatement(query);
			ps.setInt(1, policyGroupId);
			rs = ps.executeQuery();
			while (rs.next()) {
				JSONObject objPartial = new JSONObject();
				objPartial.put("POLICY_PARTIAL_ID", rs.getInt("POLICY_PARTIAL_ID"));
				objPartial.put("EFFECT", rs.getString("EFFECT"));
				objPartial.put("POLICY_ID", rs.getString("POLICY_ID"));
				objPartial.put("POLICY_GRP_ID", policyGroupId);
				arrPartials.add(objPartial);
			}
		} catch (SQLException e) {
            handleException("SQL Error while executing the query to fetch policy group wise entitled partials list  : "
                    + query + " : (Policy Group Id" +
                    policyGroupId + ")", e);
        } finally {
			APIMgtDBUtil.closeAllConnections(ps, null, rs);
		}
		return arrPartials;
	}

    /**
     * delete policy groups
     *
     * @param applicationId   Application Id
     * @param policyGroupId   Policy Group Id
     * @param authorizedAdminCookie Authorized cookie to access IDP admin services
     * @throws AppManagementException on error
     */
    public void deletePolicyGroup(String applicationId, String policyGroupId, String authorizedAdminCookie)
            throws AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
		String query = "";
		try {
	   		conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);

            //Remove XACML Policies from Entitlement Service
            deleteXACMLPoliciesFromEntitlementService(Integer.parseInt(policyGroupId), conn, authorizedAdminCookie);

		 	//delete from master table
			query = "DELETE FROM APM_POLICY_GROUP WHERE POLICY_GRP_ID = ? ";
			ps = conn.prepareStatement(query);
			ps.setInt(1, Integer.parseInt(policyGroupId));
			ps.executeUpdate();

			conn.commit();

			if (log.isDebugEnabled()) {
				String strDataContext =
						"(applicationId:" + applicationId + ", policyGroupId:" + policyGroupId + ")";
				log.debug("Policy Group deleted successfully. " + strDataContext);
			}
		} catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Rollback while deleting the policy group : " + policyGroupId, e);
                }
            }
            String strDataContext =
                    "(applicationId:" + applicationId + ", policyGroupId:" + policyGroupId + ")";
            handleException("Error while executing the query to delete XACML policies : " + query + " : " +
                    strDataContext, e);
        } finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, null);
		}
	}


	/**
	 * Save XACML policies, policy group wise
	 *
	 * @param policyGroupId Policy Group Id
	 * @param objPartialMappings XACML policy related details object array
	 * @param conn sql connection
	 * @throws AppManagementException if any an error found while saving data to DB
	 */
    private static void savePolicyPartialMappings(int policyGroupId,
                                                  Object[] objPartialMappings, Connection conn)
            throws SQLException {
        String query = "INSERT INTO APM_POLICY_GRP_PARTIAL_MAPPING(POLICY_GRP_ID, POLICY_PARTIAL_ID) "
                + "VALUES(?,?) ";
        PreparedStatement preparedStatement = null;

		try {
			preparedStatement = conn.prepareStatement(query);

			for (int i = 0; i < objPartialMappings.length; i++) {
				preparedStatement.setInt(1, policyGroupId);
				preparedStatement.setInt(2, ((Double)(objPartialMappings[i])).intValue());
				preparedStatement.addBatch();
			}
			preparedStatement.executeBatch();
		} catch (SQLException e) {
            log.error("SQL Error while executing the query to save policy partial mappings: " +
                    query + " : (Policy Group Id:" + policyGroupId + ", Policy Partial Mappings:" +
                    objPartialMappings + ")", e);
            /* In the code im using a single SQL connection passed from the parent function so I'm logging the error here
            and throwing the SQLException so  the connection will be disposed by the parent function. */
            throw e;
		} finally {
			APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
		}
	}

	/**
	 * Delete XACML policies, policy group wise
	 *
	 * @param policyGroupId Policy Group Id
	 * @param conn sql connection
	 * @throws AppManagementException if any an error found while saving data to DB
	 */
    private static void deletePolicyPartialMappings(Integer policyGroupId, Connection conn)
            throws SQLException {

        String query = " DELETE FROM APM_POLICY_GRP_PARTIAL_MAPPING WHERE POLICY_GRP_ID = ? ";
        PreparedStatement ps = null;
		try {
			ps = conn.prepareStatement(query);
			ps.setInt(1, policyGroupId);
			ps.executeUpdate();

		} catch (SQLException e) {
            log.error("SQL Error while executing the query to delete policy partial mappings: "
                    + query + " : (Policy Group Id:" +
                    policyGroupId + ")", e);
            /* In the code im using a single SQL connection passed from the parent function so I'm logging the error here
            and throwing the SQLException so  the connection will be disposed by the parent function. */
            throw e;
        } finally {
			APIMgtDBUtil.closeAllConnections(ps, null, null);
		}
	}

    /**
     * Remove XACML Policies from Entitlement Service
     *
     * @param policyGroupId
     * @param conn
     * @param authorizedAdminCookie Authorized cookie to access IDP admin services
     * @throws SQLException,AppManagementException
     */
    private static void deleteXACMLPoliciesFromEntitlementService(int policyGroupId, Connection conn,
                                                                  String authorizedAdminCookie)
            throws SQLException, AppManagementException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        String query = "SELECT POLICY_ID FROM APM_POLICY_GRP_PARTIAL_MAPPING WHERE POLICY_GRP_ID = ? ";

        //Define Entitlement Service
        AppManagerConfiguration config = ServiceReferenceHolder.getInstance().
                getAPIManagerConfigurationService().getAPIManagerConfiguration();
        EntitlementService entitlementService = EntitlementServiceFactory.getEntitlementService(config, authorizedAdminCookie);

        try {
            ps = conn.prepareStatement(query);
            ps.setInt(1, policyGroupId);
            rs = ps.executeQuery();
            while (rs.next()) {
                String policyId = rs.getString(1);
                //If policy id not null, remove the Entitlement policy with reference to policy id
                if (policyId != null) {
                    entitlementService.removePolicy(policyId);
                }
            }
        } catch (SQLException e) {
            log.error("SQL Error while executing the query to get policy id's under policy group : " +
                    policyGroupId + ". SQL Query : " + query, e);
            /* In the code im using a single SQL connection passed from the parent function so I'm logging the error
            here and throwing the SQLException so  the connection will be disposed by the parent function. */
            throw e;
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, null, rs);
        }
    }



	/**
	 * Get policy group related polices for the given application UUID
	 *
	 * @param AppUUID application UUID
	 * @return an array of policy details objects
	 */
	public static JSONArray getPolicyGroupXACMLPoliciesByApplication(String AppUUID)
			throws AppManagementException {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		JSONArray objPartialArr = new JSONArray();

		// query to get policies under each policy group mapped with the
		// application
		String query =
				"SELECT DISTINCT MAP.POLICY_GRP_ID AS POLICY_GRP_ID, POL.NAME AS POLICY_GRP_NAME, "
						+ "POL.THROTTLING_TIER AS THROTTLING_TIER, POL.USER_ROLES AS USER_ROLES, URL_ALLOW_ANONYMOUS "
						+ "FROM APM_POLICY_GROUP_MAPPING MAP "
						+ "LEFT JOIN APM_POLICY_GROUP POL ON MAP.POLICY_GRP_ID =POL.POLICY_GRP_ID "
						+ "WHERE MAP.APP_ID = (SELECT APP_ID FROM APM_APP WHERE UUID = ?) ";

		try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(query);
			ps.setString(1, AppUUID);
			rs = ps.executeQuery();
			while (rs.next()) {
                JSONObject objPartial = new JSONObject();
                objPartial.put("POLICY_GRP_ID", rs.getInt("POLICY_GRP_ID"));
				objPartial.put("POLICY_GRP_NAME", rs.getString("POLICY_GRP_NAME"));
				objPartial.put("THROTTLING_TIER", rs.getString("THROTTLING_TIER"));
				objPartial.put("USER_ROLES", rs.getString("USER_ROLES"));
				objPartial.put("URL_ALLOW_ANONYMOUS", rs.getBoolean("URL_ALLOW_ANONYMOUS"));
				objPartial.put("POLICY_PARTIAL_NAME",
						getPolicyGroupWisePolicyPartials(rs.getInt("POLICY_GRP_ID"), conn));
				objPartialArr.add(objPartial);
			}
		} catch (SQLException e) {
			handleException("SQL Error while executing the query to get policies under each policy group mapped " +
					"with the application : " + query + " : (Application UUID:" + AppUUID + ")", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return objPartialArr;
	}

    /**
     * returns the comma separated list of policy partial names to display in
     * the view assets
     *
     * @param policyGroupId policy group id
     * @param conn          sql connection
     * @return comma separated list of policy partial names
     * @throws AppManagementException on error
     */
    private static String getPolicyGroupWisePolicyPartials(int policyGroupId, Connection conn)
			throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		String policyPartialNamesList = "";

        // query to get policies under each policy group mapped with the
        // application
        String query = "SELECT MAP.POLICY_PARTIAL_ID AS POLICY_PARTIAL_ID, POL.NAME AS POLICY_PARTIAL_NAME, "
                + "MAP.EFFECT AS EFFECT "
                + "FROM APM_POLICY_GRP_PARTIAL_MAPPING MAP "
                + "LEFT JOIN APM_ENTITLEMENT_POLICY_PARTIAL  POL "
                + "ON MAP.POLICY_PARTIAL_ID = POL.ENTITLEMENT_POLICY_PARTIAL_ID "
                + "WHERE MAP.POLICY_GRP_ID = ? ";

		try {
			ps = conn.prepareStatement(query);
			ps.setInt(1, policyGroupId);
			rs = ps.executeQuery();
            if (rs.next()) {
                // creates the policy partial name list with effect
                policyPartialNamesList = "[ Name: " + rs.getString("POLICY_PARTIAL_NAME") + ", Effect: " +
                        rs.getString("EFFECT") + "]";
                //add comma sign in the beginning for other records
                while (rs.next()) {
                    policyPartialNamesList += ",[ Name: " + rs.getString("POLICY_PARTIAL_NAME") + ", Effect: " +
                            rs.getString("EFFECT") + "]";
                }
            }
        } catch (SQLException e) {
			log.error("SQL Error while executing the query to get policies under each policy group mapped with " +
					"the application : " + query + " : (Policy Group Id:" + policyGroupId + ")", e);
			/*
			In the code im using a single SQL connection passed from the parent function so I'm logging the error here
			and throwing the SQLException so  the connection will be disposed by the parent function.
			*/
			throw e;
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, null, rs);
		}
		return policyPartialNamesList;
	}

    /**
     * Fetch predefined all the non mandatory Java policy list with the mapped Application Id If each policy is mapped
     * with the given appId it will return the same appId else it will return NULL The mandatory policies are excluded
     * from the returned list And also only includes the Global (application level) policies
     *
     * @param applicationUUId
     * @param isGlobalPolicy  :if application level policy - true else if resource level policy - false
     * @return array of all available java policies
     * @throws barley.appmgt.api.AppManagementException on error
     */
    public static JSONArray getAvailableJavaPolicyList(String applicationUUId, boolean isGlobalPolicy)
            throws AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        Boolean isMandatory = false; //no need to show the mandatory fields as options
        JSONArray arrJavaPolicies = new JSONArray();

        String query = " SELECT distinct POL.JAVA_POLICY_ID AS JAVA_POLICY_ID, POL.DISPLAY_NAME, POL.DESCRIPTION " +
                ",DISPLAY_ORDER_SEQ_NO, ? AS APP_ID " +
                "FROM APM_APP_JAVA_POLICY POL " +
                "LEFT JOIN APM_APP_JAVA_POLICY_MAPPING MAP ON POL.JAVA_POLICY_ID=MAP.JAVA_POLICY_ID " +
                "LEFT JOIN APM_APP APP ON APP.APP_ID=MAP.APP_ID AND APP.UUID = ? " +
                "WHERE IS_MANDATORY= ? AND IS_GLOBAL= ? " +
                "ORDER BY DISPLAY_ORDER_SEQ_NO  ";

        try {
            conn = APIMgtDBUtil.getConnection();
            ps = conn.prepareStatement(query);
            ps.setString(1, applicationUUId);
            ps.setString(2, applicationUUId);
            ps.setBoolean(3, isMandatory);
            ps.setBoolean(4, isGlobalPolicy);
            rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject objPolicy = new JSONObject();
                objPolicy.put("javaPolicyId", rs.getInt("JAVA_POLICY_ID"));
                objPolicy.put("displayName", rs.getString("DISPLAY_NAME"));
                objPolicy.put("description", rs.getString("DESCRIPTION"));
                objPolicy.put("displayOrder", rs.getInt("DISPLAY_ORDER_SEQ_NO"));
                objPolicy.put("applicationId", rs.getString("APP_ID"));
                arrJavaPolicies.add(objPolicy);
            }

        } catch (SQLException e) {
            handleException("SQL Error while executing the query to get available Java Policies : "
                                    + query + e.getMessage(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return arrJavaPolicies;
    }

	/**
	 * save java policy and application mapping
	 *
	 * @param connection    : SQL Connection
	 * @param applicationId : Application Id
	 * @param javaPolicyIds : selected Java Policy
	 * @throws AppManagementException
	 */
	public void saveJavaPolicyMappings(Connection connection, int applicationId, Object[] javaPolicyIds)
			throws SQLException {

		PreparedStatement preparedStatement = null;
		String query = " INSERT INTO APM_APP_JAVA_POLICY_MAPPING(APP_ID, JAVA_POLICY_ID) VALUES(?,?) ";

		try {
			preparedStatement = connection.prepareStatement(query);

			for (Object policyId : javaPolicyIds) {
				preparedStatement.setInt(1, applicationId);
				preparedStatement.setInt(2, Integer.parseInt(policyId.toString()));
				preparedStatement.addBatch();
			}
			preparedStatement.executeBatch();

		} catch (SQLException e) {
			StringBuilder builder = new StringBuilder(); //build log description String
			builder.append("SQL Error while executing the query to save Java Policy mappings : ").append(query)
					.append(" : (applicationId:").append(applicationId).append(", Java Policy Ids:")
					.append(javaPolicyIds).append(") : ").append(e.getMessage());
			log.error(builder.toString(), e);
			/*
			In the code im using a single SQL connection passed from the parent function so I'm logging the error here
			and throwing the SQLException so the connection will be disposed by the parent function.
			*/
			throw e;
		} finally {
			APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
		}
	}


	/**
	 * Fetch all the mandatory and mapped Java policy list with the mapped Application Id
	 * Only includes the Global (Application level) policies
	 *
	 * @param applicationUUId
	 * @param isGlobalPolicy :if application level policy - true else if resource level policy - false
	 * @return array of all available java policies
	 * @throws barley.appmgt.api.AppManagementException on error
	 */
	public static List<JavaPolicy> getMappedJavaPolicyList(String applicationUUId, boolean isGlobalPolicy)
			throws AppManagementException {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		List<JavaPolicy> policies = new ArrayList<JavaPolicy>();
		String strJavaPolicyProperty = "";
        boolean isMandatory = true;

        String query = " SELECT POL.JAVA_POLICY_ID AS JAVA_POLICY_ID, DISPLAY_NAME, " +
                "DISPLAY_ORDER_SEQ_NO, APP.APP_ID AS APP_ID, FULL_QUALIFI_NAME, POLICY_PROPERTIES " +
                "FROM APM_APP_JAVA_POLICY POL " +
                "LEFT JOIN APM_APP_JAVA_POLICY_MAPPING MAP ON POL.JAVA_POLICY_ID=MAP.JAVA_POLICY_ID " +
                "LEFT JOIN APM_APP APP ON APP.APP_ID=MAP.APP_ID AND APP.UUID = ? " +
                "WHERE (IS_MANDATORY= ? OR APP.APP_ID IS NOT NULL) AND IS_GLOBAL= ? " +
                "ORDER BY DISPLAY_ORDER_SEQ_NO  ";

		try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(query);
			ps.setString(1, applicationUUId);
			ps.setBoolean(2, isMandatory);
            ps.setBoolean(3, isGlobalPolicy);
            rs = ps.executeQuery();
			JSONParser parser = new JSONParser();
			while (rs.next()) {
				JavaPolicy policy = new JavaPolicy();

				policy.setPolicyID(rs.getInt("JAVA_POLICY_ID"));
				policy.setPolicyName(rs.getString("DISPLAY_NAME"));
				policy.setFullQualifiName(rs.getString("FULL_QUALIFI_NAME"));
				policy.setOrder(rs.getInt("DISPLAY_ORDER_SEQ_NO"));
				strJavaPolicyProperty = rs.getString("POLICY_PROPERTIES");
				if (strJavaPolicyProperty != null) {
					policy.setProperties((JSONObject) parser.parse(strJavaPolicyProperty));
				}
				policies.add(policy);
			}

		} catch (SQLException e) {
			handleException("SQL Error while executing the query to get mapped Java Policies : "
					+ query + e.getMessage(), e);
		} catch (org.json.simple.parser.ParseException e) {
			handleException("JSON parsing error while fetching the mapped Java Policy Property : " +
					strJavaPolicyProperty, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return policies;
	}


    /**
     * Get an array of url patterns/ http verbs mapped with policy group
     *
     * @param policyGroupId
     * @return url Pattens/http verbs mapped with policy group
     * @throws barley.appmgt.api.AppManagementException
     */
    public NativeArray getPolicyGroupAssociatedApps(int policyGroupId) throws AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String query = "SELECT HTTP_METHOD,URL_PATTERN FROM APM_APP_URL_MAPPING WHERE POLICY_GRP_ID= ?";
        NativeArray arrUrlPatterns = new NativeArray(0); //contains objects of url patterns and will be returned
        int count = 0;

        try {
            conn = APIMgtDBUtil.getConnection();
            ps = conn.prepareStatement(query);
            ps.setInt(1, policyGroupId);
            rs = ps.executeQuery();

            while (rs.next()) {
                NativeObject objUrlPattern = new NativeObject();
                objUrlPattern.put("urlPattern", objUrlPattern, rs.getString("URL_PATTERN"));
                objUrlPattern.put("httpMethod", objUrlPattern, rs.getString("HTTP_METHOD"));
                count++;
                arrUrlPatterns.put(count, arrUrlPatterns, objUrlPattern);
            }

        } catch (SQLException e) {
            handleException("Failed to retrieve url patterns associated with policy group : " + policyGroupId, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return arrUrlPatterns;
    }

    public static boolean isUsagePublishingEnabledForApp(WebApp webApp) throws AppManagementException {
        Connection conn = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        boolean isUsagePublishingEnabledForApp = false;

        String getStatisticsPolicyIdQuery = "SELECT JAVA_POLICY_ID " +
                                  "FROM APM_APP_JAVA_POLICY " +
                                  "WHERE FULL_QUALIFI_NAME = 'barley.appmgt.usage.publisher.APIMgtUsageHandler' ";

        String getPoliciesOfAppQuery = "SELECT JAVA_POLICY_ID " +
                                    "FROM APM_APP_JAVA_POLICY_MAPPING " +
                                    "WHERE APP_ID = (SELECT APP_ID FROM APM_APP WHERE APP_PROVIDER = ? " +
                                    "AND APP_NAME = ? AND APP_VERSION = ? AND CONTEXT = ?)";

        try {
            conn = APIMgtDBUtil.getConnection();
            prepStmt = conn.prepareStatement(getStatisticsPolicyIdQuery);
            rs = prepStmt.executeQuery();

            int statisticsPolicyId = 0;

            while (rs.next()) {
                statisticsPolicyId = rs.getInt("JAVA_POLICY_ID");
            }
            prepStmt.close();

            APIIdentifier webAppIdentifier = webApp.getId();
            prepStmt = conn.prepareStatement(getPoliciesOfAppQuery);
            prepStmt.setString(1, webAppIdentifier.getProviderName());
            prepStmt.setString(2, webAppIdentifier.getApiName());
            prepStmt.setString(3, webAppIdentifier.getVersion());
            prepStmt.setString(4, webApp.getContext());

            rs = prepStmt.executeQuery();
            while (rs.next()) {
                if (statisticsPolicyId == rs.getInt("JAVA_POLICY_ID")) {
                    isUsagePublishingEnabledForApp = true;
                    break;
                }
            }

        } catch (SQLException e) {
            handleException("Error while retrieving java policies for web app :" +
                            " Provider : " + webApp.getId().getProviderName() +
                            " App : " + webApp.getId().getApiName() +
                            " Version : " +  webApp.getId().getVersion() , e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, conn, rs);
        }

        return isUsagePublishingEnabledForApp;
    }

	/**
	 * Retrieves TRACKING_CODE sequences from APM_APP Table
	 *@param uuid : Application UUID
	 *@return TRACKING_CODE
	 *@throws barley.appmgt.api.AppManagementException
	 */
	public  String getTrackingID(String uuid) throws AppManagementException {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		String query = "SELECT TRACKING_CODE  FROM APM_APP WHERE UUID= ?";
		String value =  null;
		try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(query);
			ps.setString(1, uuid);
			rs = ps.executeQuery();
			while (rs.next()) {
				value=rs.getString("TRACKING_CODE");
			}
		} catch (SQLException e) {
			handleException("Sorry wrong UUID " + uuid, e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return value;
	}

	public List<String> getApplicableEntitlementPolicyIds(int appId,
			String matchedUrlPattern, String httpVerb) throws AppManagementException {

		List<String> applicableEntitlementPolicies = new ArrayList<String>();

		Connection con = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        String query = "SELECT "
		        		+ "POLICY_XACML.POLICY_ID AS POLICY_ID "
		        		+ "FROM "
		        		+ "APM_APP_URL_MAPPING APP_URL, "
		        		+ "APM_POLICY_GRP_PARTIAL_MAPPING POLICY_XACML "
		        		+ "WHERE "
		        		+ "APP_URL.POLICY_GRP_ID = POLICY_XACML.POLICY_GRP_ID "
		        		+ "AND APP_URL.APP_ID = ? "
		        		+ "AND URL_PATTERN = ? "
		        		+ "AND HTTP_METHOD = ?";

        try {
            con = APIMgtDBUtil.getConnection();
            prepStmt = con.prepareStatement(query);

            prepStmt.setInt(1, appId);
            prepStmt.setString(2, matchedUrlPattern);
            prepStmt.setString(3, httpVerb);

            rs = prepStmt.executeQuery();

            while(rs.next()) {
                applicableEntitlementPolicies.add(rs.getString("POLICY_ID"));
            }


        } catch (SQLException e) {
            handleException(
            		String.format("Error while getting applicable entitlement policies for "
            				+ "AppId : %d, URL pattern : %s, HTTP verb : %s", appId, matchedUrlPattern, httpVerb), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, con, rs);
        }

        return applicableEntitlementPolicies;

	}

	/**
	 * Check availability of the web application according to given version and web app name
	 *
	 *@param webAppName
	 * 				name of the web application
	 *@param version
	 * 			   version of the web application
	 *
	 *@throws barley.appmgt.api.AppManagementException
	 */
	public  static  boolean isWebAppAvailable(String webAppName,String version) throws AppManagementException {
		Connection conn = null;
		PreparedStatement ps = null;
		boolean status = true;
		ResultSet rs = null;
        String query = "SELECT APP_ID FROM APM_APP WHERE APP_NAME = ? AND APP_VERSION = ?";
        try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(query);
			ps.setString(1, webAppName);
			ps.setString(2, version);
			rs = ps.executeQuery();
			if (rs.next()) {
				status= true;
			}else {
				status= false;
			}
		} catch (SQLException e) {
			handleException("Error while retrieving web application details", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return  status;
	}

	/**
	 * Get the seq number of the Publish Statistics: policy
	 *
	 *@return seq number of the java policy
	 *
	 *@throws barley.appmgt.api.AppManagementException
	 */
	public static String getDisplayOrderSeqNo() throws AppManagementException {
		Connection conn = null;
		PreparedStatement ps = null;
		String seqNo = null;
		ResultSet rs = null;
		String query = "SELECT DISPLAY_ORDER_SEQ_NO FROM APM_APP_JAVA_POLICY WHERE DISPLAY_NAME='Publish Statistics:'";
		try {
			conn = APIMgtDBUtil.getConnection();
			ps = conn.prepareStatement(query);
			rs = ps.executeQuery();
			if(rs.next()) {
				seqNo = rs.getInt(1)+"";
			}
		} catch (SQLException e) {
			handleException("Error while retrieving display order seq number", e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, conn, rs);
		}
		return  seqNo;
	}

    public static WebAppInfoDTO getWebAppByTrackingCode(String trackingCode) throws AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        WebAppInfoDTO webAppInfoDTO = new WebAppInfoDTO();

        String ssoInfoSqlQuery = "SELECT APP_NAME, APP_VERSION, APP_PROVIDER, CONTEXT, " +
                "SAML2_SSO_ISSUER " +
                "FROM APM_APP " +
                "WHERE TRACKING_CODE = ? ";

        try {
            conn = APIMgtDBUtil.getConnection();
            ps = conn.prepareStatement(ssoInfoSqlQuery);
            ps.setString(1, trackingCode);
            rs = ps.executeQuery();
            if (rs.next()) {
                webAppInfoDTO.setSaml2SsoIssuer("SAML2_SSO_ISSUER");
                webAppInfoDTO.setContext(rs.getString("CONTEXT"));
                webAppInfoDTO.setVersion(rs.getString("APP_VERSION"));
                webAppInfoDTO.setWebAppName(rs.getString("APP_NAME"));
                webAppInfoDTO.setProviderId(rs.getString("APP_PROVIDER"));
            }
        } catch (SQLException e) {
            handleException("Error while retrieving web application details", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }

        return webAppInfoDTO;
    }


    /**
     * Get external published APP Stores details which are stored in database.
     *
     * @param apiIdentifier WebApp Identifier
     * @throws barley.appmgt.api.AppManagementException if failed to get external APPStores
     */
    public Set<AppStore> getExternalAppStoresDetails(APIIdentifier apiIdentifier) throws AppManagementException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        Connection conn = null;
        Set<AppStore> storesSet = new HashSet<AppStore>();
        try {
            conn = APIMgtDBUtil.getConnection();
            final String sqlQuery = "SELECT STORE_ID " +
                    "FROM APM_EXTERNAL_STORES " +
                    "WHERE APP_ID = ? ";

            ps = conn.prepareStatement(sqlQuery);
            if (log.isDebugEnabled()) {
                String msg = String.format("Getting web app id of app : provider:%s ,name :%s, version :%s"
                        , apiIdentifier.getProviderName(), apiIdentifier.getApiName(), apiIdentifier.getVersion());
                log.debug(msg);
            }
            //Get app id
            int appId;
            appId = getAPIID(apiIdentifier, conn);
            if (appId == -1) {
                String msg = String.format("Could not load App record  of app : provider:%s ,name :%s, version :%s"
                        , apiIdentifier.getProviderName(), apiIdentifier.getApiName(), apiIdentifier.getVersion());
                log.error(msg);
                throw new AppManagementException(msg);
            }

            if (log.isDebugEnabled()) {
                String msg = "Getting published external app store details for app id : " + appId;
                log.debug(msg);
            }
            ps.setInt(1, appId);
            rs = ps.executeQuery();
            while (rs.next()) {
                AppStore store = new AppStore();
                store.setName(rs.getString("STORE_ID"));
                store.setPublished(true);
                storesSet.add(store);
            }


        } catch (SQLException e) {
            handleException("Error while getting external app store details from the database for  the app" +
                    " : " + apiIdentifier.getApiName() + "-" + apiIdentifier.getVersion(), e);

        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return storesSet;
    }


    /**
     * Store external AppStore details to which app successfully published.
     *
     * @param apiId     APIIdentifier
     * @param appStores AppStore set
     * @throws barley.appmgt.api.AppManagementException
     */
    public void addExternalAppStoresDetails(APIIdentifier apiId, Set<AppStore> appStores)
            throws AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);

            //This query to add external App Stores to database table
            final String sqlQuery = "INSERT " +
                    "INTO APM_EXTERNAL_STORES (APP_ID, STORE_ID) " +
                    "VALUES (?,?)";

            if (log.isDebugEnabled()) {
                String msg = String.format("Getting web app id of app : provider:%s ,name :%s, version :%s"
                        , apiId.getProviderName(), apiId.getApiName(), apiId.getVersion());
                log.debug(msg);
            }

            //Get app id
            int appId;
            appId = getAPIID(apiId, conn);
            if (appId == -1) {
                String msg = String.format("Could not load app record of app : provider:%s ,name :%s, version :%s"
                        , apiId.getProviderName(), apiId.getApiName(), apiId.getVersion());
                log.error(msg);
                throw new AppManagementException(msg);
            }

            if (log.isDebugEnabled()) {
                String msg = String.format("Add published external app store details of app ->" +
                        " provider:%s ,name :%s, version :%s"
                        , apiId.getProviderName(), apiId.getApiName(), apiId.getVersion());
                log.debug(msg);
            }
            ps = conn.prepareStatement(sqlQuery);
            Iterator it = appStores.iterator();
            while (it.hasNext()) {
                AppStore store = (AppStore) it.next();
                ps.setInt(1, appId);
                ps.setString(2, store.getName());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback storing external app store details  for  app" +
                            " : " + apiId.getApiName() + "-" + apiId.getVersion(), e1);
                }
            }
            handleException("Failed to store external app store details for  app" +
                    " : " + apiId.getApiName() + "-" + apiId.getVersion(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, null);
        }
    }

    /**
     * Delete the records of external app Store details.
     *
     * @param identifier APIIdentifier
     * @param appStores  AppStores set
     * @throws AppManagementException
     */
    public void deleteExternalAppStoresDetails(APIIdentifier identifier, Set<AppStore> appStores)
            throws AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);

            final String sqlQuery = "DELETE FROM APM_EXTERNAL_STORES WHERE APP_ID=? AND STORE_ID=? ";

            if (log.isDebugEnabled()) {
                String msg = String.format("Getting web app Id for provider:%s ,name :%s, version :%s"
                        , identifier.getProviderName(), identifier.getApiName(), identifier.getVersion());
                log.debug(msg);
            }
            //Get app id
            int appId;
            appId = getAPIID(identifier, conn);
            if (appId == -1) {
                String msg = String.format("Could not load app record of app : provider:%s ,name :%s, version :%s"
                        , identifier.getProviderName(), identifier.getApiName(), identifier.getVersion());
                log.error(msg);
                throw new AppManagementException(msg);
            }

            if (log.isDebugEnabled()) {
                String msg = String.format("Delete external app store details of app :" +
                        " provider:%s ,name :%s, version :%s"
                        , identifier.getProviderName(), identifier.getApiName(), identifier.getVersion());
                log.debug(msg);
            }

            ps = conn.prepareStatement(sqlQuery);
            Iterator it = appStores.iterator();
            while (it.hasNext()) {
                Object storeObject = it.next();
                AppStore store = (AppStore) storeObject;
                ps.setInt(1, appId);
                ps.setString(2, store.getName());
                ps.addBatch();
            }

            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback deleting external app store details  for  app" +
                            " : " + identifier.getApiName() + "-" + identifier.getVersion(), e1);
                }
            }
            handleException("Failed to delete external app store details for  app" +
                    " : " + identifier.getApiName() + "-" + identifier.getVersion(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, null);
        }
    }

    /**
     * Update default version when updating information without appId.
     *
     * @param webApp
     */
    public void updateDefaultVersionDetails(WebApp webApp) throws AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            saveDefaultVersionDetails(webApp, conn);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException sqlEx) {
                    log.error("Failed to rollback version updating details for  app" +
                                      " : " + webApp.getApiName(), sqlEx);
                }
            }
            handleException("Failed to update version details for  app" +
                                    " : " + webApp.getApiName(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, null);
        }
    }

    /**
     * Check if the given version is the default version.
     *
     * @param appName
     * @param providerName
     * @param appStatus  if true then return published app version else default app version
     * @return default app version
     * @throws AppManagementException
     */
    public static String getDefaultVersion(String appName, String providerName, AppDefaultVersion appStatus)
            throws AppManagementException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        Connection conn = null;
        String defaultVersion = "";
        try {
            String columnName;
            conn = APIMgtDBUtil.getConnection();
            if (appStatus == AppDefaultVersion.APP_IS_PUBLISHED) {
                columnName = "PUBLISHED_DEFAULT_APP_VERSION";
            } else {
                columnName = "DEFAULT_APP_VERSION";
            }
            String sqlQuery =
                    "SELECT " + columnName +
                            " FROM APM_APP_DEFAULT_VERSION WHERE APP_NAME =? AND APP_PROVIDER=? AND TENANT_ID=? ";

            ps = conn.prepareStatement(sqlQuery);
            if (log.isDebugEnabled()) {
                String msg = String.format("Getting default version details of app : provider:%s ,name :%s"
                        , providerName, appName);
                log.debug(msg);
            }
            int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);

            ps.setString(1, appName);
            ps.setString(2, providerName);
            ps.setInt(3, tenantId);
            rs = ps.executeQuery();
            if (rs.next()) {
                defaultVersion = rs.getString(columnName);
            }
        } catch (SQLException e) {
            handleException("Error while getting default version details from the database for the app" +
                                    " : " + appName, e);

        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return defaultVersion;
    }


    /**
     * Check if the given version is the default version.
     *
     * @param appName
     * @param providerName
     * @param appStatus    if true then return published app version else default app version
     * @return default app version
     * @throws AppManagementException
     * @conn SQL connection
     */
    public static String getDefaultVersion(String appName, String providerName, AppDefaultVersion appStatus,
                                           Connection conn)
            throws AppManagementException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        String defaultVersion = "";
        try {
            String columnName;
            if (appStatus == AppDefaultVersion.APP_IS_PUBLISHED) {
                columnName = "PUBLISHED_DEFAULT_APP_VERSION";
            } else {
                columnName = "DEFAULT_APP_VERSION";
            }
            String sqlQuery =
                    "SELECT " + columnName +
                            " FROM APM_APP_DEFAULT_VERSION WHERE APP_NAME =? AND APP_PROVIDER=? AND TENANT_ID=? ";

            ps = conn.prepareStatement(sqlQuery);
            if (log.isDebugEnabled()) {
                String msg = String.format("Getting default version details of app : provider:%s ,name :%s"
                        , providerName, appName);
                log.debug(msg);
            }
            int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);

            ps.setString(1, appName);
            ps.setString(2, providerName);
            ps.setInt(3, tenantId);
            rs = ps.executeQuery();
            if (rs.next()) {
                defaultVersion = rs.getString(columnName);
            }
        } catch (SQLException e) {
            handleException("Error while getting default version details from the database for the app" +
                                    " : " + appName, e);

        } finally {
            APIMgtDBUtil.closeAllConnections(ps, null, rs);
        }
        return defaultVersion == null ? "" : defaultVersion;
    }


    /**
     * Direct update default version for published apps.
     *
     * @param app
     * @throws AppManagementException
     */
    public void updatePublishedDefaultVersion(WebApp app) throws AppManagementException {
        Connection connection = null;
        PreparedStatement ps = null;
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            PreparedStatement prepStmt = null;
            String query =
                    "UPDATE APM_APP_DEFAULT_VERSION SET PUBLISHED_DEFAULT_APP_VERSION=? " +
                            "WHERE APP_NAME=? AND APP_PROVIDER=? AND TENANT_ID=? ";

            int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);

            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, app.getId().getVersion());
            prepStmt.setString(2, app.getId().getApiName());
            prepStmt.setString(3, app.getId().getProviderName());
            prepStmt.setInt(4, tenantId);

            prepStmt.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            handleException("Failed to update version details for  app" +
                                    " : " + app.getApiName(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, null);
        }
    }

    /**
     * Get all versions of a WebApp
     *
     * @param appName
     * @param providerName
     * @return List of Versions
     * @throws AppManagementException
     */
    public static List<String> getAllVersionOfWebApp(String appName, String providerName)
            throws AppManagementException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        Connection conn = null;
        List<String> webAppVersions = new ArrayList<>();
        try {
            String columnName;
            conn = APIMgtDBUtil.getConnection();
            String sqlQuery = "SELECT APP_VERSION FROM APM_APP where APP_NAME =? and APP_PROVIDER =? ";

            ps = conn.prepareStatement(sqlQuery);
            if (log.isDebugEnabled()) {
                String msg = String.format("Getting all versions of app : provider:%s ,name :%s"
                        , providerName, appName);
                log.debug(msg);
            }
            ps.setString(1, appName);
            ps.setString(2, providerName);
            rs = ps.executeQuery();
            while (rs.next()) {
                webAppVersions.add(rs.getString("APP_VERSION"));
            }
        } catch (SQLException e) {
            handleException("Error while getting all the versions from the database for the app" +
                                    " : " + appName, e);

        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return webAppVersions;
    }

    /**
     * Check if a given WebApp has more versions.
     *
     * @param apiIdentifier
     * @return true if has more versions
     * @throws AppManagementException
     */
    public static boolean hasMoreVersions(APIIdentifier apiIdentifier)
            throws AppManagementException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        Connection conn = null;
        boolean hasMoreVersions = false;
        try {
            conn = APIMgtDBUtil.getConnection();
            String sqlQuery =
                    "SELECT COUNT(*) AS ROWCOUNT FROM APM_APP WHERE APP_NAME =? AND APP_PROVIDER =? AND APP_VERSION!=?";
            ps = conn.prepareStatement(sqlQuery);
            ps.setString(1, apiIdentifier.getApiName());
            ps.setString(2, apiIdentifier.getProviderName());
            ps.setString(3, apiIdentifier.getVersion());

            rs = ps.executeQuery();
            if (rs.next()) {
                hasMoreVersions = (rs.getInt("ROWCOUNT") > 0);
            }
        } catch (SQLException e) {
            handleException("Error while getting more version details for the app" +
                                    " : " + apiIdentifier.getApiName(), e);

        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return hasMoreVersions;
    }

    /**
     * Check if the given app is the default version.
     *
     * @param apiIdentifier
     * @return true if given is the default version
     * @throws AppManagementException
     */
    public static boolean isDefaultVersion(APIIdentifier apiIdentifier)
            throws AppManagementException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        Connection conn = null;
        boolean isDefaultVersion = false;
        try {
            conn = APIMgtDBUtil.getConnection();
            String sqlQuery =
                    "SELECT COUNT(*) AS ROWCOUNT FROM APM_APP_DEFAULT_VERSION WHERE APP_NAME =? AND APP_PROVIDER =? " +
                            "AND  " +
                            "(DEFAULT_APP_VERSION=? OR PUBLISHED_DEFAULT_APP_VERSION =?)";
            ps = conn.prepareStatement(sqlQuery);
            ps.setString(1, apiIdentifier.getApiName());
            ps.setString(2, apiIdentifier.getProviderName());
            ps.setString(3, apiIdentifier.getVersion());
            ps.setString(4, apiIdentifier.getVersion());

            rs = ps.executeQuery();
            if (rs.next()) {
                isDefaultVersion = (rs.getInt("ROWCOUNT") > 0);
            }
        } catch (SQLException e) {
            handleException("Error while checking if the default version for the app" +
                                    " : " + apiIdentifier.getApiName(), e);

        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return isDefaultVersion;
    }


    /**
     * Get WebApp basic details by app uuid.
     *
     * @param webAppUUID
     * @return Asset details
     * @throws AppManagementException
     */
    public static WebApp getAppDetailsFromUUID(String webAppUUID) throws
                                                                AppManagementException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        WebApp webApp = null;
        String query = "SELECT APP_ID, APP_PROVIDER, APP_NAME, APP_VERSION, CONTEXT " +
                "FROM APM_APP WHERE UUID = ? ";

        try {
            conn = APIMgtDBUtil.getConnection();
            ps = conn.prepareStatement(query);
            ps.setString(1, webAppUUID);
            rs = ps.executeQuery();
            if (rs.next()) {
                APIIdentifier apiIdentifier = new APIIdentifier(rs.getString("APP_PROVIDER"), rs.getString("APP_NAME"),
                                                                rs.getString("APP_VERSION"));
                webApp = new WebApp(apiIdentifier);
                webApp.setApiName(rs.getString("APP_NAME"));
                webApp.setContext(rs.getString("CONTEXT"));
            }
        } catch (SQLException e) {
            handleException("Error when executing the SQL: " + query + " (WebApp UUID:" + webAppUUID + ")", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return webApp;
    }

    /**
     * This method add a new entry to table APM_FAVOURITE_APPS which contains the favourite apps detail of user. (Add
     * the given app as favouirte app for given user)
     *
     * @param identifier      API Identifier
     * @param username        Username
     * @param tenantIdOfUser  Tenant Id of Logged in user
     * @param tenantIdOfStore Tenant Id of Store
     * @throws AppManagementException
     */
    public void addToFavouriteApps(APIIdentifier identifier, String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {

        Connection conn = null;
        ResultSet resultSet = null;
        PreparedStatement ps = null;
        int apiId = -1;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            String getApiQuery =
                    "SELECT APP_ID FROM APM_APP  WHERE APP_PROVIDER = ? AND APP_NAME = ? AND " +
                            "APP_VERSION = ? AND TENANT_ID = ? ";
            ps = conn.prepareStatement(getApiQuery);
            ps.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            ps.setString(2, identifier.getApiName());
            ps.setString(3, identifier.getVersion());
            ps.setInt(4, tenantIdOfStore);

            if (log.isDebugEnabled()) {
                log.debug("Getting web  app id of : " + identifier.getApiName() + "-" + identifier.getProviderName() +
                                  "-" + identifier.getVersion());
            }

            resultSet = ps.executeQuery();
            if (resultSet.next()) {
                apiId = resultSet.getInt("APP_ID");
            }
            resultSet.close();
            ps.close();

            if (apiId == -1) {
                String msg = "Unable to get the WebApp ID for: " + identifier;
                log.error(msg);
                throw new AppManagementException(msg);
            }

            if (log.isDebugEnabled()) {
                log.debug("Adding  app: " + identifier.getApiName() + "-" + identifier.getProviderName() +
                                  "-" + identifier.getVersion() + " as favourite app for  user : " + username +
                                  " of tenant: " + tenantIdOfUser);
            }
            // This query to insert to the APM_FAVOURITE_APPS table
            String sqlQuery =
                    "INSERT INTO APM_FAVOURITE_APPS (USER_ID, TENANT_ID, APP_ID, CREATED_TIME) "
                            + "VALUES (?,?,?,?)";

            ps = conn.prepareStatement(sqlQuery);
            ps.setString(1, username);
            ps.setInt(2, tenantIdOfUser);
            ps.setInt(3, apiId);
            ps.setTimestamp(4, new Timestamp(new java.util.Date().getTime()));

            ps.executeUpdate();
            // finally commit transaction
            conn.commit();

        } catch (SQLException e) {
            handleException("Failed to add app: " + identifier.getApiName() + "-" + identifier.getProviderName() +
                                    "-" + identifier.getVersion() + " as favourite app for  user : " + username +
                                    " of tenant: " + tenantIdOfUser, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
        }
    }

    /**
     * This method delete  an entry from APM_FAVOURITE_APPS based on given app detail and username.
     * (remove the given app from favourite app list of given user)
     * @param identifier API Identifier
     * @param username   Username
     * @param tenantIdOfUser Tenant Id of Logged in user
     * @param tenantIdOfStore Tenant Id of Store
     * @throws AppManagementException
     */
    public void removeFromFavouriteApps(APIIdentifier identifier, String username, int tenantIdOfUser,
                                        int tenantIdOfStore)
            throws
            AppManagementException {
        Connection conn = null;
        ResultSet resultSet = null;
        PreparedStatement ps = null;
        int apiId = -1;

        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            String getApiQuery =
                    "SELECT APP_ID FROM APM_APP  WHERE APP_PROVIDER = ? AND APP_NAME = ? AND " +
                            "APP_VERSION = ? AND TENANT_ID = ? ";

            ps = conn.prepareStatement(getApiQuery);
            ps.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            ps.setString(2, identifier.getApiName());
            ps.setString(3, identifier.getVersion());
            ps.setInt(4, tenantIdOfStore);

            if (log.isDebugEnabled()) {
                log.debug("Getting web  app id of : " + identifier.getApiName() + "-" + identifier.getProviderName() +
                                  "-" + identifier.getVersion());
            }

            resultSet = ps.executeQuery();
            if (resultSet.next()) {
                apiId = resultSet.getInt("APP_ID");
            }
            resultSet.close();
            ps.close();

            if (apiId == -1) {
                throw new AppManagementException("Unable to get the WebApp ID for: " + identifier);
            }
            if (log.isDebugEnabled()) {
                log.debug("Removing  app: " + identifier.getApiName() + "-" + identifier.getProviderName() +
                                  "-" + identifier.getVersion() + " from favourite apps for  user : " + username +
                                  " of tenant: " + tenantIdOfUser);
            }
            // This query to updates the APM_FAVOURITE_APPS
            String sqlQuery =
                    "DELETE FROM APM_FAVOURITE_APPS WHERE APP_ID = ? AND USER_ID = ? AND TENANT_ID = ?";

            ps = conn.prepareStatement(sqlQuery);
            ps.setInt(1, apiId);
            ps.setString(2, username);
            ps.setInt(3, tenantIdOfUser);
            ps.executeUpdate();
            // finally commit transaction
            conn.commit();
        } catch (SQLException e) {
            handleException("Failed to remove app: " + identifier.getApiName() + "-" + identifier.getProviderName() +
                                    "-" + identifier.getVersion() + "from favourite apps for  user : " + username +
                                    " of tenant: " + tenantIdOfUser, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
        }
    }


    /**
     * Check whether given app is favourite app of user in given tenant store.
     *
     * @param identifier      APP Identifier
     * @param username        Username
     * @param tenantIdOfUser  Tenant Id of Logged in user
     * @param tenantIdOfStore Tenant Id of Store(=Tenant Id of App)
     * @return true if favourite app else false
     * @throws AppManagementException
     */
    public boolean isFavouriteApp(APIIdentifier identifier, String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        if (log.isDebugEnabled()) {
            log.debug("Checking whether given app: " + identifier.getApiName() + "-" + identifier.getProviderName() +
                              "-" + identifier.getVersion() + " is selected as favourite by  user : " + username +
                              " of tenant: " + tenantIdOfUser +" in the tenat store: "+tenantIdOfStore);
        }
        Connection conn = null;
        PreparedStatement ps = null;
        boolean status = false;
        ResultSet rs = null;
        String query = "SELECT COUNT(*) AS ROWCOUNT FROM APM_FAVOURITE_APPS  " +
                "WHERE    APP_ID = (SELECT APP_ID  FROM APM_APP " +
                "WHERE APP_NAME = ? " +
                "AND APP_VERSION = ? AND APP_PROVIDER = ? AND TENANT_ID = ? ) " +
                "AND USER_ID = ? " +
                "AND TENANT_ID = ?";
        try {
            conn = APIMgtDBUtil.getConnection();
            ps = conn.prepareStatement(query);
            ps.setString(1, identifier.getApiName());
            ps.setString(2, identifier.getVersion());
            ps.setString(3, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            ps.setInt(4, tenantIdOfStore);
            ps.setString(5, username);
            ps.setInt(6, tenantIdOfUser);


            rs = ps.executeQuery();
            if (rs.next()) {
                if (rs.getInt("ROWCOUNT") > 0) {
                    status = true;
                }
            }
        } catch (SQLException e) {
            handleException("Error while checking whether given app: " + identifier.getApiName() + "-" +
                                    identifier.getProviderName() +
                                    "-" + identifier.getVersion() + " is selected as favourite by  user : " + username +
                                    " of tenant: " + tenantIdOfUser, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return status;
    }

    /**
     * Returns the favourite app of given user for given tenant store based on given sort option.
     *
     * @param username        Username
     * @param tenantIdOfUser  Tenant Id of Logged in user
     * @param tenantIdOfStore Tenant Id of Store
     * @param sortOption      Sorting option
     * @return List of APP Identifier
     * @throws AppManagementException
     */
    public List<APIIdentifier> getFavouriteApps(String username, int tenantIdOfUser, int tenantIdOfStore,
                                                WebAppSortOption sortOption)
            throws AppManagementException {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving favourite apps details of  user : " + username + " of tenant: " + tenantIdOfUser +
                              " for tenant store: " + tenantIdOfStore + ",Sort option: " + sortOption);
        }
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet appInfoResult = null;
        List<APIIdentifier> apiIdentifiers = new ArrayList<APIIdentifier>();

        try {
            connection = APIMgtDBUtil.getConnection();
            String query = null;

            if (sortOption == WebAppSortOption.SORT_BY_CREATED_TIME_DESC) {
                ps = connection.prepareStatement(SQLConstants.GET_FAVOURITE_APPS_SORT_BY_CREATED_TIME_DESC);
            } else {
                ps = connection.prepareStatement(SQLConstants.GET_FAVOURITE_APPS_SORT_BY_APP_NAME_ASC);
            }

            ps.setString(1, username);
            ps.setInt(2, tenantIdOfUser);
            ps.setInt(3, tenantIdOfStore);
            appInfoResult = ps.executeQuery();

            while (appInfoResult.next()) {
                APIIdentifier identifier = new APIIdentifier(
                        appInfoResult.getString("APP_PROVIDER"),
                        appInfoResult.getString("APP_NAME"),
                        appInfoResult.getString("APP_VERSION"));
                apiIdentifiers.add(identifier);
            }

        } catch (SQLException e) {
            handleException("Error while getting all favourite apps of  user : " + username + " of tenant: " +
                                    tenantIdOfUser + " for tenant store:" + tenantIdOfStore,
                            e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, appInfoResult);
        }
        return apiIdentifiers;
    }

    /**
     * Returns favourite apps of user based on given search criteria.
     *
     * @param username        Username
     * @param tenantIdOfUser  Tenant Id of Logged in user
     * @param tenantIdOfStore Tenant Id of Store
     * @param searchOption    Search Option
     * @param searchValue     Search Value
     * @return List of App Identifiers
     * @throws AppManagementException
     */
    public List<APIIdentifier> searchFavouriteApps(String username, int tenantIdOfUser, int tenantIdOfStore,
                                                   WebAppSearchOption searchOption, String searchValue)
            throws AppManagementException {
        if (log.isDebugEnabled()) {
            log.debug("Searching favourite apps details of  user : " + username + " of tenant: " + tenantIdOfUser +
                              " for tenant store: " + tenantIdOfStore + ", SearchOption: " + searchOption +
                              ",Search Value: " + searchValue);
        }
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet appInfoResult = null;
        List<APIIdentifier> apiIdentifiers = new ArrayList<APIIdentifier>();

        try {
            connection = APIMgtDBUtil.getConnection();

            if (searchOption == WebAppSearchOption.SEARCH_BY_APP_PROVIDER) {
                ps = connection.prepareStatement(SQLConstants.SEARCH_FAVOURITE_APPS_BY_APP_PROVIDER);
                searchValue = AppManagerUtil.replaceEmailDomainBack(searchValue);
            } else {
                ps = connection.prepareStatement(SQLConstants.SEARCH_FAVOURITE_APPS_BY_APP_NAME);
            }

            ps.setString(1, username);
            ps.setInt(2, tenantIdOfUser);
            ps.setInt(3, tenantIdOfStore);
            ps.setString(4, "%" + searchValue + "%");
            appInfoResult = ps.executeQuery();

            while (appInfoResult.next()) {
                APIIdentifier identifier = new APIIdentifier(
                        appInfoResult.getString("APP_PROVIDER"),
                        appInfoResult.getString("APP_NAME"),
                        appInfoResult.getString("APP_VERSION"));
                apiIdentifiers.add(identifier);
            }

        } catch (SQLException e) {
            handleException("Error while searching  favourite apps of  user : " + username + " of tenant: " +
                                    tenantIdOfUser + " for tenant store:" + tenantIdOfStore + ", SearchOption: " +
                                    searchOption + ",Search Value: " + searchValue, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, appInfoResult);
        }
        return apiIdentifiers;
    }

    /**
     * Returns the  apps(anonymous + subscribed) which can be accessed by user in given tenant store.
     *
     * @param username        Username
     * @param tenantIdOfUser  Tenant Id of Logged in User
     * @param tenantIdOfStore Tenant Id of Store
     * @param sortOption      Sorting option
     * @param treatAsSite     Treat As Site (TRUE->site,FALSE->WebApp)
     * @return List of APP Identifier
     * @throws AppManagementException
     */
    public List<APIIdentifier> getUserAccessibleApps(String username, int tenantIdOfUser, int tenantIdOfStore,
                                                     WebAppSortOption sortOption, boolean treatAsSite)
            throws AppManagementException {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving accessible apps details of  user : " + username + " of tenant: " + tenantIdOfUser +
                              " for tenant store:" + tenantIdOfStore + ", " + "Sort Option: " + sortOption +
                              ",Treat As Site: " + treatAsSite);
        }
        List<APIIdentifier> apiIdentifiers = new ArrayList<APIIdentifier>();
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;
        boolean allowAnonymous = true;
        try {
            connection = APIMgtDBUtil.getConnection();
            int applicationId = getApplicationId(username, tenantIdOfUser, connection);

            if (sortOption == WebAppSortOption.SORT_BY_SUBSCRIBED_TIME_DESC) {
                ps = connection.prepareStatement(SQLConstants.GET_USER_ACCESSIBlE_APPS_ORDER_BY_SUBSCRIPTION_TIME);
            } else {
                ps = connection.prepareStatement(SQLConstants.GET_USER_ACCESSIBlE_APPS_ORDER_BY_APP_NAME);
            }

            ps.setBoolean(1,treatAsSite);
            ps.setInt(2,tenantIdOfStore);
            ps.setInt(3,applicationId);
            ps.setBoolean(4,allowAnonymous);
            result = ps.executeQuery();

            while (result.next()) {
                APIIdentifier apiIdentifier = new APIIdentifier(
                        AppManagerUtil.replaceEmailDomain(result.getString("APP_PROVIDER")),
                        result.getString("APP_NAME"),
                        result.getString("APP_VERSION")
                );
                apiIdentifiers.add(apiIdentifier);
            }

        } catch (SQLException e) {
            handleException(
                    "Failed to get accessible apps details from tenant store :" + tenantIdOfStore + " for  user : " +
                            username + " of tenant: " + tenantIdOfUser, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
        return apiIdentifiers;
    }

    /**
     * Returns apps(anonymous + subscribed) of given user for given tenant store based on given search option.
     *
     * @param username        UserName
     * @param tenantIdOfUser  Tenant Id of Logged in user
     * @param tenantIdOfStore Tenant Id of Store(=Tenant Id of App)
     * @param treatAsSite     Treat As Site (TRUE->site,FALSE->WebApp)
     * @param searchOption    Search Option
     * @param searchValue     Search Value
     * @param registry Registry of the current store.
     * @return List of App Identifiers
     * @throws AppManagementException
     */
    public List<APIIdentifier> searchUserAccessibleApps(String username, int tenantIdOfUser, int tenantIdOfStore,
                                                        boolean treatAsSite, WebAppSearchOption searchOption,
                                                        String searchValue, Registry registry) throws
                                                                                               AppManagementException {
        if (log.isDebugEnabled()) {
            log.debug("Searching accessible apps details of  user : " + username + " of tenant: " + tenantIdOfUser +
                              " for tenant store: " + tenantIdOfStore + ",Search Option: " + searchOption +
                              ",Search Value: " + searchValue + ",Treat As Site: " + treatAsSite);
        }

        List<APIIdentifier> apiIdentifiers = new ArrayList<APIIdentifier>();
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet result = null;
        boolean allowAnonymous = true;

        try {
            connection = APIMgtDBUtil.getConnection();
            int applicationId = getApplicationId(username, tenantIdOfUser, connection);

            if (searchOption == WebAppSearchOption.SEARCH_BY_APP_PROVIDER) {
                ps = connection.prepareStatement(SQLConstants.SEARCH_USER_ACCESSIBLE_APPS_BY_APP_PROVIDER );
                searchValue = AppManagerUtil.replaceEmailDomainBack(searchValue);
            } else if (searchOption == WebAppSearchOption.SEARCH_BY_BUSINESS_OWNER) {
                Map<String, List<String>> businessOwnerIdsMap = new HashMap<String, List<String>>();
                List<String> businessOwnerIdList = getBusinessOwnerIdsBySearchPrefix(searchValue, tenantIdOfStore);
                for (String businessOwnerId : businessOwnerIdList) {
                    businessOwnerIdsMap.put(AppMConstants.API_OVERVIEW_BUSS_OWNER , Arrays.asList(businessOwnerId));
                    businessOwnerIdsMap.put(AppMConstants.APP_OVERVIEW_TREAT_AS_A_SITE, Arrays.asList(String.valueOf
                            (treatAsSite)));
                    getUserAccessibleAppsByBusinessOwner(apiIdentifiers, businessOwnerIdsMap, registry, tenantIdOfStore,
                                                         tenantIdOfUser, username);
                }
            } else {
                ps = connection.prepareStatement(SQLConstants.SEARCH_USER_ACCESSIBLE_APPS_BY_APP_NAME);
            }

            // ps is null when search by business owner.
            if (ps != null) {
                ps.setBoolean(1,treatAsSite);
                ps.setInt(2,tenantIdOfStore);
                ps.setInt(3,applicationId);
                ps.setBoolean(4,allowAnonymous);
                ps.setString(5, "%" + searchValue + "%");
                result = ps.executeQuery();

                while (result.next()) {
                    APIIdentifier apiIdentifier = new APIIdentifier(
                            AppManagerUtil.replaceEmailDomain(result.getString("APP_PROVIDER")),
                            result.getString("APP_NAME"),
                            result.getString("APP_VERSION")
                    );
                    apiIdentifiers.add(apiIdentifier);
                }

            }

        } catch (SQLException e) {
            handleException(
                    "Failed to search accessible apps details from tenant store :" + tenantIdOfStore + " for  user : " +
                            username + " of tenant: " + tenantIdOfUser, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, result);
        }
        return apiIdentifiers;
    }

    private void getUserAccessibleAppsByBusinessOwner(List<APIIdentifier> apiIdentifiers, Map<String, List<String>>
            businessOwnerIdsMap, Registry registry, int tenantIdOfStore, int tenantOfUser, String userName) throws
                                                                                           AppManagementException {
        boolean isTenantFlowStarted = false;
        try {
            UserRealmService realmService =
                    (UserRealmService) PrivilegedBarleyContext.getThreadLocalCarbonContext()
                            .getOSGiService(UserRealmService.class);
            String requestedTenantDomain = realmService.getTenantManager().getDomain(tenantIdOfStore);

            if (requestedTenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(
                    requestedTenantDomain)) {
                isTenantFlowStarted = true;
                PrivilegedBarleyContext.startTenantFlow();
                PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(requestedTenantDomain, true);
            }

            APIInfoDTO[] subscribedApps = getSubscribedAPIsOfUser(userName);
            if (tenantIdOfStore != tenantOfUser) {
                // User has to set anonnymous to get myapps.
                PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(BarleyConstants
                        .REGISTRY_ANONNYMOUS_USERNAME);
            }
            GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);
            GenericArtifactManager artifactManager = new GenericArtifactManager(registry,
                                                                                AppMConstants.API_KEY);
            GenericArtifact[] artifacts = artifactManager.findGenericArtifacts(businessOwnerIdsMap);
            for (GenericArtifact artifact : artifacts) {
                String provider = artifact.getAttribute(AppMConstants.API_OVERVIEW_PROVIDER);
                String appName = artifact.getAttribute(AppMConstants.API_OVERVIEW_NAME);
                String appVersion = artifact.getAttribute(AppMConstants.API_OVERVIEW_VERSION);
                for (APIInfoDTO apiInfoDTO : subscribedApps) {
                    if (appName.equals(apiInfoDTO.getApiName()) && appVersion.equals(apiInfoDTO.getVersion()) &&
                            provider.equals(apiInfoDTO.getProviderId())) {
                        APIIdentifier apiIdentifier = new APIIdentifier(
                                AppManagerUtil
                                        .replaceEmailDomain(artifact.getAttribute(AppMConstants.API_OVERVIEW_PROVIDER)),
                                artifact.getAttribute(AppMConstants.API_OVERVIEW_NAME),
                                artifact.getAttribute(AppMConstants.API_OVERVIEW_VERSION)
                        );
                        apiIdentifiers.add(apiIdentifier);
                        break;
                    }
                }
            }
        } catch (RegistryException e) {
            handleException("Failed to search accessible apps details from tenant store :" + tenantIdOfStore, e);
        } catch (UserStoreException e) {
            handleException("Failed to get tenant domain for tenant id :" + tenantIdOfStore, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedBarleyContext.endTenantFlow();
            }
        }
    }

    /**
     * Returns application id mapped to username.
     * @param username Username
     * @param tenantId Tenant Id of User
     * @param connection SQL Connection to the data store
     * @return Application Id
     * @throws AppManagementException
     */
    private int getApplicationId(String username, int tenantId, Connection connection) throws AppManagementException {
        PreparedStatement ps = null;
        ResultSet result = null;
        String query = "SELECT APPLICATION_ID" +
                " FROM APM_SUBSCRIBER  ,APM_APPLICATION" +
                " WHERE APM_SUBSCRIBER.SUBSCRIBER_ID = APM_APPLICATION.SUBSCRIBER_ID" +
                " AND APM_SUBSCRIBER.TENANT_ID = ? AND APM_SUBSCRIBER.USER_ID = ?";
        int applicationId = -1;

        try {
            ps = connection.prepareStatement(query);
            ps.setInt(1, tenantId);
            ps.setString(2, username);
            result = ps.executeQuery();

            if (result.next()) {
                applicationId = result.getInt("APPLICATION_ID");
            }

        } catch (SQLException e) {
            handleException(
                    "Failed to get application id from table APM_APPLICATION for user :" + username + "of tenant id :" +
                            tenantId, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, null, result);
        }
        return applicationId;
    }

    /**
     * This method add myfaouvrite page of given store as home page for given user
     *
     * @param username        Username
     * @param tenantIdOfUser  Tenant Id of Logged in user
     * @param tenantIdOfStore Tenant Id of Store
     * @throws AppManagementException
     */
    public void addToStoreFavouritePage(String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        if (log.isDebugEnabled()) {
            log.debug("Adding myfavourite page of tenant store : " + tenantIdOfStore + " as home page of user : "
                              + username + " of tenant: " + tenantIdOfUser);
        }
        Connection conn = null;
        ResultSet resultSet = null;
        PreparedStatement ps = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            // This query to insert to the APM_STORE_FAVOURITE_PAGE table
            String sqlQuery =
                    "INSERT INTO APM_STORE_FAVOURITE_PAGE (USER_ID, TENANT_ID_OF_USER, TENANT_ID_OF_STORE) "
                            + "VALUES (?,?,?)";

            ps = conn.prepareStatement(sqlQuery);
            ps.setString(1, username);
            ps.setInt(2, tenantIdOfUser);
            ps.setInt(3, tenantIdOfStore);
            ps.executeUpdate();
            // finally commit transaction
            conn.commit();
        } catch (SQLException e) {
            handleException("Failed to add favourite page detail for user : " + username + "of tenant :" +
                                    tenantIdOfUser, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
        }
    }

    /**
     * This method remove myfavourite page of given tenant store from home page for given user.
     *
     * @param username        Username
     * @param tenantIdOfUser  Tenant Id of Logged in user
     * @param tenantIdOfStore Tenant Id of Store
     * @throws AppManagementException
     */
    public void removeFromStoreFavouritePage(String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        if (log.isDebugEnabled()) {
            log.debug("Removing myfavourite page of tenant store : " + tenantIdOfStore + " from home page of user : "
                              + username + " of tenant: " + tenantIdOfUser);
        }
        Connection conn = null;
        ResultSet resultSet = null;
        PreparedStatement ps = null;
        String sqlQuery =
                "DELETE FROM APM_STORE_FAVOURITE_PAGE WHERE USER_ID = ? AND TENANT_ID_OF_USER = ? AND " +
                        "TENANT_ID_OF_STORE= ?";

        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            ps = conn.prepareStatement(sqlQuery);
            ps.setString(1, username);
            ps.setInt(2, tenantIdOfUser);
            ps.setInt(3, tenantIdOfStore);
            ps.executeUpdate();
            // finally commit transaction
            conn.commit();
        } catch (SQLException e) {
            handleException("Failed to remove favourite page detail for user : " + username + "of tenant :" +
                                    tenantIdOfUser,
                            e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
        }
    }

    /**
     * This method check whether given user has selected myfavourite page given tenant store as homepage.
     *
     * @param username        Username
     * @param tenantIdOfUser  Tenant Id of Logged in user
     * @param tenantIdOfStore Tenant Id of Store
     * @return true if user has selected else false
     * @throws AppManagementException
     */
    public boolean hasFavouritePage(String username, int tenantIdOfUser, int tenantIdOfStore)
            throws AppManagementException {
        if (log.isDebugEnabled()) {
            log.debug("Checking whether user : " + username + " of tenant: " + tenantIdOfUser +
                              " has selected myfavourite page of tenant store : " + tenantIdOfStore + " as homepage");
        }
        Connection conn = null;
        PreparedStatement ps = null;
        boolean status = false;
        ResultSet rs = null;
        String query = "SELECT * FROM APM_STORE_FAVOURITE_PAGE  WHERE " +
                "USER_ID = ? AND TENANT_ID_OF_USER = ? AND " +
                "TENANT_ID_OF_STORE= ?";
        try {
            conn = APIMgtDBUtil.getConnection();
            ps = conn.prepareStatement(query);
            ps.setString(1, username);
            ps.setInt(2, tenantIdOfUser);
            ps.setInt(3, tenantIdOfStore);

            rs = ps.executeQuery();
            if (rs.next()) {
                status = true;
            }
        } catch (SQLException e) {
            handleException("Error while checking whether user : " + username + " of tenant : " + tenantIdOfUser +
                                    " has selecte favourite page as home page in tenant store:" + tenantIdOfStore, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return status;
    }

    private String addRangeCondition(String rangeField, boolean andNeeded, String connectionType) {
        String query = "";
        if (andNeeded) {
            query += " AND ";
        }
        if (!connectionType.contains("Oracle")) {
            query += rangeField + " BETWEEN ? AND ? ";
        } else {
            query += rangeField + " BETWEEN TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS') AND TO_TIMESTAMP(?, 'YYYY-MM-DD " +
                    "HH24:MI:SS') ";
        }
        return query;
    }

    
    /** 
     * Adds a comment for an API
     *
     * @param identifier  API Identifier
     * @param commentText Commented Text
     * @param userId        User who did the comment
     * @return Comment ID
     */
    public int addComment(APIIdentifier identifier, String commentText, String userId) throws AppManagementException {

        Connection connection = null;
        ResultSet resultSet = null;
        ResultSet insertSet = null;
        PreparedStatement getPrepStmt = null;
        PreparedStatement insertPrepStmt = null;
        int commentId = -1;
        int appId = -1;

        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);

            String getApiQuery = SQLConstants.GET_APP_ID_SQL;
            getPrepStmt = connection.prepareStatement(getApiQuery);
            getPrepStmt.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            getPrepStmt.setString(2, identifier.getApiName());
            getPrepStmt.setString(3, identifier.getVersion());
            resultSet = getPrepStmt.executeQuery();
            if (resultSet.next()) {
                appId = resultSet.getInt("APP_ID");
            }

            if (appId == -1) {
                String msg = "Unable to get the API ID for: " + identifier;
                log.error(msg);
                throw new AppManagementException(msg);
            }

            /*This query to update the AM_API_COMMENTS table */
            String addCommentQuery = SQLConstants.ADD_COMMENT_SQL;

            /*Adding data to the AM_API_COMMENTS table*/
            String dbProductName = connection.getMetaData().getDatabaseProductName();
            insertPrepStmt = connection.prepareStatement(addCommentQuery,
                new String[]{DBUtils.getConvertedAutoGeneratedColumnName(dbProductName, "comment_id")});

            insertPrepStmt.setString(1, commentText);
            insertPrepStmt.setString(2, userId);
            insertPrepStmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()), Calendar.getInstance());
            insertPrepStmt.setInt(4, appId);

            insertPrepStmt.executeUpdate();
            insertSet = insertPrepStmt.getGeneratedKeys();
            while (insertSet.next()) {
                commentId = Integer.parseInt(insertSet.getString(1));
            }
            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback the add comment ", e1);
                }
            }
            handleException("Failed to add comment data, for  " + identifier.getApiName() + '-' + identifier
                    .getVersion(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(getPrepStmt, connection, resultSet);
            APIMgtDBUtil.closeAllConnections(insertPrepStmt, null, insertSet);
        }
        return commentId;
    }
    
    public void updateComment(int commentId, String commentText) throws AppManagementException {

        Connection connection = null;
        PreparedStatement prepStmt = null;
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);

            /*This query to update the AM_API_COMMENTS table */
            String updateCommentQuery = SQLConstants.UPDATE_COMMENT_SQL;

            /*Adding data to the AM_API_COMMENTS table*/
            prepStmt = connection.prepareStatement(updateCommentQuery);
            prepStmt.setString(1, commentText);
            prepStmt.setInt(2, commentId);
            prepStmt.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback the update comment ", e1);
                }
            }
            handleException("Failed to update comment data, for  " + commentId, e);
        } finally {
        	APIMgtDBUtil.closeAllConnections(prepStmt, connection, null);
        }
    }
    
    public void deleteComment(String userId, int commentId) throws AppManagementException {
    	
    	Comment commentObj = getCommentById(commentId);
    	
    	if(!userId.equals(commentObj.getUser())){
    		throw new AppManagementException("Unmatched comment register - " + userId);
    	}
    	
    	deleteComment(commentId);
 	}
    
    public void deleteComment(int commentId) throws AppManagementException {

        Connection connection = null;
        PreparedStatement prepStmt = null;
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);

            /*This query to update the AM_API_COMMENTS table */
            String deleteCommentQuery = SQLConstants.DELETE_COMMENT_SQL;

            /*Adding data to the AM_API_COMMENTS table*/
            prepStmt = connection.prepareStatement(deleteCommentQuery);
            prepStmt.setInt(1, commentId);
            prepStmt.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback the delete comment ", e1);
                }
            }
            handleException("Failed to update delete data, for  " + commentId, e);
        } finally {
        	APIMgtDBUtil.closeAllConnections(prepStmt, connection, null);
        }
    }


    /**
     * Returns all the Comments on an API
     *
     * @param identifier API Identifier
     * @return Comment Array
     * @throws APIManagementException
     */
    public Comment[] getComments(APIIdentifier identifier) throws AppManagementException {
        List<Comment> commentList = new ArrayList<Comment>();
        Connection connection = null;
        ResultSet resultSet = null;
        PreparedStatement prepStmt = null;

        String sqlQuery = SQLConstants.GET_COMMENTS_SQL;
        try {
            connection = APIMgtDBUtil.getConnection();
            prepStmt = connection.prepareStatement(sqlQuery);
            prepStmt.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            prepStmt.setString(2, identifier.getApiName());
            prepStmt.setString(3, identifier.getVersion());
            resultSet = prepStmt.executeQuery();
            while (resultSet.next()) {
                Comment comment = new Comment();
                comment.setCommentId(resultSet.getInt("COMMENT_ID"));
                comment.setText(resultSet.getString("COMMENT_TEXT"));
                comment.setUser(resultSet.getString("COMMENTED_USER"));
                comment.setCreatedTime(new java.util.Date(resultSet.getTimestamp("DATE_COMMENTED").getTime()));
                commentList.add(comment);
            }
        } catch (SQLException e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e1) {
                log.error("Failed to retrieve comments ", e1);
            }
            handleException("Failed to retrieve comments for  " + identifier.getApiName() + '-' + identifier
                    .getVersion(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, connection, resultSet);
        }
        return commentList.toArray(new Comment[commentList.size()]);
    }
    
    public Comment getCommentById(int commentId) throws AppManagementException {
   	 	Connection connection = null;
        ResultSet resultSet = null;
        PreparedStatement prepStmt = null;
        Comment comment = null;
              
        String sqlQuery = SQLConstants.GET_COMMENT_BY_ID_SQL;
        try {
            connection = APIMgtDBUtil.getConnection();
            prepStmt = connection.prepareStatement(sqlQuery);
            prepStmt.setInt(1, commentId);
            resultSet = prepStmt.executeQuery();
            if (resultSet.next()) {
                comment = new Comment();
                comment.setCommentId(resultSet.getInt("COMMENT_ID"));
                comment.setText(resultSet.getString("COMMENT_TEXT"));
                comment.setUser(resultSet.getString("COMMENTED_USER"));
                comment.setCreatedTime(new java.util.Date(resultSet.getTimestamp("DATE_COMMENTED").getTime()));
              
                comment.setAgreeCount(resultSet.getInt("COMMENT_AGREE_COUNT"));
                comment.setDisagreeCount(resultSet.getInt("COMMENT_DISAGREE_COUNT"));
            }
        } catch (SQLException e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e1) {
                log.error("Failed to retrieve comment ", e1);
            }
            handleException("Failed to retrieve comment for Comment ID : " + commentId , e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, connection, resultSet);
        }
        
        
        return comment;
   }
    
    public Comment[] getSortedCreatedTimeComments(APIIdentifier identifier, int page, int count) throws AppManagementException {
    	String query = SQLConstants.GET_SORTED_CREATED_TIME_COMMENTS_SQL;
    	return getSortedComments(query, identifier, page, count);
    }
    
    
    public Comment[] getSortedAgreeCountComments(APIIdentifier identifier, int page, int count) throws AppManagementException {
    	String query = SQLConstants.GET_SORTED_AGREE_COUNT_COMMENT_SQL;
    	return getSortedComments(query, identifier, page, count);
    }
    
    
    public Comment[] getSortedComments(String query, APIIdentifier identifier, int page, int count) throws AppManagementException {
        List<Comment> commentList = new ArrayList<Comment>();
        Connection connection = null;
        ResultSet resultSet = null;
        PreparedStatement prepStmt = null;
        
        int startNo = (page-1) * count;

        try {
            connection = APIMgtDBUtil.getConnection();
            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, AppManagerUtil.replaceEmailDomainBack(identifier.getProviderName()));
            prepStmt.setString(2, identifier.getApiName());
            prepStmt.setString(3, identifier.getVersion());
            prepStmt.setInt(4, startNo);
            prepStmt.setInt(5, count);
            
            
            resultSet = prepStmt.executeQuery();
            while (resultSet.next()) {
                Comment comment = new Comment();
                comment.setCommentId(resultSet.getInt("COMMENT_ID"));
                comment.setText(resultSet.getString("COMMENT_TEXT"));
                comment.setUser(resultSet.getString("COMMENTED_USER"));
                comment.setCreatedTime(new java.util.Date(resultSet.getTimestamp("DATE_COMMENTED").getTime()));
                
                comment.setAgreeCount(resultSet.getInt("COMMENT_AGREE_COUNT"));
                comment.setDisagreeCount(resultSet.getInt("COMMENT_DISAGREE_COUNT"));
                
                commentList.add(comment);
            }
        } catch (SQLException e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e1) {
                log.error("Failed to retrieve comments ", e1);
            }
            handleException("Failed to retrieve comments for  " + identifier.getApiName() + '-' + identifier
                    .getVersion(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, connection, resultSet);
        }
        return commentList.toArray(new Comment[commentList.size()]);
    }
    

    
    public void addRating(APIIdentifier apiId, int rating, String user) throws AppManagementException {
        Connection conn = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            addRating(apiId, rating, user, conn);

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback the add Application ", e1);
                }
            }
            handleException("Failed to add Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null, conn, null);
        }
    }

    /**
     * @param apiIdentifier API Identifier
     * @param userId        User Id
     * @throws APIManagementException if failed to add Application
     */
    public void addRating(APIIdentifier apiIdentifier, int rating, String userId, Connection conn)
            throws AppManagementException, SQLException {
        PreparedStatement ps = null;
        PreparedStatement psSelect = null;
        ResultSet rs = null;

        try {
            int tenantId;
            tenantId = AppManagerUtil.getTenantId(userId);
            //Get subscriber Id
            Subscriber subscriber = getSubscriber(userId, tenantId, conn);
            if (subscriber == null) {
                String msg = "Could not load Subscriber records for: " + userId;
                log.error(msg);
                throw new AppManagementException(msg);
            }
            int apiId;
            apiId = getAPIID(apiIdentifier, conn);
            if (apiId == -1) {
                String msg = "Could not load API record for: " + apiIdentifier.getApiName();
                log.error(msg);
                throw new AppManagementException(msg);
            }
            boolean userRatingExists = false;
            //This query to check the ratings already exists for the user in the AM_API_RATINGS table
            String sqlQuery = SQLConstants.GET_API_RATING_SQL;

            psSelect = conn.prepareStatement(sqlQuery);
            psSelect.setInt(1, apiId);
            psSelect.setInt(2, subscriber.getId());
            rs = psSelect.executeQuery();

            while (rs.next()) {
                userRatingExists = true;
            }

            String sqlAddQuery;
            if (!userRatingExists) {
                //This query to update the AM_API_RATINGS table
                sqlAddQuery = SQLConstants.APP_API_RATING_SQL;
            } else {
                //This query to insert into the AM_API_RATINGS table
                sqlAddQuery = SQLConstants.UPDATE_API_RATING_SQL;
            }
            // Adding data to the AM_API_RATINGS  table
            ps = conn.prepareStatement(sqlAddQuery);
            ps.setInt(1, rating);
            ps.setInt(2, apiId);
            ps.setInt(3, subscriber.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            handleException("Failed to add API rating of the user:" + userId, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, null, rs);
            APIMgtDBUtil.closeAllConnections(psSelect, null, null);
        }
    }

    public void removeAPIRating(APIIdentifier apiId, String user) throws AppManagementException {
        Connection conn = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);

            removeAPIRating(apiId, user, conn);

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback the add Application ", e1);
                }
            }
            handleException("Failed to add Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null, conn, null);
        }
    }

    /**
     * @param apiIdentifier API Identifier
     * @param userId        User Id
     * @throws APIManagementException if failed to add Application
     */
    public void removeAPIRating(APIIdentifier apiIdentifier, String userId, Connection conn)
            throws AppManagementException, SQLException {
        PreparedStatement ps = null;
        PreparedStatement psSelect = null;
        ResultSet rs = null;

        try {
            int tenantId;
            int rateId = -1;
            tenantId = AppManagerUtil.getTenantId(userId);
            //Get subscriber Id
            Subscriber subscriber = getSubscriber(userId, tenantId, conn);
            if (subscriber == null) {
                String msg = "Could not load Subscriber records for: " + userId;
                log.error(msg);
                throw new AppManagementException(msg);
            }
            //Get API Id
            int apiId = -1;
            apiId = getAPIID(apiIdentifier, conn);
            if (apiId == -1) {
                String msg = "Could not load API record for: " + apiIdentifier.getApiName();
                log.error(msg);
                throw new AppManagementException(msg);
            }

            //This query to check the ratings already exists for the user in the AM_API_RATINGS table
            String sqlQuery = SQLConstants.GET_RATING_ID_SQL;

            psSelect = conn.prepareStatement(sqlQuery);
            psSelect.setInt(1, apiId);
            psSelect.setInt(2, subscriber.getId());
            rs = psSelect.executeQuery();

            while (rs.next()) {
                rateId = rs.getInt("RATING_ID");
            }
            String sqlAddQuery;
            if (rateId != -1) {
                //This query to delete the specific rate row from the AM_API_RATINGS table
                sqlAddQuery = SQLConstants.REMOVE_RATING_SQL;
                // Adding data to the AM_API_RATINGS  table
                ps = conn.prepareStatement(sqlAddQuery);
                ps.setInt(1, rateId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            handleException("Failed to delete API rating", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, null, null);
            APIMgtDBUtil.closeAllConnections(psSelect, null, rs);
        }
    }

    public int getUserRating(APIIdentifier apiId, String user) throws AppManagementException {
        Connection conn = null;
        int userRating = 0;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);

            userRating = getUserRating(apiId, user, conn);

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback getting user ratings ", e1);
                }
            }
            handleException("Failed to get user ratings", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null, conn, null);
        }
        return userRating;
    }

    /**
     * @param apiIdentifier API Identifier
     * @param userId        User Id
     * @throws APIManagementException if failed to add Application
     */
    public int getUserRating(APIIdentifier apiIdentifier, String userId, Connection conn)
            throws AppManagementException, SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        int userRating = 0;
        try {
            int tenantId;
            tenantId = AppManagerUtil.getTenantId(userId);
            //Get subscriber Id
            Subscriber subscriber = getSubscriber(userId, tenantId, conn);
            if (subscriber == null) {
                String msg = "Could not load Subscriber records for: " + userId;
                log.error(msg);
                throw new AppManagementException(msg);
            }
            //Get API Id
            int apiId = -1;
            apiId = getAPIID(apiIdentifier, conn);
            if (apiId == -1) {
                String msg = "Could not load API record for: " + apiIdentifier.getApiName();
                log.error(msg);
                throw new AppManagementException(msg);
            }
            //This query to update the AM_API_RATINGS table
            String sqlQuery = SQLConstants.GET_RATING_SQL;
            // Adding data to the AM_API_RATINGS  table
            ps = conn.prepareStatement(sqlQuery);
            ps.setInt(1, subscriber.getId());
            ps.setInt(2, apiId);
            rs = ps.executeQuery();

            while (rs.next()) {
                userRating = rs.getInt("RATING");
            }

        } catch (SQLException e) {
            handleException("Failed to add Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, null, rs);
        }
        return userRating;
    }

    public float getAverageRating(int apiId) throws AppManagementException {
        Connection conn = null;
        float avrRating = 0;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);

            if (apiId == -1) {
                String msg = "Invalid APIId : " + apiId;
                log.error(msg);
                return Float.NEGATIVE_INFINITY;
            }
            //This query to update the AM_API_RATINGS table
            String sqlQuery = SQLConstants.GET_AVERAGE_RATING_SQL;

            ps = conn.prepareStatement(sqlQuery);
            ps.setInt(1, apiId);
            rs = ps.executeQuery();

            while (rs.next()) {
                avrRating = rs.getFloat("RATING");
            }
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback getting user ratings ", e1);
                }
            }
            handleException("Failed to get user ratings", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return avrRating;
    }
    
    public float getAverageRating(APIIdentifier apiId) throws AppManagementException {
        Connection conn = null;
        float avrRating = 0;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);

            avrRating = getAverageRating(apiId, conn);
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback getting user ratings ", e1);
                }
            }
            handleException("Failed to get user ratings", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null, conn, null);
        }
        return avrRating;
    }

    /**
     * @param apiIdentifier API Identifier
     * @throws APIManagementException if failed to add Application
     */
    public float getAverageRating(APIIdentifier apiIdentifier, Connection conn)
            throws AppManagementException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        float avrRating = 0;
        try {
            //Get API Id
            int apiId;
            apiId = getAPIID(apiIdentifier, conn);
            if (apiId == -1) {
                String msg = "Could not load API record for: " + apiIdentifier.getApiName();
                log.error(msg);
                return Float.NEGATIVE_INFINITY;
            }
            //This query to update the AM_API_RATINGS table
            String sqlQuery = SQLConstants.GET_AVERAGE_RATING_SQL;

            ps = conn.prepareStatement(sqlQuery);
            ps.setInt(1, apiId);
            rs = ps.executeQuery();

            while (rs.next()) {
                avrRating = rs.getFloat("RATING");
            }

        } catch (SQLException e) {
            handleException("Failed to add Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, null, rs);
        }

        BigDecimal decimal = new BigDecimal(avrRating);
        return Float.parseFloat(decimal.setScale(1, BigDecimal.ROUND_UP).toString());
    }
    
    
    public int getRatingUserCount(APIIdentifier apiId) throws AppManagementException {
        Connection conn = null;
        int userCount = 0;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);

            userCount = getRatingUserCount(apiId, conn);
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback getting rating user count ", e1);
                }
            }
            handleException("Failed to get rating user count", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null, conn, null);
        }
        return userCount;
    }
    
    
    public int getRatingUserCount(int apiId) throws AppManagementException {
        Connection conn = null;
        int userCount = 0;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);

            if (apiId == -1) {
                String msg = "Invalid APIId : " + apiId;
                log.error(msg);
                return -1;
            }
            //This query to update the AM_API_RATINGS table
            String sqlQuery = SQLConstants.GET_RATING_USER_COUNT_SQL;

            ps = conn.prepareStatement(sqlQuery);
            ps.setInt(1, apiId);
            rs = ps.executeQuery();

            while (rs.next()) {
            	userCount = rs.getInt("USER_COUNT");
            }
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback getting rating user count ", e1);
                }
            }
            handleException("Failed to get rating user count", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return userCount;
    }
    
    public int getRatingUserCount(APIIdentifier apiIdentifier, Connection conn)
            throws AppManagementException, SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        int userCount = 0;
        try {
            //Get API Id
            int apiId;
            apiId = getAPIID(apiIdentifier, conn);
            if (apiId == -1) {
                String msg = "Could not load API record for: " + apiIdentifier.getApiName();
                log.error(msg);
                return -1;
            }
            //This query to update the AM_API_RATINGS table
            String sqlQuery = SQLConstants.GET_RATING_USER_COUNT_SQL;

            ps = conn.prepareStatement(sqlQuery);
            ps.setInt(1, apiId);
            rs = ps.executeQuery();

            while (rs.next()) {
            	userCount = rs.getInt("USER_COUNT");
            }

        } catch (SQLException e) {
            handleException("Failed to add Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, null, rs);
        }

        return userCount;
    }
    
    
    public List<WebApp> getSortedRatingApp(String tenantDomain, int page, int count, String keyword, String tag, String category) throws AppManagementException {
    	String query = SQLConstants.GET_SORTED_RATING_APP_SQL;
        return getSortedAppList(query, tenantDomain, page, count, AppMConstants.PUBLISHED, keyword, tag, category);
    }
    
    
    public List<WebApp> getSortedSubscribersCountApp(String tenantDomain, int page, int count, String keyword, String tag, String category) throws AppManagementException {
    	String query = SQLConstants.GET_SORTED_RATING_APP_SQL;
    	return getSortedAppList(query, tenantDomain, page, count, AppMConstants.PUBLISHED, keyword, tag, category);
    }
    
    public List<WebApp> getSortedCreatedTimeApp(String tenantDomain, int page, int count, String keyword, String tag, String category) throws AppManagementException {
        String query = SQLConstants.GET_SORTED_CREATED_TIME_APP_SQL;
    	return getSortedAppList(query, tenantDomain, page, count, AppMConstants.PUBLISHED, keyword, tag, category);
    }

    public int getPublishedAppCount(String tenantDomain, String keyword, String tag, String category) throws AppManagementException {
        return getSortedAppCount(tenantDomain, AppMConstants.PUBLISHED, keyword, tag, category);
    }

    public List<WebApp> getAllAppList(String tenantDomain, int page, int count, String appState) throws AppManagementException {
        String query = SQLConstants.GET_SORTED_CREATED_TIME_APP_SQL;
        return getSortedAppList(query, tenantDomain, page, count, appState, "", "", "");
    }
    public int getAllAppCount(String tenantDomain, String appState) throws AppManagementException {
        return getSortedAppCount(tenantDomain, appState, "", "", "");
    }
    
    public int getSortedAppCount(String tenantDomain, String appState, String keyword, String tag, String category) throws AppManagementException {
    	Connection connection = null;
        PreparedStatement selectPreparedStatement = null;
        ResultSet resultSet = null;
        
        int count = 0;
        
        String query = SQLConstants.GET_SORTED_APP_CNT_SQL_PREFIX + SQLConstants.GET_SORTED_APP_WHERE_SQL;
        
        try {
            
        	connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            selectPreparedStatement = connection.prepareStatement(query);
            selectPreparedStatement.setNString(1, appState);
            selectPreparedStatement.setNString(2, tenantDomain);
            selectPreparedStatement.setNString(3, keyword);
            selectPreparedStatement.setNString(4, keyword);
            selectPreparedStatement.setNString(5, keyword);
            selectPreparedStatement.setNString(6, keyword);
            selectPreparedStatement.setNString(7, tag);
            selectPreparedStatement.setNString(8, category);
            
            resultSet = selectPreparedStatement.executeQuery();
            while (resultSet.next()) {
            	count = resultSet.getInt("ROW_CNT");
            }
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    handleException("Failed to rollback getting paginated app count", ex);
                }
            }
            handleException("Failed to get paginated app count", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(selectPreparedStatement, connection, resultSet);
        }
        
        return count;
    }
    
        
    private List<WebApp> getSortedAppList(String query, String tenantDomain, int page, int count, String appState, String keyword, String tag, String category) throws AppManagementException {
    	Connection connection = null;
        PreparedStatement selectPreparedStatement = null;
        ResultSet resultSet = null;
        
        int startNo = (page-1) * count;
        List<WebApp> appList = new ArrayList<WebApp>();
        
        try {
           
        	connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            selectPreparedStatement = connection.prepareStatement(query);
            selectPreparedStatement.setNString(1, appState);
            selectPreparedStatement.setNString(2, tenantDomain);
            selectPreparedStatement.setNString(3, keyword);
            selectPreparedStatement.setNString(4, keyword);
            selectPreparedStatement.setNString(5, keyword);
            selectPreparedStatement.setNString(6, keyword);
            selectPreparedStatement.setNString(7, tag);
            selectPreparedStatement.setNString(8, category);
            selectPreparedStatement.setInt(9, startNo);
            selectPreparedStatement.setInt(10, count);
            resultSet = selectPreparedStatement.executeQuery();
            while (resultSet.next()) {
         	
            	//?????? ??? ???
            	/*	
            	WebApp app = new WebApp(new APIIdentifier(resultSet.getString("APP_PROVIDER"), resultSet.getString("APP_NAME"), resultSet.getString("APP_VERSION")));
            	app.setRating(resultSet.getFloat("RATING"));
            	Date createdDate = resultSet.getDate("CREATED_TIME");
            	if(createdDate != null) app.setCreatedDate(createdDate);
            	Date updatedDate = resultSet.getDate("UPDATED_TIME");
            	if(updatedDate != null) app.setLastUpdated(updatedDate);
            	app.setStatus(AppManagerUtil.getApiStatus(resultSet.getString("STATE")));
            	app.setSubscriptionCount(resultSet.getInt("SUBS_CNT"));
            	app.setCategory(resultSet.getString("CATEGORY"));
            	app.setThumbnailUrl(resultSet.getString("THUMBNAIL_URL"));
            	app.setDescription(resultSet.getString("DESCRIPTION"));
            	app.setTitle(resultSet.getString("TITLE"));
            	app.setTag(resultSet.getString("TAG"));
            	*/
            	
            	WebApp app = createAppFromResultSet(resultSet);
            	
            	appList.add(app);
            }
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    handleException("Failed to rollback getting Block conditions ", ex);
                }
            }
            handleException("Failed to get Block conditions", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(selectPreparedStatement, connection, resultSet);
        }
        
        return appList;
    }
    
    @Deprecated
    public int getPublicAppCount(String tenantDomain) throws AppManagementException {
    	Connection connection = null;
        PreparedStatement selectPreparedStatement = null;
        ResultSet resultSet = null;
        
        int pubAppCnt = 0;
        
        try {
           
        	connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            selectPreparedStatement = connection.prepareStatement(SQLConstants.GET_PUBLIC_APP_CNT_SQL);
            selectPreparedStatement.setNString(1, tenantDomain);

            resultSet = selectPreparedStatement.executeQuery();
            
            while (resultSet.next()) {      	
            	pubAppCnt = resultSet.getInt("PUB_APP_CNT");
 
            }
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    handleException("Failed to rollback getting Block conditions ", ex);
                }
            }
            handleException("Failed to get Block conditions", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(selectPreparedStatement, connection, resultSet);
        }
        
        return pubAppCnt;
    }
    
    
    public void addTag(APIIdentifier apiIdentifier, String tag)
            throws AppManagementException, SQLException {
        PreparedStatement ps = null;
        Connection connection = null;
        
        try {
        	connection = APIMgtDBUtil.getConnection();
            int apiId;
            apiId = getAPIID(apiIdentifier, connection);
            if (apiId == -1) {
                String msg = "Could not load API record for: " + apiIdentifier.getApiName();
                log.error(msg);
                throw new AppManagementException(msg);
            }
            
            String sqlAddQuery = SQLConstants.APP_TAG_SQL;
            // Adding data to the APM_APP_TAG  table
            ps = connection.prepareStatement(sqlAddQuery);
            ps.setInt(1, apiId);
            ps.setString(2, tag);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()), Calendar.getInstance());
            ps.executeUpdate();

        } catch (SQLException e) {
            handleException("Failed to add APP Tag of the api Name:" + apiIdentifier.getApiName(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, null);
        }
    }
    
    public void removeTag(APIIdentifier apiIdentifier) throws AppManagementException {
        PreparedStatement ps = null;
        Connection connection = null;

        try {
        	connection = APIMgtDBUtil.getConnection();
        	int apiId;
            apiId = getAPIID(apiIdentifier, connection);
            if (apiId == -1) {
                String msg = "Could not load APP record for: " + apiIdentifier.getApiName();
                log.error(msg);
                throw new AppManagementException(msg);
            }
            
        	//This query to check the ratings already exists for the user in the AM_API_RATINGS table
            String sqlQuery = SQLConstants.REMOVE_TAG_SQL;

            // Adding data to the AM_API_RATINGS  table
            ps = connection.prepareStatement(sqlQuery);
            ps.setInt(1, apiId);
            ps.executeUpdate();
        } catch (SQLException e) {
            handleException("Failed to delete API Tag", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, null);
        }
    }
    
    public List<String> getTags(APIIdentifier apiIdentifier) throws AppManagementException {
        Connection connection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String sqlQuery = SQLConstants.GET_TAG_SQL;

        List<String> tags = new ArrayList<String>();
        try {
            connection = APIMgtDBUtil.getConnection();
            int apiId;
            apiId = getAPIID(apiIdentifier, connection);
            if (apiId == -1) {
                String msg = "Could not load APP record for: " + apiIdentifier.getApiName();
                log.error(msg);
                throw new AppManagementException(msg);
            }
            
            prepStmt = connection.prepareStatement(sqlQuery);
            prepStmt.setInt(1, apiId);
            rs = prepStmt.executeQuery();

            while (rs.next()) {
            	String tagName = rs.getString("TAG_NAME");
                tags.add(tagName);
            }            
        } catch (SQLException e) {
            handleException("Error when executing the SQL : " + sqlQuery, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
        }	
        return tags;
    }
    
    
    public int setCommentAgreeValue(String userName, int commnetId, int agreeValue) throws AppManagementException {
        Connection connection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        int rtnVal = 0;

        String sqlQuery = SQLConstants.GET_COMMENT_AGREE_VALUE_SQL;
		
        try {
        	connection = APIMgtDBUtil.getConnection();     
            
            prepStmt = connection.prepareStatement(sqlQuery);
            prepStmt.setNString(1, userName);
            prepStmt.setInt(2, commnetId);
            prepStmt.setInt(3, commnetId);
            
            rs = prepStmt.executeQuery();          
                 
            if(rs.next()) {
            	
            	//?????? ???????????? ?????? ???????????? ????????? ??????
            	if(rs.getString("COMMENTED_USER").equals(userName))
            		return 0;
            	
            	if(rs.getInt("AGREE")==0) {
            		//?????? ????????? ???????????? ?????? ????????? ?????? ??????
            		sqlQuery = SQLConstants.SET_COMMENT_AGREE_VALID_VALUE_SQL;
                    prepStmt = connection.prepareStatement(sqlQuery);
                    prepStmt.setNString(1, userName);
                    prepStmt.setInt(2, commnetId);
                    prepStmt.setInt(3, agreeValue);
                    prepStmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()), Calendar.getInstance());
                    
                    rtnVal = agreeValue;
            	} else if(rs.getInt("AGREE") != agreeValue) {
            		//?????? ????????? ?????????????????? ???????????? ?????? ???????????? ??????????????? ?????? ?????? ?????? ???????????? ??????
            		return rs.getInt("AGREE");
            	} else {
            		//????????? ?????? ?????? ?????? ?????? ??????
            		sqlQuery = SQLConstants.SET_COMMENT_AGREE_EMPTY_VALUE_SQL;
                	prepStmt = connection.prepareStatement(sqlQuery);
                    prepStmt.setNString(1, userName);
                    prepStmt.setInt(2, commnetId);
            	}
            	
            	prepStmt.executeUpdate();
            }
        } catch (SQLException e) {
            handleException("Error when executing the SQL : " + sqlQuery, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(prepStmt, connection, rs);
        }
        
        return rtnVal;
    }
    
    public WebApp getApp(APIIdentifier apiIdentifier) throws AppManagementException {
        Connection connection = null;
        PreparedStatement selectPreparedStatement = null;
        ResultSet resultSet = null;
        
        WebApp app = null;
        
        String tenantDomain = MultitenantUtils.getTenantDomain(apiIdentifier.getProviderName());
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            String query = SQLConstants.GET_APP_SQL;
            selectPreparedStatement = connection.prepareStatement(query);
            selectPreparedStatement.setNString(1, "");
            selectPreparedStatement.setNString(2, tenantDomain);
            selectPreparedStatement.setNString(3, apiIdentifier.getProviderName());
            selectPreparedStatement.setNString(4, apiIdentifier.getApiName());
            selectPreparedStatement.setNString(5, apiIdentifier.getVersion());
            resultSet = selectPreparedStatement.executeQuery();
            if (resultSet.next()) {
            	app = createAppFromResultSet(resultSet);
            }
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    handleException("Failed to rollback getting sorted rating api ", ex);
                }
            }
            handleException("Failed to get sorted rating api", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(selectPreparedStatement, connection, resultSet);
        }
        
        return app;
    }
    
    
    public WebApp getAppById(int appId) throws AppManagementException {
        Connection connection = null;
        PreparedStatement selectPreparedStatement = null;
        ResultSet resultSet = null;
        
        WebApp app = null;
        
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            String query = SQLConstants.GET_APP_BY_ID_SQL;
            selectPreparedStatement = connection.prepareStatement(query);
            selectPreparedStatement.setInt(1, appId);
            resultSet = selectPreparedStatement.executeQuery();
            if (resultSet.next()) {
            	app = new WebApp(new APIIdentifier(resultSet.getString("APP_PROVIDER"), resultSet.getString("APP_NAME"), resultSet.getString("APP_VERSION")));
            	
            	//APM_APP??? TENANT_ID, APP_ENDPOINT??? ????????? ?????? ??????
            	
            	app.setContext(resultSet.getString("CONTEXT"));
            	app.setTrackingCode(resultSet.getString("TRACKING_CODE"));
            	app.setVisibleRoles(resultSet.getString("VISIBLE_ROLES"));
            	app.setUUID(resultSet.getString("UUID"));
            	app.setSaml2SsoIssuer(resultSet.getString("SAML2_SSO_ISSUER"));
            	app.setLogoutURL(resultSet.getString("LOG_OUT_URL"));
            	app.setAllowAnonymous(resultSet.getBoolean("APP_ALLOW_ANONYMOUS"));
            	
            	app.setTreatAsASite(resultSet.getString("TREAT_AS_SITE"));
            	app.setCategory(resultSet.getString("CATEGORY"));
            	app.setThumbnailUrl(resultSet.getString("THUMBNAIL_URL"));
            	app.setDescription(resultSet.getString("DESCRIPTION"));
            	app.setTitle(resultSet.getString("TITLE"));
            	
            	Date createdDate = resultSet.getDate("CREATED_TIME");
            	if(createdDate != null) app.setCreatedDate(createdDate);
            	Date updatedDate = resultSet.getDate("UPDATED_TIME");
            	if(updatedDate != null) app.setLastUpdated(updatedDate);
            	            	
            }
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    handleException("Failed to rollback getting sorted rating api ", ex);
                }
            }
            handleException("Failed to get sorted rating api", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(selectPreparedStatement, connection, resultSet);
        }
        
        return app;
    }
    
    private WebApp createAppFromResultSet(ResultSet resultSet) throws SQLException, AppManagementException {
    	
    	WebApp app = new WebApp(new APIIdentifier(resultSet.getString("APP_PROVIDER"), resultSet.getString("APP_NAME"), resultSet.getString("APP_VERSION")));
    	app.setRating(resultSet.getFloat("RATING"));
    	Date createdDate = resultSet.getDate("CREATED_TIME");
    	if(createdDate != null) app.setCreatedDate(createdDate);
    	Date updatedDate = resultSet.getDate("UPDATED_TIME");
    	if(updatedDate != null) app.setLastUpdated(updatedDate);
    	app.setStatus(AppManagerUtil.getApiStatus(resultSet.getString("STATE")));
    	app.setSubscriptionCount(resultSet.getInt("SUBS_CNT"));
    	app.setCategory(resultSet.getString("CATEGORY"));
    	app.setThumbnailUrl(resultSet.getString("THUMBNAIL_URL"));
    	app.setDescription(resultSet.getString("DESCRIPTION"));
    	app.setTitle(resultSet.getString("TITLE"));
    	app.setTag(resultSet.getString("TAG"));
    	app.setContext(resultSet.getString("CONTEXT"));

    	return app;
    }
    
    
 // (??????) 2019.11.21 - ???????????? ????????? app ?????? ????????????   
    public List<WebApp> getAPPsByProvider(String providerName) throws AppManagementException {
        Connection connection = null;
        PreparedStatement selectPreparedStatement = null;
        ResultSet resultSet = null;
        
        List<WebApp> appList = new ArrayList<WebApp>();
        String tenantDomain = MultitenantUtils.getTenantDomain(providerName);
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            String query = SQLConstants.GET_APPS_OF_PUBLISHER_SQL;
            selectPreparedStatement = connection.prepareStatement(query);
            selectPreparedStatement.setNString(1, "");
            selectPreparedStatement.setNString(2, tenantDomain);
            selectPreparedStatement.setNString(3, providerName);
            resultSet = selectPreparedStatement.executeQuery();
            while (resultSet.next()) {
            	WebApp app = createAppFromResultSet(resultSet);
            	appList.add(app);
            }
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    handleException("Failed to rollback getting sorted rating api ", ex);
                }
            }
            handleException("Failed to get api list by provider", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(selectPreparedStatement, connection, resultSet);
        }
        
        return appList;
    }
}
