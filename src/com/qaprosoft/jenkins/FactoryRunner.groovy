package com.qaprosoft.jenkins

import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import groovy.json.JsonOutput

public class FactoryRunner {
	protected def context
	protected ISCM scmClient

	protected final def FACTORY_TARGET = "qps-pipeline/src/com/qaprosoft/jenkins/Factory.groovy"
	protected def additionalClasspath = "qps-pipeline/src"
	
	protected def isCloned = false

	public FactoryRunner(context) {
		this.context = context
		this.scmClient = new GitHub(context)
	}
	
	
	/*
	 * Export dslObjects into factories.json and start Factory.groovy as JobDSL script to regenerate jenkins items (jobs, views etc)
	 * removedConfigFilesAction, removedJobAction and removedViewAction are set to 'IGNORE' by default
	 */
	public void run(dslObjects) {
		run(dslObjects, 'IGNORE', 'IGNORE', 'IGNORE')
	}

	/*
	 * Export dslObjects into factories.json and start Factory.groovy as JobDSL script to regenerate jenkins items (jobs, views etc)
	 */
	public void run(dslObjects, removedConfigFilesAction, removedJobAction, removedViewAction) {
		if (!isCloned()) {
			clone()
		}
		
		context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)
		context.jobDsl additionalClasspath: this.additionalClasspath,
			removedConfigFilesAction: removedConfigFilesAction,
			removedJobAction: removedJobAction,
			removedViewAction: removedViewAction,
			targets: FACTORY_TARGET,
			ignoreExisting: false
	}

	public void setDslClasspath(additionalClasspath) {
		this.additionalClasspath = additionalClasspath
	}
	
	public void clone() {
		String QPS_PIPELINE_GIT_URL = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_URL)
		String QPS_PIPELINE_GIT_BRANCH = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_BRANCH)
		scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")
		
		this.isCloned = true
	}
	
	public void isCloned() {
		return this.isCloned
	}
}
