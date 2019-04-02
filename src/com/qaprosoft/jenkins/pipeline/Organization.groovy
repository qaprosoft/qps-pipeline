package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.integration.zafira.ZafiraUpdater
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty
import org.jenkinsci.plugins.matrixauth.inheritance.NonInheritingStrategy
import jp.ikedam.jenkins.plugins.extensible_choice_parameter.ExtensibleChoiceParameterDefinition
import jenkins.security.ApiTokenProperty
import javaposse.jobdsl.plugin.actions.GeneratedJobsBuildAction
import com.wangyin.parameter.WHideParameterDefinition
import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

class Organization {

    def context
    protected Repository repository
    protected ISCM scmClient
    protected Logger logger
    protected ZafiraUpdater zafiraUpdater
    protected def onlyUpdated = false
    protected def currentBuild
    protected def repo
    protected def organization
    protected def securityEnabled
    protected Configuration configuration = new Configuration(context)

    public Organization(context) {
        this.context = context
        repository = new Repository(context)
        scmClient = new GitHub(context)
        logger = new Logger(context)
        zafiraUpdater = new ZafiraUpdater(context)
        repo = Configuration.get("repo")
        organization = Configuration.get("organization")
        securityEnabled = Configuration.get("security_enabled")?.toBoolean()
        currentBuild = context.currentBuild
    }

    def register() {
        logger.info("Organization->register")
        context.node('master') {
            context.timestamps {
                prepare()
                repository.register()
                createLaunchers(getLatestOnPushBuild())
                createSystemLaunchers()
                if(securityEnabled){
                    setSecurity()
                }
                clean()
            }
        }
    }

    protected def getLatestOnPushBuild(){
        def jobName = "${organization}/${repo}/onPush-${repo}"
        def job = Jenkins.instance.getItemByFullName(jobName)
        return job.getBuilds().get(0)
    }

    protected def setSecurity(){
        def userName = organization + "-user"
        createJenkinsUser(userName)
        grantUserBaseGlobalPermissions(userName)
        setUserFolderPermissions(organization, userName)
        def token = getAPIToken(userName)
        if(token != null){
            registerTokenInZafira(userName, token.tokenValue)
        }
    }

    protected def createLaunchers(build){
        build.getAction(GeneratedJobsBuildAction).modifiedObjects.each { job ->
            generateLauncher(job)
        }
    }

    protected def getParametersMap(jobName) {
        def job = Jenkins.instance.getItemByFullName(jobName)
        def parameterDefinitions = job.getProperty('hudson.model.ParametersDefinitionProperty').parameterDefinitions
        Map parameters = [:]
        parameterDefinitions.each { parameterDefinition ->
            def value
            if(parameterDefinition instanceof ExtensibleChoiceParameterDefinition){
                value = parameterDefinition.choiceListProvider.getDefaultChoice()
            } else if (parameterDefinition instanceof ChoiceParameterDefinition) {
                value = parameterDefinition.choices[0]
            }  else {
                value = parameterDefinition.defaultValue
            }
            if(!(parameterDefinition instanceof WHideParameterDefinition) && !parameterDefinition.name.equals("ci_run_id")) {
                logger.info(parameterDefinition.name)
                parameters.put(parameterDefinition.name, !isParamEmpty(value)?value:'')
            }
        }
        return parameters
    }

    protected def createSystemLaunchers(){
        context.currentBuild.rawBuild.getAction(GeneratedJobsBuildAction).modifiedObjects.each { job ->
            if(job.jobName.contains("onPush") || job.jobName.contains("Launcher")){
                generateLauncher(job)
            }
        }
    }

    protected def generateLauncher(job){
        def jobUrl = getJobUrl(job)
        def parameters = getParametersMap(job.jobName)
        zafiraUpdater.createLauncher(parameters, jobUrl, repo)
    }

    protected def getJobUrl(job){
        String separator = "/job/"
        String jenkinsUrl = Configuration.get(Configuration.Parameter.JOB_URL).split(separator)[0]
        job.jobName.split("/").each {
            jenkinsUrl += separator + it
        }
        return jenkinsUrl
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

    protected def grantUserBaseGlobalPermissions(userName){
        def authStrategy = Jenkins.instance.getAuthorizationStrategy()
        authStrategy.add(hudson.model.Hudson.READ, userName)
    }

    protected def setUserFolderPermissions(folderName, userName) {
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

    protected def registerTokenInZafira(userName, tokenValue){
        zafiraUpdater.registerTokenInZafira(userName, tokenValue)
    }

    protected void prepare() {
        scmClient.clone(!onlyUpdated)
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
