package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.integration.zafira.ZafiraUpdater
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty
import org.jenkinsci.plugins.matrixauth.inheritance.NonInheritingStrategy
import jp.ikedam.jenkins.plugins.extensible_choice_parameter.ExtensibleChoiceParameterDefinition
import jenkins.security.ApiTokenProperty

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
    protected Configuration configuration = new Configuration(context)

    //TODO: get zafira properties
    public Organization(context) {
        this.context = context
        repository = new Repository(context)
        scmClient = new GitHub(context)
        logger = new Logger(context)
        zafiraUpdater = new ZafiraUpdater(context)
        repo = Configuration.get("repo")
        organization = Configuration.get("organization")
        onlyUpdated = Configuration.get("onlyUpdated")?.toBoolean() //to remove
        currentBuild = context.currentBuild
    }

    def register() {
        logger.info("Organization->register")
        context.node('master') {
            context.timestamps {
                prepare()
                repository.register()
                createLauncher(getLatestOnPushBuild())
                setSecurity()
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
        def token = generateAPIToken(userName)
        //TODO: register token in zafira by tenancy/org
        logger.info(token.dump())
    }

    protected def createLauncher(build){
        build.getAction(javaposse.jobdsl.plugin.actions.GeneratedJobsBuildAction).modifiedObjects.each {
            def currentJobUrl = Configuration.get(Configuration.Parameter.JOB_URL)
            def jobUrl = currentJobUrl.substring(0, currentJobUrl.lastIndexOf("/job/") + "/job/".length()) + it.jobName.substring(it.jobName.lastIndexOf("/"))
            def parameters = getParametersMap(it.jobName)
            zafiraUpdater.createLauncher(parameters, jobUrl, repo)
        }
    }

    private def getParametersMap(jobName) {
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
            parameters.put(parameterDefinition.name, !isParamEmpty(value)?value:'')
        }
        return parameters
    }

    def generateAPIToken(userName){
        //saveInZafira(token.tokenName, token.tokenValue)
        def user = User.getById(userName, false)
        return Jenkins.instance.getDescriptorByType(ApiTokenProperty.DescriptorImpl.class).doGenerateNewToken(user, userName + '_token').jsonObject.data
    }

    def createJenkinsUser(userName){
        def password = UUID.randomUUID().toString()
//        logger.info("USER_GET: " + User.getById(userName, false))
        logger.info("USER_CREATE: " + Jenkins.instance.securityRealm.createAccount(userName, password))
        def user =  Jenkins.instance.securityRealm.createAccount(userName, password)
        logger.info(user.dump())
        return user
    }

    def grantUserBaseGlobalPermissions(userName){
        def authStrategy = Jenkins.instance.getAuthorizationStrategy()
        authStrategy.add(hudson.model.Hudson.READ, userName)
    }

    def setUserFolderPermissions(folderName, userName) {
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
        //TODO: find move permission if necessary
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
                                hudson.model.Item.READ,
                                hudson.model.Item.WORKSPACE,
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
