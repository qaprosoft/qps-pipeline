package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
class DeployJobFactory extends PipelineFactory {

	def host
	def repo
	def organization

	public DeployJobFactory(folder, pipelineScript, jobName, host, organization, repo) {
		this.name = jobName
		this.folder = folder
		this.pipelineScript = pipelineScript
		this.host = host
		this.repo = repo
		this.organization = organization
	}

	def create() {
		logger.info("DeployJobFactory->Create")

		def pipelineJob = super.create()

		pipelineJob.with {
			parameters {
				configure addExtensibleChoice('TARGET_ENVIRONMENT', 'gc_DEPLOY_ENV', '', 'stage')
				configure stringParam('RELEASE_VERSION', '', '')
				configure addHiddenParameter('repo', '', repo)
				configure addHiddenParameter('GITHUB_HOST', '', host)
				configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
			}
		}

		return pipelineJob
	}

}