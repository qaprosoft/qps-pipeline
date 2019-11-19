package com.qaprosoft.jenkins.jobdsl.factory.job.hook


import groovy.transform.InheritConstructors

@InheritConstructors
public class PullRequestJobFactory extends FreestyleJobFactory {

    def host
    def organization
    def repo
    def scmRepoUrl

    public PullRequestJobFactory(folder, jobName, jobDesc, host, organization, repo, scmRepoUrl) {
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
            scm {
                git {
                    remote {
                        github(organization + '/' + repo)
                        refspec('+refs/pull/*:refs/remotes/origin/pr/*')
                    }
                    branch('')
                }
            }

            triggers {
                githubPullRequest {
                    admin('')
                    userWhitelist('')
                    orgWhitelist(organization)
                    cron('H/5 * * * *')
                    triggerPhrase('')
                    onlyTriggerPhrase(false)
                    useGitHubHooks(true)
                    permitAll(true)
                    autoCloseFailedPullRequests(false)
                    displayBuildErrorsOnDownstreamBuilds(false)
                    allowMembersOfWhitelistedOrgsAsAdmin(false)
                    extensions {
                        commitStatus {
                            context('')
                            triggeredStatus('starting deployment...')
                            startedStatus('deploying...')
                            addTestResults(true)
                            statusUrl('http://mystatussite.com/prs')
                            completedStatus('SUCCESS', 'All is well')
                            completedStatus('FAILURE', 'Something went wrong. Investigate!')
                            completedStatus('PENDING', 'still in progress...')
                            completedStatus('ERROR', 'Something went really wrong. Investigate!')
                        }
                        buildStatus {
                            completedStatus('SUCCESS', '')
                            completedStatus('FAILURE', '')
                            completedStatus('ERROR', '')
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