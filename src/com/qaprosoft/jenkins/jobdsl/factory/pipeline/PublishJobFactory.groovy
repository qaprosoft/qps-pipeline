package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
class PublishJobFactory extends PipelineFactory {

	def host
	def repo
	def organization
	def branch

    public PublishJobFactory(folder, pipelineScript, jobName, host, organization, repo, branch) {
        this.name = jobName
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.host = host
        this.repo = repo
        this.organization = organization
        this.branch = branch
    }

    def create() {
    	logger.info("PublishJobFactory->Create")

    	def pipelineJob = super.create()

    	pipelineJob.with {
            parameters {
                configure stringParam('VERSION', '', 'SemVer-compliant upcoming release or RC version (e.g. 1.13.1 or 1.13.1.RC1)')
                configure addExtensibleChoice('RELEASE_TYPE', 'gc_RELEASE_TYPE', 'Component release type', 'SNAPSHOT')
                configure stringParam('branch', 'devleop', 'Branch containing sources for component build')
                configure stringParam('MAVEN_REPO_URL', '', 'Maven repo url')
                configure stringParam('MAVEN_USERNAME', '', 'Maven username')
                configure stringParam('MAVEN_PASSWORD', '', 'Maven password')
                configure stringParam('SIGNING_PASSWORD', '', 'PGP key signing password')
                configure stringParam('SIGNING_KEY_BASE64', '', 'Base64 encoded PGP secret key')
                configure addHiddenParameter('repo', '', repo)
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
            }
    	}

        return pipelineJob
    }

}