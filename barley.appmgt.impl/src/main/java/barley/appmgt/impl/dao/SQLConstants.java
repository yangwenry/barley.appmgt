package barley.appmgt.impl.dao;

import barley.appmgt.impl.AppMConstants;

/**
 * This class is dedicated to store SQL queries in DAO
 */
public class SQLConstants {
    public static final String GET_FAVOURITE_APPS =
            "SELECT " +
                    " APP.APP_PROVIDER AS APP_PROVIDER," +
                    " APP.APP_NAME AS APP_NAME," +
                    " APP.APP_VERSION AS APP_VERSION" +
                    " FROM APM_APP APP" +
                    " INNER JOIN APM_FAVOURITE_APPS FAV_APP" +
                    " ON  (APP.APP_ID =FAV_APP.APP_ID" +
                    " AND FAV_APP.USER_ID  = ?" +
                    " AND FAV_APP.TENANT_ID = ? )" +
                    " WHERE APP.TENANT_ID = ?";
    public static final String GET_FAVOURITE_APPS_SORT_BY_CREATED_TIME_DESC =
            GET_FAVOURITE_APPS + " ORDER BY FAV_APP.CREATED_TIME DESC";
    public static final String GET_FAVOURITE_APPS_SORT_BY_APP_NAME_ASC =
            GET_FAVOURITE_APPS + " ORDER BY APP.APP_NAME ASC";
    public static final String SEARCH_FAVOURITE_APPS =
            "SELECT " +
                    " APP.APP_PROVIDER AS APP_PROVIDER," +
                    " APP.APP_NAME AS APP_NAME," +
                    " APP.APP_VERSION AS APP_VERSION" +
                    " FROM APM_APP APP" +
                    " INNER JOIN APM_FAVOURITE_APPS FAV_APP" +
                    " ON  (APP.APP_ID =FAV_APP.APP_ID" +
                    " AND FAV_APP.USER_ID  = ?" +
                    " AND FAV_APP.TENANT_ID = ? )" +
                    " WHERE APP.TENANT_ID = ?";
    public static final String SEARCH_FAVOURITE_APPS_BY_APP_PROVIDER =
            SEARCH_FAVOURITE_APPS + " AND  APP.APP_PROVIDER LIKE ?";
    public static final String SEARCH_FAVOURITE_APPS_BY_APP_NAME =
            SEARCH_FAVOURITE_APPS + " AND  APP.APP_NAME LIKE ?";
    public static final String SEARCH_USER_ACCESSIBLE_APPS =
            "SELECT APP_NAME,APP_PROVIDER,APP_VERSION" +
                    " FROM APM_APP LEFT JOIN APM_SUBSCRIPTION ON APM_APP.APP_ID = APM_SUBSCRIPTION.APP_ID" +
                    " WHERE APM_APP.TREAT_AS_SITE = ? AND APM_APP.TENANT_ID = ?" +
                    " AND (APM_SUBSCRIPTION.APPLICATION_ID =? OR APM_APP.APP_ALLOW_ANONYMOUS= ?)" +
                    " AND APM_SUBSCRIPTION.SUB_STATUS = 'UNBLOCKED'";
    public static final String SEARCH_USER_ACCESSIBLE_APPS_BY_APP_PROVIDER =
            SEARCH_USER_ACCESSIBLE_APPS + " AND  APM_APP.APP_PROVIDER LIKE ?";
    public static final String SEARCH_USER_ACCESSIBLE_APPS_BY_APP_NAME =
            SEARCH_USER_ACCESSIBLE_APPS + " AND  APM_APP.APP_NAME LIKE ?";
    public static final String GET_USER_ACCESSIBlE_APPS =
            "SELECT APP_NAME,APP_PROVIDER,APP_VERSION" +
                    " FROM APM_APP LEFT JOIN APM_SUBSCRIPTION ON APM_APP.APP_ID = APM_SUBSCRIPTION.APP_ID" +
                    " WHERE APM_APP.TREAT_AS_SITE = ? AND APM_APP.TENANT_ID = ?" +
                    " AND (APM_SUBSCRIPTION.APPLICATION_ID =? OR APM_APP.APP_ALLOW_ANONYMOUS= ?)" +
                    " AND APM_SUBSCRIPTION.SUB_STATUS = 'UNBLOCKED'";
    public static final String GET_USER_ACCESSIBlE_APPS_ORDER_BY_SUBSCRIPTION_TIME = GET_USER_ACCESSIBlE_APPS +
            " ORDER BY APM_SUBSCRIPTION.SUBSCRIPTION_TIME DESC";
    public static final String GET_USER_ACCESSIBlE_APPS_ORDER_BY_APP_NAME = GET_USER_ACCESSIBlE_APPS +
            " ORDER BY APM_APP.APP_NAME ASC";

    
    // (추가) 2018.03.21  
    public static final String GET_ACCESS_TOKEN_DATA_PREFIX =
            " SELECT " +
            "   IAT.ACCESS_TOKEN, " +
            "   IAT.AUTHZ_USER, " +
//            "   IAT.DOMAIN_NAME, " +
			"   IAT.USER_DOMAIN, " +
            "   ISAT.TOKEN_SCOPE, " +
            "   ICA.CONSUMER_KEY, " +
            "   IAT.TIME_CREATED, " +
            "   IAT.VALIDITY_PERIOD " +
            " FROM ";
    
    // (추가) 2018.03.21
    public static final String GET_ACCESS_TOKEN_DATA_SUFFIX =
            "   IAT, " +
            AppMConstants.TOKEN_SCOPE_ASSOCIATION_TABLE + " ISAT, " +
            AppMConstants.CONSUMER_KEY_SECRET_TABLE + " ICA " +
            " WHERE IAT.TOKEN_ID = ISAT.TOKEN_ID " +
            "   AND IAT.CONSUMER_KEY_ID = ICA.ID " +
            "   AND IAT.ACCESS_TOKEN= ? " +
            "   AND IAT.TOKEN_STATE='ACTIVE' ";
    
    // (추가) 2018.03.21
    public static final String GET_ACCESS_TOKEN_BY_USER_PREFIX =
            " SELECT " +
            "   IAT.ACCESS_TOKEN, " +
            "   IAT.AUTHZ_USER, " +
            "   IAT.USER_DOMAIN, " +
            "   ISAT.TOKEN_SCOPE, " +
            "   ICA.CONSUMER_KEY, " +
            "   IAT.TIME_CREATED, " +
            "   IAT.VALIDITY_PERIOD " +
            " FROM ";
    
    // (추가) 2018.03.21
    public static final String GET_ACCESS_TOKEN_BY_USER_SUFFIX =
            "   IAT, " +
            AppMConstants.TOKEN_SCOPE_ASSOCIATION_TABLE + " ISAT, " +
            AppMConstants.CONSUMER_KEY_SECRET_TABLE + " ICA " +
            " WHERE IAT.AUTHZ_USER= ? " +
            "   AND IAT.TOKEN_STATE='ACTIVE'" +
            "   AND IAT.TOKEN_ID = ISAT.TOKEN_ID " +
            "   AND IAT.CONSUMER_KEY_ID = ICA.ID " +
            " ORDER BY IAT.TOKEN_ID";
    
    public static final String IS_ACCESS_TOKEN_EXISTS_PREFIX = " SELECT ACCESS_TOKEN " + " FROM ";
    
    public static final String IS_ACCESS_TOKEN_EXISTS_SUFFIX = " WHERE ACCESS_TOKEN= ? ";
    
    public static final String IS_ACCESS_TOKEN_REVOKED_PREFIX = " SELECT TOKEN_STATE " + " FROM ";

    public static final String IS_ACCESS_TOKE_REVOKED_SUFFIX = " WHERE ACCESS_TOKEN= ? ";
    
    public static final String ADD_COMMENT_SQL =
            " INSERT INTO APM_APP_COMMENTS (COMMENT_TEXT,COMMENTED_USER,DATE_COMMENTED, APP_ID)" +
            " VALUES (?,?,?,?)";
    
    // (추가) 2019.05.16 
    public static final String UPDATE_COMMENT_SQL =
    		"UPDATE " +
            "   APM_APP_COMMENTS " +
            " SET " +
            "   COMMENT_TEXT = ? " +            
            " WHERE " +
            "   COMMENT_ID = ? ";
    
    // (추가) 2019.05.16 
    public static final String DELETE_COMMENT_SQL =
            " DELETE FROM APM_APP_COMMENTS WHERE COMMENT_ID = ? ";

    public static final String GET_COMMENTS_SQL =
            " SELECT " +
            "   APM_APP_COMMENTS.COMMENT_ID AS COMMENT_ID," +
            "   APM_APP_COMMENTS.COMMENT_TEXT AS COMMENT_TEXT," +
            "   APM_APP_COMMENTS.COMMENTED_USER AS COMMENTED_USER," +
            "   APM_APP_COMMENTS.DATE_COMMENTED AS DATE_COMMENTED " +
            " FROM " +
            "   APM_APP_COMMENTS, " +
            "   APM_APP APP " +
            " WHERE " +
            "   APP.APP_PROVIDER = ? " +
            "   AND APP.APP_NAME = ? " +
            "   AND APP.APP_VERSION  = ? " +
            "   AND APP.APP_ID = APM_APP_COMMENTS.APP_ID";
    
    // (추가) 2019.06.03
    public static final String GET_APP_ID_SQL =
            "SELECT APP.APP_ID FROM APM_APP APP WHERE APP.APP_PROVIDER = ? AND APP.APP_NAME = ? AND APP.APP_VERSION = ?";

    // (추가) 2019.06.04
    public static final String GET_API_RATING_SQL =
            "SELECT RATING FROM APM_APP_RATINGS WHERE APP_ID= ? AND SUBSCRIBER_ID=? ";

    public static final String APP_API_RATING_SQL =
            "INSERT INTO APM_APP_RATINGS (RATING, APP_ID, SUBSCRIBER_ID)  VALUES (?,?,?)";

    public static final String UPDATE_API_RATING_SQL =
            "UPDATE APM_APP_RATINGS SET RATING=? WHERE APP_ID= ? AND SUBSCRIBER_ID=?";

    public static final String GET_RATING_ID_SQL =
            "SELECT RATING_ID FROM APM_APP_RATINGS WHERE APP_ID= ? AND SUBSCRIBER_ID=? ";

    public static final String REMOVE_RATING_SQL =
            "DELETE FROM APM_APP_RATINGS WHERE RATING_ID =? ";

    public static final String GET_RATING_SQL =
            "SELECT RATING FROM APM_APP_RATINGS WHERE SUBSCRIBER_ID  = ? AND APP_ID= ? ";

    public static final String GET_AVERAGE_RATING_SQL =
            " SELECT " +
            "   CAST( SUM(RATING) AS DECIMAL)/COUNT(RATING) AS RATING " +
            " FROM " +
            "   APM_APP_RATINGS " +
            " WHERE " +
            "   APP_ID =? " +
            " GROUP BY " +
            "   APP_ID ";
    
    // (추가) 2019.06.05
    public static final String GET_APPLICATION_BY_NAME_PREFIX =
            " SELECT " +
            "   APP.APPLICATION_ID," +
            "   APP.NAME," +
            "   APP.SUBSCRIBER_ID," +
            "   APP.APPLICATION_TIER," +
            "   APP.CALLBACK_URL," +
            "   APP.DESCRIPTION, " +
            "   APP.SUBSCRIBER_ID," +
            "   APP.APPLICATION_STATUS," +
            "   SUB.USER_ID" +
            " FROM " +
            "   APM_SUBSCRIBER SUB," +
            "   APM_APPLICATION APP";
    
    
    public static final String ADD_SUBSCRIBER_SQL =
    		"INSERT INTO APM_SUBSCRIBER (USER_ID, TENANT_ID, EMAIL_ADDRESS, " +
                    "DATE_SUBSCRIBED) VALUES (?,?,?,?)";

    public static final String UPDATE_SUBSCRIBER_SQL =
    		"UPDATE APM_SUBSCRIBER SET USER_ID = ?, TENANT_ID = ?, " +
                    "EMAIL_ADDRESS = ?, DATE_SUBSCRIBED = ? WHERE SUBSCRIBER_ID = ?";
    
    public static final String REMOVE_SUBSCRIBER_SQL = 
            " DELETE FROM APM_SUBSCRIBER WHERE SUBSCRIBER_ID = ?";
    
    public static final String GET_SUBSCRIBER_SQL = 
    		"SELECT USER_ID, TENANT_ID, EMAIL_ADDRESS, DATE_SUBSCRIBED " +
                    "FROM APM_SUBSCRIBER WHERE SUBSCRIBER_ID = ?";
    
    // 어플리케이션 삭제시 구독삭제 
    public static final String REMOVE_APPLICATION_FROM_SUBSCRIPTIONS_SQL =
            "DELETE FROM APM_SUBSCRIPTION WHERE APPLICATION_ID = ?";
    
    public static final String REMOVE_APPLICATION_FROM_APPLICATIONS_SQL =
            "DELETE FROM APM_APPLICATION WHERE APPLICATION_ID = ?";
}
