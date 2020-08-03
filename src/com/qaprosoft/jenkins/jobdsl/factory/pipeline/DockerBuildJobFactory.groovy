
package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

import static com.qaprosoft.jenkins.Utils.*

@InheritConstructors
class DockerBuildJobFactory extends PipelineFactory {

	def host
	def repo
	def organization
	def branch
	def scmUrl
	def buildTool

	public DockerBuildJobFactory(folder, pipelineScript, jobName, host, organization, repo, branch, scmUrl, buildTool) {
		this.folder = folder
		this.pipelineScript = pipelineScript
		this.name = jobName
		this.host = host
		this.organization = organization
		this.repo = repo
		this.branch = branch
		this.scmUrl = scmUrl
		this.buildTool = buildTool
	}

	def create() {
		logger.info("DockerBuildJobFactory->create")

		def pipelineJob = super.create()

		pipelineJob.with {

			parameters {
				if (!isParamEmpty(buildTool)) {
					configure stringParam('goals', 'clean build', "${this.buildTool} goals to build the project")
				}
				configure stringParam('release_version', '', 'SemVer-compliant upcoming release or RC version (e.g. 1.13.1 or 1.13.1.RC1)')
				configure stringParam('branch', 'develop', 'Branch containing sources for component build')
				configure stringParam('dockerfile', 'Dockerfile', 'Relative path to your dockerfile')
				configure addHiddenParameter('build_tool', '', buildTool)
                configure addHiddenParameter('repo', '', repo)
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
			}
		}

		return pipelineJob
	}

}