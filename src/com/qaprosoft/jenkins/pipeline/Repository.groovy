package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.LauncherJobFactory
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PullRequestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PushJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.scm.MergeJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.jobdsl.factory.folder.FolderFactory
import groovy.json.JsonOutput
import java.nio.file.Paths
import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

class Repository {

    def context
    protected ISCM scmClient
    protected Logger logger
    protected Configuration configuration = new Configuration(context)
    protected final def FACTORY_TARGET = "qps-pipeline/src/com/qaprosoft/jenkins/Factory.groovy"
    protected final def EXTRA_CLASSPATH = "qps-pipeline/src"
    protected def pipelineLibrary
    protected def runnerClass

    protected Map dslObjects = new LinkedHashMap()

    public Repository(context) {
        this.context = context
        //TODO: howto register repository not at github?
        scmClient = new GitHub(context)
        logger = new Logger(context)
        pipelineLibrary = Configuration.get("pipelineLibrary")
        runnerClass =  Configuration.get("runnerClass")
    }

    public void register() {
        logger.info("Repository->register")
        //create only high level management jobs.
        context.node('master') {
            context.timestamps {
                prepare()
                generateCiItems()
                clean()
            }
        }
        // execute new _trigger-<repo> to regenerate other views/jobs/etc
        def repo = Configuration.get("repo")
        def branch = Configuration.get("branch")
        def onPushJobLocation = repo + "/onPush-" + repo

        def rootFolder = Configuration.get("rootFolder")

        if (!isParamEmpty(rootFolder)){
            onPushJobLocation = rootFolder + "/" + onPushJobLocation
        }
        context.build job: onPushJobLocation,
                propagate: true,
                parameters: [
                        context.string(name: 'repo', value: repo),
                        context.string(name: 'branch', value: branch),
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

    private void prepare() {
        //[VD] do not clone repo by default. Just qps-pipeline is enough
        //scmClient.clone(true) //do shallow clone during repo registration
        String QPS_PIPELINE_GIT_URL = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_URL)
        String QPS_PIPELINE_GIT_BRANCH = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_BRANCH)
        scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")
    }


    private void generateCiItems() {

        context.stage("Create Repository") {
            def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
            def organization = Configuration.get("organization")
            def repo = Configuration.get("repo")
            def branch = Configuration.get("branch")

            def repoFolder = repo
            def rootFolder = ''

            // Folder from which RegisterRepository job was started
            def registerRepositoryFolder = Paths.get(Configuration.get(Configuration.Parameter.JOB_NAME)).getName(0).toString()
			if ("RegisterRepository".equals(registerRepositoryFolder)) {
				// use case when RegisterRepository is on root!
				RegisterRepository = ""
			}
            logger.warn("registerRepositoryFolder: " + registerRepositoryFolder)

            logger.warn("rootFolder: " + rootFolder)
            if (!isParamEmpty(organization)){
                rootFolder = registerRepositoryFolder
                if (isParamEmpty(getJenkinsFolderByName(rootFolder))){
                    registerObject("organization_folder", new FolderFactory(rootFolder, ""))
                }
            }
            logger.warn("rootFolder: " + rootFolder)

            if (!isParamEmpty(rootFolder)) {
                //For both cases when rootFolder exists job was started with existing organization value,
                //so it should be used by default
                Configuration.set(Configuration.Parameter.GITHUB_ORGANIZATION, organization)
                repoFolder = rootFolder + "/" + repoFolder
            }

            // Used on the next step to detect onPush job location
            Configuration.set("rootFolder", rootFolder)

            //Job build display name
            context.currentBuild.displayName = "#${buildNumber}|${repo}|${branch}"

            def githubHost = Configuration.get(Configuration.Parameter.GITHUB_HOST)
            def githubOrganization = Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)
            def credentialsId = "${githubOrganization}-${repo}"

            updateJenkinsCredentials(credentialsId, "${githubOrganization} GitHub token", Configuration.get("githubUser"), Configuration.get("githubToken"))
//			createPRChecker(credentialsId)

            registerObject("project_folder", new FolderFactory(repoFolder, ""))
//			 TODO: move folder and main trigger job creation onto the createRepository method

            // Support DEV related CI workflow
//			TODO: analyze do we need system jobs for QA repo... maybe prametrize CreateRepository call
            def gitUrl = Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_HTML_URL)}/${Configuration.get("repo")}")

            def userId = isParamEmpty(Configuration.get("userId")) ? '' : Configuration.get("userId")
            def zafiraFields = isParamEmpty(Configuration.get("zafiraFields")) ? '' : Configuration.get("zafiraFields")

            registerObject("hooks_view", new ListViewFactory(repoFolder, 'SYSTEM', null, ".*onPush.*|.*onPullRequest.*|.*CutBranch-.*"))

            def pullRequestJobDescription = "Customized pull request verification checker"

            registerObject("pull_request_job", new PullRequestJobFactory(repoFolder, getOnPullRequestScript(), "onPullRequest-" + repo, pullRequestJobDescription, githubHost, githubOrganization, repo, gitUrl))

            def pushJobDescription = "To finish GitHub WebHook setup, please, follow the steps below:\n- Go to your GitHub repository\n- Click \"Settings\" tab\n- Click \"Webhooks\" menu option\n" +
                    "- Click \"Add webhook\" button\n- Type http://your-jenkins-domain.com/github-webhook/ into \"Payload URL\" field\n" +
                    "- Select application/json in \"Content Type\" field\n- Tick \"Send me everything.\" option\n- Click \"Add webhook\" button"

            registerObject("push_job", new PushJobFactory(repoFolder, getOnPushScript(), "onPush-" + repo, pushJobDescription, githubHost, githubOrganization, repo, branch, gitUrl, userId, zafiraFields))


            def mergeJobDescription = "SCM branch merger job"
            registerObject("merge_job", new MergeJobFactory(repoFolder, getMergeScript(), "CutBranch-" + repo, mergeJobDescription, githubHost, githubOrganization, repo, gitUrl))

            def launcher = isParamEmpty(rootFolder) ? getItemByFullName("launcher") : getItemByFullName(rootFolder + "/launcher")
            if (isParamEmpty(launcher)){
                registerObject("launcher_job", new LauncherJobFactory(rootFolder, getPipelineScript(), "launcher", "Custom job launcher"))
            }

            // put into the factories.json all declared jobdsl factories to verify and create/recreate/remove etc
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
            logger.warn("WARNING! key ${name} already defined and will be replaced!")
            logger.info("Old Item: ${dslObjects.get(name).dump()}")
            logger.info("New Item: ${object.dump()}")
        }
        dslObjects.put(name, object)
    }

    public def registerCredentials(){
        context.stage("Register Credentials") {
            def user = Configuration.get("githubUser")
            def token = Configuration.get("githubToken")
            def jenkinsUser = !isParamEmpty(Configuration.get("jenkinsUser")) ? Configuration.get("jenkinsUser") : getBuildUser(context.currentBuild)
            if (updateJenkinsCredentials("token_" + jenkinsUser, jenkinsUser + " GitHub token", user, token)){
                logger.info(jenkinsUser + " credentials were successfully registered.")
            } else {
                throw new RuntimeException("Required fields are missing.")
            }
        }
    }
}