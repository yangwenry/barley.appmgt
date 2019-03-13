package barley.appmgt.gateway.service;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.cache.Caching;

import org.apache.axiom.util.base64.Base64Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opensaml.xml.util.Base64;
import org.wso2.carbon.core.AbstractAdmin;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.gateway.dto.Token;
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.dao.AppMDAO;
import barley.appmgt.impl.dto.SAMLTokenInfoDTO;
import barley.identity.oauth.common.OAuth2ErrorCodes;
import barley.identity.oauth2.dto.OAuth2AccessTokenReqDTO;
import barley.identity.oauth2.dto.OAuth2AccessTokenRespDTO;

/**
 * OAuth2 Service which is used to issue access tokens upon authorizing by the
 * user and issue/validateGrant access tokens.
 */
@SuppressWarnings("unused")
public class AppManagerOAuth2Service extends AbstractAdmin {

    private static Log log = LogFactory.getLog(AppManagerOAuth2Service.class);


    /**
     * Issue access token in exchange to an Authorization Grant.
     *
     * @param tokenReqDTO <Code>OAuth2AccessTokenReqDTO</Code> representing the Access Token request
     * @return <Code>OAuth2AccessTokenRespDTO</Code> representing the Access Token response
     */
    public Token issueAccessToken(OAuth2AccessTokenReqDTO tokenReqDTO) {
        if (log.isDebugEnabled()) {
            log.debug("Access Token Request Received with the Client Id : " + tokenReqDTO.getClientId() +
                    ", Grant Type : " + tokenReqDTO.getGrantType());
        }

        Token accessToken = null;
        try {
            String webAppConsumerKey = tokenReqDTO.getClientId();
            String webAppConsumerSecret = tokenReqDTO.getClientSecret();
            String saml2SsoIssuer = null;

            if (!AppMDAO.webAppKeyPairExist(webAppConsumerKey, webAppConsumerSecret)) {
                throw new Exception("Invalid Credentials");
            }

            saml2SsoIssuer = AppMDAO.getSAML2SSOIssuerByAppConsumerKey(webAppConsumerKey);

            //scope received as samlssoTokenId,apiAlias
            String[] scopes = tokenReqDTO.getScope();
            String samlssoTokenId = null;
            String apiAlias = null;
            if (scopes != null && scopes.length > 0) {
                String scope = scopes[0];
                if (scope != null) {
                    String[] tmp = scope.split(",");
                    samlssoTokenId = tmp[0].trim();
                    apiAlias = tmp[1].trim();
                }
            }

            Map<String, String> registeredAPIs = getRegisteredAPIs(webAppConsumerKey);
            if (isAuthorizedAPI(registeredAPIs, apiAlias)) {
                Map<String, SAMLTokenInfoDTO> encodedSAMLResponseMap = (HashMap<String, SAMLTokenInfoDTO>) Caching.getCacheManager(AppMConstants.SAML2_CONFIG_CACHE_MANAGER)
                        .getCache(AppMConstants.SAML2_CONFIG_CACHE).get(samlssoTokenId);
                String samlResponseOfApp = encodedSAMLResponseMap.get(saml2SsoIssuer).getEncodedSamlToken();
                String decodedSAMLResponse = getSamlAssetionString(new String(Base64.decode(samlResponseOfApp)));
                String encodedSamlAssertion = URLEncoder.encode(Base64.encodeBytes(getSamlAssetionString(decodedSAMLResponse).getBytes()), "UTF-8");
              
                //consumerKey,consumerSecret,tokenEndpoint
                String tokenEpKeyPair = registeredAPIs.get(apiAlias);
                String[] value = tokenEpKeyPair.split(",");
                String consumerKey = value[0].trim();
                String consumerSecret = value[1].trim();
                String tokenEndPoint = value[2].trim();

                accessToken = getAccessToken(consumerKey, consumerSecret, encodedSamlAssertion, tokenEndPoint);

            } else {
                throw new Exception("API :" + apiAlias + " not registered in webApp");
            }

            return accessToken;
        } catch (Exception e) { 
            log.error("Error when issuing the access token. ", e);
            OAuth2AccessTokenRespDTO tokenRespDTO = new OAuth2AccessTokenRespDTO();
            tokenRespDTO.setError(true);
            tokenRespDTO.setErrorCode(OAuth2ErrorCodes.SERVER_ERROR);
            tokenRespDTO.setErrorMsg("Error when issuing the access token");
            return accessToken;
        }
    }

    public Token getAccessToken(String consumerKey, String consumerSecret, String encodedSamlAssertion, String tokenEndPoint) {
        try {
            String applicationToken = consumerKey + ":" + consumerSecret;
            applicationToken = "Basic " + Base64Utils.encode(applicationToken.getBytes()).trim();

            String payload = "grant_type=urn:ietf:params:oauth:grant-type:saml2-bearer&assertion=" + encodedSamlAssertion;
            // String payload = "grant_type=password&username=" + "admin" + "&password=admin";

            String response = doPost(tokenEndPoint, applicationToken, payload, "application/x-www-form-urlencoded");

            JSONParser parser = new JSONParser();
            Object obj = null;
            try {
                obj = parser.parse(response);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            JSONObject jsonObject = (JSONObject) obj;

            Token token = new Token();
            token.setAccessToken((String) jsonObject.get("access_token"));
            long expiresIn = ((Long) jsonObject.get("expires_in")).intValue();
            token.setExpiresIn(expiresIn);
            token.setRefreshToken((String) jsonObject.get("refresh_token"));
            token.setTokenType((String) jsonObject.get("token_type"));

            return token;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isAuthorizedAPI(Map<String, String> registeredAPIs, String apiName) throws
                                                                                        AppManagementException {
        if (registeredAPIs.containsKey(apiName)) {
            return true;
        }
        return false;
    }

    private Map<String, String> getRegisteredAPIs(String webAppConsumerKey) throws
                                                                            AppManagementException {
        return AppMDAO.getRegisteredAPIs(webAppConsumerKey);
    }

    private boolean isAuthorizedKeyPair() {
        return false;
    }

    public String getResponsePayload(HttpResponse response) throws IOException {
        StringBuffer buffer = new StringBuffer();
        InputStream in = null;
        try {
            if (response.getEntity() != null) {
                in = response.getEntity().getContent();
                int length;
                byte[] tmp = new byte[2048];
                while ((length = in.read(tmp)) != -1) {
                    buffer.append(new String(tmp, 0, length));
                }
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                in.close();
            }
        }

        return buffer.toString();
    }


    public String doPost(String url, String token, String payload, String contentType)
            throws IOException {
        URL url1;
        HttpURLConnection connection = null;
        String response = null;
        try {
            //Create connection
            url1 = new URL(url);
            connection = (HttpURLConnection) url1.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            connection.setRequestProperty("Authorization", token);
            connection.setRequestProperty("Content-Length", "" +
                                                            Integer.toString(payload.getBytes().length));
            connection.setRequestProperty("Content-Language", "en-US");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(payload);
            wr.flush();
            wr.close();

            //Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuffer endpointResponse = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                endpointResponse.append(line);
                endpointResponse.append('\r');
            }

            rd.close();
            response = endpointResponse.toString();
        } catch (Exception e) {
            e.printStackTrace();
            //return null;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return response;
    }

    private void addSecurityHeaders(HttpRequest request, String token) {
        if (token != null) {
            request.setHeader(HttpHeaders.AUTHORIZATION, token);
        }
    }

    public String getCookieValue(String cookieString, String cookieName) {
        if (cookieString.length() > 0) {
            int cStart = cookieString.indexOf(cookieName + "=");
            int cEnd;
            if (cStart != -1) {
                cStart = cStart + cookieName.length() + 1;
                cEnd = cookieString.indexOf(";", cStart);
                if (cEnd == -1) {
                    cEnd = cookieString.length();
                }
                return cookieString.substring(cStart, cEnd);
            }
        }
        return "";
    }

    public String getSamlAssetionString(String samlResponse) {
        return samlResponse.substring(samlResponse.indexOf("<saml2:Assertion"),
                                      samlResponse.indexOf("</saml2:Assertion>")) + "</saml2:Assertion>";

    }

}
