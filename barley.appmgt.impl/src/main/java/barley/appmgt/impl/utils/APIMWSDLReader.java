/*
*  Copyright (c) 2005-2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package barley.appmgt.impl.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.Service;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.wsdl.xml.WSDLWriter;

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ibm.wsdl.extensions.http.HTTPAddressImpl;
import com.ibm.wsdl.extensions.soap.SOAPAddressImpl;
import com.ibm.wsdl.extensions.soap12.SOAP12AddressImpl;

import barley.appmgt.api.AppManagementException;

/**
 * This class is used to read the WSDL file using WSDL4J library.
 * 
 */

public class APIMWSDLReader {	
	
	private static WSDLFactory wsdlFactoryInstance;
	
	private String baseURI; //WSDL Original URL
	
	private static final String JAVAX_WSDL_VERBOSE_MODE = "javax.wsdl.verbose";

	private static final Log log = LogFactory.getLog(APIMWSDLReader.class);
	
	public APIMWSDLReader(String baseURI) {
		this.baseURI = baseURI;
	}

	private static WSDLFactory getWsdlFactoryInstance() throws WSDLException {
		if (null == wsdlFactoryInstance) {
			wsdlFactoryInstance = WSDLFactory.newInstance();
		}
		return wsdlFactoryInstance;
	}

	/**
	 * Read the wsdl and clean the actual service endpoint instead of that set
	 * the gateway endpoint.
	 * 
	 * @return {@link OMElement} - the OMElemnt of the new WSDL content
	 * @throws org.wso2.carbon.appmgt.api.AppManagementException
	 * 
	 */
	
	public OMElement readAndCleanWsdl() throws AppManagementException {

		try {
			Definition wsdlDefinition = readWSDLFile();

			setServiceDefinition(wsdlDefinition);

			WSDLWriter writer = getWsdlFactoryInstance().newWSDLWriter();

			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

			writer.writeWSDL(wsdlDefinition, byteArrayOutputStream);

			ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream( byteArrayOutputStream.toByteArray());

			OMElement wsdlElement = AppManagerUtil.buildOMElement(byteArrayInputStream);

			return wsdlElement;

		} catch (Exception e) {
			String msg = " Error occurs when change the addres URL of the WSDL";
			log.error(msg);
			throw new AppManagementException(msg, e);
		}

	}

	/**
	 * Create the WSDL definition <javax.wsdl.Definition> from the baseURI of
	 * the WSDL
	 * 
	 * @return {@link Definition} - WSDL4j definition constructed form the wsdl
	 *         original baseuri
	 * @throws org.wso2.carbon.appmgt.api.AppManagementException
	 * @throws WSDLException
	 */
	
	private Definition readWSDLFile() throws AppManagementException, WSDLException {
		WSDLReader reader = getWsdlFactoryInstance().newWSDLReader();
		// switch off the verbose mode
		reader.setFeature(JAVAX_WSDL_VERBOSE_MODE, false);
		reader.setFeature("javax.wsdl.importDocuments", false);

		Definition wsdlDefinition;
		if (log.isDebugEnabled()) {
			log.debug("Reading  the WSDL. Base uri is " + baseURI);
		}
		wsdlDefinition = reader.readWSDL(baseURI);
		return wsdlDefinition;

	}

	/**
	 * Clear the actual service Endpoint and use Gateway Endpoint instead of the
	 * actual Endpoint.
	 * 
	 * @param definition
	 *            - {@link Definition} - WSDL4j wsdl definition
	 * @throws org.wso2.carbon.appmgt.api.AppManagementException
	 */
	
	private void setServiceDefinition(Definition definition) throws AppManagementException {

		Map serviceMap = definition.getAllServices();
		Iterator serviceItr = serviceMap.entrySet().iterator();
		URL addressURI = null;
		try {
			while (serviceItr.hasNext()) {
				Map.Entry svcEntry = (Map.Entry) serviceItr.next();
				Service svc = (Service) svcEntry.getValue();
				Map portMap = svc.getPorts();
				Iterator portItr = portMap.entrySet().iterator();
				while (portItr.hasNext()) {
					Map.Entry portEntry = (Map.Entry) portItr.next();
					Port port = (Port) portEntry.getValue();

					List<ExtensibilityElement> extensibilityElementList = port.getExtensibilityElements();
					for (int i = 0; i < extensibilityElementList.size(); i++) {
						
						ExtensibilityElement extensibilityElement = (ExtensibilityElement) port.getExtensibilityElements()
						                                                                       .get(i);

						addressURI = new URL(getAddressUrl(extensibilityElement));
						if (addressURI == null) {
							break;
						} else {
							setAddressUrl(extensibilityElement);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("Error occured while getting the wsdl address location", e);
			throw new AppManagementException(e);
		}

	}


	/**
	 * Get the addressURl from the Extensibility element
	 * @param exElement - {@link ExtensibilityElement}
	 * @return {@link String}
	 * @throws org.wso2.carbon.appmgt.api.AppManagementException
	 */

	private String getAddressUrl(ExtensibilityElement exElement) throws AppManagementException {

		if (exElement instanceof SOAP12AddressImpl) {
			return ((SOAP12AddressImpl) exElement).getLocationURI();
		} else if (exElement instanceof SOAPAddressImpl) {
			return ((SOAPAddressImpl) exElement).getLocationURI();
		} else if (exElement instanceof HTTPAddressImpl) {
			return ((HTTPAddressImpl) exElement).getLocationURI();
		} else {
			String msg = "Unsupported WSDL errors!";
			log.error(msg);
			throw new AppManagementException(msg);
		}
	}

	/**
	 * Get the addressURl from the Extensibility element
	 * @param exElement - {@link ExtensibilityElement}
	 * @return {@link String}
	 * @throws org.wso2.carbon.appmgt.api.AppManagementException
	 */

	private void setAddressUrl(ExtensibilityElement exElement) throws AppManagementException {

		if (exElement instanceof SOAP12AddressImpl) {
			((SOAP12AddressImpl) exElement).setLocationURI(AppManagerUtil.getGatewayendpoint());
		} else if (exElement instanceof SOAPAddressImpl) {
			 ((SOAPAddressImpl) exElement).setLocationURI(AppManagerUtil.getGatewayendpoint());
		} else if (exElement instanceof HTTPAddressImpl) {
			 ((HTTPAddressImpl) exElement).setLocationURI(AppManagerUtil.getGatewayendpoint());
		} else {
			String msg = "Unsupported WSDL errors!";
			log.error(msg);
			throw new AppManagementException(msg);
		}
	}
	
}
