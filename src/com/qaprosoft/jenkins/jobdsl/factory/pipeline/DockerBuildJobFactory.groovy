
package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
class DockerBuildJobFactory extends BuildJobFactory {

	def buildTool

	public DockerBuildJobFactory(folder, pipelineScript, jobName, host, organization, repo, branch, scmUrl, buildTool) {
		super(foler, pipelineScript, jobName, host, organization, repo, branch, scmUrl)
		this.buildTool = buildTool
	}

	def create() {
		logger.info("DockerBuildJobFactory->create")

		def pipelineJob = super.create()

		pipelineJob.with {

			parameters {
				configure stringParam('goals', './gradlew clean', 'Gradle task to build the project')
				configure stringParam('release_version', '', 'SemVer-compliant upcoming release or RC version (e.g. 1.13.1 or 1.13.1.RC1)')
				configure stringParam('branch', 'develop', 'Branch containing sources for component build')
				configure stringParam('dockerfile', 'Dockerfile', 'Relative path to your dockerfile')
				configure addHiddenParameter('build_tool', '', buildTool)
			}
		}

		return pipelineJob
	}	

}