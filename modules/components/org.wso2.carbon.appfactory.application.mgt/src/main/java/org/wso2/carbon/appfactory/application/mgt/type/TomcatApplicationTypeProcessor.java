/*
 * Copyright 2014 WSO2, Inc. (http://wso2.com)
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specifnStic language governing permissions and
 *      limitations under the License.
 */

package org.wso2.carbon.appfactory.application.mgt.type;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.common.beans.topology.ApplicationInstanceBean;
import org.wso2.carbon.appfactory.application.mgt.util.Util;
import org.wso2.carbon.appfactory.common.AppFactoryConfiguration;
import org.wso2.carbon.appfactory.common.AppFactoryConstants;
import org.wso2.carbon.appfactory.common.AppFactoryException;
import org.wso2.carbon.appfactory.common.util.AppFactoryUtil;
import org.wso2.carbon.appfactory.s4.integration.StratosRestService;
import org.wso2.carbon.appfactory.s4.integration.utils.CloudUtils;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.user.api.UserStoreException;

public class TomcatApplicationTypeProcessor extends MavenBasedApplicationTypeProcessor{
    
    private static final Log log = LogFactory.getLog(TomcatApplicationTypeProcessor.class);

    public String getDeployedURL(String tenantDomain, String applicationID, String applicationVersion, String stage)
                                 throws AppFactoryException {

        AppFactoryConfiguration appfactoryConfiguration = AppFactoryUtil.getAppfactoryConfiguration();

        String stratosServerURL = appfactoryConfiguration.getFirstProperty(AppFactoryConstants.DEPLOYMENT_STAGES
                                                                           + AppFactoryConstants.DOT_SEPERATOR + stage + AppFactoryConstants.DOT_SEPERATOR
                                                                           + AppFactoryConstants.TENANT_MGT_URL);

        int tenantId ;
        try {
            tenantId =  Util.getRealmService().getTenantManager().getTenantId(tenantDomain);
        } catch (UserStoreException e) {
            String errorMsg = "Unable to get tenant ID for tenant domain " + tenantDomain;
            log.error(errorMsg, e);
            throw new AppFactoryException(errorMsg, e);
        }

        String tenantUsername = CarbonContext.getThreadLocalCarbonContext().getUsername();
        StratosRestService stratosRestService = new StratosRestService(stratosServerURL,tenantUsername,"nopassword");

        ApplicationInstanceBean applicationInstanceBean =  stratosRestService.getApplicationRuntime(
                   CloudUtils.generateUniqueStratosApplicationId(tenantId, applicationID, applicationVersion));

        int port = 80;
        if(applicationInstanceBean != null)
        {
            if(applicationInstanceBean.getStatus().equalsIgnoreCase(AppFactoryConstants.STRATOS_RUNTIME_STATUS_ACTIVE)){
                port = (applicationInstanceBean.getClusterInstances()).get(0).getMember().get(0).getPorts().get(0).getPort();
            }else{
                return null;
            }
        }else{
            return null;
        }
//        if(!stratosRestService.isApplicationDeployed(CloudUtils.generateUniqueStratosApplicationId(tenantId,applicationID,applicationVersion))){
//           return null;
//        }

        String url = (String) this.properties.getProperty(LAUNCH_URL_PATTERN);

        String artifactTrunkVersionName = AppFactoryUtil.getAppfactoryConfiguration().
                                          getFirstProperty(AppFactoryConstants.TRUNK_WEBAPP_ARTIFACT_VERSION_NAME);
        String sourceTrunkVersionName = AppFactoryUtil.getAppfactoryConfiguration().
                                        getFirstProperty(AppFactoryConstants.TRUNK_WEBAPP_SOURCE_VERSION_NAME);
        if(applicationVersion.equalsIgnoreCase(sourceTrunkVersionName)) {
            applicationVersion = artifactTrunkVersionName;
        }

        String urlStageValue = "";

        try {
            urlStageValue = (String) this.properties.getProperty(stage + PARAM_APP_STAGE_NAME_SUFFIX);
        } catch (Exception e){
            log.error("Error while getting the url stage value fo application:" + applicationID, e);
        }

        url = url.replace(PARAM_APP_ID, applicationID).replace(PARAM_APP_VERSION, applicationVersion).replace("{port}","" + port);

        return url;
    }
}
