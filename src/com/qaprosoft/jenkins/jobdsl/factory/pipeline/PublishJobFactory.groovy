package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
class PublishJobFactory extends PipelineFactory {

	def host
	def repo
	def organization
	def branch
	def scmUrl

    public PublishJobFactory(folder, pipelineScript, jobName, host, organization, repo, branch, scmUrl) {
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
    	logger.info("PublishJobFactory->Create")

    	def pipelineJob = super.create()

    	pipelineJob.with {
            parameters {
                configure stringParam('VERSION', '', '')
                configure addExtensibleChoice('RELEASE_TYPE', 'gc_RELEASE_TYPE', 'Component release type', 'SNAPSHOT')
                configure stringParam('BRANCH', 'devleop', 'Branch containing sources for component build')
                configure stringParam('MAVEN_REPO_URL', '', 'Maven repo url')
                configure stringParam('MAVEN_USERNAME', '', 'Maven username')
                configure stringParam('MAVEN_PASSWORD', '', 'Maven password')
                configure stringParam('SIGNING_PASSWORD', '', 'PGP key signing password')
                configure stringParam('SIGNING_KEY_BASE64', '', 'Base64 encoded PGP secret key')
            }
    	}

        return pipelineJob
    }

}