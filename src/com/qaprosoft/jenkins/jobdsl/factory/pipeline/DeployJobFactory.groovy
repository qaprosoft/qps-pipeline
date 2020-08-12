package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
class DeployJobFactory extends PipelineFactory {

	def host
	def repo
	def organization
	def branch
	def scmUrl

    public DeployJobFactory(folder, pipelineScript, jobName, host, organization, repo, branch, scmUrl) {
        this.name = jobName
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.host = host
        this.repo = repo
        this.organization = organization
        this.branch = branch
        this.scmUrl = scmUrl
    }

    def create() {
    	logger.info("DeployJobFactory->Create")

    	def pipelineJob = super.create()

    	pipelineJob.with {

			configure addExtensibleChoice('target_enviroment', 'gc_DEPLOY_ENV', '', 'stage')
			configure stringParam('version', "", "")
    	}

        return pipelineJob
    }

}