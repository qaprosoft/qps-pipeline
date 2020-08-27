package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.BaseObject
import com.qaprosoft.jenkins.jobdsl.factory.folder.FolderFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.LauncherJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.QTestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.TestRailJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.RegisterRepositoryJobFactory
import com.qaprosoft.jenkins.pipeline.integration.zebrunner.ZebrunnerUpdater
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty
import org.jenkinsci.plugins.matrixauth.inheritance.NonInheritingStrategy
import jenkins.security.ApiTokenProperty

import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

class Organization extends BaseObject {
    private static final String PIPELINE_LIBRARY = "QPS-Pipeline"
    private static final String RUNNER_CLASS = "com.qaprosoft.jenkins.pipeline.runner.maven.TestNG"

    protected ISCM scmClient
    protected ZebrunnerUpdater zebrunnerUpdater

    protected def folderName
    protected def reportingServiceUrl
    protected def reportingAccessToken
    protected def sonarGithubOAuth
    protected def customPipeline


    public Organization(context) {
        super(context)
        scmClient = new GitHub(context)

        zebrunnerUpdater = new ZebrunnerUpdater(context)

        this.folderName = Configuration.get("folderName")

        this.reportingServiceUrl = Configuration.get("reportingServiceUrl")
        this.reportingAccessToken = Configuration.get("reportingAccessToken")

        this.sonarGithubOAuth = Configuration.get("sonarGithubOAuth")
        this.customPipeline = Configuration.get("customPipeline")
    }

    public def register() {
        logger.info("Organization->register")
        setDisplayNameTemplate('#${BUILD_NUMBER}|${folderName}')
        currentBuild.displayName = getDisplayName()
        context.node('master') {
            context.timestamps {
                generateCreds()
                generateCiItems()
                logger.info("securityEnabled: " + Configuration.get("securityEnabled"))
                if (Configuration.get("securityEnabled")?.toBoolean()) {
                    setSecurity()
                }
                clean()
            }
        }
    }

    public def delete() {
        logger.info("Organization->register")
        context.node('master') {
            context.timestamps {
                def folder = Configuration.get("folderName")
                def userName = folder + "-user"
                deleteFolder(folder)
                deleteUser(userName)
                clean()
            }
        }
    }


    public def registerQTestCredentials() {
        logger.info("Organization->registerQTestCredentials")
        context.node('master') {
            def orgFolderName = Configuration.get("folderName")

            // Example: https://<CHANGE_ME>/api/v3/
            def url = Configuration.get("url")
            def token = Configuration.get("token")

            registerQTestCredentials(orgFolderName, url, token)
        }
    }

    public def registerTestRailCredentials() {
        logger.info("Organization->registerTestRailCredentials")
        context.node('master') {
            def orgFolderName = Configuration.get("folderName")

            // Example: https://mytenant.testrail.com?/api/v2/
            def url = Configuration.get("url")
            def username = Configuration.get("username")
            def password = Configuration.get("password")

            registerTestRailCredentials(orgFolderName, url, username, password)
        }
    }

    protected def deleteFolder(folderName) {
        context.stage("Delete folder") {
            def folder = getJenkinsFolderByName(folderName)
            if (!isParamEmpty(folder)) {
                folder.delete()
            }
        }
    }

    protected def deleteUser(userName) {
        context.stage("Delete user") {
            def user = User.getById(userName, false)
            if (!isParamEmpty(user)) {
                // deleteUserGlobalPermissions(userName)
                user.delete()
            }
        }
    }

    protected def generateCiItems() {
        def folder = this.folderName
        context.stage("Register Organization") {
            if (!isParamEmpty(folder)) {
                registerObject("project_folder", new FolderFactory(folder, ""))
            }
            registerObject("launcher_job", new LauncherJobFactory(folder, getPipelineScript(), "launcher", "Custom job launcher"))

            registerObject("register_repository_job", new RegisterRepositoryJobFactory(folder, 'RegisterRepository', ''))

            factoryRunner.run(dslObjects)
        }
    }

    protected def setSecurity() {
        def folder = this.folderName
        logger.info("Organization->setSecurity")
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
            integrationParameters = generateIntegrationParemetersMap(userName, token.tokenValue, folder)
            initialized = true
        } catch (Exception e) {
            logger.error("Something went wrong during secure folder initialization: \n${e}")
        }
        zebrunnerUpdater.sendInitResult(integrationParameters, initialized)
    }

    protected def generateAPIToken(userName) {
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

    protected def createJenkinsUser(userName) {
        def password = UUID.randomUUID().toString()
        return !isParamEmpty(User.getById(userName, false)) ? User.getById(userName, false) : Jenkins.instance.securityRealm.createAccount(userName, password)
    }

    protected def grantUserGlobalPermissions(userName) {
        def authStrategy = Jenkins.instance.getAuthorizationStrategy()
        authStrategy.add(hudson.model.Hudson.READ, userName)
    }

/*    protected def deleteUserGlobalPermissions(userName){
        def authStrategy = Jenkins.instance.getAuthorizationStrategy()
        authStrategy.remove(hudson.model.Hudson.READ, userName)
    }
*/

    protected def grantUserFolderPermissions(folderName, userName) {
        def folder = getJenkinsFolderByName(folderName)
        if (folder == null) {
            logger.error("No folder ${folderName} was detected.")
            return
        }
        def authProperty = folder.properties.find {
            it instanceof AuthorizationMatrixProperty
        }

        if (authProperty == null) {
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

    protected def generateIntegrationParemetersMap(userName, tokenValue, folder) {
        def integrationParameters = [:]
        String jenkinsUrl = Configuration.get(Configuration.Parameter.JOB_URL).split("/job/")[0]
        integrationParameters.JENKINS_URL = jenkinsUrl
        integrationParameters.JENKINS_USER = userName
        integrationParameters.JENKINS_API_TOKEN_OR_PASSWORD = tokenValue
        integrationParameters.JENKINS_FOLDER = folder
        return integrationParameters
    }

    protected String getPipelineScript() {
        return "@Library(\'${PIPELINE_LIBRARY}\')\nimport ${RUNNER_CLASS};\nnew ${RUNNER_CLASS}(this).runJob()"
    }

    protected String getTestRailScript() {
        return "@Library(\'${PIPELINE_LIBRARY}\')\nimport ${RUNNER_CLASS};\nnew ${RUNNER_CLASS}(this).sendTestRailResults()"
    }

    protected String getQTestScript() {
        return "@Library(\'${PIPELINE_LIBRARY}\')\nimport ${RUNNER_CLASS};\nnew ${RUNNER_CLASS}(this).sendQTestResults()"
    }

    protected def generateCreds() {
        logger.debug("INFRA_HOST: " + Configuration.get(Configuration.Parameter.INFRA_HOST))
        logger.debug("selenium: " + "http://demo:demo@\${INFRA_HOST}/selenoid/wd/hub")
        registerHubCredentials(this.folderName, "selenium", "http://demo:demo@\${INFRA_HOST}/selenoid/wd/hub")

        if (!isParamEmpty(this.reportingServiceUrl) && !isParamEmpty(this.reportingAccessToken)) {
            registerReportingCredentials(this.folderName, this.reportingServiceUrl, this.reportingAccessToken)
        }

        if (customPipeline?.toBoolean()) {
            registerCustomPipelineCreds(this.folderName, customPipeline)
        }


    }

    public def registerHubCredentials() {
        context.stage("Register Hub Credentials") {
            def orgFolderName = Configuration.get("folderName")
            def provider = Configuration.get("provider")
            // Example: http://demo.qaprosoft.com/selenoid/wd/hub
            def url = Configuration.get("url")

            registerHubCredentials(orgFolderName, provider, url)
        }
    }

    protected def registerHubCredentials(orgFolderName, provider, url) {
        setDisplayNameTemplate('#${BUILD_NUMBER}|${folderName}|${provider}')
        currentBuild.displayName = getDisplayName()
        if (isParamEmpty(url)) {
            throw new RuntimeException("Required 'url' field is missing!")
        }
        def hubURLCredName = "${provider}_hub"
        if (!isParamEmpty(orgFolderName)) {
            hubURLCredName = "${orgFolderName}" + "-" + hubURLCredName
        }

        if (isParamEmpty(getCredentials(hubURLCredName))) {
            if (updateJenkinsCredentials(hubURLCredName, "${provider} URL", Configuration.Parameter.SELENIUM_URL.getKey(), url)) {
                logger.info("${hubURLCredName} was successfully registered.")
            }
        }
        else {
            logger.info("Skip registration of ${hubURLCredName}")
        }
    }

    public def registerReportingCredentials() {
        context.stage("Register Reporting Credentials") {
            Organization.registerReportingCredentials(this.folderName, this.reportingServiceUrl, this.reportingAccessToken)
        }
    }

    public static void registerReportingCredentials(orgFolderName, reportingServiceUrl, reportingAccessToken) {
        def reportingURLCredentials = Configuration.CREDS_REPORTING_SERVICE_URL
        def reportingTokenCredentials = Configuration.CREDS_REPORTING_ACCESS_TOKEN

        if (!isParamEmpty(orgFolderName)) {
            reportingURLCredentials = orgFolderName + "-" + reportingURLCredentials
            reportingTokenCredentials = orgFolderName + "-" + reportingTokenCredentials
        }

        if (isParamEmpty(reportingServiceUrl)) {
            throw new RuntimeException("Unable to register reporting credentials! Required field 'reportingServiceUrl' is missing!")
        }

        if (isParamEmpty(reportingAccessToken)) {
            throw new RuntimeException("Unable to register reporting credentials! Required field 'reportingAccessToken' is missing!")
        }

        updateJenkinsCredentials(reportingURLCredentials, "Reporting service URL", Configuration.Parameter.REPORTING_SERVICE_URL.getKey(), reportingServiceUrl)
        updateJenkinsCredentials(reportingTokenCredentials, "Reporting access token", Configuration.Parameter.REPORTING_ACCESS_TOKEN.getKey(), reportingAccessToken)
    }

    protected def registerTestRailCredentials(orgFolderName, url, username, password) {
        def testrailURLCredentials = Configuration.CREDS_TESTRAIL_SERVICE_URL
        def testrailUserCredentials = Configuration.CREDS_TESTRAIL

        if (!isParamEmpty(orgFolderName)) {
            testrailURLCredentials = orgFolderName + "-" + testrailURLCredentials
            testrailUserCredentials = orgFolderName + "-" + testrailUserCredentials
        }

        if (isParamEmpty(url)) {
            throw new RuntimeException("Unable to register TestRail credentials! Required field 'url' is missing!")
        }

        if (isParamEmpty(username)) {
            throw new RuntimeException("Unable to register TestRail credentials! Required field 'username' is missing!")
        }

        if (isParamEmpty(password)) {
            throw new RuntimeException("Unable to register TestRail credentials! Required field 'password' is missing!")
        }

        updateJenkinsCredentials(testrailURLCredentials, "TestRail Service API URL", Configuration.Parameter.TESTRAIL_SERVICE_URL.getKey(), url)
        updateJenkinsCredentials(testrailUserCredentials, "TestRaul User credentials", username, password)

        registerObject("testrail_job", new TestRailJobFactory(orgFolderName, getTestRailScript(), Configuration.TESTRAIL_UPDATER_JOBNAME, "Custom job testrail"))

        factoryRunner.run(dslObjects)
    }

    protected def registerQTestCredentials(orgFolderName, url, token) {
        def qtestURLCredentials = Configuration.CREDS_QTEST_SERVICE_URL
        def qtestTokenCredentials = Configuration.CREDS_QTEST_ACCESS_TOKEN

        if (!isParamEmpty(orgFolderName)) {
            qtestURLCredentials = orgFolderName + "-" + qtestURLCredentials
            qtestTokenCredentials = orgFolderName + "-" + qtestTokenCredentials
        }

        if (isParamEmpty(url)) {
            throw new RuntimeException("Unable to register QTest credentials! Required field 'url' is missing!")
        }

        if (isParamEmpty(token)) {
            throw new RuntimeException("Unable to register QTest credentials! Required field 'token' is missing!")
        }

        updateJenkinsCredentials(qtestURLCredentials, "QTest Service API URL", Configuration.Parameter.QTEST_SERVICE_URL.getKey(), url)
        updateJenkinsCredentials(qtestTokenCredentials, "QTest access token", Configuration.Parameter.QTEST_ACCESS_TOKEN.getKey(), token)

        registerObject("qtest_job", new QTestJobFactory(orgFolderName, getQTestScript(), Configuration.QTEST_UPDATER_JOBNAME, "Custom job qtest"))

        factoryRunner.run(dslObjects)
    }

    protected def registerCustomPipelineCreds(orgFolderName, token) {
        def customPipelineCreds = Configuration.CREDS_CUSTOM_PIPELINE

        if (!isParamEmpty(orgFolderName)) {
            customPipeline = orgFolderName + "-" + customPipelineCreds
        }

        updateJenkinsCredentials(customPipeline, "", Configuration.CREDS_CUSTOM_PIPELINE + "-token", token)
    }
}
