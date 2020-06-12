package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.BaseObject
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.jobdsl.factory.job.hook.PullRequestJobFactoryTrigger
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PushJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.BuildJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PullRequestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.scm.MergeJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.jobdsl.factory.folder.FolderFactory
import com.qaprosoft.jenkins.pipeline.runner.maven.TestNG
import java.nio.file.Paths
import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

class Repository extends BaseObject {

    protected ISCM scmClient
    protected def pipelineLibrary
    protected def runnerClass
    protected def rootFolder
    private static final String SCM_ORG = "scmOrg"
    private static final String SCM_HOST = "scmHost"
    private static final String REPO = "repo"
    private static final String BRANCH = "branch"
    private static final String SCM_USER = "scmUser"
    private static final String SCM_TOKEN = "scmToken"

    protected Map dslObjects = new LinkedHashMap()

    public Repository(context) {
		super(context)

        scmClient = new GitHub(context)
        pipelineLibrary = Configuration.get("pipelineLibrary")
        runnerClass = Configuration.get("runnerClass")
    }

    public void register() {
        logger.info("Repository->register")
        Configuration.set("GITHUB_ORGANIZATION", Configuration.get(SCM_ORG))
        Configuration.set("GITHUB_HOST", Configuration.get(SCM_HOST))
        context.node('master') {
            context.timestamps {
                prepare()
                generateCiItems()
                clean()
            }
        }
        
        // execute new _trigger-<repo> to regenerate other views/jobs/etc
        def onPushJobLocation = Configuration.get(REPO) + "/onPush-" + Configuration.get(REPO)

        if (!isParamEmpty(this.rootFolder)) {
            onPushJobLocation = this.rootFolder + "/" + onPushJobLocation
        }
        context.build job: onPushJobLocation,
                propagate: true,
                parameters: [
                        context.string(name: 'repo', value: Configuration.get(REPO)),
                        context.string(name: 'branch', value: Configuration.get(BRANCH)),
                        context.booleanParam(name: 'onlyUpdated', value: false),
                        context.string(name: 'removedConfigFilesAction', value: 'DELETE'),
                        context.string(name: 'removedJobAction', value: 'DELETE'),
                        context.string(name: 'removedViewAction', value: 'DELETE'),
                ]
    }

    public void create() {
        //TODO: incorporate maven project generation based on archetype (carina?)
        throw new RuntimeException("Not implemented yet!")

    }

    protected void prepare() {
        def githubOrganization = Configuration.get(SCM_ORG)
        def credentialsId = "${githubOrganization}-${Configuration.get(REPO)}"

        updateJenkinsCredentials(credentialsId, "${githubOrganization} SCM token", Configuration.get(SCM_USER), Configuration.get(SCM_TOKEN))

        getScm().clone(true)
    }


    private void generateCiItems() {
        context.stage("Create Repository") {
            def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
            def repoFolder = Configuration.get(REPO)

            // Folder from which RegisterRepository job was started
            // Important! using getOrgFolderNam from Utils is prohibited here!
            this.rootFolder = Paths.get(Configuration.get(Configuration.Parameter.JOB_NAME)).getName(0).toString()
            if ("RegisterRepository".equals(this.rootFolder)) {
                // use case when RegisterRepository is on root!
                this.rootFolder = "/"
            } else {
                def zafiraFields = Configuration.get("zafiraFields")
                logger.debug("zafiraFields: " + zafiraFields)
                if (!isParamEmpty(zafiraFields) && zafiraFields.contains("zafira_service_url") && zafiraFields.contains("zafira_access_token")) {
                    def reportingServiceUrl = Configuration.get(Configuration.Parameter.REPORTING_SERVICE_URL)
                    def reportingRefreshToken = Configuration.get(Configuration.Parameter.REPORTING_ACCESS_TOKEN)
                    logger.debug("reportingServiceUrl: " + reportingServiceUrl)
                    logger.debug("reportingRefreshToken: " + reportingRefreshToken)
                    if (!isParamEmpty(reportingServiceUrl) && !isParamEmpty(reportingRefreshToken)){
                        Organization.registerReportingCredentials(repoFolder, reportingServiceUrl, reportingRefreshToken)
                    }
                }
            }

            logger.debug("organization: " + Configuration.get(SCM_ORG))
            logger.debug("rootFolder: " + this.rootFolder)

            // TODO: test with SZ his custom CI setup
            // there is no need to register organization_folder at all as this fucntionality is provided in dedicated RegisterOrganization job logic            
/*
            if (!"/".equals(this.rootFolder)) {
                //register for JobDSL only non root organization folder
                if (isParamEmpty(getJenkinsFolderByName(this.rootFolder))){
                    registerObject("organization_folder", new FolderFactory(this.rootFolder, ""))
                }
            }
*/

            if (!"/".equals(this.rootFolder)) {
                //For both cases when rootFolder exists job was started with existing organization value,
                //so it should be used by default
                Configuration.set(Configuration.Parameter.GITHUB_ORGANIZATION, Configuration.get(SCM_ORG))
                repoFolder = this.rootFolder + "/" + repoFolder
            }

            logger.debug("repoFolder: " + repoFolder)

            //Job build display name
            context.currentBuild.displayName = "#${buildNumber}|${Configuration.get(REPO)}|${Configuration.get(BRANCH)}"

            def githubHost = Configuration.get(SCM_HOST)
            def githubOrganization = Configuration.get(SCM_ORG)

//			createPRChecker(credentialsId)

            registerObject("project_folder", new FolderFactory(repoFolder, ""))
//			 TODO: move folder and main trigger job creation onto the createRepository method

            // Support DEV related CI workflow
//			TODO: analyze do we need system jobs for QA repo... maybe prametrize CreateRepository call
            def gitUrl = Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_HTML_URL)}/${Configuration.get(REPO)}")

            def userId = isParamEmpty(Configuration.get("userId")) ? '' : Configuration.get("userId")
            def zafiraFields = isParamEmpty(Configuration.get("zafiraFields")) ? '' : Configuration.get("zafiraFields")
            logger.error("zafiraFields: " + zafiraFields)

            registerObject("hooks_view", new ListViewFactory(repoFolder, 'SYSTEM', null, ".*onPush.*|.*onPullRequest.*|.*CutBranch-.*|build"))

            def pullRequestFreestyleJobDescription = "To finish GitHub Pull Request Checker setup, please, follow the steps below:\n" +
                    "- Manage Jenkins -> Configure System -> Populate 'GitHub Pull Request Builder': usr should have admin privileges, Auto-manage webhooks should be enabled\n" +
                    "- Go to your GitHub repository\n- Click \"Settings\" tab\n- Click \"Webhooks\" menu option\n" +
                    "- Click \"Add webhook\" button\n- Type http://your-jenkins-domain.com/ghprbhook/ into \"Payload URL\" field\n" +
                    "- Select application/x-www-form-urlencoded in \"Content Type\" field\n- Tick \"Let me select individual events\" with \"Issue comments\" and \"Pull requests enabled\" option\n- Click \"Add webhook\" button"
            def pullRequestPipelineJobDescription = "Verify compilation and/or do Sonar PullRequest analysis"


            registerObject("pull_request_job", new PullRequestJobFactory(repoFolder, getOnPullRequestScript(), "onPullRequest-" + Configuration.get(REPO), pullRequestPipelineJobDescription, githubHost, githubOrganization, Configuration.get(REPO), gitUrl))
            registerObject("pull_request_job_trigger", new PullRequestJobFactoryTrigger(repoFolder, "onPullRequest-" + Configuration.get(REPO) + "-trigger", pullRequestFreestyleJobDescription, githubHost, githubOrganization, Configuration.get(REPO), gitUrl))

            def pushJobDescription = "To finish GitHub WebHook setup, please, follow the steps below:\n- Go to your GitHub repository\n- Click \"Settings\" tab\n- Click \"Webhooks\" menu option\n" +
                    "- Click \"Add webhook\" button\n- Type http://your-jenkins-domain.com/github-webhook/ into \"Payload URL\" field\n" +
                    "- Select application/json in \"Content Type\" field\n- Tick \"Send me everything.\" option\n- Click \"Add webhook\" button"

            def isTestNgRunner = Class.forName(runnerClass, false, Thread.currentThread().getContextClassLoader()) in TestNG

            registerObject("push_job", new PushJobFactory(repoFolder, getOnPushScript(), "onPush-" + Configuration.get(REPO), pushJobDescription, githubHost, githubOrganization, Configuration.get(REPO), Configuration.get(BRANCH), gitUrl, userId, isTestNgRunner, zafiraFields))

            def mergeJobDescription = "SCM branch merger job"
            registerObject("merge_job", new MergeJobFactory(repoFolder, getMergeScript(), "CutBranch-" + Configuration.get(REPO), mergeJobDescription, githubHost, githubOrganization, Configuration.get(REPO), gitUrl))

            // TODO: maybe for custom runner classes for dev repo we can check if the runnerClass field is in the list of pre register runner classes
            // https://github.com/qaprosoft/jenkins-master/issues/225
            // https://github.com/qaprosoft/jenkins-master/issues/222
            def isMavenRunner = runnerClass.contains("com.qaprosoft.jenkins.pipeline.runner.maven.") ? true : false

            if (isMavenRunner) {
                registerObject("build_job", new BuildJobFactory(repoFolder, getPipelineScript(), "Build", githubHost, githubOrganization, Configuration.get(REPO), Configuration.get(BRANCH), gitUrl))
            }

			factoryRunner.run(dslObjects)

        }
    }

    private clean() {
        context.stage('Wipe out Workspace') { context.deleteDir() }
    }

    private String getOnPullRequestScript() {
        if ("QPS-Pipeline".equals(pipelineLibrary)) {
            return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass}\nnew ${runnerClass}(this).onPullRequest()"
        } else {
            return "@Library(\'QPS-Pipeline\')\n@Library(\'${pipelineLibrary}\')\nimport ${runnerClass}\nnew ${runnerClass}(this).onPullRequest()"
        }
    }

    private String getOnPushScript() {
        if ("QPS-Pipeline".equals(pipelineLibrary)) {
            return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass}\nnew ${runnerClass}(this).onPush()"
        } else {
            return "@Library(\'QPS-Pipeline\')\n@Library(\'${pipelineLibrary}\')\nimport ${runnerClass}\nnew ${runnerClass}(this).onPush()"
        }
    }

    protected String getPipelineScript() {
        if ("QPS-Pipeline".equals(pipelineLibrary)) {
            return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
        } else {
            return "@Library(\'QPS-Pipeline\')\n@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
        }
    }

    protected String getMergeScript() {
        if ("QPS-Pipeline".equals(pipelineLibrary)) {
            return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).mergeBranch()"
        } else {
            return "@Library(\'QPS-Pipeline\')\n@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).mergeBranch()"
        }
    }

    private void registerObject(name, object) {
        if (dslObjects.containsKey(name)) {
            logger.warn("key ${name} already defined and will be replaced!")
            logger.info("Old Item: ${dslObjects.get(name).dump()}")
            logger.info("New Item: ${object.dump()}")
        }
        dslObjects.put(name, object)
    }
	
    public def registerCredentials() {
        context.stage("Register Credentials") {
            def user = Configuration.get(SCM_USER)
            def token = Configuration.get(SCM_TOKEN)
            def jenkinsUser = !isParamEmpty(Configuration.get("jenkinsUser")) ? Configuration.get("jenkinsUser") : getBuildUser(context.currentBuild)
            if (updateJenkinsCredentials("token_" + jenkinsUser, jenkinsUser + " SCM token", user, token)) {
                logger.info(jenkinsUser + " credentials were successfully registered.")
            } else {
                throw new RuntimeException("Required fields are missing.")
            }
        }
    }
	
}