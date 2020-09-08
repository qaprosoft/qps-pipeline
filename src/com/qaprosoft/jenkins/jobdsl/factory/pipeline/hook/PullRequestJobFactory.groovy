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
                stringParam('repo', repo, 'Your GitHub repository for scanning')
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                configure addHiddenParameter('pr_number', '', '')
                configure addHiddenParameter('pr_repository', '', '')
                configure addHiddenParameter('pr_source_branch', '', '')
                configure addHiddenParameter('pr_target_branch', '', '')
            }

            triggers {
              genericTrigger {
               genericVariables {
                genericVariable {
                 key("pr_number")
                 value("\$.number")
                }

                genericVariable {
                  key("pr_repository")
                  value("\$.repo.name")
                }

                genericVariable {
                  key("pr_source_branch")
                  value("\$.head.ref")
                }

                genericVariable {
                  key("pr_target_branch")
                  value("\$.base.ref")
                }
               }
               // genericRequestVariables {
               //  genericRequestVariable {
               //   key("requestParameterName")
               //   regexpFilter("")
               //  }
               // }
               genericHeaderVariables {
                genericHeaderVariable {
                 key("X-GitHub-Event")
                 regexpFilter("pull_request")
                }
               }
               token('abc123')
               printContributedVariables(true)
               printPostContent(true)
               silentResponse(false)
               regexpFilterText("")
               regexpFilterExpression("")
              }
            }
        }
        return pipelineJob
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}