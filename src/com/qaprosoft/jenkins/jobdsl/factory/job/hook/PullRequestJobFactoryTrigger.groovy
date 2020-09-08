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
                configure addHiddenParameter('pr_number', '', '')
                configure addHiddenParameter('pr_repository', '', '')
                configure addHiddenParameter('pr_source_branch', '', '')
                configure addHiddenParameter('pr_target_branch', '', '')
                configure addHiddenParameter('pr_action', '', '')
            }

            scm {
                git {
                    remote {
                        //TODO: potential issue for unsecure github setup! 
                        github(this.organization + '/' + this.repo, 'https', host)
                        credentials("${organization}-${repo}")
                        refspec('+refs/pull/*:refs/remotes/origin/pr/*')
                    }
                    branch('${sha1}')
                }
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
                  value("\$.pull_request.base.repo.name")
                }

                genericVariable {
                  key("pr_source_branch")
                  value("\$.pull_request.head.ref")
                }

                genericVariable {
                  key("pr_target_branch")
                  value("\$.pull_request.base.ref")
                }

                genericVariable {
                  key("pr_action")
                  value("\$.action")
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
                 regexpFilter("^(pull_request)*?")
                }
               }
               token('abc123')
               printContributedVariables(true)
               printPostContent(true)
               silentResponse(false)
               regexpFilterText("\$pr_action")
               regexpFilterExpression("^(opened|reopened)")
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