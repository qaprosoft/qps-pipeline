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
                stringParam('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                configure addHiddenParameter('repo', 'Your GitHub repository for scanning', repo)
                configure addHiddenParameter('sha1', '', '')
                configure addHiddenParameter('ghprbActualCommit', '', '')
                configure addHiddenParameter('ghprbActualCommitAutho', '', '')
                configure addHiddenParameter('ghprbActualCommitAuthorEmail', '', '')
                configure addHiddenParameter('ghprbAuthorRepoGitUrl', '', '')
                configure addHiddenParameter('ghprbTriggerAuthor', '', '')
                configure addHiddenParameter('ghprbTriggerAuthorEmai', '', '')
                configure addHiddenParameter('ghprbTriggerAuthorLogi', '', '')
                configure addHiddenParameter('ghprbTriggerAuthorLoginMention', '', '')
                configure addHiddenParameter('ghprbPullI', '', '')
                configure addHiddenParameter('ghprbTargetBranch', '', '')
                configure addHiddenParameter('ghprbSourceBranch', '', '')
                configure addHiddenParameter('GIT_BRANCH', '', '')
                configure addHiddenParameter('ghprbPullAuthorEmail', '', '')
                configure addHiddenParameter('ghprbPullAuthorLogin', '', '')
                configure addHiddenParameter('ghprbPullAuthorLoginMentio', '', '')
                configure addHiddenParameter('ghprbPullDescription', '', '')
                configure addHiddenParameter('ghprbPullTitle', '', '')
                configure addHiddenParameter('ghprbPullLink', '', '')
                configure addHiddenParameter('ghprbPullLongDescription', '', '')
                configure addHiddenParameter('ghprbCommentBody', '', '')
                configure addHiddenParameter('ghprbGhRepository', '', '')
                configure addHiddenParameter('ghprbCredentialsId', '', '')

                configure addHiddenParameter('gitHubAuthId', '', getGitHubAuthId(folder))
                configure addHiddenParameter('adminlist', '', '')
                configure addHiddenParameter('useGitHubHooks', '', true)
                configure addHiddenParameter('triggerPhrase', '', '')
                configure addHiddenParameter('autoCloseFailedPullRequests', '', false)
                configure addHiddenParameter('skipBuildPhrase', '', '.*\\[skip\\W+ci\\].*')
                configure addHiddenParameter('displayBuildErrorsOnDownstreamBuilds', '', false)
                configure addHiddenParameter('cron', '', 'H/5 * * * *')
                configure addHiddenParameter('whitelist', '', '')
                configure addHiddenParameter('orgslist', '', organization)
                configure addHiddenParameter('blackListLabels', '', '')
                configure addHiddenParameter('whiteListLabels', '', '')
                configure addHiddenParameter('allowMembersOfWhitelistedOrgsAsAdmin', '', false)
                configure addHiddenParameter('permitAll', '', true)
                configure addHiddenParameter('buildDescTemplate', '', '')
                configure addHiddenParameter('blackListCommitAuthor', '', '')
                configure addHiddenParameter('includedRegions', '', '')
                configure addHiddenParameter('excludedRegions', '', '')
                configure addHiddenParameter('onlyTriggerPhrase', '', false)
                configure addHiddenParameter('commentFilePath', '', '')
                configure addHiddenParameter('msgSuccess', '', '')
                configure addHiddenParameter('msgFailure', '', '')
                configure addHiddenParameter('commitStatusContext', '', '')
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