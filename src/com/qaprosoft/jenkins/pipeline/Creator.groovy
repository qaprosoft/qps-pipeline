package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.scanner.Scanner
import com.qaprosoft.scm.github.GitHub;
import groovy.transform.InheritConstructors

@InheritConstructors
class Creator extends Executor {

	public Creator(context) {
		super(context)
		this.context = context

		scmClient = new GitHub(context)
		//Creator always should use default qps inplementation of Scanner for original create operation
		scanner = new Scanner(context)
	}

	public void create() {
		context.println("Creator->create")

		//create only high level management jobs.
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