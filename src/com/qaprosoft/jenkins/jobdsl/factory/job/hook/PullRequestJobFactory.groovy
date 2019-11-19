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
            parameters {
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                configure addHiddenParameter('repo', 'Your GitHub repository for scanning', repo)
                configure addHiddenParameter('sha1', '', null)
                configure addHiddenParameter('ghprbActualCommit', '', null)
                configure addHiddenParameter('ghprbActualCommitAutho', '', null)
                configure addHiddenParameter('ghprbActualCommitAuthorEmail', '', null)
                configure addHiddenParameter('ghprbAuthorRepoGitUrl', '', null)
                configure addHiddenParameter('ghprbTriggerAuthor', '', null)
                configure addHiddenParameter('ghprbTriggerAuthorEmai', '', null)
                configure addHiddenParameter('ghprbTriggerAuthorLogi', '', null)
                configure addHiddenParameter('ghprbTriggerAuthorLoginMention', '', null)
                configure addHiddenParameter('ghprbPullI', '', null)
                configure addHiddenParameter('ghprbTargetBranch', '', null)
                configure addHiddenParameter('ghprbSourceBranch', '', null)
                configure addHiddenParameter('GIT_BRANCH', '', null)
                configure addHiddenParameter('ghprbPullAuthorEmail', '', null)
                configure addHiddenParameter('ghprbPullAuthorLogin', '', null)
                configure addHiddenParameter('ghprbPullAuthorLoginMentio', '', null)
                configure addHiddenParameter('ghprbPullDescription', '', null)
                configure addHiddenParameter('ghprbPullTitle', '', null)
                configure addHiddenParameter('ghprbPullLink', '', null)
                configure addHiddenParameter('ghprbPullLongDescription', '', null)
                configure addHiddenParameter('ghprbCommentBody', '', null)
                configure addHiddenParameter('ghprbGhRepository', '', null)
                configure addHiddenParameter('ghprbCredentialsId', '', null)
            }
            /*
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
              */
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