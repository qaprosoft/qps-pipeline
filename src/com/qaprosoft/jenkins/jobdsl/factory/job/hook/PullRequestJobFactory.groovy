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
                stringParam('GITHUB_HOST', host, '')
                stringParam('GITHUB_ORGANIZATION', organization, '')
                stringParam('repo', repo, 'Your GitHub repository for scanning')
                stringParam('sha1', '', '')
                stringParam('ghprbActualCommit', '', '')
                stringParam('ghprbActualCommitAutho', '', '')
                stringParam('ghprbActualCommitAuthorEmail', '', '')
                stringParam('ghprbAuthorRepoGitUrl', '', '')
                stringParam('ghprbTriggerAuthor', '', '')
                stringParam('ghprbTriggerAuthorEmai', '', '')
                stringParam('ghprbTriggerAuthorLogi', '', '')
                stringParam('ghprbTriggerAuthorLoginMention', '', '')
                stringParam('ghprbPullI', '', '')
                stringParam('ghprbTargetBranch', '', '')
                stringParam('ghprbSourceBranch', '', '')
                stringParam('GIT_BRANCH', '', '')
                stringParam('ghprbPullAuthorEmail', '', '')
                stringParam('ghprbPullAuthorLogin', '', '')
                stringParam('ghprbPullAuthorLoginMentio', '', '')
                stringParam('ghprbPullDescription', '', '')
                stringParam('ghprbPullTitle', '', '')
                stringParam('ghprbPullLink', '', '')
                stringParam('ghprbPullLongDescription', '', '')
                stringParam('ghprbCommentBody', '', '')
                stringParam('ghprbGhRepository', '', '')
                stringParam('ghprbCredentialsId', '', '')

                stringParam('gitHubAuthId', getGitHubAuthId(folder), '')
                stringParam('adminlist', '', '')
                booleanParam('useGitHubHooks', true, '')
                stringParam('triggerPhrase', '', '')
                booleanParam('autoCloseFailedPullRequests', false, '')
                stringParam('skipBuildPhrase', '.*\\[skip\\W+ci\\].*', '')
                booleanParam('displayBuildErrorsOnDownstreamBuilds', false, '')
                stringParam('cron', 'H/5 * * * *', '')
                stringParam('whitelist', '', '')
                stringParam('orgslist', organization, '')
                stringParam('blackListLabels', '', '')
                stringParam('whiteListLabels', '', '')
                booleanParam('allowMembersOfWhitelistedOrgsAsAdmin', false, '')
                booleanParam('permitAll', true, '')
                stringParam('buildDescTemplate', '', '')
                stringParam('blackListCommitAuthor', '', '')
                stringParam('includedRegions', '', '')
                stringParam('excludedRegions', '', '')
                booleanParam('onlyTriggerPhrase', false, '')
                stringParam('commentFilePath', '', '')
                stringParam('msgSuccess', '', '')
                stringParam('msgFailure', '', '')
                stringParam('commitStatusContext', '', '')
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

//            triggers {
//                githubPullRequest {
//                    admin('')
//                    userWhitelist('')
//                    orgWhitelist(organization)
//                    cron('H/5 * * * *')
//                    triggerPhrase('')
//                    onlyTriggerPhrase(false)
//                    useGitHubHooks(true)
//                    permitAll(true)
//                    autoCloseFailedPullRequests(false)
//                    displayBuildErrorsOnDownstreamBuilds(false)
//                    allowMembersOfWhitelistedOrgsAsAdmin(false)
//                    extensions {
//                        commitStatus {
//                            context('')
//                            triggeredStatus('starting deployment...')
//                            startedStatus('deploying...')
//                            addTestResults(true)
//                            statusUrl('http://mystatussite.com/prs')
//                            completedStatus('SUCCESS', 'All is well')
//                            completedStatus('FAILURE', 'Something went wrong. Investigate!')
//                            completedStatus('PENDING', 'still in progress...')
//                            completedStatus('ERROR', 'Something went really wrong. Investigate!')
//                        }
//                        buildStatus {
//                            completedStatus('SUCCESS', '')
//                            completedStatus('FAILURE', '')
//                            completedStatus('ERROR', '')
//                        }
//                    }
//                }
//            }

            steps {
                remoteTrigger('onPullRequest-carina-demo-trigger', 'onPullRequest-carina-demo-trigger') {
                    parameter('GITHUB_HOST', host)
                    parameter('GITHUB_ORGANIZATION', organization)
                    parameter('repo', repo)
                    parameter('sha1', '')
                    parameter('ghprbActualCommit', '')
                    parameter('ghprbActualCommitAutho', '')
                    parameter('ghprbActualCommitAuthorEmail', '')
                    parameter('ghprbAuthorRepoGitUrl', '')
                    parameter('ghprbTriggerAuthor', '')
                    parameter('ghprbTriggerAuthorEmai', '')
                    parameter('ghprbTriggerAuthorLogi', '')
                    parameter('ghprbTriggerAuthorLoginMention', '')
                    parameter('ghprbPullI', '')
                    parameter('ghprbTargetBranch', '')
                    parameter('ghprbSourceBranch', '')
                    parameter('GIT_BRANCH', '')
                    parameter('ghprbPullAuthorEmail', '')
                    parameter('ghprbPullAuthorLogin', '')
                    parameter('ghprbPullAuthorLoginMentio', '')
                    parameter('ghprbPullDescription', '')
                    parameter('ghprbPullTitle', '')
                    parameter('ghprbPullLink', '')
                    parameter('ghprbPullLongDescription', '')
                    parameter('ghprbCommentBody', '')
                    parameter('ghprbGhRepository', '')
                    parameter('ghprbCredentialsId', '')

                    parameter('gitHubAuthId', getGitHubAuthId(folder))
                    parameter('adminlist', '')
                    parameter('useGitHubHooks', true)
                    parameter('triggerPhrase', '')
                    parameter('autoCloseFailedPullRequests', false)
                    parameter('skipBuildPhrase', '.*\\[skip\\W+ci\\].*')
                    parameter('displayBuildErrorsOnDownstreamBuilds', false)
                    parameter('cron', 'H/5 * * * *')
                    parameter('whitelist', '')
                    parameter('orgslist', organization)
                    parameter('blackListLabels', '')
                    parameter('whiteListLabels', '')
                    parameter('allowMembersOfWhitelistedOrgsAsAdmin', false)
                    parameter('permitAll', true)
                    parameter('buildDescTemplate', '')
                    parameter('blackListCommitAuthor', '')
                    parameter('includedRegions', '')
                    parameter('excludedRegions', '')
                    parameter('onlyTriggerPhrase', false)
                    parameter('commentFilePath', '')
                    parameter('msgSuccess', '')
                    parameter('msgFailure', '')
                    parameter('commitStatusContext', '')
                }
            }
        }
        return freestyleJob
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}