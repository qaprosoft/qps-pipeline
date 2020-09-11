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
    def userId
    def zafiraFields
    def isTestNgRunner

    public PushJobFactory(folder, pipelineScript, jobName, jobDesc, host, organization, repo, branch, scmRepoUrl, userId, isTestNgRunner, zafiraFields) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.name = jobName
        this.description = jobDesc
        this.host = host
        this.organization = organization
        this.repo = repo
        this.branch = branch
        this.scmRepoUrl = scmRepoUrl
        this.userId = userId
        this.isTestNgRunner = isTestNgRunner
        this.zafiraFields = zafiraFields
    }

    def create() {
        def pipelineJob = super.create()

        pipelineJob.with {
            // properties {
            //     //TODO: add SCM artifacts
            //     githubProjectUrl(scmRepoUrl)
            //     pipelineTriggers {
            //         triggers {
            //             githubPush()
            //         }
            //     }
            // }

            //TODO: think about other parameters to support DevOps CI operations
            parameters {
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                stringParam('repo', repo, 'GitHub repository for scanning')
                //TODO: analyze howto support several gc_GIT_BRACH basing on project
                stringParam('branch', this.branch, "SCM repository branch to run against")
                if (isTestNgRunner) {
                    booleanParam('onlyUpdated', true, 'If chosen, scan will be performed only in case of any change in *.xml suites.')
                }
                choiceParam('removedConfigFilesAction', ['IGNORE', 'DELETE'], '')
                choiceParam('removedJobAction', ['IGNORE', 'DELETE'], '')
                choiceParam('removedViewAction', ['IGNORE', 'DELETE'], '')
                configure addHiddenParameter('userId', 'Identifier of the user who triggered the process', userId)
                configure addHiddenParameter('zafiraFields', '', zafiraFields)
            }

            def headerEventName = "x-github-event"
            def refJsonPath = "\$.ref"

            if (host.contains("gitlab")) {
                headerEventName = "x-gitlab-event"
            } else if (host.contains("bitbucket")) {
                headerEventName = "x-event-key"
                refJsonPath = "\$.push.changes[0].new.name"
            }

            properties {
            	pipelineTriggers {
	            	triggers {
		              genericTrigger {
			               genericVariables {
			                genericVariable {
			                 key("ref")
			                 value(refJsonPath)
			                }
			               }

			               genericHeaderVariables {
			                genericHeaderVariable {
			                 key(headerEventName)
			                 regexpFilter("")
			                }
			               }
			               token('abc123')
			               printContributedVariables(false)
			               printPostContent(false)
			               silentResponse(false)
			               regexpFilterText("\$ref \$${headerEventName.replaceAll('-','_')}")
			               regexpFilterExpression("^(refs/heads/master\\s(push|Push\\sHook)|master\\srepo:push)\$")
	              		}
	              	}
	            }
            }

            /** Git Stuff **/

        }
        return pipelineJob
    }
}