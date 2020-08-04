package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

import com.qaprosoft.jenkins.pipeline.Configuration

@InheritConstructors
public class BuildJobFactory extends PipelineFactory {

    def host
    def repo
    def organization
    def branch
    def scmUrl
    def buildTool
    def isDockerRepo

    public BuildJobFactory(folder, pipelineScript, jobName, host, organization, repo, branch, scmUrl, buildTool, isDockerRepo=false) {
        this.name = jobName
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.host = host
        this.repo = repo
        this.organization = organization
        this.branch = branch
        this.scmUrl = scmUrl
        this.buildTool = buildTool
        this.isDockerRepo = isDockerRepo
    }

    def create() {
        logger.info("BuildJobFactory->create")

        def pipelineJob = super.create()

        pipelineJob.with {

            parameters {
                if (isDockerRepo) {
                    configure stringParam('release_version', '', 'SemVer-compliant upcoming release or RC version (e.g. 1.13.1 or 1.13.1.RC1)')
                    configure stringParam('dockerfile', 'Dockerfile', 'Relative path to your dockerfile')
                }

                configure stringParam('branch', branch, "SCM repository branch to build against")
                configure stringParam('goals', Configuration.goals.get(buildTool), "$buildTool goals to build the project")
                configure booleanParam('fork', false, "Reuse forked repository for ${repo}.")
                configure addHiddenParameter('repo', '', repo)
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")
                configure stringParam('email_list', "", 'List of Users to be emailed after the build')
            }

        }

        return pipelineJob
    }
}
