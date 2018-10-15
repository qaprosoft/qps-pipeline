package com.qaprosoft.jenkins.pipeline

import groovy.json.JsonOutput

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.ISCM
import com.qaprosoft.scm.github.GitHub;

import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PullRequestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PushJobFactory

import com.qaprosoft.jenkins.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.jobdsl.factory.folder.FolderFactory

//import hudson.FilePath

class Repository {
	def context
	protected ISCM scmClient
	protected Configuration configuration = new Configuration(context)

	protected final def FACTORY_TARGET = "qps-pipeline/src/com/qaprosoft/jenkins/jobdsl/Factory.groovy"
	protected final def EXTRA_CLASSPATH = "qps-pipeline/src"

	protected Map dslObjects = [:]

	public Repository(context) {
		this.context = context

		//TODO: howto register repository not at github?
		scmClient = new GitHub(context)
	}

	public void register() {
		context.println("Repository->register")

		//create only high level management jobs.
		context.node('master') {
			context.timestamps {
				prepare()
				generateCiItems()
				clean()
			}
		}

		// execute new _trigger-<project> to regenerate other views/jobs/etc
		def project = Configuration.get("project")
		def newJob = project + "/" + "onPush-" + project

		context.build job: newJob,
		propagate: true,
		parameters: [
			context.string(name: 'branch', value: Configuration.get("branch")),
			context.string(name: 'project', value: project),
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
			def BUILD_NUMBER = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
			def project = Configuration.get("project")
			def jobFolder = Configuration.get("project")

			def branch = Configuration.get("branch")
			context.currentBuild.displayName = "#${BUILD_NUMBER}|${project}|${branch}"

			// TODO: move folder and main trigger job creation onto the createRepository method
			def folder = new FolderFactory(jobFolder, "")
			registerObject("project_folder", new FolderFactory(jobFolder, ""))

			// Support DEV related CI workflow
			//TODO: analyze do we need system jobs for QA repo... maybe prametrize CreateRepository call
			def gitUrl = Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_HTML_URL)}/${Configuration.get("project")}")

			registerObject("hooks_view", new ListViewFactory(jobFolder, 'SYSTEM', null, ".*onPush.*|.*onPullRequest.*"))

			def pullRequestJobDescription = "Customized pull request verification checker"

			registerObject("pull_request_job", new PullRequestJobFactory(jobFolder, getOnPullRequestScript(), "onPullRequest-" + project, pullRequestJobDescription, project, gitUrl))

			def pushJobDescription = "To finish GitHub WebHook setup, please, follow the steps below:\n- Go to your GitHub repository\n- Click \"Settings\" tab\n- Click \"Webhooks\" menu option\n" +
					"- Click \"Add webhook\" button\n- Type http://your-jenkins-domain.com/github-webhook/ into \"Payload URL\" field\n" +
					"- Select application/json in \"Content Type\" field\n- Tick \"Send me everything.\" option\n- Click \"Add webhook\" button"

			registerObject("push_job", new PushJobFactory(jobFolder, getOnPushScript(), "onPush-" + project, pushJobDescription, project, gitUrl))

			// put into the factories.json all declared jobdsl factories to verify and create/recreate/remove etc
			context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)

			context.jobDsl additionalClasspath: EXTRA_CLASSPATH,
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
		def pipelineLibrary = Configuration.get("pipelineLibrary")
		def runnerClass = Configuration.get("runnerClass")

		return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass}\nnew ${runnerClass}(this).onPullRequest()"
	}

	private String getOnPushScript() {
		def pipelineLibrary = Configuration.get("pipelineLibrary")
		def runnerClass = Configuration.get("runnerClass")

		return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass}\nnew ${runnerClass}(this).onPush()"
	}


	private void registerObject(name, object) {
		if (dslObjects.containsKey(name)) {
			context.println("WARNING! key '" + name + "' already defined and will be replaced!")
			context.println("old item: " + dslObjects.get(name).dump())
			context.println("new item: " + object.dump())
		}
		dslObjects.put(name, object)
	}

}