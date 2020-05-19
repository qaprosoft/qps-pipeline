package com.qaprosoft.jenkins.jobdsl.factory.pipeline

@Grab('org.testng:testng:6.8.8')

import static com.qaprosoft.jenkins.Utils.*
import groovy.transform.InheritConstructors

@InheritConstructors
public class BuildJobFactory extends PipelineFactory {

    def host
    def repo
    def organization
    def branch

    //folder, pipelineScript, host, repo, organization, branch,
    //            sub_project, zafira_project, suitePath, suiteName, jobDesc, orgRepoScheduling, threadCount, dataProviderThreadCount

    public TestJobFactory(folder, pipelineScript, jobName, host, organization, repo, branch) {

        this.host = host
        this.repo = repo
        this.organization = organization
        this.branch = branch
        this.jobName = jobName
        this.folder = folder
        this.pipelineScript = pipelineScript
    }

    def create() {
        logger.info("BuildJobFactory->create")

        def pipelineJob = super.create()
        pipelineJob.with {

            booleanParam('fork', false, "Reuse forked repository for ${repo} repository.")
            //booleanParam('debug', false, 'Check to start tests in remote debug mode.')

            configure stringParam('branch', branch, "SCM repository branch to build against")
            configure addHiddenParameter('repo', '', repo)
            configure addHiddenParameter('GITHUB_HOST', '', host)
            configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
            configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")
            stringParam('email_list',  "", 'List of Users to be emailed after the test')
            }

        return pipelineJob
    }
}