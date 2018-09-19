package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.github.GitHub;

import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PullRequestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PushJobFactory

import com.qaprosoft.jenkins.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.jobdsl.factory.folder.FolderFactory

import groovy.transform.InheritConstructors

@InheritConstructors
//TODO: do we need Executor here? What about executor static calls intead?
class Creator extends Executor {

	public Creator(context) {
		super(context)
		this.context = context

		scmClient = new GitHub(context)
	}

	public void registerRepository() {
		context.println("Creator->registerRepository")

		//create only high level management jobs.
		context.node('master') {
			context.timestamps {
				this.prepare()
				this.create()
				this.clean()
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
	
	
	private void clone() {
		scmClient.clone(!onlyUpdated)
		String QPS_PIPELINE_GIT_URL = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_URL)
		String QPS_PIPELINE_GIT_BRANCH = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_BRANCH)
		scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")
	}


	private void create() {

		context.stage("Create Repository") {
			def BUILD_NUMBER = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
			def project = Configuration.get("project")
			def jobFolder = Configuration.get("project")

			def branch = Configuration.get("branch")
			context.currentBuild.displayName = "#${BUILD_NUMBER}|${project}|${branch}"

			def workspace = getWorkspace()
			context.println("WORKSPACE: ${workspace}")



			// TODO: move folder and main trigger job creation onto the createRepository method
			def folder = new FolderFactory(jobFolder, "")
			registerObject("project_folder", new FolderFactory(jobFolder, ""))

			// Support DEV related CI workflow
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

			context.println("factoryTarget: " + factoryTarget)

			context.jobDsl additionalClasspath: additionalClasspath,
			removedConfigFilesAction: 'IGNORE',
			removedJobAction: 'IGNORE',
			removedViewAction: 'IGNORE',
			targets: factoryTarget,
			ignoreExisting: false

		}
	}

	
	private String getOnPullRequestScript() {
		def pipelineLibrary = Configuration.get("pipelineLibrary")
		def runnerClass = Configuration.get("runnerClass")

		return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).onPullRequest()"
	}
	
	private String getOnPushScript() {
		def pipelineLibrary = Configuration.get("pipelineLibrary")
		def runnerClass = Configuration.get("runnerClass")

		return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).onPush()"
	}


}