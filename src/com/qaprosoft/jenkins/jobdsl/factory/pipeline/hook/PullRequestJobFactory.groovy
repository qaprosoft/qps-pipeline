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
                stringParam('pr_number', '', '')
                stringParam('pr_repository', '', '')
                stringParam('pr_source_branch', '', '')
                stringParam('pr_target_branch', '', '')
                stringParam('pr_action', '', '')
            }

            def headerEventName = "x-github-event"
            def prNumberJsonPath = "\$.number"
            def prRepositoryJsonPath = "\$.pull_request.base.repo.name"
            def prSourceBranchJsonPath = "\$.pull_request.head.ref"
            def prTargetBranchJsonPath = "\$.pull_request.base.ref"
            def prActionJsonPath = "\$.action"
            def filterText = "\$pr_action \$${headerEventName.replaceAll('-', '_')}"
            def filterExpression = "^(opened|reopened)\\s(Merge\\sRequest\\sHook|pull_request)*?\$"

            if (host.contains('gitlab')) {
                headerEventName = "x-gitlab-event"
                prNumberJsonPath = "\$.object_attributes.iid"
                prRepositoryJsonPath = "\$.project.id"
                prSourceBranchJsonPath = "\$.object_attributes.source_branch"
                prTargetBranchJsonPath = "\$.object_attributes.target_branch"
                prActionJsonPath = "\$.object_attributes.state"
            } else if(host.contains('bitbucket')) {
                filterText = "\$${headerEventName.replaceAll('-', '_')}"
                filterExpression = "^(pullrequest:(created|updated))*?\$"
                headerEventName = "x-event-type"
                prNumberJsonPath = "\$.pullrequest.id"
                prRepositoryJsonPath = "\$.pullrequest.destination.repository.name"
                prSourceBranchJsonPath = "\$.pullrequest.source.branch.name"
                prTargetBranchJsonPath = "\$.pullrequest.destination.branch.name"
            }

            properties {
              pipelineTriggers {
                  triggers {
                    genericTrigger {
                     genericVariables {
                      genericVariable {
                       key("pr_number")
                       value(prNumberJsonPath)
                      }

                      genericVariable {
                        key("pr_repository")
                        value(prRepositoryJsonPath)
                      }

                      genericVariable {
                        key("pr_source_branch")
                        value(prSourceBranchJsonPath)
                      }

                      genericVariable {
                        key("pr_target_branch")
                        value(prTargetBranchJsonPath)
                      }

                      genericVariable {
                        key("pr_action")
                        value(prActionJsonPath)
                      }
                     }

                     genericHeaderVariables {
                      genericHeaderVariable {
                       key(headerEventName)
                       regexpFilter("")
                      }
                     }

                     token('abc123')
                     printContributedVariables(true)
                     printPostContent(true)
                     silentResponse(false)
                     regexpFilterText(filterText)
                     regexpFilterExpression(filterExpression)
                    }
                  }
                }
              }
            }

        return pipelineJob
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}