package barley.appmgt.impl;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.APIIdentifier;
import barley.appmgt.api.model.APIStatus;
import barley.appmgt.api.model.APPLifecycleActions;
import barley.appmgt.api.model.App;
import barley.appmgt.api.model.Application;
import barley.appmgt.api.model.CustomProperty;
import barley.appmgt.api.model.EntitlementPolicyGroup;
import barley.appmgt.api.model.FileContent;
import barley.appmgt.api.model.MobileApp;
import barley.appmgt.api.model.OneTimeDownloadLink;
import barley.appmgt.api.model.SSOProvider;
import barley.appmgt.api.model.Subscriber;
import barley.appmgt.api.model.Subscription;
import barley.appmgt.api.model.URITemplate;
import barley.appmgt.api.model.WebApp;
import barley.appmgt.impl.dao.AppMDAO;
import barley.appmgt.impl.dto.Environment;
import barley.appmgt.impl.idp.sso.SSOConfiguratorUtil;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.APIMgtDBUtil;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.appmgt.impl.utils.AppMgtDataSourceProvider;
import barley.core.MultitenantConstants;
import barley.core.context.BarleyContext;
import barley.core.context.PrivilegedBarleyContext;
import barley.core.multitenancy.MultitenantUtils;
import barley.governance.api.exception.GovernanceException;
import barley.governance.api.generic.GenericArtifactManager;
import barley.governance.api.generic.dataobjects.GenericArtifact;
import barley.governance.api.util.GovernanceUtils;
import barley.registry.core.ActionConstants;
import barley.registry.core.Registry;
import barley.registry.core.RegistryConstants;
import barley.registry.core.config.RegistryContext;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.session.UserRegistry;
import barley.registry.core.utils.RegistryUtils;
import barley.user.api.AuthorizationManager;
import barley.user.api.UserStoreException;

/**
 * The default implementation of DefaultAppRepository which uses RDBMS and Carbon registry for persistence.
 */
public class DefaultAppRepository implements AppRepository {

    private static final Log log = LogFactory.getLog(DefaultAppRepository.class);

    private static final String POLICY_GROUP_TABLE_NAME = "APM_POLICY_GROUP";
    private static final String POLICY_GROUP_PARTIAL_MAPPING_TABLE_NAME = "APM_POLICY_GRP_PARTIAL_MAPPING";

    private Registry registry;

    public DefaultAppRepository(){

    }

    public DefaultAppRepository(Registry registry){
        this.registry = registry;
    }

    // ------------------- START : Repository API implementation methods. ----------------------------------

    @Override
    public String saveApp(App app) throws AppManagementException {

        if (AppMConstants.WEBAPP_ASSET_TYPE.equals(app.getType())) {
            return persistWebApp((WebApp) app);
        } else if (AppMConstants.MOBILE_ASSET_TYPE.equals(app.getType())) {
            return persistMobileApp((MobileApp) app);
        }

        return null;
    }

    @Override
    public String createNewVersion(App app) throws AppManagementException {

        if (AppMConstants.WEBAPP_ASSET_TYPE.equals(app.getType())) {
            WebApp newVersion = createNewWebAppVersion((WebApp) app);
            return newVersion.getUUID();
        } else if (AppMConstants.MOBILE_ASSET_TYPE.equals(app.getType())) {
            MobileApp newVersion = createNewMobileAppVersion((MobileApp) app);
            return newVersion.getUUID();
        }

        return null;
    }

    @Override
    public void updateApp(App app) throws AppManagementException {

        if (AppMConstants.WEBAPP_ASSET_TYPE.equals(app.getType())) {
            updateWebApp((WebApp) app);
        }
    }

    @Override
    public App getApp(String type, String uuid) throws AppManagementException {


        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, type);
            GenericArtifact artifact = artifactManager.getGenericArtifact(uuid);

            if(artifact != null){
                App app = getApp(type, artifact);
                app.setType(type);
                return app;
            }else{
                return null;
            }
        } catch (GovernanceException e) {
            throw new AppManagementException(String.format("Error while querying registry for '%s':'%s'", type, uuid));
        }
    }

    public WebApp getWebAppByNameAndVersion(String name, String version, int tenantId) throws AppManagementException {

        Connection connection = null;
        PreparedStatement preparedStatementToGetBasicApp = null;
        ResultSet resultSetOfBasicApp = null;

        WebApp webApp = null;
        try {
            connection = getRDBMSConnectionWithoutAutoCommit();
            String basicQuery = "SELECT * FROM APM_APP WHERE APP_NAME = ? AND APP_VERSION = ? AND TENANT_ID = ?";
            preparedStatementToGetBasicApp = connection.prepareStatement(basicQuery);

            preparedStatementToGetBasicApp.setString(1, name);
            preparedStatementToGetBasicApp.setString(2, version);
            preparedStatementToGetBasicApp.setInt(3, tenantId);

            resultSetOfBasicApp = preparedStatementToGetBasicApp.executeQuery();

            while(resultSetOfBasicApp.next()){

                String appName = resultSetOfBasicApp.getString("APP_NAME");
                String appProvider = resultSetOfBasicApp.getString("APP_PROVIDER");
                APIIdentifier id = new APIIdentifier(appProvider, appName, version);
                webApp = new WebApp(id);

                webApp.setAppTenant(Integer.toString(tenantId));
                webApp.setDatabaseId(resultSetOfBasicApp.getInt("APP_ID"));
                webApp.setUUID(resultSetOfBasicApp.getString("UUID"));

                webApp.setVersion(version);
                webApp.setContext(resultSetOfBasicApp.getString("CONTEXT"));
                webApp.setTrackingCode(resultSetOfBasicApp.getString("TRACKING_CODE"));
                webApp.setSaml2SsoIssuer(resultSetOfBasicApp.getString("SAML2_SSO_ISSUER"));
                webApp.setLogoutURL(resultSetOfBasicApp.getString("LOG_OUT_URL"));
                webApp.setAllowAnonymous(resultSetOfBasicApp.getBoolean("APP_ALLOW_ANONYMOUS"));
                webApp.setUrl(resultSetOfBasicApp.getString("APP_ENDPOINT"));
                webApp.setVisibleRoles(resultSetOfBasicApp.getString("VISIBLE_ROLES"));

                // There should be only one app for the given combination
                break;
            }

            fillWebApp(webApp, connection);

            return webApp;

        } catch (SQLException e) {
            handleException(String.format("Error while fetching the web app for name : '%s', version : '%s', tenantId : '%d'", name, version, tenantId), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatementToGetBasicApp, connection, resultSetOfBasicApp);
        }

        return null;

    }

    @Override
    public WebApp getWebAppByContextAndVersion(String context, String version, int tenantId) throws AppManagementException {

        Connection connection = null;
        PreparedStatement preparedStatementToGetBasicApp = null;
        ResultSet resultSetOfBasicApp = null;

        WebApp webApp = null;

        try {

            connection = getRDBMSConnectionWithoutAutoCommit();

            String basicQuery = "SELECT * FROM APM_APP WHERE CONTEXT = ? AND APP_VERSION = ?";
            preparedStatementToGetBasicApp = connection.prepareStatement(basicQuery);
            preparedStatementToGetBasicApp.setString(1, context);
            preparedStatementToGetBasicApp.setString(2, version);

            resultSetOfBasicApp = preparedStatementToGetBasicApp.executeQuery();

            while(resultSetOfBasicApp.next()){

                String appName = resultSetOfBasicApp.getString("APP_NAME");
                String appProvider = resultSetOfBasicApp.getString("APP_PROVIDER");
                APIIdentifier id = new APIIdentifier(appProvider, appName, version);
                webApp = new WebApp(id);

                webApp.setDatabaseId(resultSetOfBasicApp.getInt("APP_ID"));
                webApp.setUUID(resultSetOfBasicApp.getString("UUID"));

                webApp.setVersion(version);
                webApp.setContext(context);
                webApp.setTrackingCode(resultSetOfBasicApp.getString("TRACKING_CODE"));
                webApp.setSaml2SsoIssuer(resultSetOfBasicApp.getString("SAML2_SSO_ISSUER"));
                webApp.setLogoutURL(resultSetOfBasicApp.getString("LOG_OUT_URL"));
                webApp.setAllowAnonymous(resultSetOfBasicApp.getBoolean("APP_ALLOW_ANONYMOUS"));
                webApp.setUrl(resultSetOfBasicApp.getString("APP_ENDPOINT"));
                webApp.setVisibleRoles(resultSetOfBasicApp.getString("VISIBLE_ROLES"));
                webApp.setAppTenant(String.valueOf(resultSetOfBasicApp.getInt("TENANT_ID")));

                // There should be only one app for the given combination
                break;
            }

            fillWebApp(webApp, connection);

            return webApp;
        } catch (SQLException e) {
            handleException(String.format("Error while fetching the web app for context : '%s', version : '%s', tenantId : '%d'", context, version, tenantId), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatementToGetBasicApp, connection, resultSetOfBasicApp);
        }

        return null;
    }

    private WebApp fillWebApp(WebApp webApp, Connection connection) throws SQLException {

        PreparedStatement preparedStatementToGetURLMappings = null;
        PreparedStatement preparedStatementToGetEntitlementPolicies = null;
        PreparedStatement preparedStatementToGetDefaultVersion = null;
        ResultSet resultSetOfURLMappings  = null;
        ResultSet resultSetOfEntitlementPolicies = null;
        ResultSet resultSetOfDefaultVersions = null;

        try{

            String urlMappingQuery = "SELECT * from APM_APP_URL_MAPPING URL, APM_POLICY_GROUP POLICY " +
                    "WHERE URL.POLICY_GRP_ID = POLICY.POLICY_GRP_ID AND URL.APP_ID = ?";

            preparedStatementToGetURLMappings = connection.prepareStatement(urlMappingQuery);
            preparedStatementToGetURLMappings.setInt(1, webApp.getDatabaseId());
            resultSetOfURLMappings = preparedStatementToGetURLMappings.executeQuery();

            webApp.getUriTemplates().clear();
            while (resultSetOfURLMappings.next()){

                EntitlementPolicyGroup policyGroup = new EntitlementPolicyGroup();
                policyGroup.setPolicyGroupName(resultSetOfURLMappings.getString("NAME"));
                policyGroup.setPolicyGroupId(resultSetOfURLMappings.getInt("POLICY_GRP_ID"));
                policyGroup.setUserRoles(resultSetOfURLMappings.getString("USER_ROLES"));
                policyGroup.setAllowAnonymous(resultSetOfURLMappings.getBoolean("URL_ALLOW_ANONYMOUS"));
                policyGroup.setThrottlingTier(resultSetOfURLMappings.getString("THROTTLING_TIER"));

                URITemplate uriTemplate = new URITemplate();
                uriTemplate.setId(resultSetOfURLMappings.getInt("URL_MAPPING_ID"));
                uriTemplate.setPolicyGroup(policyGroup);
                uriTemplate.setHTTPVerb(resultSetOfURLMappings.getString("HTTP_METHOD"));
                uriTemplate.setUriTemplate(resultSetOfURLMappings.getString("URL_PATTERN"));

                webApp.addURITemplate(uriTemplate);
            }

            String entitlementPolicyQuery = "SELECT * FROM " +
                    "APM_POLICY_GRP_PARTIAL_MAPPING ENTITLEMENT, " +
                    "APM_APP_URL_MAPPING TEMPLATE " +
                    "WHERE " +
                    "TEMPLATE.POLICY_GRP_ID = ENTITLEMENT.POLICY_GRP_ID AND APP_ID = ?";

            preparedStatementToGetEntitlementPolicies = connection.prepareStatement(entitlementPolicyQuery);
            preparedStatementToGetEntitlementPolicies.setInt(1, webApp.getDatabaseId());
            resultSetOfEntitlementPolicies = preparedStatementToGetEntitlementPolicies.executeQuery();

            while (resultSetOfEntitlementPolicies.next()){

                int urlMappingId = resultSetOfEntitlementPolicies.getInt("URL_MAPPING_ID");

                URITemplate uriTemplate = webApp.getURITemplate(urlMappingId);

                String entitlementPolicyId = resultSetOfEntitlementPolicies.getString("POLICY_PARTIAL_ID");

                if(uriTemplate != null && entitlementPolicyId != null){
                    uriTemplate.getPolicyGroup().setEntitlementPolicyId(Integer.parseInt(entitlementPolicyId));
                }
            }

            // Fetch version information. e.g. default version.
            // Get the default version for the app group (name + provider)
            String defaultVersionQuery = "SELECT * FROM APM_APP_DEFAULT_VERSION WHERE APP_NAME = ? AND APP_PROVIDER = ? AND TENANT_ID = ?";
            preparedStatementToGetDefaultVersion = connection.prepareStatement(defaultVersionQuery);
            preparedStatementToGetDefaultVersion.setString(1, webApp.getId().getApiName());
            preparedStatementToGetDefaultVersion.setString(2, webApp.getId().getProviderName());
            preparedStatementToGetDefaultVersion.setInt(3, Integer.parseInt(webApp.getAppTenant()));

            resultSetOfDefaultVersions = preparedStatementToGetDefaultVersion.executeQuery();

            while (resultSetOfDefaultVersions.next()){
                String defaultVersion = resultSetOfDefaultVersions.getString("DEFAULT_APP_VERSION");
                webApp.setDefaultVersion(webApp.getId().getVersion().equals(defaultVersion));

                // There should be only one record for the above query.
                break;
            }

            return webApp;

        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatementToGetEntitlementPolicies, null, resultSetOfEntitlementPolicies);
            APIMgtDBUtil.closeAllConnections(preparedStatementToGetURLMappings, null, resultSetOfURLMappings);
            APIMgtDBUtil.closeAllConnections(preparedStatementToGetDefaultVersion, null, resultSetOfDefaultVersions);
        }
    }

    @Override
    public List<App> searchApps(String type, Map<String, String> searchTerms) throws AppManagementException {

        List<App> apps = new ArrayList<App>();
        List<GenericArtifact> appArtifacts = null;

        try {
            appArtifacts = getAllAppArtifacts(type);
        } catch (GovernanceException e) {
            handleException(String.format("Error while retrieving registry artifacts during app search for the type '%s'", type), e);
        }

        for(GenericArtifact artifact : appArtifacts){
            if(isSearchHit(artifact, searchTerms)){
                App app = getApp(type, artifact);
                app.setType(type);
                apps.add(app);
            }
        }

        return apps;

    }

    @Override
    public void persistStaticContents(FileContent fileContent) throws AppManagementException {
        Connection connection = null;

        PreparedStatement preparedStatement = null;
        String query = "INSERT INTO resource (UUID,TENANTID,FILENAME,CONTENTLENGTH,CONTENTTYPE,CONTENT) VALUES (?,?,?,?,?,?)";
        try {
            connection = AppMgtDataSourceProvider.getStorageDBConnection();
            if (connection.getMetaData().getDriverName().contains(AppMConstants.DRIVER_TYPE_ORACLE)) {
                query = "INSERT INTO \"resource\" (UUID,TENANTID,FILENAME,CONTENTLENGTH,CONTENTTYPE,CONTENT) VALUES " +
                        "(?,?,?,?,?,?)";
            }
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, fileContent.getUuid());
            preparedStatement.setString(2, getTenantDomainOfCurrentUser());
            preparedStatement.setString(3, fileContent.getFileName());
            preparedStatement.setInt(4, fileContent.getContentLength());
            preparedStatement.setString(5, fileContent.getContentType());
            preparedStatement.setBlob(6, fileContent.getContent());
            preparedStatement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException e1) {
                handleException(String.format("Couldn't rollback save operation for the static content"), e1);
            }
            handleException("Error occurred while saving static content :" + fileContent.getFileName(), e);
        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, null);
        }
    }

    @Override
    public FileContent getStaticContent(String contentId)throws AppManagementException {
        Connection connection = null;
        FileContent fileContent = null;

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            String query = "SELECT CONTENT,CONTENTTYPE FROM resource WHERE FILENAME = ? AND TENANTID = ?";
            connection = AppMgtDataSourceProvider.getStorageDBConnection();
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, contentId);
            preparedStatement.setString(2, getTenantDomainOfCurrentUser());
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()){
                Blob staticContentBlob = resultSet.getBlob("CONTENT");
                InputStream inputStream = staticContentBlob.getBinaryStream();
                fileContent = new FileContent();
                fileContent.setContentType(resultSet.getString("CONTENTTYPE"));
                fileContent.setContent(inputStream);
            }
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException e1) {
                handleException(String.format("Couldn't rollback retrieve operation for the static content '"+contentId+"'"), e1);
            }
            handleException("Error occurred while saving static content :" + contentId, e);
        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, null);
        }
        return fileContent;

    }

	@Override
    public int addSubscription(String subscriberName, WebApp webApp, String applicationName) throws AppManagementException {
        Connection connection = null;
        int subscriptionId = -1;
        int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);
        try {
            connection = getRDBMSConnectionWithoutAutoCommit();
            //Check for subscriber existence
            Subscriber subscriber = getSubscriber(connection, subscriberName);
            int applicationId = -1;
            int subscriberId = -1;
            if (subscriber == null) {
                subscriber = new Subscriber(subscriberName);
                subscriber.setSubscribedDate(new Date());
                subscriber.setEmail("");
                subscriber.setTenantId(tenantId);
                subscriberId = addSubscriber(connection, subscriber);

                subscriber.setId(subscriberId);
                // Add default application
                Application defaultApp = new Application(applicationName, subscriber);
                defaultApp.setTier(AppMConstants.UNLIMITED_TIER);
                applicationId = addApplication(connection, defaultApp, subscriber);
            }else{
                applicationId = getApplicationId(connection, AppMConstants.DEFAULT_APPLICATION_NAME, subscriber);
            }
            APIIdentifier appIdentifier = webApp.getId();

            /* Tenant based validation for subscription*/
            String userTenantDomain = MultitenantUtils.getTenantDomain(subscriberName);
            String appProviderTenantDomain = MultitenantUtils.getTenantDomain(
                    AppManagerUtil.replaceEmailDomainBack(appIdentifier.getProviderName()));
            boolean subscriptionAllowed = false;
            if (!userTenantDomain.equals(appProviderTenantDomain)) {
                String subscriptionAvailability = webApp.getSubscriptionAvailability();
                if (AppMConstants.SUBSCRIPTION_TO_ALL_TENANTS.equals(subscriptionAvailability)) {
                    subscriptionAllowed = true;
                } else if (AppMConstants.SUBSCRIPTION_TO_SPECIFIC_TENANTS.equals(subscriptionAvailability)) {
                    String subscriptionAllowedTenants = webApp.getSubscriptionAvailableTenants();
                    String allowedTenants[] = null;
                    if (subscriptionAllowedTenants != null) {
                        allowedTenants = subscriptionAllowedTenants.split(",");
                        if (allowedTenants != null) {
                            for (String tenant : allowedTenants) {
                                if (tenant != null && userTenantDomain.equals(tenant.trim())) {
                                    subscriptionAllowed = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            } else {
                subscriptionAllowed = true;
            }

            if (!subscriptionAllowed) {
                throw new AppManagementException("Subscription is not allowed for " + userTenantDomain);
            }
            subscriptionId =
                    persistSubscription(connection, webApp, applicationId, Subscription.SUBSCRIPTION_TYPE_INDIVIDUAL, null);
        } catch (SQLException e) {
            handleException("Error occurred in obtaining database connection.", e);
        }
        return subscriptionId;
    }

    /**
     * Persist one-tim download link reference in database
     * @param oneTimeDownloadLink
     * @throws AppManagementException
     */
    @Override
    public void persistOneTimeDownloadLink(OneTimeDownloadLink oneTimeDownloadLink) throws AppManagementException {
        Connection connection = null;

        PreparedStatement preparedStatement = null;
        String queryToPersistOneTimeDownload =
                "INSERT INTO APM_ONE_TIME_DOWNLOAD_LINK (BINARY_FILE,UUID,IS_DOWNLOADED,USERNAME, TENANT_ID, TENANT_DOMAIN, CREATED_TIME) VALUES (?,?,?,?,?,?,?)";
        try {
            connection = getRDBMSConnectionWithoutAutoCommit();
            preparedStatement = connection.prepareStatement(queryToPersistOneTimeDownload);
            preparedStatement.setString(1, oneTimeDownloadLink.getFileName());
            preparedStatement.setString(2, oneTimeDownloadLink.getUUID());
            preparedStatement.setBoolean(3, oneTimeDownloadLink.isDownloaded());
            preparedStatement.setString(4, getUsernameOfCurrentUser());
            preparedStatement.setInt(5, getTenantIdOfCurrentUser());
            preparedStatement.setString(6, getTenantDomainOfCurrentUser());
            preparedStatement.setTimestamp(7, new Timestamp(new java.util.Date().getTime()));
            preparedStatement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException e1) {
                handleException(
                        String.format("Couldn't rollback save operation of one-time download link reference for uuid "+
                                oneTimeDownloadLink.getUUID()), e1);
            }
            handleException("Error occurred while persisting one-time download link reference for uuid " +
                    oneTimeDownloadLink.getUUID(), e);
        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, null);
        }
    }

    @Override
    public String getAppUUIDbyName(String appName, String appVersion, int tenantId)
            throws AppManagementException {
        Connection conn = null;
        ResultSet resultSet = null;
        PreparedStatement ps = null;
        ResultSet result;
        String uuid = "";
        String sqlQuery =
                "SELECT UUID FROM APM_APP WHERE APP_NAME=? AND APP_VERSION=? AND TENANT_ID=?";

        try {
            conn = APIMgtDBUtil.getConnection();
            ps = conn.prepareStatement(sqlQuery);
            ps.setString(1, appName);
            ps.setString(2, appVersion);
            ps.setInt(3, tenantId);

            result = ps.executeQuery();
            if (result.next()) {
                uuid = result.getString("UUID");
            }
        } catch (SQLException e) {
            handleException("Failed to retrieve app uuid of app: " + appName + " and version: " + appVersion, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, resultSet);
        }
        return uuid;
    }

    /**
     * Retrieve one-time download link details from database
     * @param UUID
     * @return
     * @throws AppManagementException
     */
    @Override
    public OneTimeDownloadLink getOneTimeDownloadLinkDetails(String UUID) throws AppManagementException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        OneTimeDownloadLink oneTimeDownloadLink = null;
        String queryToRetrieveOneTimeDownloadLinkDetails =
                "SELECT BINARY_FILE, IS_DOWNLOADED, USERNAME, TENANT_ID, TENANT_DOMAIN FROM APM_ONE_TIME_DOWNLOAD_LINK WHERE UUID = ?";
        ResultSet downloadLinkData = null;
        try {
            connection = getRDBMSConnectionWithoutAutoCommit();
            preparedStatement = connection.prepareStatement(queryToRetrieveOneTimeDownloadLinkDetails);
            preparedStatement.setString(1, UUID);
            downloadLinkData = preparedStatement.executeQuery();
            while (downloadLinkData.next()){
                oneTimeDownloadLink = new OneTimeDownloadLink();
                oneTimeDownloadLink.setUUID(UUID);
                oneTimeDownloadLink.setFileName(downloadLinkData.getString("BINARY_FILE"));
                oneTimeDownloadLink.setDownloaded(downloadLinkData.getBoolean("IS_DOWNLOADED"));
                oneTimeDownloadLink.setCreatedUserName(downloadLinkData.getString("USERNAME"));
                oneTimeDownloadLink.setCreatedTenantID(downloadLinkData.getInt("TENANT_ID"));
                oneTimeDownloadLink.setCreatedTenantDomain(downloadLinkData.getString("TENANT_DOMAIN"));
//                oneTimeDownloadLink.setCreatedTime(downloadLinkData.getTimestamp("CREATED_TIME").getTime());
            }

        } catch (SQLException e) {

            handleException("Error occurred while retrieving one-time download link details for uuid " +
                    oneTimeDownloadLink.getUUID(), e);
        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, downloadLinkData);
        }
        return oneTimeDownloadLink;
    }

    @Override
    public void updateOneTimeDownloadLinkStatus(OneTimeDownloadLink oneTimeDownloadLink) throws AppManagementException{
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        String queryToUpdateOneTimeDownloadLinkStatus =
                "UPDATE APM_ONE_TIME_DOWNLOAD_LINK SET IS_DOWNLOADED=? WHERE UUID = ?";
        try {
            connection = getRDBMSConnectionWithoutAutoCommit();
            preparedStatement = connection.prepareStatement(queryToUpdateOneTimeDownloadLinkStatus);
            preparedStatement.setBoolean(1, oneTimeDownloadLink.isDownloaded());
            preparedStatement.setString(2, oneTimeDownloadLink.getUUID());
            preparedStatement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException e1) {
                handleException(
                        String.format("Couldn't rollback update operation of one-time download link reference for uuid "+
                                oneTimeDownloadLink.getUUID()), e1);
            }
            handleException("Error occurred while retrieving one-time download link details for uuid " +
                    oneTimeDownloadLink.getUUID(), e);
        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, null);
        }
    }

    @Override
    public Subscription getEnterpriseSubscription(String webAppContext, String webAppVersion) throws AppManagementException {

        Connection connection = null;

        try {
            connection = getRDBMSConnectionWithoutAutoCommit();
            return new AppMDAO().getEnterpriseSubscription(webAppContext, webAppVersion, connection);
        } catch (SQLException e) {
            handleException(String.format("Can't enterprise subscription for '%s':'%s'", webAppContext, webAppVersion), e);
        }finally {
            APIMgtDBUtil.closeAllConnections(null, connection, null);
        }

        return null;
    }
    // ------------------- END : Repository API implementation methods. ----------------------------------

    private AppFactory getAppFactory(String appType) {
        if(AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)){
            return new WebAppFactory();
        }else if(AppMConstants.MOBILE_ASSET_TYPE.equals(appType)){
            return new MobileAppFactory();
        }else{
            return null;
        }
    }

    private App getApp(String type, GenericArtifact appArtifact) throws AppManagementException {

        if (AppMConstants.WEBAPP_ASSET_TYPE.equals(type)) {
            return getWebApp(appArtifact);
        } else if (AppMConstants.MOBILE_ASSET_TYPE.equals(type)) {
            return getMobileApp(appArtifact);
        }
        return null;
    }

    private boolean isSearchHit(GenericArtifact artifact, Map<String, String> searchTerms) throws AppManagementException {

        boolean isSearchHit = true;

        for(Map.Entry<String, String> term : searchTerms.entrySet()){
            try {
                if("ID".equalsIgnoreCase(term.getKey())) {
                    if(!artifact.getId().equals(term.getValue())){
                        isSearchHit = false;
                        break;
                    }
                }else if(!term.getValue().equalsIgnoreCase(artifact.getAttribute(getRxtAttributeName(term.getKey())))){
                    isSearchHit = false;
                    break;
                }
            } catch (GovernanceException e) {
                String errorMessage = String.format("Error while determining whether artifact '%s' is a search hit.", artifact.getId());
                throw new AppManagementException(errorMessage, e);
            }
        }

        return isSearchHit;
    }

    private String getRxtAttributeName(String searchKey) {

        String rxtAttributeName = null;

        if (searchKey.equalsIgnoreCase("NAME")) {
            rxtAttributeName = AppMConstants.API_OVERVIEW_NAME;
        } else if (searchKey.equalsIgnoreCase("PROVIDER")) {
            rxtAttributeName = AppMConstants.API_OVERVIEW_PROVIDER;
        } else if (searchKey.equalsIgnoreCase("VERSION")) {
            rxtAttributeName = AppMConstants.API_OVERVIEW_VERSION;
        } else if (searchKey.equalsIgnoreCase("BUSINESS_OWNER_ID")) {
            rxtAttributeName = AppMConstants.API_OVERVIEW_BUSS_OWNER;
        } else if (searchKey.equalsIgnoreCase("TREATASASITE")) {
            rxtAttributeName = AppMConstants.APP_OVERVIEW_TREAT_AS_A_SITE;
        }

        return rxtAttributeName;
    }

    private List<GenericArtifact> getAllAppArtifacts(String appType) throws GovernanceException, AppManagementException {

        List<GenericArtifact> appArtifacts = new ArrayList<GenericArtifact>();

        GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry, appType);
        GenericArtifact[] artifacts = artifactManager.getAllGenericArtifacts();
        for (GenericArtifact artifact : artifacts) {
            appArtifacts.add(artifact);
        }

        return appArtifacts;
    }

    private WebApp getWebApp(GenericArtifact webAppArtifact) throws AppManagementException {

        Connection connection = null;
        PreparedStatement preparedStatementToGetBasicApp = null;
        ResultSet resultSetOfBasicApp = null;

        try {

            AppFactory appFactory = getAppFactory(AppMConstants.WEBAPP_ASSET_TYPE);
            WebApp webApp = (WebApp) appFactory.createApp(webAppArtifact, registry);

            // Fill fields from the database.
            connection = getRDBMSConnectionWithoutAutoCommit();
            String basicQuery = "SELECT * FROM APM_APP WHERE UUID = ?";
            preparedStatementToGetBasicApp = connection.prepareStatement(basicQuery);

            preparedStatementToGetBasicApp.setString(1, webAppArtifact.getId());

            resultSetOfBasicApp = preparedStatementToGetBasicApp.executeQuery();

            while(resultSetOfBasicApp.next()){

                webApp.setAppTenant(String.valueOf(resultSetOfBasicApp.getInt("TENANT_ID")));
                webApp.setDatabaseId(resultSetOfBasicApp.getInt("APP_ID"));

                webApp.setContext(resultSetOfBasicApp.getString("CONTEXT"));
                webApp.setTrackingCode(resultSetOfBasicApp.getString("TRACKING_CODE"));
                webApp.setSaml2SsoIssuer(resultSetOfBasicApp.getString("SAML2_SSO_ISSUER"));
                webApp.setLogoutURL(resultSetOfBasicApp.getString("LOG_OUT_URL"));
                webApp.setAllowAnonymous(resultSetOfBasicApp.getBoolean("APP_ALLOW_ANONYMOUS"));
                webApp.setUrl(resultSetOfBasicApp.getString("APP_ENDPOINT"));
                webApp.setVisibleRoles(resultSetOfBasicApp.getString("VISIBLE_ROLES"));

                // There should be only one app for the given combination
                break;
            }

            fillWebApp(webApp, connection);

            return webApp;
        } catch (SQLException e) {
            handleException(String.format("Error while building the app for the web app registry artifact '%s'", webAppArtifact.getId()), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null, connection, null);
        }

        return null;
    }


    private MobileApp getMobileApp(GenericArtifact mobileAppArtifact) throws AppManagementException {
        AppFactory appFactory = getAppFactory(AppMConstants.MOBILE_ASSET_TYPE);
        MobileApp mobileApp = (MobileApp) appFactory.createApp(mobileAppArtifact, registry);
        return mobileApp;
    }


    private Set<URITemplate> getURITemplates(int webAppDatabaseId, Connection connection) throws SQLException {

        String query = "SELECT * FROM APM_APP_URL_MAPPING WHERE APP_ID=?";

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            preparedStatement = connection.prepareStatement(query);

            preparedStatement.setInt(1, webAppDatabaseId);
            resultSet = preparedStatement.executeQuery();

            Set<URITemplate> uriTemplates = new HashSet<URITemplate>();
            while(resultSet.next()){
                URITemplate uriTemplate = new URITemplate();
                uriTemplate.setId(resultSet.getInt("URL_MAPPING_ID"));
                uriTemplate.setUriTemplate(resultSet.getString("URL_PATTERN"));
                uriTemplate.setHTTPVerb(resultSet.getString("HTTP_METHOD"));
                uriTemplate.setPolicyGroupId(resultSet.getInt("POLICY_GRP_ID"));

                uriTemplates.add(uriTemplate);
            }

            return uriTemplates;

        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, resultSet);
        }
    }

    private List<EntitlementPolicyGroup> getPolicyGroups(int webAppDatabaseId, Connection connection) throws SQLException {

        String query = "SELECT GRP.*,PARTIAL_MAPPING.POLICY_PARTIAL_ID " +
                                        "FROM " +
                                        "APM_POLICY_GROUP GRP " +
                                        "LEFT JOIN APM_POLICY_GRP_PARTIAL_MAPPING PARTIAL_MAPPING " +
                                        "ON GRP.POLICY_GRP_ID=PARTIAL_MAPPING.POLICY_GRP_ID, " +
                                        "APM_POLICY_GROUP_MAPPING MAPPING " +
                                        "WHERE " +
                                        "MAPPING.POLICY_GRP_ID=GRP.POLICY_GRP_ID " +
                                        "AND MAPPING.APP_ID=? " +
                                        "ORDER BY GRP.POLICY_GRP_ID";

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setInt(1, webAppDatabaseId);

            resultSet = preparedStatement.executeQuery();

            List<EntitlementPolicyGroup> policyGroups = new ArrayList<EntitlementPolicyGroup>();
            while(resultSet.next()){

                EntitlementPolicyGroup policyGroup = new EntitlementPolicyGroup();

                policyGroup.setPolicyGroupId(resultSet.getInt("POLICY_GRP_ID"));
                policyGroup.setPolicyGroupName(resultSet.getString("NAME"));
                policyGroup.setPolicyDescription(resultSet.getString("DESCRIPTION"));
                policyGroup.setThrottlingTier(resultSet.getString("THROTTLING_TIER"));
                policyGroup.setUserRoles(resultSet.getString("USER_ROLES"));
                policyGroup.setAllowAnonymous(resultSet.getBoolean("URL_ALLOW_ANONYMOUS"));

                Integer entitlementPolicyId = resultSet.getInt("POLICY_PARTIAL_ID");

                if(entitlementPolicyId > 0){
                    policyGroup.setEntitlementPolicyId(entitlementPolicyId);
                }

                policyGroups.add(policyGroup);

            }

            return policyGroups;
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, resultSet);
        }
    }

    private String persistWebApp(WebApp webApp) throws AppManagementException {

        Connection connection = null;

        try {
            connection = getRDBMSConnectionWithoutAutoCommit();
        } catch (SQLException e) {
            handleException("Can't get the database connection.", e);
        }

        try {

            webApp.setCreatedTime(String.valueOf(new Date().getTime()));

            // Persist master data first.
            persistPolicyGroups(webApp.getAccessPolicyGroups(), connection);

            // Add the registry artifact
            registry.beginTransaction();
            String uuid = saveRegistryArtifact(webApp);
            webApp.setUUID(uuid);
            registry.commitTransaction();

            // Persist web app data to the database (RDBMS)
            int webAppDatabaseId = persistWebAppToDatabase(webApp, connection);

            associatePolicyGroupsWithWebApp(webApp.getAccessPolicyGroups(), webAppDatabaseId, connection);

            persistURLTemplates(new ArrayList<URITemplate>(webApp.getUriTemplates()), webApp.getAccessPolicyGroups(), webAppDatabaseId, connection);

            if(!StringUtils.isEmpty(webApp.getJavaPolicies())){
                persistJavaPolicyMappings(webApp.getJavaPolicies(), webAppDatabaseId, connection);
            }

            persistLifeCycleEvent(webAppDatabaseId, null, APIStatus.CREATED, connection);

            if(webApp.isServiceProviderCreationEnabled()){
                createSSOProvider(webApp);
            }

            // Commit JDBC and Registry transactions.
            connection.commit();

            return uuid;
        } catch (SQLException e) {
            rollbackTransactions(webApp, registry, connection);
            handleException(String.format("Can't persist web app '%s'.", webApp.getDisplayName()), e);
        } catch (RegistryException e) {
            rollbackTransactions(webApp, registry, connection);
            handleException(String.format("Can't persist web app '%s'.", webApp.getDisplayName()), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null,connection,null);
        }

        // Return null to make the compiler doesn't complain.
        return null;
    }

    private String persistMobileApp(MobileApp mobileApp) throws AppManagementException {
        String artifactId = null;
        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                                       AppMConstants.MOBILE_ASSET_TYPE);

            registry.beginTransaction();
            GenericArtifact genericArtifact =
                    artifactManager.newGovernanceArtifact(new QName(mobileApp.getAppName()));
            GenericArtifact artifact = AppManagerUtil.createMobileAppArtifactContent(genericArtifact, mobileApp);
            artifactManager.addGenericArtifact(artifact);
            artifactId = artifact.getId();
            mobileApp.setUUID(artifactId);
            changeLifeCycleStatus(AppMConstants.MOBILE_ASSET_TYPE, artifactId, APPLifecycleActions.CREATE.getStatus());
            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
            Set<String> tagSet = mobileApp.getTags();
            if (tagSet != null) {
                for (String tag : tagSet) {
                    registry.applyTag(artifactPath, tag);
                }
            }

            if (mobileApp.getAppVisibility() != null) {
                AppManagerUtil.setResourcePermissions(mobileApp.getAppProvider(),
                                                      AppMConstants.API_RESTRICTED_VISIBILITY,
                                                      mobileApp.getAppVisibility(), artifactPath);
            }
            registry.commitTransaction();
        } catch (RegistryException e) {
            try {
                registry.rollbackTransaction();
            } catch (RegistryException re) {
                handleException(
                        "Error while rolling back the transaction for mobile application: "
                                + mobileApp.getAppName(), re);
            }
            handleException("Error occurred while creating the mobile application : " + mobileApp.getAppName(), e);
        }
        return artifactId;
    }


    /**
     * Change the lifecycle state of a given application
     *
     * @param appType         application type ie: webapp, mobileapp
     * @param appId           application uuid
     * @param lifecycleAction lifecycle action perform on the application
     * @throws AppManagementException
     */
    private void changeLifeCycleStatus(String appType, String appId, String lifecycleAction)
            throws AppManagementException, RegistryException {

        try {
            String username = getUsernameOfCurrentUser();
            String tenantDomain = getTenantDomainOfCurrentUser();

            String requiredPermission = null;

            if (AppMConstants.LifecycleActions.SUBMIT_FOR_REVIEW.equals(lifecycleAction)) {
                if (AppMConstants.MOBILE_ASSET_TYPE.equals(appType)) {
                    requiredPermission = AppMConstants.Permissions.MOBILE_APP_CREATE;
                } else if (AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)) {
                    requiredPermission = AppMConstants.Permissions.WEB_APP_CREATE;
                }
            } else {
                if (AppMConstants.MOBILE_ASSET_TYPE.equals(appType)) {
                    requiredPermission = AppMConstants.Permissions.MOBILE_APP_PUBLISH;
                } else if (AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)) {
                    requiredPermission = AppMConstants.Permissions.WEB_APP_PUBLISH;
                }
            }

            if (!AppManagerUtil.checkPermissionQuietly(username, requiredPermission)) {
                handleException("The user " + username +
                                        " is not authorized to perform lifecycle action " + lifecycleAction + " on " +
                                        appType + " with uuid " + appId, null);
            }
            //Check whether the user has enough permissions to change lifecycle
            PrivilegedBarleyContext.startTenantFlow();
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setUsername(username);
            PrivilegedBarleyContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);

            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().
                    getTenantId(tenantDomain);

            AuthorizationManager authManager = ServiceReferenceHolder.getInstance().getRealmService().
                    getTenantUserRealm(tenantId).getAuthorizationManager();

            //Get system registry for logged in tenant domain
            Registry systemRegistry = ServiceReferenceHolder.getInstance().
                    getRegistryService().getGovernanceSystemRegistry(tenantId);
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(systemRegistry, appType);
            GenericArtifact appArtifact = artifactManager.getGenericArtifact(appId);
            String resourcePath = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                                                                RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH +
                                                                        appArtifact.getPath());

            if (appArtifact != null) {
                if (!authManager.isUserAuthorized(username, resourcePath, "authorize")) {
                    //Throws resource authorization exception
                    handleException("The user " + username +
                                            " is not authorized to" + appType + " with uuid " + appId, null);
                }
                //Change lifecycle status
                if (AppMConstants.MOBILE_ASSET_TYPE.equals(appType)) {
                    appArtifact.invokeAction(lifecycleAction, AppMConstants.MOBILE_LIFE_CYCLE);
                } else if (AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)) {
                    appArtifact.invokeAction(lifecycleAction, AppMConstants.WEBAPP_LIFE_CYCLE);
                }

                //If application is role restricted, deny read rights for Internal/everyone and system/wso2.anonymous
                // .role roles
                if ((AppMConstants.LifecycleActions.PUBLISH.equals(lifecycleAction) ||
                        AppMConstants.LifecycleActions.RE_PUBLISH.equals(lifecycleAction)) &&
                        !StringUtils.isBlank(appArtifact.getAttribute("overview_visibleRoles"))) {

                    authManager.denyRole(AppMConstants.EVERYONE_ROLE, resourcePath, ActionConstants.GET);
                    authManager.denyRole(AppMConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
                }

                if (log.isDebugEnabled()) {
                    String logMessage =
                            "Lifecycle action " + lifecycleAction + " has been successfully performed on " + appType
                                    + " with id" + appId;
                    log.debug(logMessage);
                }
            } else {
                handleException("Failed to get " + appType + " artifact corresponding to artifactId " +
                                        appId + ". Artifact does not exist", null);
            }
        } catch (UserStoreException e) {
            handleException("Error occurred while performing lifecycle action : " + lifecycleAction + " on " + appType +
                                    " with id : " + appId + ". Failed to retrieve tenant id for user : ", e);
        } finally {
            PrivilegedBarleyContext.endTenantFlow();
        }
    }

    private WebApp createNewWebAppVersion(WebApp targetApp) throws AppManagementException {

        // Get the attributes of the source.
        WebApp sourceApp = (WebApp) getApp(targetApp.getType(), targetApp.getUUID());

        //check if the new app identity already exists
        final String appName = sourceApp.getApiName().toString();
        final String appVersion = targetApp.getId().getVersion();
        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                                       AppMConstants.WEBAPP_ASSET_TYPE);
            Map<String, List<String>> attributeListMap = new HashMap<String, List<String>>();
            attributeListMap.put(AppMConstants.API_OVERVIEW_NAME, new ArrayList<String>() {{
                add(appName);
            }});
            attributeListMap.put(AppMConstants.API_OVERVIEW_VERSION, new ArrayList<String>() {{
                add(appVersion);
            }});

            GenericArtifact[] existingArtifacts = artifactManager.findGenericArtifacts(attributeListMap);

            if (existingArtifacts != null && existingArtifacts.length > 0) {
                handleException("A duplicate webapp already exists with name '" +
                                        appName + "' and version '" + appVersion + "'", null);
            }
        } catch (GovernanceException e) {
            handleException("Error occurred while checking existence for webapp with name '" + appName +
                                    "' and version '" + appVersion + "'", null);
        }


        // Clear the ID.
        sourceApp.setUUID(null);

        // Set New Version.
        sourceApp.setOriginVersion(sourceApp.getId().getVersion());
        sourceApp.setVersion(targetApp.getId().getVersion());
        sourceApp.setDefaultVersion(targetApp.isDefaultVersion());

        // Clear URL Template database IDs.
        for(URITemplate template : sourceApp.getUriTemplates()){
            template.setId(-1);

            String policyGroupName = getPolicyGroupName(sourceApp.getAccessPolicyGroups(), template.getPolicyGroupId());
            template.setPolicyGroupName(policyGroupName);

            template.setPolicyGroupId(-1);
        }

        // Clear Policy Group database IDs.
        for(EntitlementPolicyGroup policyGroup : sourceApp.getAccessPolicyGroups()){
            policyGroup.setPolicyGroupId(-1);
        }

        // Set the other properties accordingly.
        sourceApp.setDisplayName(targetApp.getDisplayName());
        sourceApp.setCreatedTime(String.valueOf(new Date().getTime()));

        //Set the new Saml2SsoIssuer
        String issuerName = buildIssuerName(new APIIdentifier(sourceApp.getId().getProviderName(),
                sourceApp.getId().getApiName(), targetApp.getId().getVersion()));
        sourceApp.setSaml2SsoIssuer(issuerName);

        saveApp(sourceApp);

        return sourceApp;
    }

    private MobileApp createNewMobileAppVersion(MobileApp targetApp) throws AppManagementException {

        // Get the attributes of the source.
        MobileApp sourceApp = (MobileApp) getApp(targetApp.getType(), targetApp.getUUID());

        //check if the new app identity already exists
        final String appName = sourceApp.getAppName().toString();
        final String appVersion = targetApp.getVersion();
        try {
            GenericArtifactManager artifactManager = AppManagerUtil.getArtifactManager(registry,
                                                                                       AppMConstants.MOBILE_ASSET_TYPE);
            Map<String, List<String>> attributeListMap = new HashMap<String, List<String>>();
            attributeListMap.put(AppMConstants.API_OVERVIEW_NAME, new ArrayList<String>() {{
                add(appName);
            }});
            attributeListMap.put(AppMConstants.API_OVERVIEW_VERSION, new ArrayList<String>() {{
                add(appVersion);
            }});

            GenericArtifact[] existingArtifacts = artifactManager.findGenericArtifacts(attributeListMap);

            if (existingArtifacts != null && existingArtifacts.length > 0) {
                handleException("A duplicate webapp already exists with name '" +
                                        appName + "' and version '" + appVersion + "'", null);
            }
        } catch (GovernanceException e) {
            handleException("Error occurred while checking existence for webapp with name '" + appName +
                                    "' and version '" + appVersion + "'", null);
        }


        // Clear the ID.
        sourceApp.setUUID(null);

        // Set New Version.
        sourceApp.setOriginVersion(sourceApp.getVersion());
        sourceApp.setVersion(targetApp.getVersion());

        // Set the other properties accordingly.
        sourceApp.setDisplayName(targetApp.getDisplayName());
        sourceApp.setCreatedTime(String.valueOf(new Date().getTime()));
        saveApp(sourceApp);
        return sourceApp;
    }

    private void updateWebApp(WebApp webApp) throws AppManagementException {

        Connection connection = null;

        try {
            connection = getRDBMSConnectionWithoutAutoCommit();
        } catch (SQLException e) {
            handleException("Can't get the database connection.", e);
        }

        try {
            int webAppDatabaseId = getDatabaseId(webApp, connection);

            // Set the Status from the existing app in the repository.
            // TODO : Only a thin version should be fetched from the database.
            WebApp existingApp = (WebApp) getApp(AppMConstants.WEBAPP_ASSET_TYPE, webApp.getUUID());
            webApp.setStatus(existingApp.getStatus());

            webApp.setCreatedTime(String.valueOf(new Date().getTime()));

            // Add and/or update policy groups.
            addAndUpdatePolicyGroups(webApp, webAppDatabaseId, connection);

            // Add / Update / Delete URL templates.
            addUpdateDeleteURLTemplates(webApp, webAppDatabaseId, connection);

            //Update app master metadata
            updateWebAppToDatabase(webApp, connection);

            // Delete the existing policy groups in the repository which are not in the updating web app.
            // URI templates should be passed too, since the association between templates and policy groups should be checked.
            deletePolicyGroupsNotIn(webApp.getAccessPolicyGroups(), webApp.getUriTemplates(),webAppDatabaseId, connection);

            updateRegistryArtifact(webApp);

            //Set default versioning details
            persistDefaultVersionDetails(webApp, connection);

            connection.commit();
        } catch (SQLException e) {
            rollbackTransactions(webApp, registry, connection);
            handleException(String.format("Error while updating web app '%s'", webApp.getUUID()), e);
        } catch (RegistryException e) {
            rollbackTransactions(webApp, registry, connection);
            handleException(String.format("Error while updating web app '%s'", webApp.getUUID()), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null, connection, null);
        }
    }

    private void updateRegistryArtifact(App app) throws RegistryException, AppManagementException {

        if(AppMConstants.WEBAPP_ASSET_TYPE.equalsIgnoreCase(app.getType())){
            updateWebAppRegistryArtifact((WebApp) app);
        }
    }

    private void updateWebAppRegistryArtifact(WebApp webApp) throws RegistryException, AppManagementException {

        GenericArtifactManager artifactManager = getArtifactManager(registry, AppMConstants.WEBAPP_ASSET_TYPE);

        GenericArtifact updatedWebAppArtifact = buildRegistryArtifact(artifactManager, AppMConstants.WEBAPP_ASSET_TYPE, webApp);
        updatedWebAppArtifact.setId(webApp.getUUID());
        artifactManager.updateGenericArtifact(updatedWebAppArtifact);

        // Apply tags
        String artifactPath = GovernanceUtils.getArtifactPath(registry, webApp.getUUID());
        if (webApp.getTags() != null) {
            for (String tag : webApp.getTags()) {
                registry.applyTag(artifactPath, tag);
            }
        }

        // Set resources permissions based on app visibility.
        if (webApp.getAppVisibility() == null) {
            AppManagerUtil.setResourcePermissions(webApp.getId().getProviderName(),
                                                  AppMConstants.API_GLOBAL_VISIBILITY, webApp.getAppVisibility(),
                                                  artifactPath);
        } else {
            AppManagerUtil.setResourcePermissions(webApp.getId().getProviderName(),
                                                  AppMConstants.API_RESTRICTED_VISIBILITY, webApp.getAppVisibility(),
                                                  artifactPath);
        }

    }

    private void addUpdateDeleteURLTemplates(WebApp webApp, int webAppDatabaseId, Connection connection) throws SQLException {

        List<URITemplate> urlTemplatesToBeUpdated = new ArrayList<URITemplate>();
        List<URITemplate> urlTemplatesToBeAdded = new ArrayList<URITemplate>();

        for(URITemplate template : webApp.getUriTemplates()){
            if(template.getId() > 0){
                urlTemplatesToBeUpdated.add(template);
            }else{
                urlTemplatesToBeAdded.add(template);
            }
        }

        persistURLTemplates(urlTemplatesToBeAdded, webApp.getAccessPolicyGroups(), webAppDatabaseId, connection);
        updateURLTemplates(urlTemplatesToBeUpdated, webApp.getAccessPolicyGroups(), connection);
        deleteURLTemplatesNotIn(webApp.getUriTemplates(), webAppDatabaseId, connection);
    }

    private void deleteURLTemplatesNotIn(Set<URITemplate> uriTemplates, int webAppDatabaseId, Connection connection) throws SQLException {

        String queryTemplate = "DELETE FROM APM_APP_URL_MAPPING WHERE APP_ID=%d AND URL_MAPPING_ID NOT IN (%s)";
        PreparedStatement preparedStatement = null;

        try{

            StringBuilder templateIdsBuilder = new StringBuilder();
            for(URITemplate uriTemplate : uriTemplates){
                templateIdsBuilder.append(uriTemplate.getId()).append(",");
            }
            String templateIds = templateIdsBuilder.toString();

            if(templateIds.endsWith(",")){
                templateIds = templateIds.substring(0, templateIds.length() - 1);
            }

            String query = String.format(queryTemplate, webAppDatabaseId, templateIds);

            preparedStatement = connection.prepareStatement(query);

            preparedStatement.executeUpdate();

        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private void updateURLTemplates(List<URITemplate> urlTemplatesToBeUpdated, List<EntitlementPolicyGroup> accessPolicyGroups, Connection connection) throws SQLException {

        String query = "UPDATE APM_APP_URL_MAPPING SET URL_PATTERN=?, HTTP_METHOD=?, POLICY_GRP_ID=? WHERE URL_MAPPING_ID=?";
        PreparedStatement preparedStatement = null;

        try{
            preparedStatement = connection.prepareStatement(query);

            for(URITemplate urlTemplate : urlTemplatesToBeUpdated){
                preparedStatement.setString(1, urlTemplate.getUriTemplate());
                preparedStatement.setString(2, urlTemplate.getHTTPVerb());

                int policyGroupId = urlTemplate.getPolicyGroupId();
                if(urlTemplate.getPolicyGroupId() <= 0){
                    policyGroupId = getPolicyGroupId(accessPolicyGroups, urlTemplate.getPolicyGroupName());
                    urlTemplate.setPolicyGroupId(policyGroupId);
                }

                preparedStatement.setInt(3, policyGroupId);
                preparedStatement.setInt(4, urlTemplate.getId());

                preparedStatement.addBatch();
            }

            preparedStatement.executeBatch();

        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private int getDatabaseId(WebApp webApp, Connection connection) throws SQLException {

        String query = "SELECT APP_ID FROM APM_APP WHERE UUID=? AND TENANT_ID=?";
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        preparedStatement = connection.prepareStatement(query);

        preparedStatement.setString(1, webApp.getUUID());
        preparedStatement.setInt(2, getTenantIdOfCurrentUser());

        resultSet = preparedStatement.executeQuery();

        while(resultSet.next()){
            return resultSet.getInt("APP_ID");
        }

        return -1;
    }

    private void addAndUpdatePolicyGroups(WebApp webApp, int webAppDatabaseId, Connection connection) throws SQLException {

        List<EntitlementPolicyGroup> groupsToBeAdded = new ArrayList<>();
        List<EntitlementPolicyGroup> groupsToBeUpdated = new ArrayList<>();

        for(EntitlementPolicyGroup policyGroup : webApp.getAccessPolicyGroups()){
            if(policyGroup.getPolicyGroupId() > 0){
                groupsToBeUpdated.add(policyGroup);
            }else{
                groupsToBeAdded.add(policyGroup);
            }
        }

        // Update existing policy groups
        String queryToUpdateGroups = String.format("UPDATE %s SET DESCRIPTION=?,THROTTLING_TIER=?,USER_ROLES=?,URL_ALLOW_ANONYMOUS=? WHERE POLICY_GRP_ID=?", POLICY_GROUP_TABLE_NAME);
        PreparedStatement preparedStatementToUpdateGroups = connection.prepareStatement(queryToUpdateGroups);

        for(EntitlementPolicyGroup policyGroup : groupsToBeUpdated){
            preparedStatementToUpdateGroups.setString(1,policyGroup.getPolicyDescription());
            preparedStatementToUpdateGroups.setString(2,policyGroup.getThrottlingTier());
            preparedStatementToUpdateGroups.setString(3,policyGroup.getUserRoles());
            preparedStatementToUpdateGroups.setBoolean(4, policyGroup.isAllowAnonymous());
            preparedStatementToUpdateGroups.setInt(5, policyGroup.getPolicyGroupId());

            preparedStatementToUpdateGroups.addBatch();

        }
        preparedStatementToUpdateGroups.executeBatch();
        updateEntitlementPolicyMappings(groupsToBeUpdated, connection);
        deleteUnlinkedEntitlementPolicyMappings(groupsToBeUpdated, connection);

        // Add new policy groups
        persistPolicyGroups(groupsToBeAdded, connection);
        associatePolicyGroupsWithWebApp(groupsToBeAdded, webAppDatabaseId, connection);
	}

    private void deletePolicyGroupsNotIn(List<EntitlementPolicyGroup> groupsToBeRetained, Set<URITemplate> uriTemplates, int webAppDatabaseId, Connection connection) throws SQLException {

        // Get all the policy groups for the web app.

        String queryToGetPolicyGroupsForApp = "SELECT POLICY_GRP_ID FROM APM_POLICY_GROUP_MAPPING WHERE APP_ID=?";
        PreparedStatement preparedStatementToGetPolicyGroupsForApp = null;
        ResultSet policyGroupsResultSet = null;
        PreparedStatement preparedStatementToDeletePolicyGroups = null;

        try{
            preparedStatementToGetPolicyGroupsForApp = connection.prepareStatement(queryToGetPolicyGroupsForApp);
            preparedStatementToGetPolicyGroupsForApp.setInt(1, webAppDatabaseId);

            policyGroupsResultSet = preparedStatementToGetPolicyGroupsForApp.executeQuery();

            List<Integer> policyGroupIdsForApp = new ArrayList<Integer>();

            while (policyGroupsResultSet.next()){
                policyGroupIdsForApp.add(policyGroupsResultSet.getInt("POLICY_GRP_ID"));
            }

            List<Integer> retainedPolicyGroupIds = new ArrayList<Integer>();
            if(groupsToBeRetained != null){
                for(EntitlementPolicyGroup policyGroup : groupsToBeRetained){
                    retainedPolicyGroupIds.add(policyGroup.getPolicyGroupId());
                }
            }

            List<Integer> policyGroupIdsToBeDeleted = new ArrayList<Integer>();

            // Omit the policy groups which has associations with URI templates.
            List<Integer> candidatePolicyGroupIdsToBeDeleted = ListUtils.subtract(policyGroupIdsForApp, retainedPolicyGroupIds);

            for(final Integer id : candidatePolicyGroupIdsToBeDeleted){

                if(!CollectionUtils.exists(uriTemplates, new Predicate() {
                    @Override
                    public boolean evaluate(Object o) {
                        URITemplate template = (URITemplate) o;
                        return template.getPolicyGroupId() == id;
                    }
                })){
                    policyGroupIdsToBeDeleted.add(id);
                }

            }

            disassociatePolicyGroupsFromWebApp(policyGroupIdsToBeDeleted, webAppDatabaseId, connection);

            String queryToDeletePolicyMappings = String.format("DELETE FROM %s WHERE POLICY_GRP_ID=?", POLICY_GROUP_TABLE_NAME);
            preparedStatementToDeletePolicyGroups = connection.prepareStatement(queryToDeletePolicyMappings);

            for (Integer id : policyGroupIdsToBeDeleted) {
                preparedStatementToDeletePolicyGroups.setInt(1, id);
                preparedStatementToDeletePolicyGroups.addBatch();
            }

            preparedStatementToDeletePolicyGroups.executeBatch();

        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatementToGetPolicyGroupsForApp, null, policyGroupsResultSet);
            APIMgtDBUtil.closeAllConnections(preparedStatementToDeletePolicyGroups, null, null);
        }
    }

    private String saveRegistryArtifact(App app) throws AppManagementException, RegistryException {
        String appId = null;
        if (AppMConstants.WEBAPP_ASSET_TYPE.equals(app.getType())) {
            appId = saveWebAppRegistryArtifact((WebApp) app);
        }
        return appId;
    }

    private String saveWebAppRegistryArtifact(WebApp webApp) throws RegistryException, AppManagementException {

        String artifactId = null;

        GenericArtifactManager artifactManager = getArtifactManager(registry, AppMConstants.WEBAPP_ASSET_TYPE);

        GenericArtifact appArtifact = buildRegistryArtifact(artifactManager, AppMConstants.WEBAPP_ASSET_TYPE, webApp);
        artifactManager.addGenericArtifact(appArtifact);

        artifactId = appArtifact.getId();

        // Set the life cycle for the persisted artifact
        GenericArtifact persistedArtifact = artifactManager.getGenericArtifact(artifactId);
        persistedArtifact.invokeAction(AppMConstants.LifecycleActions.CREATE, AppMConstants.WEBAPP_LIFE_CYCLE);

        // Apply tags
        String artifactPath = GovernanceUtils.getArtifactPath(registry, artifactId);
        if (webApp.getTags() != null) {
            for (String tag : webApp.getTags()) {
                registry.applyTag(artifactPath, tag);
            }
        }

        // Set resources permissions based on app visibility.
        if (webApp.getAppVisibility() != null) {
            AppManagerUtil.setResourcePermissions(webApp.getId().getProviderName(), AppMConstants.API_RESTRICTED_VISIBILITY, webApp.getAppVisibility(), artifactPath);
        }

        // Add registry associations.
        String providerPath = AppManagerUtil.getAPIProviderPath(webApp.getId());
        registry.addAssociation(providerPath, artifactPath, AppMConstants.PROVIDER_ASSOCIATION);

        return artifactId;
    }

    public static GenericArtifactManager getArtifactManager(Registry registry, String key) throws RegistryException {

        GenericArtifactManager artifactManager = null;

        GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);
        if (GovernanceUtils.findGovernanceArtifactConfiguration(key, registry) != null) {
            artifactManager = new GenericArtifactManager(registry, key);
        }

        return artifactManager;
    }

    private GenericArtifact buildRegistryArtifact(GenericArtifactManager artifactManager, String type, App app) throws GovernanceException {

        GenericArtifact artifact = null;

        if(AppMConstants.WEBAPP_ASSET_TYPE.equals(type)){
            artifact = buildWebAppRegistryArtifact(artifactManager, (WebApp) app);
        }

        // Add custom properties.
        if(app.getCustomProperties() != null &&  !app.getCustomProperties().isEmpty()){
            for(CustomProperty customProperty : app.getCustomProperties()){
                artifact.setAttribute(customProperty.getName(), customProperty.getValue());
            }
        }

        return artifact;
    }

    private GenericArtifact buildWebAppRegistryArtifact(GenericArtifactManager artifactManager, WebApp webApp) throws GovernanceException {

        GenericArtifact artifact = artifactManager.newGovernanceArtifact(new QName(webApp.getId().getApiName()));

        artifact.setAttribute(AppMConstants.API_OVERVIEW_NAME, webApp.getId().getApiName());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_VERSION, webApp.getId().getVersion());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_CONTEXT, webApp.getContext());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_DISPLAY_NAME, webApp.getDisplayName());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_PROVIDER, AppManagerUtil.replaceEmailDomainBack(
                webApp.getId().getProviderName()));
        artifact.setAttribute(AppMConstants.API_OVERVIEW_DESCRIPTION, webApp.getDescription());
        artifact.setAttribute(AppMConstants.APP_OVERVIEW_TREAT_AS_A_SITE, webApp.getTreatAsASite());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_ENDPOINT_URL, webApp.getUrl());
        artifact.setAttribute(AppMConstants.APP_IMAGES_THUMBNAIL,
                              (webApp.getThumbnailUrl() == null ? " " : webApp.getThumbnailUrl()));
        artifact.setAttribute(AppMConstants.APP_IMAGES_BANNER, (webApp.getBanner() == null ? " " : webApp.getBanner()));
        artifact.setAttribute(AppMConstants.API_OVERVIEW_LOGOUT_URL, webApp.getLogoutURL());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_BUSS_OWNER, webApp.getBusinessOwner());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_BUSS_OWNER_EMAIL, webApp.getBusinessOwnerEmail());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_VISIBILITY, webApp.getVisibility());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_VISIBLE_ROLES, webApp.getVisibleRoles());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_VISIBLE_TENANTS, webApp.getVisibleTenants());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_TRANSPORTS, webApp.getTransports());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_TIER, "Unlimited");
        artifact.setAttribute(AppMConstants.APP_TRACKING_CODE, webApp.getTrackingCode());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_CREATED_TIME, webApp.getCreatedTime());
        artifact.setAttribute(AppMConstants.API_OVERVIEW_ALLOW_ANONYMOUS, Boolean.toString(webApp.getAllowAnonymous()));
        artifact.setAttribute(AppMConstants.API_OVERVIEW_SKIP_GATEWAY, Boolean.toString(webApp.getSkipGateway()));
        artifact.setAttribute(AppMConstants.APP_OVERVIEW_ACS_URL, webApp.getAcsURL());
        artifact.setAttribute(AppMConstants.APP_OVERVIEW_MAKE_AS_DEFAULT_VERSION, String.valueOf(
                webApp.isDefaultVersion()));
        if (webApp.getSsoProviderDetails() != null) {
            artifact.setAttribute(AppMConstants.APP_SSO_SSO_PROVIDER, String.valueOf(
                    webApp.getSsoProviderDetails().getProviderName() + "-" +
                            webApp.getSsoProviderDetails().getProviderVersion()));
        }
        artifact.setAttribute(AppMConstants.APP_SSO_SAML2_SSO_ISSUER, webApp.getSaml2SsoIssuer());

        if(webApp.getOriginVersion() != null){
            artifact.setAttribute(AppMConstants.APP_OVERVIEW_OLD_VERSION, webApp.getOriginVersion());
        }

        // Add policy groups
        if(webApp.getAccessPolicyGroups() != null){
            int[] policyGroupIds = new int[webApp.getAccessPolicyGroups().size()];

            for(int i = 0; i < webApp.getAccessPolicyGroups().size(); i++){
                policyGroupIds[i] = webApp.getAccessPolicyGroups().get(i).getPolicyGroupId();
            }

            artifact.setAttribute(AppMConstants.APP_URITEMPLATE_POLICYGROUP_IDS, policyGroupIds.toString());
        }

        // Add URI Template attributes
        int counter = 0;
        for(URITemplate uriTemplate : webApp.getUriTemplates()){
            artifact.setAttribute(AppMConstants.APP_URITEMPLATE_URLPATTERN + counter, uriTemplate.getUriTemplate());
            artifact.setAttribute(AppMConstants.APP_URITEMPLATE_HTTPVERB + counter, uriTemplate.getHTTPVerb());

            int policyGroupId = uriTemplate.getPolicyGroupId();
            if(policyGroupId <= 0){
                policyGroupId = getPolicyGroupId(webApp.getAccessPolicyGroups(), uriTemplate.getPolicyGroupName());
            }

            artifact.setAttribute(AppMConstants.APP_URITEMPLATE_POLICYGROUP_IDS + counter, String.valueOf(policyGroupId));

            counter++;
        }

        return artifact;
    }

    private int getPolicyGroupId(List<EntitlementPolicyGroup> accessPolicyGroups, String policyGroupName) {

        for(EntitlementPolicyGroup policyGroup : accessPolicyGroups){
            if(policyGroupName.equals(policyGroup.getPolicyGroupName())){
                return policyGroup.getPolicyGroupId();
            }
        }

        return -1;
    }

    private String getPolicyGroupName(List<EntitlementPolicyGroup> accessPolicyGroups, int policyGroupId) {

        for(EntitlementPolicyGroup policyGroup : accessPolicyGroups){
            if(policyGroupId == policyGroup.getPolicyGroupId()){
                return policyGroup.getPolicyGroupName();
            }
        }

        return null;
    }

    private int persistWebAppToDatabase(WebApp webApp, Connection connection) throws SQLException, AppManagementException {

        String query = "INSERT INTO APM_APP(APP_PROVIDER, TENANT_ID, APP_NAME, APP_VERSION, CONTEXT, TRACKING_CODE, " +
                            "UUID, SAML2_SSO_ISSUER, LOG_OUT_URL, APP_ALLOW_ANONYMOUS, APP_ENDPOINT, TREAT_AS_SITE, VISIBLE_ROLES) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

        PreparedStatement preparedStatement = null;
        ResultSet generatedKeys = null;

        try {
            Environment gatewayEnvironment = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().
                                                getAPIManagerConfiguration().getApiGatewayEnvironments().get(0);

            String gatewayUrl = gatewayEnvironment.getApiGatewayEndpoint().split(",")[0];

            String logoutURL = webApp.getLogoutURL();
            if (logoutURL != null && !"".equals(logoutURL.trim())) {
                logoutURL = gatewayUrl.concat(webApp.getContext()).concat("/" + webApp.getId().getVersion()).concat(logoutURL);
            }

            preparedStatement = connection.prepareStatement(query, new String[]{"APP_ID"});
            preparedStatement.setString(1, AppManagerUtil.replaceEmailDomainBack(webApp.getId().getProviderName()));
            preparedStatement.setInt(2, getTenantIdOfCurrentUser());
            preparedStatement.setString(3, webApp.getId().getApiName());
            preparedStatement.setString(4, webApp.getId().getVersion());
            preparedStatement.setString(5, webApp.getContext());
            preparedStatement.setString(6, webApp.getTrackingCode());
            preparedStatement.setString(7, webApp.getUUID());
            preparedStatement.setString(8, webApp.getSaml2SsoIssuer());
            preparedStatement.setString(9, logoutURL);
            preparedStatement.setBoolean(10, webApp.getAllowAnonymous());
            preparedStatement.setString(11, webApp.getUrl());
            preparedStatement.setBoolean(12, Boolean.parseBoolean(webApp.getTreatAsASite()));
            preparedStatement.setString(13, webApp.getVisibleRoles());

            preparedStatement.execute();

            generatedKeys = preparedStatement.getGeneratedKeys();
            int webAppId = -1;
            if (generatedKeys.next()) {
                webAppId = generatedKeys.getInt(1);
            }

            //Set default versioning details
            persistDefaultVersionDetails(webApp, connection);

            return webAppId;
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, generatedKeys);
        }

    }


    private void updateWebAppToDatabase(WebApp webApp, Connection connection)
            throws SQLException, AppManagementException {
        String query = "UPDATE APM_APP SET TRACKING_CODE=?, " +
                "APP_ALLOW_ANONYMOUS=?, APP_ENDPOINT=?, TREAT_AS_SITE=?, VISIBLE_ROLES=? " +
                "WHERE UUID=?";
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, webApp.getTrackingCode());
            preparedStatement.setBoolean(2, webApp.getAllowAnonymous());
            preparedStatement.setString(3, webApp.getUrl());
            preparedStatement.setBoolean(4, Boolean.parseBoolean(webApp.getTreatAsASite()));
            preparedStatement.setString(5, webApp.getVisibleRoles());
            preparedStatement.setString(6, webApp.getUUID());
            preparedStatement.execute();
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private void persistJavaPolicyMappings(String javaPolicies, int webAppDatabaseId, Connection connection) throws SQLException {

        JSONArray javaPolicyIds = (JSONArray) JSONValue.parse(javaPolicies);

        PreparedStatement preparedStatement = null;
        String query = " INSERT INTO APM_APP_JAVA_POLICY_MAPPING(APP_ID, JAVA_POLICY_ID) VALUES(?,?) ";

        try {
            preparedStatement = connection.prepareStatement(query);

            for (Object policyId : javaPolicyIds) {
                preparedStatement.setInt(1, webAppDatabaseId);
                preparedStatement.setInt(2, Integer.parseInt(policyId.toString()));
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();

        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private void persistPolicyGroups(List<EntitlementPolicyGroup> policyGroups, Connection connection) throws SQLException {

        for (EntitlementPolicyGroup policyGroup : policyGroups) {

            // Don't try to use batch insert for the policy groups since we need the auto-generated IDs.
            persistPolicyGroup(policyGroup, connection);
        }

        persistEntitlementPolicyMappings(policyGroups, connection);
    }

    private void persistPolicyGroup(EntitlementPolicyGroup policyGroup, Connection connection) throws SQLException {

        String query = String.format("INSERT INTO %s(NAME,THROTTLING_TIER,USER_ROLES,URL_ALLOW_ANONYMOUS,DESCRIPTION) VALUES(?,?,?,?,?) ", POLICY_GROUP_TABLE_NAME);

        PreparedStatement preparedStatement = null;

        ResultSet resultSet = null;

        try {

            preparedStatement = connection.prepareStatement(query, new String[]{"POLICY_GRP_ID"});
            preparedStatement.setString(1, policyGroup.getPolicyGroupName());
            preparedStatement.setString(2, policyGroup.getThrottlingTier());
            preparedStatement.setString(3, policyGroup.getUserRoles());
            preparedStatement.setBoolean(4, policyGroup.isAllowAnonymous());
            preparedStatement.setString(5, policyGroup.getPolicyDescription());
            preparedStatement.executeUpdate();

            resultSet = preparedStatement.getGeneratedKeys();

            int generatedPolicyGroupId = 0;
            if (resultSet.next()) {
                generatedPolicyGroupId = Integer.parseInt(resultSet.getString(1));
                policyGroup.setPolicyGroupId(generatedPolicyGroupId);
            }

        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, resultSet);
        }
    }


    private void associatePolicyGroupsWithWebApp(List<EntitlementPolicyGroup> policyGroups, int appDatabaseId, Connection connection) throws SQLException {

        PreparedStatement preparedStatementToPersistPolicyMappings = null;
        String queryToPersistPolicyMappings = "INSERT INTO APM_POLICY_GROUP_MAPPING(APP_ID, POLICY_GRP_ID) VALUES(?,?)";

        try{
            preparedStatementToPersistPolicyMappings = connection.prepareStatement(queryToPersistPolicyMappings);

            for (EntitlementPolicyGroup policyGroup : policyGroups) {

                // Add mapping query to the batch
                preparedStatementToPersistPolicyMappings.setInt(1, appDatabaseId);
                preparedStatementToPersistPolicyMappings.setInt(2, policyGroup.getPolicyGroupId());
                preparedStatementToPersistPolicyMappings.addBatch();
            }

            preparedStatementToPersistPolicyMappings.executeBatch();
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatementToPersistPolicyMappings, null, null);
        }
    }

    private void disassociatePolicyGroupsFromWebApp(List<Integer> policyGroupIds, int appDatabaseId, Connection connection) throws SQLException {

        PreparedStatement preparedStatementToDeletePolicyMappings = null;
        String queryToDeletePolicyMappings = "DELETE FROM APM_POLICY_GROUP_MAPPING WHERE APP_ID=? AND POLICY_GRP_ID=?";

        try{
            preparedStatementToDeletePolicyMappings = connection.prepareStatement(queryToDeletePolicyMappings);

            for (Integer policyGroupId : policyGroupIds) {

                // Add mapping query to the batch
                preparedStatementToDeletePolicyMappings.setInt(1, appDatabaseId);
                preparedStatementToDeletePolicyMappings.setInt(2, policyGroupId);
                preparedStatementToDeletePolicyMappings.addBatch();
            }

            preparedStatementToDeletePolicyMappings.executeBatch();
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatementToDeletePolicyMappings, null, null);
        }
    }

    private void persistEntitlementPolicyMappings(List<EntitlementPolicyGroup> policyGroups, Connection connection) throws SQLException {

		String query = String.format("INSERT INTO %s(POLICY_GRP_ID, POLICY_PARTIAL_ID) VALUES(?,?) ", POLICY_GROUP_PARTIAL_MAPPING_TABLE_NAME);
        PreparedStatement preparedStatement = null;

        try {
            preparedStatement = connection.prepareStatement(query);

            for(EntitlementPolicyGroup policyGroup : policyGroups){

                if(policyGroup.getPolicyPartials() != null){
                    preparedStatement.setInt(1, policyGroup.getPolicyGroupId());
                    preparedStatement.setInt(2, policyGroup.getFirstEntitlementPolicyId());
                    preparedStatement.addBatch();
                }
            }

            preparedStatement.executeBatch();

        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private void updateEntitlementPolicyMappings(List<EntitlementPolicyGroup> policyGroups, Connection connection) throws SQLException {

        String query = String.format("UPDATE %s SET POLICY_PARTIAL_ID=? WHERE POLICY_GRP_ID=? ", POLICY_GROUP_PARTIAL_MAPPING_TABLE_NAME);
        PreparedStatement preparedStatement = null;

        try {
            preparedStatement = connection.prepareStatement(query);

            for(EntitlementPolicyGroup policyGroup : policyGroups){

                if(policyGroup.getPolicyPartials() != null){
                    preparedStatement.setInt(1, policyGroup.getFirstEntitlementPolicyId());
                    preparedStatement.setInt(2, policyGroup.getPolicyGroupId());
                    preparedStatement.addBatch();
                }
            }

            preparedStatement.executeBatch();
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private void deleteUnlinkedEntitlementPolicyMappings(List<EntitlementPolicyGroup> policyGroups, Connection connection) throws SQLException {

        String query = String.format("DELETE FROM %s WHERE POLICY_GRP_ID=? ", POLICY_GROUP_PARTIAL_MAPPING_TABLE_NAME);
        PreparedStatement preparedStatement = null;

        try {
            preparedStatement = connection.prepareStatement(query);

            for(EntitlementPolicyGroup policyGroup : policyGroups){

                // If the policy group doesn't have entitlement policy, then delete the possible existing entitlement policy mappings for those policy groups.
                if(policyGroup.getPolicyPartials() == null){
                    preparedStatement.setInt(1, policyGroup.getPolicyGroupId());
                    preparedStatement.addBatch();
                }
            }

            preparedStatement.executeBatch();
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private void persistLifeCycleEvent(int webAppDatabaseId, APIStatus oldStatus, APIStatus newStatus, Connection conn)
            throws SQLException {

        PreparedStatement preparedStatement = null;

        String query = "INSERT INTO APM_APP_LC_EVENT (APP_ID, PREVIOUS_STATE, NEW_STATE, USER_ID, TENANT_ID, EVENT_DATE)"
                            + " VALUES (?,?,?,?,?,?)";

        try {

            preparedStatement = conn.prepareStatement(query);
            preparedStatement.setInt(1, webAppDatabaseId);

            if (oldStatus != null) {
                preparedStatement.setString(2, oldStatus.getStatus());
            } else {
                preparedStatement.setNull(2, Types.VARCHAR);
            }

            preparedStatement.setString(3, newStatus.getStatus());
            preparedStatement.setString(4, getUsernameOfCurrentUser());
            preparedStatement.setInt(5, getTenantIdOfCurrentUser());
            preparedStatement.setTimestamp(6, new Timestamp(System.currentTimeMillis()));

            preparedStatement.executeUpdate();

        } finally {
             APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private void persistDefaultVersionDetails(WebApp webApp, Connection connection) throws SQLException {

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        int recordCount = 0;

        String sqlQuery = "SELECT COUNT(*) AS ROWCOUNT FROM APM_APP_DEFAULT_VERSION WHERE APP_NAME=? AND APP_PROVIDER=? AND " +
                        "TENANT_ID=? ";

        try {
            int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);

            preparedStatement = connection.prepareStatement(sqlQuery);
            preparedStatement.setString(1, webApp.getId().getApiName());
            preparedStatement.setString(2, webApp.getId().getProviderName());
            preparedStatement.setInt(3, tenantId);
            resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                recordCount = resultSet.getInt("ROWCOUNT");
            }

            // If there are no 'default version' records for this app identity, then set this app as the default version.
            if (recordCount == 0 ) {
                setAsDefaultVersion(webApp, false, connection);
            } else if(webApp.isDefaultVersion()){
                // If there is an existing record, update that record to make this app the 'default version'.
               setAsDefaultVersion(webApp, true, connection);
            }
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, resultSet);
        }
    }

    private void setAsDefaultVersion(WebApp app, boolean update, Connection connection) throws SQLException {

        if(update){
            updateDefaultVersion(app, connection);
        }else{
            addDefaultVersion(app, connection);
        }
    }

    private void updateDefaultVersion(WebApp app, Connection connection) throws SQLException {

        PreparedStatement preparedStatement = null;
        String query = "UPDATE APM_APP_DEFAULT_VERSION SET DEFAULT_APP_VERSION=?, PUBLISHED_DEFAULT_APP_VERSION=? WHERE APP_NAME=? AND APP_PROVIDER=? AND TENANT_ID=? ";
        try {

            preparedStatement = connection.prepareStatement(query);

            preparedStatement.setString(1, app.getId().getVersion());

            String publishedDefaultAppVersion = null;
            if(APIStatus.PUBLISHED.equals(app.getStatus())){
                publishedDefaultAppVersion = app.getId().getVersion();
            }
            preparedStatement.setString(2, publishedDefaultAppVersion);

            preparedStatement.setString(3, app.getId().getApiName());
            preparedStatement.setString(4, app.getId().getProviderName());

            int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);
            preparedStatement.setInt(5, tenantId);

            preparedStatement.executeUpdate();
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private void addDefaultVersion(WebApp app, Connection connection) throws SQLException {

        PreparedStatement preparedStatement = null;
        String query = "INSERT INTO APM_APP_DEFAULT_VERSION  (APP_NAME, APP_PROVIDER, DEFAULT_APP_VERSION, " +
                            "PUBLISHED_DEFAULT_APP_VERSION, TENANT_ID) VALUES (?,?,?,?,?)";

        try {
            int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId(true);

            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, app.getId().getApiName());
            preparedStatement.setString(2, app.getId().getProviderName());
            preparedStatement.setString(3, app.getId().getVersion());

            if (app.getStatus() == APIStatus.PUBLISHED) {
                preparedStatement.setString(4, app.getId().getVersion());
            } else {
                preparedStatement.setString(4, null);
            }

            preparedStatement.setInt(5, tenantId);

            preparedStatement.executeUpdate();
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private void persistURLTemplates(List<URITemplate> uriTemplates, List<EntitlementPolicyGroup> policyGroups, int webAppDatabaseId, Connection connection) throws SQLException {

        PreparedStatement preparedStatement = null;
        ResultSet generatedKeys = null;

        try {
            String query = "INSERT INTO APM_APP_URL_MAPPING (APP_ID, HTTP_METHOD, URL_PATTERN, POLICY_GRP_ID) VALUES (?,?,?,?)";
            preparedStatement = connection.prepareStatement(query, new String[]{"URL_MAPPING_ID"});

            for(URITemplate uriTemplate : uriTemplates){

                preparedStatement.setInt(1, webAppDatabaseId);
                preparedStatement.setString(2, uriTemplate.getHTTPVerb());
                preparedStatement.setString(3, uriTemplate.getUriTemplate());

                // Set the database ID of the relevant policy group.
                // The URL templates to be persisted, maintain the relationship to the policy groups using the indexes of the policy groups list.
                int policyGroupId;
                if (uriTemplate.getPolicyGroup() != null ) {
                    policyGroupId = uriTemplate.getPolicyGroup().getPolicyGroupId();
                } else {
                    policyGroupId = uriTemplate.getPolicyGroupId();
                }
                if(policyGroupId <= 0){
                    policyGroupId = getPolicyGroupId(policyGroups, uriTemplate.getPolicyGroupName());
                    uriTemplate.setPolicyGroupId(policyGroupId);
                }
                preparedStatement.setInt(4, policyGroupId);

                preparedStatement.executeUpdate();

                generatedKeys = preparedStatement.getGeneratedKeys();

                int generatedURLTemplateId = 0;
                if (generatedKeys.next()) {
                    generatedURLTemplateId = Integer.parseInt(generatedKeys.getString(1));
                    uriTemplate.setId(generatedURLTemplateId);
                }
            }

            preparedStatement.executeBatch();
        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, null);
        }
    }

    private void createSSOProvider(WebApp app) {

        SSOProvider ssoProvider = app.getSsoProviderDetails();

        if(ssoProvider == null){
            ssoProvider = AppManagerUtil.getDefaultSSOProvider();
            app.setSsoProviderDetails(ssoProvider);
        }

        // Build the issuer name.
        APIIdentifier appIdentifier = app.getId();
        String issuerName = buildIssuerName(appIdentifier);
        ssoProvider.setIssuerName(issuerName);

        // Set the logout URL
        if(!StringUtils.isNotEmpty(app.getLogoutURL())){
            ssoProvider.setLogoutUrl(app.getLogoutURL());
        }

        SSOConfiguratorUtil ssoConfiguratorUtil = new SSOConfiguratorUtil();
        ssoConfiguratorUtil.createSSOProvider(app, false, new HashMap<String, String>());
    }

    private String buildIssuerName(APIIdentifier appIdentifier) {
        String tenantDomain = getTenantDomainOfCurrentUser();

        String issuerName = null;
        if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
            issuerName = appIdentifier.getApiName() + "-" + appIdentifier.getVersion();
        } else {
            issuerName = appIdentifier.getApiName() + "-" + tenantDomain + "-" + appIdentifier.getVersion();
        }
        return issuerName;
    }

    private int persistSubscription(Connection connection, WebApp webApp, int applicationId, String subscriptionType,
                                    String trustedIDPs)throws AppManagementException {

        int subscriptionId = -1;
        APIIdentifier appIdentifier = webApp.getId();
        if (APIStatus.PUBLISHED.equals(webApp.getStatus())) {

            Subscription subscription = getSubscription(connection, appIdentifier, applicationId, subscriptionType);
            //If subscription already exists, then update
            if (subscription != null) {
                subscriptionId = subscription.getSubscriptionId();
                if (Subscription.SUBSCRIPTION_TYPE_ENTERPRISE.equals(subscriptionType)) {
                    updateSubscription(connection, subscriptionId, subscriptionType, trustedIDPs, subscription.getSubscriptionStatus());
                } else if (Subscription.SUBSCRIPTION_TYPE_INDIVIDUAL.equals(subscriptionType)) {
                    updateSubscription(connection, subscriptionId, subscriptionType, trustedIDPs, AppMConstants.SubscriptionStatus.ON_HOLD);
                }
            }else{
                subscriptionId = addSubscription(connection, appIdentifier, subscriptionType,
                        applicationId, AppMConstants.SubscriptionStatus.ON_HOLD, trustedIDPs);
            }
        }
        return subscriptionId;
    }

    private int addSubscription(Connection connection, APIIdentifier appIdentifier, String subscriptionType, int applicationId,
                                String status, String trustedIdps) throws AppManagementException {

        ResultSet appIdResultSet = null;
        ResultSet subscriptionIdResultSet = null;
        PreparedStatement preparedStmtToGetApp = null;
        PreparedStatement preparedStmtToInsertSubscription = null;

        int subscriptionId = -1;
        int apiId = -1;

        try {
            String getAppIdQuery = "SELECT APP_ID FROM APM_APP API WHERE APP_PROVIDER = ? AND APP_NAME = ? AND APP_VERSION = ?";
            preparedStmtToGetApp = connection.prepareStatement(getAppIdQuery);
            preparedStmtToGetApp.setString(1, AppManagerUtil.replaceEmailDomainBack(appIdentifier.getProviderName()));
            preparedStmtToGetApp.setString(2, appIdentifier.getApiName());
            preparedStmtToGetApp.setString(3, appIdentifier.getVersion());
            appIdResultSet = preparedStmtToGetApp.executeQuery();
            if (appIdResultSet.next()) {
                apiId = appIdResultSet.getInt("APP_ID");
            }
            preparedStmtToGetApp.close();

            if (apiId == -1) {
                String msg = "Unable to retrieve the WebApp ID for webapp with name '" + appIdentifier.getApiName() +
                        "' version '"+appIdentifier.getVersion()+ "'";
                log.error(msg);
                throw new AppManagementException(msg);
            }

            // This query to update the APM_SUBSCRIPTION table
            String sqlQuery = "INSERT INTO APM_SUBSCRIPTION (TIER_ID,SUBSCRIPTION_TYPE, APP_ID, " +
                                "APPLICATION_ID,SUB_STATUS, TRUSTED_IDP, SUBSCRIPTION_TIME) " +
                                "VALUES (?,?,?,?,?,?,?)";

            // Adding data to the APM_SUBSCRIPTION table
            preparedStmtToInsertSubscription =
                    connection.prepareStatement(sqlQuery, new String[] {AppMConstants.SUBSCRIPTION_FIELD_SUBSCRIPTION_ID});
            if (connection.getMetaData().getDriverName().contains("PostgreSQL")) {
                preparedStmtToInsertSubscription = connection.prepareStatement(sqlQuery, new String[] { "subscription_id" });
            }

            byte count = 0;
            preparedStmtToInsertSubscription.setString(++count, appIdentifier.getTier());
            preparedStmtToInsertSubscription.setString(++count, subscriptionType);
            preparedStmtToInsertSubscription.setInt(++count, apiId);
            preparedStmtToInsertSubscription.setInt(++count, applicationId);
            preparedStmtToInsertSubscription.setString(++count, status != null ? status : AppMConstants.SubscriptionStatus.UNBLOCKED);
            preparedStmtToInsertSubscription.setString(++count, trustedIdps);
            preparedStmtToInsertSubscription.setTimestamp(++count, new Timestamp(new java.util.Date().getTime()));

            preparedStmtToInsertSubscription.executeUpdate();
            subscriptionIdResultSet = preparedStmtToInsertSubscription.getGeneratedKeys();
            while (subscriptionIdResultSet.next()) {
                subscriptionId = Integer.valueOf(subscriptionIdResultSet.getString(1)).intValue();
            }

            // finally commit transaction
            connection.commit();

        } catch (SQLException e) {
            handleException("Failed to add subscriber data ", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStmtToGetApp, null, appIdResultSet);
            APIMgtDBUtil.closeAllConnections(preparedStmtToInsertSubscription, null, subscriptionIdResultSet);
        }
        return subscriptionId;
    }

    private void updateSubscription(Connection connection, int subscriptionId, String subscriptionType,
                                    String trustedIDPs, String subscriptionStatus) throws AppManagementException {

        PreparedStatement preparedStmtToUpdateSubscription = null;
        ResultSet resultSet = null;

        try{
            String queryToUpdateSubscription =
                    "UPDATE APM_SUBSCRIPTION " +
                            "SET SUBSCRIPTION_TYPE = ?, TRUSTED_IDP = ? , SUB_STATUS = ?" +
                            "WHERE SUBSCRIPTION_ID = ?";

            preparedStmtToUpdateSubscription = connection.prepareStatement(queryToUpdateSubscription);
            preparedStmtToUpdateSubscription.setString(1, subscriptionType);
            preparedStmtToUpdateSubscription.setString(2, trustedIDPs);
            preparedStmtToUpdateSubscription.setString(3, subscriptionStatus);
            preparedStmtToUpdateSubscription.setInt(4, subscriptionId);

            preparedStmtToUpdateSubscription.executeUpdate();
            connection.commit();
        }catch (SQLException e){
            handleException(String.format("Failed updating subscription with Id : %d", subscriptionId), e);
        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStmtToUpdateSubscription, connection, resultSet);
        }
    }

    private Subscription getSubscription(Connection connection, APIIdentifier appIdentifier, int applicationId,
                                         String subscriptionTyp) throws AppManagementException {
        PreparedStatement preparedStatement = null;
        ResultSet subscriptionsResultSet = null;
        Subscription subscription = null;

        try{
            String queryToGetSubscriptionId =
                    "SELECT SUBSCRIPTION_ID, SUB.APP_ID, APPLICATION_ID, SUBSCRIPTION_TYPE, SUB_STATUS, TRUSTED_IDP " +
                            "FROM APM_SUBSCRIPTION SUB, APM_APP APP " +
                            "WHERE SUB.APP_ID = APP.APP_ID AND APP.APP_PROVIDER = ? AND APP.APP_NAME = ? " +
                            "AND APP.APP_VERSION = ? AND SUB.APPLICATION_ID = ? AND SUB.SUBSCRIPTION_TYPE = ?";

            preparedStatement = connection.prepareStatement(queryToGetSubscriptionId);
            preparedStatement.setString(1, AppManagerUtil.replaceEmailDomainBack(appIdentifier.getProviderName()));
            preparedStatement.setString(2, appIdentifier.getApiName());
            preparedStatement.setString(3, appIdentifier.getVersion());
            preparedStatement.setInt(4, applicationId);
            preparedStatement.setString(5, subscriptionTyp);
            subscriptionsResultSet = preparedStatement.executeQuery();

            if (subscriptionsResultSet.next()) {
                subscription = new Subscription();
                subscription.setSubscriptionId(subscriptionsResultSet.getInt(AppMConstants.SUBSCRIPTION_FIELD_SUBSCRIPTION_ID));
                subscription.setWebAppId(subscriptionsResultSet.getInt(AppMConstants.SUBSCRIPTION_FIELD_APP_ID));
                subscription.setApplicationId(subscriptionsResultSet.getInt(AppMConstants.APPLICATION_ID));
                subscription.setSubscriptionType(subscriptionsResultSet.getString(AppMConstants.SUBSCRIPTION_FIELD_TYPE));
                subscription.setSubscriptionStatus(subscriptionsResultSet.getString(AppMConstants.SUBSCRIPTION_FIELD_SUB_STATUS));

                String trustedIdpsJson = subscriptionsResultSet.getString(AppMConstants.SUBSCRIPTION_FIELD_TRUSTED_IDP);
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
        }catch (SQLException e){
            handleException(String.format("Failed to get subscription for app identifier : %d and application id : %s",
                    appIdentifier.toString(), appIdentifier), e);
        }finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, null, subscriptionsResultSet);
        }
        return subscription;
    }

    private int getApplicationId(Connection connection, String applicationName, Subscriber subscriber) throws AppManagementException {

        PreparedStatement preparedStmtToGetApplicationId = null;
        ResultSet applicationIdResultSet = null;
        int applicationId = 0;

        String sqlQuery = "SELECT APPLICATION_ID FROM APM_APPLICATION WHERE SUBSCRIBER_ID= ? AND  NAME= ?";

        try {
            preparedStmtToGetApplicationId = connection.prepareStatement(sqlQuery);
            preparedStmtToGetApplicationId.setInt(1, subscriber.getId());
            preparedStmtToGetApplicationId.setString(2, applicationName);
            applicationIdResultSet = preparedStmtToGetApplicationId.executeQuery();

            while (applicationIdResultSet.next()) {
                applicationId = applicationIdResultSet.getInt("APPLICATION_ID");
            }

        } catch (SQLException e) {
            handleException("Error occurred while retrieving application '" + applicationName + "' for subscriber '" +
                    subscriber.getName() + "'", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStmtToGetApplicationId, null, applicationIdResultSet);
        }
        return applicationId;
    }

    private Subscriber getSubscriber(Connection connection, String subscriberName) throws AppManagementException {
        Subscriber subscriber = null;
        PreparedStatement preparedStmtToGetSubscriber = null;
        ResultSet subscribersResultSet = null;

        int tenantId = AppManagerUtil.getTenantId(subscriberName);


        String sqlQuery =
                "SELECT SUBSCRIBER_ID, USER_ID, TENANT_ID, EMAIL_ADDRESS, DATE_SUBSCRIBED FROM " +
                        "APM_SUBSCRIBER WHERE USER_ID = ? AND TENANT_ID = ?";
        try {

            preparedStmtToGetSubscriber = connection.prepareStatement(sqlQuery);
            preparedStmtToGetSubscriber.setString(1, subscriberName);
            preparedStmtToGetSubscriber.setInt(2, tenantId);
            subscribersResultSet = preparedStmtToGetSubscriber.executeQuery();

            if (subscribersResultSet.next()) {
                subscriber =
                        new Subscriber(
                                subscribersResultSet.getString(AppMConstants.SUBSCRIBER_FIELD_EMAIL_ADDRESS));
                subscriber.setEmail(subscribersResultSet.getString(AppMConstants.SUBSCRIBER_FIELD_EMAIL_ADDRESS));
                subscriber.setId(subscribersResultSet.getInt(AppMConstants.SUBSCRIBER_FIELD_SUBSCRIBER_ID));
                subscriber.setName(subscriberName);
                subscriber.setSubscribedDate(subscribersResultSet.getDate(AppMConstants.SUBSCRIBER_FIELD_DATE_SUBSCRIBED));
                subscriber.setTenantId(subscribersResultSet.getInt(AppMConstants.SUBSCRIBER_FIELD_TENANT_ID));
            }

        } catch (SQLException e) {
            handleException("Failed to get Subscriber for :" + subscriberName, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStmtToGetSubscriber, null, subscribersResultSet);
        }
        return subscriber;
    }

    private int addSubscriber(Connection connection, Subscriber subscriber) throws AppManagementException {
        ResultSet subscriberIdResultSet = null;
        PreparedStatement preparedStmtToAddSubscriber = null;
        int subscriberId = -1;
        try {
            String query = "INSERT INTO APM_SUBSCRIBER (USER_ID, TENANT_ID, EMAIL_ADDRESS, " +
                    "DATE_SUBSCRIBED) VALUES (?,?,?,?)";

            preparedStmtToAddSubscriber =
                    connection.prepareStatement(query, new String[]{AppMConstants.SUBSCRIBER_FIELD_SUBSCRIBER_ID});
            preparedStmtToAddSubscriber.setString(1, subscriber.getName());
            preparedStmtToAddSubscriber.setInt(2, subscriber.getTenantId());
            preparedStmtToAddSubscriber.setString(3, subscriber.getEmail());
            preparedStmtToAddSubscriber.setTimestamp(4, new Timestamp(subscriber.getSubscribedDate().getTime()));
            preparedStmtToAddSubscriber.executeUpdate();

            subscriberIdResultSet = preparedStmtToAddSubscriber.getGeneratedKeys();
            if (subscriberIdResultSet.next()) {
                subscriberId = Integer.valueOf(subscriberIdResultSet.getString(1)).intValue();
            }
            connection.commit();
        } catch (SQLException e) {
            handleException("Error occurred while adding subscriber with name '" + subscriber.getName(), e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStmtToAddSubscriber, null, subscriberIdResultSet);
        }
        return subscriberId;
    }

    private int addApplication(Connection connection, Application application, Subscriber subscriber) throws AppManagementException {

        PreparedStatement preparedStmtToAddApplication = null;
        ResultSet applicationIdResultSet = null;
        int applicationId = -1;
        try {
            // This query to update the APM_APPLICATION table
            String sqlQuery = "INSERT INTO APM_APPLICATION " +
                                "(NAME, SUBSCRIBER_ID, APPLICATION_TIER, CALLBACK_URL, DESCRIPTION, APPLICATION_STATUS) " +
                                "VALUES (?,?,?,?,?,?)";

            preparedStmtToAddApplication = connection.prepareStatement(sqlQuery, new String[]{AppMConstants.APPLICATION_ID});
            if (connection.getMetaData().getDriverName().contains("PostgreSQL")) {
                preparedStmtToAddApplication = connection.prepareStatement(sqlQuery, new String[]{"application_id"});
            }
            preparedStmtToAddApplication.setString(1, application.getName());
            preparedStmtToAddApplication.setInt(2, subscriber.getId());
            preparedStmtToAddApplication.setString(3, application.getTier());
            preparedStmtToAddApplication.setString(4, application.getCallbackUrl());
            preparedStmtToAddApplication.setString(5, application.getDescription());

            if (application.getName().equals(AppMConstants.DEFAULT_APPLICATION_NAME)) {
                preparedStmtToAddApplication.setString(6, AppMConstants.ApplicationStatus.APPLICATION_APPROVED);
            } else {
                preparedStmtToAddApplication.setString(6, AppMConstants.ApplicationStatus.APPLICATION_CREATED);
            }
            preparedStmtToAddApplication.executeUpdate();
            applicationIdResultSet = preparedStmtToAddApplication.getGeneratedKeys();
            while (applicationIdResultSet.next()) {
                applicationId = Integer.parseInt(applicationIdResultSet.getString(1));
            }
        } catch (SQLException e) {
            handleException("Error occurred while adding application '" + application.getName() + "' for subscriber '" +
                    subscriber.getName() + "'", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStmtToAddApplication, null, applicationIdResultSet);
        }
        return applicationId;
    }

    private int getTenantIdOfCurrentUser(){
        return BarleyContext.getThreadLocalCarbonContext().getTenantId();
    }

    private String getUsernameOfCurrentUser(){
        return BarleyContext.getThreadLocalCarbonContext().getUsername();
    }

    private String getTenantDomainOfCurrentUser() {
        return BarleyContext.getThreadLocalCarbonContext().getTenantDomain();
    }

    private Connection getRDBMSConnectionWithoutAutoCommit() throws SQLException {
        return getRDBMSConnection(false);
    }

    private Connection getRDBMSConnectionWithAutoCommit() throws SQLException {
        return getRDBMSConnection(true);
    }

    private Connection getRDBMSConnection(boolean setAutoCommit) throws SQLException {

        Connection connection = APIMgtDBUtil.getConnection();
        connection.setAutoCommit(setAutoCommit);

        return connection;
    }

    private void rollbackTransactions(App app, Registry registry, Connection connection) {

        try {
            if(registry != null){
                registry.rollbackTransaction();
            }

            if(connection != null){
                connection.rollback();
            }
        } catch (RegistryException e) {
            // No need to throw this exception.
            log.error(String.format("Can't rollback registry persist operation for the app '%s:%s'", app.getType(), app.getDisplayName()));
        } catch (SQLException e) {
            // No need to throw this exception.
            log.error(String.format("Can't rollback RDBMS persist operation for the app '%s:%s'", app.getType(), app.getDisplayName()));
        }
    }

    private void handleException(String msg, Exception e) throws AppManagementException {
        log.error(msg, e);
        throw new AppManagementException(msg, e);
    }
}
