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
                stringParam('GITHUB_HOST', host, '')
                stringParam('GITHUB_ORGANIZATION', organization, '')
                stringParam('repo', repo, 'Your GitHub repository for scanning')
            }

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
                ghprbTrigger {
                    gitHubAuthId(getGitHubAuthId(folder))
                    adminlist('')
                    useGitHubHooks(true)
                    triggerPhrase('')
                    autoCloseFailedPullRequests(false)
                    skipBuildPhrase('.*\\[skip\\W+ci\\].*')
                    displayBuildErrorsOnDownstreamBuilds(false)
                    cron('H/5 * * * *')
                    whitelist('')
                    orgslist(organization)
                    blackListLabels('')
                    whiteListLabels('')
                    allowMembersOfWhitelistedOrgsAsAdmin(false)
                    permitAll(true)
                    buildDescTemplate('')
                    blackListCommitAuthor('')
                    includedRegions('')
                    excludedRegions('')
                    onlyTriggerPhrase(false)
                    commentFilePath('')
                    msgSuccess('')
                    msgFailure('')
                    commitStatusContext('')
                }
            }

            steps {
                downstreamParameterized {
                    trigger('onPullRequest-' + repo) {
                        block{
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