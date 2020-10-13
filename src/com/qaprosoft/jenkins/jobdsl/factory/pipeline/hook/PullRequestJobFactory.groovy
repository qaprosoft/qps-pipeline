package com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook

import groovy.transform.InheritConstructors
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.PipelineFactory

@InheritConstructors
public class PullRequestJobFactory extends PipelineFactory {

    def host
    def organization
    def repo
    def scmRepoUrl
    def webHookArgs

    public PullRequestJobFactory(folder, pipelineScript, jobName, jobDesc, host, organization, repo, scmRepoUrl, webHookArgs) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.name = jobName
        this.description = jobDesc
        this.host = host
        this.organization = organization
        this.repo = repo
        this.scmRepoUrl = scmRepoUrl
        this.webHookArgs = webHookArgs
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
                stringParam('pr_sha')
            }

            properties {
                pipelineTriggers {
                    triggers {
                        genericTrigger {
                            genericVariables {
                                genericVariable {
                                    key("pr_number")
                                    value(webHookArgs.prNumber)
                                }
                                genericVariable {
                                    key("pr_repository")
                                    value(webHookArgs.prRepo)
                                }
                                genericVariable {
                                    key("pr_source_branch")
                                    value(webHookArgs.prSourceBranch)
                                }
                                genericVariable {
                                    key("pr_target_branch")
                                    value(webHookArgs.prTargetBranch)
                                }
                                genericVariable {
                                    key("pr_action")
                                    value(webHookArgs.prAction)
                                }
                                genericVariable {
                                    key("pr_sha")
                                    value(webHookArgs.prSha)
                                }
                            }

                            genericHeaderVariables {
                                genericHeaderVariable {
                                    key(webHookArgs.eventName)
                                    regexpFilter("")
                                }
                            }

                            token("abc123")
                            printContributedVariables(true)
                            printPostContent(true)
                            silentResponse(false)
                            regexpFilterText(webHookArgs.prFilterText)
                            regexpFilterExpression(prFilterRegex)
                        }
                    }
                }
            }

            return pipelineJob
        }
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}