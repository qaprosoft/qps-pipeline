package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.LauncherJobFactory
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.integration.zafira.ZafiraUpdater
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PullRequestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PushJobFactory
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
    protected ZafiraUpdater zafiraUpdater
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
        zafiraUpdater = new ZafiraUpdater(context)
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
        def organization = Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)
        def repo = Configuration.get("repo")
        def branch = Configuration.get("branch")
        def jobName = "${repo}" + "/" + "onPush-" + repo
        def jobRootFolder = Configuration.get("jobRootFolder")
        if (!isParamEmpty(jobRootFolder) && !isParamEmpty(getJenkinsFolderByName(jobRootFolder))){
            jobName = "${jobRootFolder}/" + jobName
        }
        context.build job: jobName,
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
            def gitHubOrganizationParameter = Configuration.get("organization")
            def repo = Configuration.get("repo")
            def branch = Configuration.get("branch")
            def repoFolder
            def jobRootFolder = Paths.get(Configuration.get(Configuration.Parameter.JOB_NAME)).getName(0).toString()
            if (!"Management_Jobs".equals(jobRootFolder)) {
                repoFolder = "${jobRootFolder}/${repo}"
            } else {
                jobRootFolder = ''
                if (!isParamEmpty(gitHubOrganizationParameter)){
                    Configuration.set(Configuration.Parameter.GITHUB_ORGANIZATION, gitHubOrganizationParameter)
                    jobRootFolder = gitHubOrganizationParameter
                    if (isParamEmpty(getJenkinsFolderByName(jobRootFolder))){
                        registerObject("organization_folder", new FolderFactory(jobRootFolder, ""))
                    }
                    repoFolder = "${jobRootFolder}/${repo}"
                } else {
                    repoFolder = repo
                }
            }
            Configuration.set("jobRootFolder", jobRootFolder)
            context.currentBuild.displayName = "#${buildNumber}|${repo}|${branch}"
            def githubHost = Configuration.get(Configuration.Parameter.GITHUB_HOST)
            def githubOrganization = Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)
            def credentialsId = "${githubOrganization}-${repo}"
            updateJenkinsCredentials(credentialsId, "${githubOrganization} GitHub token", Configuration.get("user"), Configuration.get("token"))
//			createPRChecker(credentialsId)

            registerObject("project_folder", new FolderFactory(repoFolder, ""))
//			 TODO: move folder and main trigger job creation onto the createRepository method

            // Support DEV related CI workflow
//			TODO: analyze do we need system jobs for QA repo... maybe prametrize CreateRepository call
            def gitUrl = Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_HTML_URL)}/${Configuration.get("repo")}")

            registerObject("hooks_view", new ListViewFactory(repoFolder, 'SYSTEM', null, ".*onPush.*|.*onPullRequest.*"))

            def pullRequestJobDescription = "Customized pull request verification checker"

            registerObject("pull_request_job", new PullRequestJobFactory(repoFolder, getOnPullRequestScript(), "onPullRequest-" + repo, pullRequestJobDescription, githubHost, githubOrganization, repo, gitUrl))

            def pushJobDescription = "To finish GitHub WebHook setup, please, follow the steps below:\n- Go to your GitHub repository\n- Click \"Settings\" tab\n- Click \"Webhooks\" menu option\n" +
                    "- Click \"Add webhook\" button\n- Type http://your-jenkins-domain.com/github-webhook/ into \"Payload URL\" field\n" +
                    "- Select application/json in \"Content Type\" field\n- Tick \"Send me everything.\" option\n- Click \"Add webhook\" button"

            registerObject("push_job", new PushJobFactory(repoFolder, getOnPushScript(), "onPush-" + repo, pushJobDescription, githubHost, githubOrganization, repo, branch, gitUrl))

            def launcher = isParamEmpty(jobRootFolder) ? getItemByFullName("launcher") : getItemByFullName(jobRootFolder + "/launcher")
            if (isParamEmpty(launcher)){
                registerObject("launcher_job", new LauncherJobFactory(jobRootFolder, getPipelineScript(), "launcher", "Custom job launcher"))
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