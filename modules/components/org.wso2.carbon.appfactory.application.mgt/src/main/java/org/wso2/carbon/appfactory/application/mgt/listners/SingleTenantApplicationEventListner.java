package org.wso2.carbon.appfactory.application.mgt.listners;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.appfactory.application.mgt.util.Util;
import org.wso2.carbon.appfactory.common.AppFactoryConfiguration;
import org.wso2.carbon.appfactory.common.AppFactoryConstants;
import org.wso2.carbon.appfactory.common.AppFactoryException;
import org.wso2.carbon.appfactory.common.beans.RuntimeBean;
import org.wso2.carbon.appfactory.common.util.AppFactoryUtil;
import org.wso2.carbon.appfactory.core.ApplicationEventsHandler;
import org.wso2.carbon.appfactory.core.apptype.ApplicationTypeBean;
import org.wso2.carbon.appfactory.core.apptype.ApplicationTypeManager;
import org.wso2.carbon.appfactory.core.dao.JDBCAppVersionDAO;
import org.wso2.carbon.appfactory.core.dto.UserInfo;
import org.wso2.carbon.appfactory.core.dto.Version;
import org.wso2.carbon.appfactory.core.runtime.RuntimeManager;
import org.wso2.carbon.appfactory.s4.integration.StratosRestService;
import org.wso2.carbon.appfactory.s4.integration.utils.CloudUtils;
import org.wso2.carbon.user.api.UserStoreException;


public class SingleTenantApplicationEventListner extends ApplicationEventsHandler {
    private static Log log = LogFactory.getLog(SingleTenantApplicationEventListner.class);
    private final String ENVIRONMENT = "ApplicationDeployment.DeploymentStage";


    public SingleTenantApplicationEventListner(String identifier, int priority) {
        super(identifier, priority);
    }

    @Override
    public void onCreation(org.wso2.carbon.appfactory.core.dto.Application application, String userName,
                           String tenantDomain, boolean isUploadableAppType) throws AppFactoryException {

    }

    @Override
    /**
     * Undeploy and delete the stratos applications created for this appfactory app
     */
    public void onDeletion(org.wso2.carbon.appfactory.core.dto.Application application, String userName,
                           String tenantDomain) throws AppFactoryException {
        ApplicationTypeBean applicationTypeBean = ApplicationTypeManager.getInstance()
                                                                        .getApplicationTypeBean(application.getType());
        if (applicationTypeBean == null) {
            throw new AppFactoryException(
                    "Application Type details cannot be found for Artifact Type : " + application.getType()
                    + ", application id" + application.getId() + " for tenant domain: " + tenantDomain);
        }

        String runtimeNameForAppType = applicationTypeBean.getRuntimes()[0];
        RuntimeBean runtimeBean = RuntimeManager.getInstance().getRuntimeBean(runtimeNameForAppType);

        if (runtimeBean == null) {
            throw new AppFactoryException(
                    "Runtime details cannot be found for Artifact Type : " + application.getType() + ", application id"+
                    application.getId() + " for tenant domain: " + tenantDomain);
        }
        
        JDBCAppVersionDAO appVersionDAO = JDBCAppVersionDAO.getInstance();
        String[] versions = appVersionDAO.getAllVersionNamesOfApplication(application.getId());

        AppFactoryConfiguration appfactoryConfiguration = AppFactoryUtil.getAppfactoryConfiguration();

        String stratosServerURL = appfactoryConfiguration.getFirstProperty(
                ENVIRONMENT + AppFactoryConstants.DOT_SEPERATOR + "Development" + AppFactoryConstants.DOT_SEPERATOR +
                AppFactoryConstants.TENANT_MGT_URL);

        String  tenantUsername = application.getOwner();
        String stratosApplicationId;
        StratosRestService restService = new StratosRestService(stratosServerURL, tenantUsername, "nopassword");
        
        int tenantId = -1;
        try {
            tenantId = Util.getRealmService().getTenantManager().getTenantId(tenantDomain);
        } catch (UserStoreException e) {
            throw new AppFactoryException("Error while getting tenantId for domain " + tenantDomain  , e);
        }
        
        for (String version : versions) {
            stratosApplicationId = CloudUtils.generateUniqueStratosApplicationId(tenantId, application.getId(), version);
            restService.undeployApplication(stratosApplicationId);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            restService.deleteApplication(stratosApplicationId);
        }      
    }

    @Override
    public void onUserAddition(org.wso2.carbon.appfactory.core.dto.Application application, UserInfo user,
                               String tenantDomain) throws AppFactoryException {

    }

    @Override
    public void onUserDeletion(org.wso2.carbon.appfactory.core.dto.Application application, UserInfo user,
                               String tenantDomain) throws AppFactoryException {

    }

    @Override
    public void onUserUpdate(org.wso2.carbon.appfactory.core.dto.Application application, UserInfo user,
                             String tenantDomain) throws AppFactoryException {

    }

    @Override
    public void onRevoke(org.wso2.carbon.appfactory.core.dto.Application application, String tenantDomain)
            throws AppFactoryException {

    }

    @Override
    public void onVersionCreation(org.wso2.carbon.appfactory.core.dto.Application application, Version source,
                                  Version target, String tenantDomain,
                                  String userName) throws AppFactoryException {
    }

    @Override
    public void onFork(org.wso2.carbon.appfactory.core.dto.Application application, String userName,
                       String tenantDomain, String version,
                       String[] forkedUsers) throws AppFactoryException {

    }

    @Override
    public void onLifeCycleStageChange(org.wso2.carbon.appfactory.core.dto.Application application, Version version,
                                       String previosStage, String nextStage,
                                       String tenantDomain) throws AppFactoryException {

    }

    @Override
    public boolean hasExecuted(org.wso2.carbon.appfactory.core.dto.Application application, String userName,
                               String tenantDomain)
            throws AppFactoryException {
        return false;
    }

}
