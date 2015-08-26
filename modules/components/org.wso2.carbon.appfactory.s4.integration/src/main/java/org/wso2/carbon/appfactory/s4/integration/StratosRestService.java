/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.appfactory.s4.integration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.appfactory.common.AppFactoryException;
import org.wso2.carbon.appfactory.common.util.MutualAuthHttpClient;
import org.wso2.carbon.appfactory.common.util.ServerResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Http Client based REST Client for calling Stratos APIs
 */
public class StratosRestService {

	private static final Log log = LogFactory.getLog(StratosRestService.class);

	private String stratosManagerURL;
	private String username;
	private String password;

	private static final String APPLICATIONS_REST_END_POINT = "/api/applications";
	private static final String LIST_DETAILS_OF_SUBSCRIBED_CARTRIDGE = "/stratos/admin/cartridge/info/";
    private static final String SINGLE_TENANT_APPLICATION_POLICY_ID = "application-policy-st";

	public StratosRestService(String stratosManagerURL, String username, String password) {
		this.username = username;
		this.password = password;
		this.stratosManagerURL = stratosManagerURL;
	}

	public static StratosRestService getInstance(String stratosManagerURL, String username, String password){
		return new StratosRestService(stratosManagerURL, username, password);
	}

	public void createApplication(String applicationId, String repoUrl, String repoUsername, String repoPassword,
                                     String cartridgeType, String cartridgeTypePrefix, String deploymentPolicy,
                                     String autoScalingPolicy) throws AppFactoryException {
		String stratosApplicationJson = getStratosApplicationJson(applicationId, repoUrl, repoUsername, repoPassword
				, cartridgeType, cartridgeTypePrefix, deploymentPolicy, autoScalingPolicy);

		ServerResponse response = MutualAuthHttpClient.sendPostRequest(this.stratosManagerURL
		                                                               + this.APPLICATIONS_REST_END_POINT,
		                                                               stratosApplicationJson,username);
		if (response.getStatusCode() == HttpStatus.SC_CREATED) {
			if (log.isDebugEnabled()) {
				log.debug("Stratos application created for appId : " + applicationId);
			}
		}
	}

	public void deployApplication(String applicationId) throws AppFactoryException {
		ServerResponse response = MutualAuthHttpClient.sendPostRequest(stratosManagerURL + APPLICATIONS_REST_END_POINT
		                                                               + "/" + applicationId + "/deploy/"
		                                                               + SINGLE_TENANT_APPLICATION_POLICY_ID, "",username);
		if (response.getStatusCode() == HttpStatus.SC_ACCEPTED) {
			if (log.isDebugEnabled()) {
				log.debug(" Stratos application deployed for appId : " + applicationId);
			}
		}
	}

	public void undeployApplication(String applicationId) throws AppFactoryException {
		ServerResponse response = MutualAuthHttpClient.sendPostRequest(this.stratosManagerURL
		                                                               + this.APPLICATIONS_REST_END_POINT + "/"
		                                                               + applicationId + "/undeploy/", "",username);
		if (response.getStatusCode() == HttpStatus.SC_ACCEPTED) {
			if (log.isDebugEnabled()) {
				log.debug("Stratos undeployment started successfully for appId : " + applicationId);
			}
		}
	}

	public void deleteApplication(String applicationId) throws AppFactoryException {
		ServerResponse response = MutualAuthHttpClient.sendDeleteRequest(this.stratosManagerURL
		                                                                 + this.APPLICATIONS_REST_END_POINT + "/"
		                                                                 + applicationId,username);
		if (response.getStatusCode() == HttpStatus.SC_OK) {
			if (log.isDebugEnabled()) {
				log.debug("Stratos application deleted successfully for appId : " + applicationId);
			}
		}
	}

    public String getApplicationRuntime(String applicationId) throws AppFactoryException{
        ServerResponse response = MutualAuthHttpClient.sendGetRequest(this.stratosManagerURL
                                                                      + this.APPLICATIONS_REST_END_POINT + "/"
                                                                      + applicationId + "/runtime",username);
        if (response.getStatusCode() == HttpStatus.SC_OK) {
            String applicationInstanceJson = response.getResponse();
            if (log.isDebugEnabled()) {
                log.debug("Stratos application runtime json : " + applicationInstanceJson);
            }
            return  applicationInstanceJson;
        }
        return null;
    }

	public boolean isApplicationCreated(String applicationId) throws AppFactoryException {
		ServerResponse response = MutualAuthHttpClient.sendGetRequest(this.stratosManagerURL
		                                                              + this.APPLICATIONS_REST_END_POINT + "/"
		                                                              + applicationId,username);
		if (response.getStatusCode() == HttpStatus.SC_OK) {
			String applicationInfoJson = response.getResponse();
            if (log.isDebugEnabled()) {
                log.debug("Stratos application information : " + applicationInfoJson );
            }
			return true;
		}
		return false;
	}

	public String getSubscribedCartridgeClusterId(String cartridgeAlias) throws AppFactoryException {
		JsonObject catridgeJson = getSubscribedCartridge(cartridgeAlias);
		if (catridgeJson != null && catridgeJson.has("clusterId")) {
			return catridgeJson.get("clusterId").getAsString();
		}
		return null;
	}

	private JsonObject getSubscribedCartridge(String cartridgeAlias) throws AppFactoryException {
		HttpClient httpClient = getNewHttpClient();
		JsonObject catridgeJson = null;
		try {
			String serviceEndPoint = stratosManagerURL + LIST_DETAILS_OF_SUBSCRIBED_CARTRIDGE + cartridgeAlias;
			ServerResponse response = doGet(httpClient, serviceEndPoint);

			if (HttpStatus.SC_OK == response.getStatusCode()) {
				if (log.isDebugEnabled()) {
					log.debug("Successfully retrieved the subscription info");
				}
				JsonParser jsonParser = new JsonParser();
				JsonObject subscriptionInfo = jsonParser.parse(response.getResponse()).getAsJsonObject();
				if (subscriptionInfo != null && subscriptionInfo.isJsonObject()) {
					JsonElement catridge = subscriptionInfo.get("cartridge");
					if (catridge.isJsonObject()) {
						catridgeJson = catridge.getAsJsonObject();
					}
				}
			}
		} catch (Exception e) {
			handleException("Error occurred while getting subscription info", e);
		}
		return catridgeJson;
	}

	private void handleException(String msg, Exception e) throws AppFactoryException {
		log.error(msg, e);
		throw new AppFactoryException(msg, e);
	}

	/**
	 * Since Mutual SSL is used, a dummy password will be sent to stratos side
	 *
	 * @return authorization header string for the requests to Stratos
	 */
	private static String getAuthHeaderValue(String userName, String password) {

		byte[] getUserPasswordInBytes = (userName + ":" + password).getBytes();
		String encodedValue = new String(Base64.encodeBase64(getUserPasswordInBytes));
		return "Basic " + encodedValue;
	}

	public ServerResponse doPost(HttpClient httpClient, String resourcePath, String jsonParamString)
			throws AppFactoryException {

		PostMethod postRequest = new PostMethod(resourcePath);

		StringRequestEntity input = null;
		try {
			input = new StringRequestEntity(jsonParamString,
			                                "application/json", "UTF-8");
		} catch (UnsupportedEncodingException e) {
			handleException("Error occurred while getting POST parameters", e);
		}

		postRequest.setRequestEntity(input);

		String basicAuth = null;
		try {
			basicAuth = getAuthHeaderValue(username, password);
		} catch (Exception e) {
			handleException("Error occurred while getting username:password", e);
		}
		postRequest.addRequestHeader("Authorization", basicAuth);

		int response = 0;
		String responseString = null;
		try {
			response = httpClient.executeMethod(postRequest);
		} catch (IOException e) {
			handleException("Error occurred while executing POST method", e);
		}
		try {
			responseString = postRequest.getResponseBodyAsString();
		} catch (IOException e) {
			handleException("error while getting response as String", e);
		}

		return new ServerResponse(responseString, response);

	}

	public ServerResponse doDelete(HttpClient httpClient, String resourcePath) throws AppFactoryException {

		DeleteMethod postRequest = new DeleteMethod(resourcePath);

		String basicAuth = null;
		try {
			basicAuth = getAuthHeaderValue(username,password);
		} catch (Exception e) {
			handleException("Error occurred while getting username:password", e);
		}
		postRequest.addRequestHeader("Authorization", basicAuth);

		int response = 0;
		String responseString = null;
		try {
			response = httpClient.executeMethod(postRequest);
		} catch (IOException e) {
			handleException("Error occurred while executing POST method", e);
		}
		try {
			responseString = postRequest.getResponseBodyAsString();
		} catch (IOException e) {
			handleException("error while getting response as String", e);
		}

		return new ServerResponse(responseString, response);

	}

	/**
	 * sends a GET request to the specified URL
	 *
	 * @param httpClient  Http client that sends the request
	 * @param resourcePath EPR for the resource
	 * @return
	 * @throws Exception
	 */
	public ServerResponse doGet(HttpClient httpClient, String resourcePath) throws AppFactoryException {

		GetMethod getRequest = new GetMethod(resourcePath);
		String userPass = this.username + ":" + this.password;
		String basicAuth = null;
		try {
			basicAuth = getAuthHeaderValue(username, password);
		} catch (Exception e) {
			handleException("Error occurred while getting username:password", e);
		}
		getRequest.addRequestHeader("Authorization", basicAuth);

		int response = 0;
		String responseString = null;
		try {
			response = httpClient.executeMethod(getRequest);
		} catch (IOException e) {
			handleException("Error occurred while executing GET method", e);
		}
		try {
			responseString = getRequest.getResponseBodyAsString();
		} catch (IOException e) {
			handleException("error while getting response as String", e);
		}

		return new ServerResponse(responseString, response);

	}

	public HttpClient getNewHttpClient() {
		return new HttpClient(new MultiThreadedHttpConnectionManager());
	}

	private String getStratosApplicationJson(String applicationId, String repoUrl, String repoUsername,
	                                            String repoPassword, String cartridgeType, String cartridgeTypePrefix,
	                                            String deploymentPolicy, String autoScalingPolicy){

		JsonObject applicationJson = new JsonObject();
		applicationJson.addProperty("applicationId", applicationId);
		applicationJson.addProperty("alias", "single-tenant");

		JsonObject components = new JsonObject();

		JsonArray cartridges = new JsonArray();
		JsonObject cartridge = new JsonObject();

		cartridge.addProperty("type",cartridgeType);
		cartridge.addProperty("cartridgeMin",1);
		cartridge.addProperty("cartridgeMax",1);

		JsonObject subscribableInfo = new JsonObject();

		if(StringUtils.isNotEmpty(cartridgeTypePrefix)) {
			cartridgeTypePrefix = cartridgeTypePrefix.toLowerCase();
		}else{
			log.error("cartridge prefix cannot be null");
		}

		subscribableInfo.addProperty("alias", cartridgeTypePrefix);
		subscribableInfo.addProperty("deploymentPolicy", deploymentPolicy);
		subscribableInfo.addProperty("autoscalingPolicy", autoScalingPolicy);

		JsonObject artifactRepository = new JsonObject();
		artifactRepository.addProperty("privateRepo",true);
		artifactRepository.addProperty("repoUrl", repoUrl);
		artifactRepository.addProperty("repoUsername", repoUsername);
		artifactRepository.addProperty("repoPassword",repoPassword);

		subscribableInfo.add("artifactRepository", artifactRepository);

		cartridge.add("subscribableInfo",subscribableInfo);

		cartridges.add(cartridge);
		components.add("cartridges",cartridges);
		applicationJson.add("components", components);

		Gson gson = new Gson();

		return gson.toJson(applicationJson);
	}
}