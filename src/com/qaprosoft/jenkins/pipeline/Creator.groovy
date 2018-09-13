package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Scanner
import com.qaprosoft.scm.github.GitHub;

class Creator extends Executor {

	protected Scanner scanner

	public Creator(context) {
		super(context)
		this.context = context

		scmClient = new GitHub(context)
		
		//TODO: parametrize Scanner FQDN classname
		scanner = new Scanner(context)
	}

	public void create() {
		context.println("Creator->create")

		//create only high level management jobs. for now it is only _trigger_<project-name>
		scanner.createRepository()

		// execute new _trigger-<project> to regenerate other views/jobs/etc
		def project = Configuration.get("project")
		def newJob = project + "/" + "onPush-" + project

		context.build job: newJob,
		propagate: false,
		parameters: [
			context.string(name: 'branch', value: Configuration.get("branch")),
			context.string(name: 'project', value: project),
			context.booleanParam(name: 'onlyUpdated', value: false),
			context.string(name: 'removedConfigFilesAction', value: 'DELETE'),
			context.string(name: 'removedJobAction', value: 'DELETE'),
			context.string(name: 'removedViewAction', value: 'DELETE'),
		]
	}

}