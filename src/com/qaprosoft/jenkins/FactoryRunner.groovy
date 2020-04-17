package com.qaprosoft.jenkins

import groovy.json.JsonOutput

public class FactoryRunner {
	protected def context

	protected final def FACTORY_TARGET = "qps-pipeline/src/com/qaprosoft/jenkins/Factory.groovy"
	protected def additionalClasspath = "qps-pipeline/src"

	public FactoryRunner(context) {
		this.context = context
	}
	
	
	/*
	 * Export dslObjects into factories.json and start Factory.groovy as JobDSL script to regenerate jenkins items (jobs, views etc)
	 * removedConfigFilesAction, removedJobAction and removedViewAction are set to 'IGNORE' by default
	 */
	protected void run(dslObjects) {
		run(dslObjects, 'IGNORE', 'IGNORE', 'IGNORE')
	}

	/*
	 * Export dslObjects into factories.json and start Factory.groovy as JobDSL script to regenerate jenkins items (jobs, views etc)
	 */
	protected void run(dslObjects, removedConfigFilesAction, removedJobAction, removedViewAction) {
		context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)
		context.jobDsl additionalClasspath: this.additionalClasspath,
			removedConfigFilesAction: removedConfigFilesAction,
			removedJobAction: removedJobAction,
			removedViewAction: removedViewAction,
			targets: FACTORY_TARGET,
			ignoreExisting: false
	}

	protected void setDslClasspath(additionalClasspath) {
		this.additionalClasspath = additionalClasspath
	}
}
