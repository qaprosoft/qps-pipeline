package com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook

import groovy.transform.InheritConstructors
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.PipelineFactory

@InheritConstructors
public class PushJobFactory extends PipelineFactory {

    def host
    def organization
    def repo
    def branch
    def scmRepoUrl

    public PushJobFactory(folder, pipelineScript, jobName, jobDesc, host, organization, repo, branch, scmRepoUrl) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.name = jobName
        this.description = jobDesc
        this.host = host
        this.organization = organization
        this.repo = repo
        this.branch = branch
        this.scmRepoUrl = scmRepoUrl
    }

    def create() {
        def pipelineJob = super.create()

        pipelineJob.with {
            properties {
                //TODO: add SCM artifacts
                githubProjectUrl(scmRepoUrl)
                pipelineTriggers {
                    triggers {
                        githubPush()
                    }
                }
            }

            //TODO: think about other parameters to support DevOps CI operations
            parameters {
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                stringParam('repo', repo, 'GitHub repository for scanning')
                //TODO: analyze howto support several gc_GIT_BRACH basing on project
                configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", branch)
                booleanParam('onlyUpdated', true, '	If chosen, scan will be performed only in case of any change in *.xml suites.')
                choiceParam('removedConfigFilesAction', ['IGNORE', 'DELETE'], '')
                choiceParam('removedJobAction', ['IGNORE', 'DELETE'], '')
                choiceParam('removedViewAction', ['IGNORE', 'DELETE'], '')
            }

        }
        return pipelineJob
    }
}