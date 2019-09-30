package barley.appmgt.impl.utils;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;

import barley.appmgt.api.AppManagementException;

public class HttpUtils {
	
	private static final Log log = LogFactory.getLog(HttpUtils.class);

	public static void doPost(String endpoint, List<NameValuePair> urlParams) throws AppManagementException {
		HttpPost httpPost = null;
    	try {
			URL endpointURL = new URL(endpoint);
	        String endpointProtocol = endpointURL.getProtocol();
	        int endpointPort = endpointURL.getPort();
	
	        HttpClient endpointClient = AppManagerUtil.getHttpClient(endpointPort, endpointProtocol);
	        
	        httpPost = new HttpPost(endpoint);
	        httpPost.setEntity(new UrlEncodedFormEntity(urlParams, "UTF-8"));
	        Builder builder = RequestConfig.custom();
			builder.setConnectTimeout(4000);
	        builder.setSocketTimeout(4000);
	        RequestConfig config = builder.build();
	        httpPost.setConfig(config);
	
	        int statusCode;
	        HttpResponse httpResponse = endpointClient.execute(httpPost);
            statusCode = httpResponse.getStatusLine().getStatusCode();
            
            log.info("http response statusCode:" + statusCode);
	        if (statusCode != HttpStatus.SC_OK) {
	            throw new IOException("failed : HTTP error code : " + statusCode);
	        } else {
	            if (log.isDebugEnabled()) {
	                log.debug("Successfully submitted request. HTTP status : 200");
	            }
	        }
		} catch (Throwable e) {
			String message = endpoint + " APP 게이트웨이 통신 중 에러가 발생하였습니다. ";
        	log.error(message, e);
            handleException(message, e);
        } finally {
        	if(httpPost != null) httpPost.reset();
        }
    }
	
	public static String receive(String endpoint, List<NameValuePair> urlParams) throws AppManagementException {
		String responseStr = null;
		HttpPost httpPost = null;
    	try {
			URL endpointURL = new URL(endpoint);
	        String endpointProtocol = endpointURL.getProtocol();
	        int endpointPort = endpointURL.getPort();
	
	        HttpClient endpointClient = AppManagerUtil.getHttpClient(endpointPort, endpointProtocol);
	
	        httpPost = new HttpPost(endpoint);
	        httpPost.setEntity(new UrlEncodedFormEntity(urlParams, "UTF-8"));
	        Builder builder = RequestConfig.custom();
			builder.setConnectTimeout(4000);
	        builder.setSocketTimeout(4000);
	        RequestConfig config = builder.build();
	        httpPost.setConfig(config);
	
	        int statusCode;
	        HttpResponse httpResponse = endpointClient.execute(httpPost);
            HttpEntity resEntity = httpResponse.getEntity();
            statusCode = httpResponse.getStatusLine().getStatusCode();
            
            log.info("http response statusCode:" + statusCode);
            if (statusCode != HttpStatus.SC_OK) {
                throw new IOException("Error occurred while calling endpoint: HTTP error code : " + statusCode);
            } else {
            	responseStr = EntityUtils.toString(resEntity);
                log.info("http response 결과:" + responseStr);
            	return responseStr;
            }
    	} catch (ParseException e) {
    		String message = endpoint + " APP 게이트웨이 결과 데이터를 파싱하는 중 에러가 발생하였습니다. ";
    		log.error(message, e);
            handleException(message, e);
        } catch (Throwable e) {
        	String message = endpoint + " APP 게이트웨이 통신 중 에러가 발생하였습니다. ";
        	log.error(message, e);
            handleException(message, e);
        } finally {
        	if(httpPost != null) httpPost.reset();
        }	
    	return responseStr;
    }
	
	private static void handleException(String msg, Throwable e) throws AppManagementException {
        throw new AppManagementException(msg, e);
    }
	
	
}
