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
            "   FORMAT(ROUND(CAST(SUM(RATING) AS DECIMAL) / COUNT(RATING), 1), 1) AS RATING " +
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
    
    public static final String REMOVE_SUBSCRIBER_FROM_API_RATING_SQL = 
    		" DELETE FROM APM_APP_RATINGS WHERE SUBSCRIBER_ID = ?";
    
    public static final String GET_SUBSCRIBER_SQL = 
    		"SELECT USER_ID, TENANT_ID, EMAIL_ADDRESS, DATE_SUBSCRIBED " +
                    "FROM APM_SUBSCRIBER WHERE SUBSCRIBER_ID = ?";
    
    // 어플리케이션 삭제시 구독삭제 
    public static final String REMOVE_APPLICATION_FROM_SUBSCRIPTIONS_SQL =
            "DELETE FROM APM_SUBSCRIPTION WHERE APPLICATION_ID = ?";
    
    public static final String REMOVE_APPLICATION_FROM_APPLICATIONS_SQL =
            "DELETE FROM APM_APPLICATION WHERE APPLICATION_ID = ?";
    
    public static final String GET_SORTED_APP_SQL_PREFIX =
    		"SELECT " + 
					"CONCAT_WS('_', TB.APP_PROVIDER, TB.APP_NAME, TB.APP_VERSION) AS APP_ID " +
					", FORMAT(TC.RATING, 1) AS RATING, TB.CREATED_TIME, TB.UPDATED_TIME, TA.NEW_STATE AS STATE, TS.SUBS_CNT " +
					", TB.CATEGORY, TB.THUMBNAIL_URL, TB.DESCRIPTION, TB.TITLE " +
					", TA.TAG " +
				"FROM( " +
					"SELECT " + 
						"SB.APP_ID, SB.EVENT_ID, SB.NEW_STATE " +
						", (SELECT COALESCE(GROUP_CONCAT(TAG_NAME SEPARATOR ','), '') FROM APM_APP_TAG IT WHERE IT.APP_ID = SA.APP_ID) AS TAG " +
					"FROM ( " +
						"SELECT " + 
							  "APP_ID " +
							", MAX(EVENT_ID) AS EVENT_ID " + 
						"FROM APM_APP_LC_EVENT " + 
						"GROUP BY APP_ID " +
						 ") SA " +
					"LEFT JOIN APM_APP_LC_EVENT SB " +
						"ON (SA.APP_ID = SB.APP_ID AND SA.EVENT_ID = SB.EVENT_ID) " +
					"WHERE SB.NEW_STATE = 'PUBLISHED' " +
					 ") TA " +
				"INNER JOIN APM_APP TB " +
					"ON (TA.APP_ID = TB.APP_ID AND SUBSTRING_INDEX(TB.APP_PROVIDER, '@', -1) = ?) " +
				"LEFT JOIN ( " +
							"SELECT " +
								"T.APP_ID, ROUND(AVG(T.RATING), 1) AS RATING " +
							"FROM APM_APP_RATINGS T " +
							"GROUP BY T.APP_ID " +
							"HAVING AVG(T.RATING) " +
							"ORDER BY AVG(T.RATING) DESC " +
							
							") TC " +
				"ON TA.APP_ID = TC.APP_ID " +
				"LEFT JOIN ( " +
							"SELECT " +
								"T.APP_ID, COUNT(T.APP_ID) AS SUBS_CNT " +
							"FROM APM_SUBSCRIPTION T " +
							"GROUP BY T.APP_ID " +
							"ORDER BY COUNT(T.APP_ID) DESC " +
							") TS " +
				"ON TA.APP_ID = TS.APP_ID " +
				"WHERE UPPER(TB.APP_PROVIDER) LIKE UPPER(CONCAT('%',?,'%')) " +
					"OR UPPER(TB.APP_NAME) LIKE UPPER(CONCAT('%',?,'%')) " +
					"OR UPPER(TB.CATEGORY) LIKE UPPER(CONCAT('%',?,'%')) " +
					"OR UPPER(TB.DESCRIPTION) LIKE UPPER(CONCAT('%',?,'%')) " +
					"OR UPPER(TB.TITLE) LIKE UPPER(CONCAT('%',?,'%')) " +
					"OR UPPER(TA.TAG) LIKE UPPER(CONCAT('%',?,'%')) ";
    
    public static final String GET_SORTED_RATING_APP_SQL =
    		GET_SORTED_APP_SQL_PREFIX +
				"ORDER BY TC.RATING DESC, TA.APP_ID DESC " +
				"LIMIT ?, ?";
    
    public static final String GET_SORTED_SUBS_CNT_APP_SQL =
    		GET_SORTED_APP_SQL_PREFIX +
				"ORDER BY TS.SUBS_CNT DESC, TA.APP_ID DESC " +
				"LIMIT ?, ?";
    
    public static final String GET_SORTED_CREATED_TIME_APP_SQL =
    		GET_SORTED_APP_SQL_PREFIX +  
				"ORDER BY TB.CREATED_TIME DESC, TA.APP_ID DESC " +
				"LIMIT ?, ?";
        
    public static final String GET_PUBLIC_APP_CNT_SQL = 
    		"SELECT " + 
    				"COUNT(TA.APP_ID) AS PUB_APP_CNT " + 
				"FROM( " +
					"SELECT " + 
						"SB.APP_ID, SB.EVENT_ID, SB.NEW_STATE " +
					"FROM ( " +
						"SELECT " + 
							  "APP_ID " +
							", MAX(EVENT_ID) AS EVENT_ID " + 
						"FROM APM_APP_LC_EVENT " + 
						"GROUP BY APP_ID " +
						 ") SA " +
					"LEFT JOIN APM_APP_LC_EVENT SB " +
						"ON (SA.APP_ID = SB.APP_ID AND SA.EVENT_ID = SB.EVENT_ID) " +
					"WHERE SB.NEW_STATE = 'PUBLISHED' " +
					 ") TA " +
				"INNER JOIN APM_APP TB " +
					"ON (TA.APP_ID = TB.APP_ID AND SUBSTRING_INDEX(TB.APP_PROVIDER, '@', -1) = ?) ";
    
    /* Tag 테이블 */
    public static final String APP_TAG_SQL =
            "INSERT INTO APM_APP_TAG(APP_ID, TAG_NAME, DATE_TAGGED) VALUES (?,?,?)";
    
    public static final String REMOVE_TAG_SQL =
            "DELETE FROM APM_APP_TAG WHERE APP_ID = ? ";
    
    public static final String GET_TAG_SQL =
            "SELECT TAG_NAME FROM APM_APP_TAG WHERE APP_ID = ? ORDER BY DATE_TAGGED ASC ";
    

}
