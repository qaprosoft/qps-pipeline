package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.integration.zafira.ZafiraUpdater
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty
import org.jenkinsci.plugins.matrixauth.inheritance.NonInheritingStrategy
import jenkins.security.ApiTokenProperty

import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

class Security {

    protected def context
    protected ISCM scmClient
    protected Logger logger
    protected ZafiraUpdater zafiraUpdater
    protected Configuration configuration

    public Security(context) {
        this.context = context
        scmClient = new GitHub(context)
        logger = new Logger(context)
        zafiraUpdater = new ZafiraUpdater(context)
        configuration = new Configuration(context)
    }

    def register() {
        logger.info("Security->enable")
        context.node('master') {
            context.timestamps {
                prepare()
                setSecurity(Configuration.get("organization"))
                clean()
            }
        }
    }

    protected def setSecurity(organization){
        def userName = organization + "-user"
        createJenkinsUser(userName)
        grantUserGlobalPermissions(userName)
        grantUserFolderPermissions(organization, userName)
        def token = getAPIToken(userName)
        if(token != null){
            registerTokenInZafira(userName, token.tokenValue, organization)
        }
    }

    protected def getAPIToken(userName){
        def token = null
        def tokenName = userName + '_token'
        def user = User.getById(userName, false)
        def apiTokenProperty = user.getAllProperties().find {
            it instanceof ApiTokenProperty
        }
        def existingToken = apiTokenProperty?.getTokenList()?.find {
            tokenName.equals(it.name)
        }
        if (isParamEmpty(existingToken)) {
            token = Jenkins.instance.getDescriptorByType(ApiTokenProperty.DescriptorImpl.class).doGenerateNewToken(user, tokenName).jsonObject.data
        }
        return token
    }

    protected def createJenkinsUser(userName){
        def password = UUID.randomUUID().toString()
        return !isParamEmpty(User.getById(userName, false))?User.getById(userName, false):Jenkins.instance.securityRealm.createAccount(userName, password)
    }

    protected def grantUserGlobalPermissions(userName){
        def authStrategy = Jenkins.instance.getAuthorizationStrategy()
        authStrategy.add(hudson.model.Hudson.READ, userName)
    }

    protected def grantUserFolderPermissions(folderName, userName) {
        def folder = getJenkinsFolderByName(folderName)
        if(folder == null){
            logger.error("No folder ${folderName} was detected.")
            return
        }
        def authProperty = folder.properties.find {
            it instanceof AuthorizationMatrixProperty
        }
        if (authProperty == null){
            authProperty = new AuthorizationMatrixProperty()
            folder.properties.add(authProperty)
        }
        authProperty.setInheritanceStrategy(new NonInheritingStrategy())
        def permissionsArray = [com.cloudbees.plugins.credentials.CredentialsProvider.CREATE,
                                com.cloudbees.plugins.credentials.CredentialsProvider.DELETE,
                                com.cloudbees.plugins.credentials.CredentialsProvider.MANAGE_DOMAINS,
                                com.cloudbees.plugins.credentials.CredentialsProvider.UPDATE,
                                com.cloudbees.plugins.credentials.CredentialsProvider.VIEW,
                                com.synopsys.arc.jenkins.plugins.ownership.OwnershipPlugin.MANAGE_ITEMS_OWNERSHIP,
                                hudson.model.Item.BUILD,
                                hudson.model.Item.CANCEL,
                                hudson.model.Item.CONFIGURE,
                                hudson.model.Item.CREATE,
                                hudson.model.Item.DELETE,
                                hudson.model.Item.DISCOVER,
                                hudson.model.Item.EXTENDED_READ,
                                hudson.model.Item.READ,
                                hudson.model.Item.WORKSPACE,
                                com.cloudbees.hudson.plugins.folder.relocate.RelocationAction.RELOCATE,
                                hudson.model.Run.DELETE,
                                hudson.model.Run.UPDATE,
                                org.jenkinsci.plugins.workflow.cps.replay.ReplayAction.REPLAY,
                                hudson.model.View.CONFIGURE,
                                hudson.model.View.CREATE,
                                hudson.model.View.DELETE,
                                hudson.model.View.READ,
                                hudson.scm.SCM.TAG]
        permissionsArray.each {
            authProperty.add(it, userName)
        }
        folder.save()
    }

    protected def registerTokenInZafira(userName, tokenValue, organization){
        zafiraUpdater.registerTokenInZafira(userName, tokenValue, organization)
    }

    protected void prepare() {
        scmClient.clone()
        String QPS_PIPELINE_GIT_URL = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_URL)
        String QPS_PIPELINE_GIT_BRANCH = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_BRANCH)
        scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")
    }

    protected clean() {
        context.stage('Wipe out Workspace') {
            context.deleteDir()
        }
    }
}
