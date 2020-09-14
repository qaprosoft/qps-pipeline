package com.qaprosoft.jenkins.jobdsl.factory.job.hook

import com.qaprosoft.jenkins.jobdsl.factory.job.JobFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class PullRequestJobFactoryTrigger extends JobFactory {

    def host
    def organization
    def repo
    def scmRepoUrl

    public PullRequestJobFactoryTrigger(folder, jobName, jobDesc, host, organization, repo, scmRepoUrl) {
        this.folder = folder
        this.name = jobName
        this.description = jobDesc
        this.host = host
        this.organization = organization
        this.repo = repo
        this.scmRepoUrl = scmRepoUrl
    }

    def create() {
        def freestyleJob = super.create()
        freestyleJob.with {
            concurrentBuild(true)
            parameters {
                //[VD] do not remove empty declaration otherwise params can't be specified dynamically
                stringParam('pr_number', '', '')
                stringParam('pr_repository', '', '')
                stringParam('pr_source_branch', '', '')
                stringParam('pr_target_branch', '', '')
                stringParam('pr_action', '', '')
            }

            scm {
                git {
                    remote {
                        //TODO: potential issue for unsecure github setup! 
                        github(this.organization + '/' + this.repo, 'https', host)
                        credentials("${organization}-${repo}")
                        refspec('+refs/pull/*:refs/remotes/origin/pr/*')
                    }
                    branch('${pr_source_branch}')
                }
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
                headerEventName = "x-event-key"
                filterText = "\$${headerEventName.replaceAll('-', '_')}"
                filterExpression = "^(pullrequest:(created|updated))*?\$"
                prNumberJsonPath = "\$.pullrequest.id"
                prRepositoryJsonPath = "\$.pullrequest.destination.repository.name"
                prSourceBranchJsonPath = "\$.pullrequest.source.branch.name"
                prTargetBranchJsonPath = "\$.pullrequest.destination.branch.name"
                prAction = ""
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

                     token("")
                     printContributedVariables(true)
                     printPostContent(true)
                     silentResponse(false)
                     regexpFilterText(filterText)
                     regexpFilterExpression(filterExpression)
                    }
                  }
                }
              }
          
            steps {
                downstreamParameterized {
                    trigger('onPullRequest-' + this.repo) {
                        block {
                            buildStepFailure('FAILURE')
                            failure('FAILURE')
                            unstable('UNSTABLE')
                        }
                        parameters {
                            currentBuild()
                        }
                    }
                }
            }
        }
        return freestyleJob
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}