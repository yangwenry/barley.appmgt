package barley.appmgt.impl.utils;

import java.io.File;

import org.apache.axis2.AxisFault;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.transport.http.HTTPConstants;
import org.wso2.carbon.um.ws.api.stub.RemoteUserStoreManagerServiceStub;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.AppManagerConfiguration;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.core.utils.BarleyUtils;

/**
 * RemoteUserStroeManager Admin service client.
 * 
 */
public class RemoteUserManagerClient {
	private static int TIMEOUT_IN_MILLIS = 15 * 60 * 1000;
	private RemoteUserStoreManagerServiceStub userStoreManagerStub;

	public RemoteUserManagerClient(String cookie) throws AppManagementException {

		AppManagerConfiguration config = ServiceReferenceHolder.getInstance()
		                                                       .getAPIManagerConfigurationService()
		                                                       .getAPIManagerConfiguration();
		String serviceURL = config.getFirstProperty(AppMConstants.AUTH_MANAGER_URL);
		String username = config.getFirstProperty(AppMConstants.AUTH_MANAGER_USERNAME);
		String password = config.getFirstProperty(AppMConstants.AUTH_MANAGER_PASSWORD);
		if (serviceURL == null || username == null || password == null) {
			throw new AppManagementException("Required connection details for authentication");
		}
		
		try {

			// (수정)
//			String clientRepo = BarleyUtils.getCarbonHome() + File.separator + "repository" +
//                    File.separator + "deployment" + File.separator + "client";
//			String clientAxisConf = BarleyUtils.getCarbonHome() + File.separator + "repository" +
//                    File.separator + "conf" + File.separator + "axis2"+ File.separator +"axis2_client.xml";
			String clientRepo = AppManagerUtil.getAppManagerHome() + File.separator + "repository" +
                    File.separator + "deployment" + File.separator + "client";
			String clientAxisConf = AppManagerUtil.getAppManagerHome() + File.separator + "repository" +
                    File.separator + "conf" + File.separator + "axis2"+ File.separator +"axis2_client.xml";
			
			ConfigurationContext configContext =   ConfigurationContextFactory. createConfigurationContextFromFileSystem(clientRepo,clientAxisConf);
			userStoreManagerStub =  new RemoteUserStoreManagerServiceStub(configContext, serviceURL +
			                                                                   "RemoteUserStoreManagerService");
			ServiceClient svcClient = userStoreManagerStub._getServiceClient();
			BarleyUtils.setBasicAccessSecurityHeaders(username, password, true,svcClient);
			Options options = svcClient.getOptions();
			options.setTimeOutInMilliSeconds(TIMEOUT_IN_MILLIS);
			options.setProperty(HTTPConstants.SO_TIMEOUT, TIMEOUT_IN_MILLIS);
			options.setProperty(HTTPConstants.CONNECTION_TIMEOUT, TIMEOUT_IN_MILLIS);
			options.setCallTransportCleanup(true);
			options.setManageSession(true);		
			options.setProperty(HTTPConstants.COOKIE_STRING, cookie);	
		
		} catch (AxisFault axisFault) {
			throw new AppManagementException(
			                                 "Error while initializing the remote user store manager stub",
			                                 axisFault);
		}
	}

	/**
	 * Return userlist based on a claim
	 * 
	 * @param claim
	 * @param claimValue
	 * @return
	 * @throws org.wso2.carbon.appmgt.api.AppManagementException
	 */
	public String[] getUserList(String claim, String claimValue) throws AppManagementException {
		String[] user ;
		try {
			user = userStoreManagerStub.getUserList(claim, claimValue, null);
			return user;
		} catch (Exception e) {
			throw new AppManagementException("Error when retriving userlist", e);
		}
	
	}
}
