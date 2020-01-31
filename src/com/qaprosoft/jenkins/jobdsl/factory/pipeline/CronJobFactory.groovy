package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

import static com.qaprosoft.jenkins.Utils.*

@InheritConstructors
public class CronJobFactory extends PipelineFactory {

    def host
    def repo
    def organization
    def branch
    def scheduling

    public CronJobFactory(folder, pipelineScript, cronJobName, host, repo, organization, branch, jobDesc) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.description = jobDesc
        this.name = cronJobName
        this.host = host
        this.repo = repo
        this.organization = organization
        this.branch = branch
    }

    def create() {
        logger.info("CronJobFactory->create")
        def pipelineJob = super.create()

        pipelineJob.with {
            //** Properties & Parameters Area **//*
            if (scheduling != null) {
                triggers { cron(parseSheduling(scheduling)) }
            }
            parameters {
                choiceParam('env', getEnvironments(currentSuite), 'Environment to test against.')
                configure addHiddenParameter('repo', '', repo)
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                configure addHiddenParameter('ci_parent_url', '', '')
                configure addHiddenParameter('ci_parent_build', '', '')

                configure stringParam('branch', this.branch, "SCM repository branch to run against")
                stringParam('email_list', '', 'List of Users to be emailed after the test. If empty then populate from jenkinsEmail suite property')
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "5")
                choiceParam('retry_count', [0, 1, 2, 3], 'Number of Times to Retry a Failed Test')
                configure addHiddenParameter('zafiraFields', '' , '')
            }

        }
        return pipelineJob
    }
	
	def setScheduling(scheduling) {
		this.scheduling = scheduling
	}

}
