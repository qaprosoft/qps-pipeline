package com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook

import groovy.transform.InheritConstructors
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.ScmJobFactory

@InheritConstructors
class PullRequestJobFactory extends ScmJobFactory {

    PullRequestJobFactory(folder, jobName, desc, pipelineScript, scmHost, scmOrg, scmRepo, scmBranch, scmUrl) {
        super(folder, jobName, desc, pipelineScript, scmHost, scmOrg, scmRepo, scmBranch, scmUrl)
    }

    def create() {
        def pipelineJob = super.create()
        pipelineJob.with {

            parameters {
                stringParam('pr_number', '', '')
                stringParam('pr_repository', '', '')
                stringParam('pr_source_branch', '', '')
                stringParam('pr_target_branch', '', '')
                stringParam('pr_action', '', '')
                stringParam('pr_sha')
            }

            def headerEventName = "x-github-event"
            def prNumberJsonPath = "\$.number"
            def prRepositoryJsonPath = "\$.pull_request.base.repo.name"
            def prSourceBranchJsonPath = "\$.pull_request.head.ref"
            def prTargetBranchJsonPath = "\$.pull_request.base.ref"
            def prShaJsonPath = "\$.pull_request.head.sha"
            def prActionJsonPath = "\$.action"
            def filterText = "\$pr_action \$${headerEventName.replaceAll('-', '_')}"
            def filterExpression = "^(opened|reopened)\\s(Merge\\sRequest\\sHook|pull_request)*?\$"

            if (host.contains('gitlab')) {
                headerEventName = "x-gitlab-event"
                filterText = "\$pr_action \$${headerEventName.replaceAll('-', '_')}"
                prNumberJsonPath = "\$.object_attributes.iid"
                prRepositoryJsonPath = "\$.project.id"
                prSourceBranchJsonPath = "\$.object_attributes.source_branch"
                prTargetBranchJsonPath = "\$.object_attributes.target_branch"
                prActionJsonPath = "\$.object_attributes.state"
                prShaJsonPath = "\$.object_attributes.last_commit.id"
            } else if(host.contains('bitbucket')) {
                headerEventName = "x-event-key"
                filterText = "\$${headerEventName.replaceAll('-', '_')}"
                filterExpression = "^(pullrequest:(created|updated))*?\$"
                prNumberJsonPath = "\$.pullrequest.id"
                prRepositoryJsonPath = "\$.pullrequest.destination.repository.name"
                prSourceBranchJsonPath = "\$.pullrequest.source.branch.name"
                prTargetBranchJsonPath = "\$.pullrequest.destination.branch.name"
                prActionJsonPath = ""
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

                            token("abc123")
                            printContributedVariables(true)
                            printPostContent(true)
                            silentResponse(false)
                            regexpFilterText(filterText)
                            regexpFilterExpression(filterExpression)
                        }
                    }
                }
            }

            return pipelineJob
        }
    }

    protected def setPrVariables(ls) {
        
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}