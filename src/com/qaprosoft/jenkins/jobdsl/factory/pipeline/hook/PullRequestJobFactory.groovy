package com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook

import groovy.transform.InheritConstructors
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.PipelineFactory

@InheritConstructors
public class PullRequestJobFactory extends PipelineFactory {

    def host
    def organization
    def repo
    def scmRepoUrl

    public PullRequestJobFactory(folder, pipelineScript, jobName, jobDesc, host, organization, repo, scmRepoUrl) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.name = jobName
        this.description = jobDesc
        this.host = host
        this.organization = organization
        this.repo = repo
        this.scmRepoUrl = scmRepoUrl
    }

    def create() {
        def pipelineJob = super.create()
        pipelineJob.with {
            parameters {
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                stringParam('repo', repo, 'Your GitHub repository for scanning')
            }

            triggers {
              genericTrigger {
               genericVariables {
                genericVariable {
                 key("ref")
                 value("\$.ref")
                 expressionType("JSONPath") //Optional, defaults to JSONPath
                 regexpFilter("") //Optional, defaults to empty string
                 defaultValue("") //Optional, defaults to empty string
                }
               }
               // genericRequestVariables {
               //  genericRequestVariable {
               //   key("requestParameterName")
               //   regexpFilter("")
               //  }
               // }
               // genericHeaderVariables {
               //  genericHeaderVariable {
               //   key("requestHeaderName")
               //   regexpFilter("")
               //  }
               // }
               token('abc123')
               printContributedVariables(true)
               printPostContent(true)
               silentResponse(false)
               regexpFilterText("\$.ref")
               regexpFilterExpression("ref")
              }
            }
        }
        return pipelineJob
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}