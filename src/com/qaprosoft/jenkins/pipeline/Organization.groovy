package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.jobdsl.factory.folder.FolderFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.LauncherJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.RegisterRepositoryJobFactory
import com.qaprosoft.jenkins.pipeline.integration.zebrunner.ZebrunnerUpdater
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty
import groovy.json.JsonOutput
import org.jenkinsci.plugins.matrixauth.inheritance.NonInheritingStrategy
import jenkins.security.ApiTokenProperty

import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

class Organization {

    protected def context
    protected ISCM scmClient
    protected Logger logger
    protected ZebrunnerUpdater zebrunnerUpdater
    protected Configuration configuration = new Configuration(context)
    protected Map dslObjects = new LinkedHashMap()
    protected def pipelineLibrary
    protected def runnerClass
    protected final def FACTORY_TARGET = "qps-pipeline/src/com/qaprosoft/jenkins/Factory.groovy"
    protected final def EXTRA_CLASSPATH = "qps-pipeline/src"


    public Organization(context) {
        this.context = context
        scmClient = new GitHub(context)
        logger = new Logger(context)
        zebrunnerUpdater = new ZebrunnerUpdater(context)
        pipelineLibrary = Configuration.get("pipelineLibrary")
        runnerClass =  Configuration.get("runnerClass")
    }

    def register() {
        logger.info("Organization->register")
        context.node('master') {
            context.timestamps {
                def folder = Configuration.get("tenancyName")
                def launcherJobName = folder + "/launcher"
                prepare()
                generateCiItems(folder)
                setSecurity(folder, launcherJobName)
//                generateLauncher(folder +'/RegisterRepository')
                clean()
            }
        }
    }

    def delete() {
        logger.info("Organization->register")
        context.node('master') {
            context.timestamps {
                def folder = Configuration.get("tenancyName")
                def userName = folder + "-user"
                prepare()
                deleteFolder(folder)
                deleteUser(userName)
                clean()
            }
        }
    }

    protected def deleteFolder(folderName) {
        context.stage("Delete folder") {
            def folder = getJenkinsFolderByName(folderName)
            if (!isParamEmpty(folder)){
                folder.delete()
            }
        }
    }

    protected def deleteUser(userName) {
        context.stage("Delete user") {
            def user = User.getById(userName, false)
            if (!isParamEmpty(user)){
                user.delete()
            }
        }
    }

    protected def generateCiItems(folder) {
        context.stage("Register Organization") {
            registerObject("project_folder", new FolderFactory(folder, ""))
            registerObject("launcher_job", new LauncherJobFactory(folder, getPipelineScript(), "launcher", "Custom job launcher"))
            registerObject("register_repository_job", new RegisterRepositoryJobFactory(folder, 'RegisterRepository', '', pipelineLibrary, runnerClass))
            context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)
            context.jobDsl additionalClasspath: EXTRA_CLASSPATH,
                    sandbox: true,
                    removedConfigFilesAction: 'IGNORE',
                    removedJobAction: 'IGNORE',
                    removedViewAction: 'IGNORE',
                    targets: FACTORY_TARGET,
                    ignoreExisting: false
        }
    }

    protected void registerObject(name, object) {
        dslObjects.put(name, object)
    }

    protected def setSecurity(folder, launcherJobName){
        def userName = folder + "-user"
        boolean initialized = false
        def integrationParameters = [:]
        try {
            createJenkinsUser(userName)
            grantUserGlobalPermissions(userName)
            grantUserFolderPermissions(folder, userName)
            def token = generateAPIToken(userName)
            if (token == null) {
                throw new RuntimeException("Token generation failed or token for user ${userName} is already exists")
            }
            integrationParameters = generateIntegrationParemetersMap(userName, token.tokenValue, launcherJobName)
            initialized = true
        } catch (Exception e) {
            logger.error("Something went wrong during secure folder initialization: \n${e}")
        }
        zebrunnerUpdater.sendInitResult(integrationParameters, initialized)
    }

    protected def generateAPIToken(userName){
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
        if (folder == null){
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

    protected def generateIntegrationParemetersMap(userName, tokenValue, launcherJobName){
        def integrationParameters = [:]
        String jenkinsUrl = Configuration.get(Configuration.Parameter.JOB_URL).split("/job/")[0]
        integrationParameters.JENKINS_URL = jenkinsUrl
        integrationParameters.JENKINS_USER = userName
        integrationParameters.JENKINS_API_TOKEN_OR_PASSWORD = tokenValue
        integrationParameters.JENKINS_LAUNCHER_JOB_NAME = launcherJobName
        return integrationParameters
    }

    public def registerZafiraCredentials(){
        context.stage("Register Zafira Credentials") {
            def orgFolderName = Configuration.get("tenancyName")
            def zafiraServiceURL = Configuration.get("zafiraServiceURL")
            def zafiraRefreshToken = Configuration.get("zafiraRefreshToken")
            if (isParamEmpty(orgFolderName) || isParamEmpty(zafiraServiceURL) || isParamEmpty(zafiraRefreshToken)){
                throw new RuntimeException("Required fields are missing")
            }
            def zafiraURLCredentials = orgFolderName + "-zafira_service_url"
            def zafiraTokenCredentials = orgFolderName + "-zafira_access_token"

            if (updateJenkinsCredentials(zafiraURLCredentials, orgFolderName + " Zafira service URL", Configuration.Parameter.ZAFIRA_SERVICE_URL.getKey(), zafiraServiceURL))
                logger.info(orgFolderName + " zafira service url was successfully registered.")
            if (updateJenkinsCredentials(zafiraTokenCredentials, orgFolderName + " Zafira access token", Configuration.Parameter.ZAFIRA_ACCESS_TOKEN.getKey(), zafiraRefreshToken))
                logger.info(orgFolderName + " zafira access token was successfully registered.")
        }
    }

    protected String getPipelineScript() {
        if ("QPS-Pipeline".equals(pipelineLibrary)) {
            return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
        } else {
            return "@Library(\'QPS-Pipeline\')\n@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
        }
    }

    protected void prepare() {
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
