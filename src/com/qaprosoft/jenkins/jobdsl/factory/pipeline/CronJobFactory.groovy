package com.qaprosoft.jenkins.jobdsl.factory.pipeline

@Grab('org.testng:testng:6.8.8')

import groovy.transform.InheritConstructors

@InheritConstructors
public class CronJobFactory extends PipelineFactory {

    def repo
    def organization
    def currentSuite

    public CronJobFactory(folder, pipelineScript, cronJobName, repo, organization, currentSuite, jobDesc) {

        this.folder = folder
		this.pipelineScript = pipelineScript
        this.description = jobDesc
        this.name = cronJobName
        this.repo = repo
        this.organization = organization
        this.currentSuite = currentSuite
    }

    def create() {

        def pipelineJob = super.create()
        pipelineJob.with {

            parameters {
                choiceParam('env', getEnvironments(currentSuite), 'Environment to test against.')
                configure addHiddenParameter('repo', '', repo)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                configure addHiddenParameter('ci_parent_url', '', '')
                configure addHiddenParameter('ci_parent_build', '', '')
                configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", "master")
                stringParam('email_list', '', 'List of Users to be emailed after the test. If empty then populate from jenkinsEmail suite property')
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "5")
                choiceParam('retry_count', [0, 1, 2, 3], 'Number of Times to Retry a Failed Test')
            }

        }
        return pipelineJob
    }

}
