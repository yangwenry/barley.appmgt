package barley.appmgt.gateway;

public class APIMgtGatewayConstants {

    public static final String CONSUMER_KEY = "api.ut.consumerKey";
    public static final String USER_ID = "api.ut.userId";
    public static final String CONTEXT = "api.ut.context";
    public static final String API_VERSION = "api.ut.api_version";
    public static final String API = "api.ut.api";
    public static final String VERSION = "api.ut.version";
    public static final String RESOURCE = "api.ut.resource";
    public static final String HTTP_METHOD = "api.ut.HTTP_METHOD";
    public static final String HOST_NAME = "api.ut.hostName";
    public static final String API_PUBLISHER = "api.ut.apiPublisher";
    public static final String APPLICATION_NAME = "api.ut.application.name";
    public static final String APPLICATION_ID = "api.ut.application.id";
    public static final String REQUEST_START_TIME = "api.ut.requestTime";
    public static final String BACKEND_REQUEST_START_TIME = "api.ut.backendRequestTime";
    public static final String BACKEND_REQUEST_END_TIME = "api.ut.backendRequestEndTime";
    public static final String END_USER_NAME = "api.ut.userName";
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String REQUEST_RECEIVED_TIME = "wso2statistics.request.received.time";
    public static final String AUTHORIZATION = "Authorization";
    public static final String REVOKED_ACCESS_TOKEN = "RevokedAccessToken";
    public static final String DEACTIVATED_ACCESS_TOKEN = "DeactivatedAccessToken";
    public static final String SCOPES = "Scopes";
    public static final String REQUEST_EXECUTION_START_TIME ="request.execution.start.time";
    public static final String SYNAPSE_ENDPOINT_ADDRESS = "ENDPOINT_ADDRESS";

    public static final String RESOURCE_PATTERN = "^/.+?/.+?([/?].+)$";

    public static final String METHOD_NOT_FOUND_ERROR_MSG = "Method not allowed for given API resource";
    public static final String RESOURCE_NOT_FOUND_ERROR_MSG = "No matching resource found for given API Request";

    public static final String BACKEND_LATENCY = "backend_latency";
    public static final String SECURITY_LATENCY = "security_latency";
    public static final String THROTTLING_LATENCY = "throttling_latency";
    public static final String REQUEST_MEDIATION_LATENCY = "request_mediation_latency";
    public static final String RESPONSE_MEDIATION_LATENCY = "response_mediation_latency";
    public static final String OTHER_LATENCY = "other_latency";
}
