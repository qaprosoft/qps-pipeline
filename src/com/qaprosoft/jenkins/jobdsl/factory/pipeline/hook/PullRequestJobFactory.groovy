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
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                stringParam('repo', repo, 'Your GitHub repository for scanning')

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

                configure addHiddenParameter('gitHubAuthId', '', '')
                configure addHiddenParameter('adminlist', '', '')
                configure addHiddenParameter('useGitHubHooks', '', '')
                configure addHiddenParameter('triggerPhrase', '', '')
                configure addHiddenParameter('autoCloseFailedPullRequests', '', '')
                configure addHiddenParameter('skipBuildPhrase', '', '')
                configure addHiddenParameter('displayBuildErrorsOnDownstreamBuilds', '', '')
                configure addHiddenParameter('cron', '', '')
                configure addHiddenParameter('whitelist', '', '')
                configure addHiddenParameter('orgslist', '', '')
                configure addHiddenParameter('blackListLabels', '', '')
                configure addHiddenParameter('whiteListLabels', '', '')
                configure addHiddenParameter('allowMembersOfWhitelistedOrgsAsAdmin', '', '')
                configure addHiddenParameter('permitAll', '', '')
                configure addHiddenParameter('buildDescTemplate', '', '')
                configure addHiddenParameter('blackListCommitAuthor', '', '')
                configure addHiddenParameter('includedRegions', '', '')
                configure addHiddenParameter('excludedRegions', '', '')
                configure addHiddenParameter('onlyTriggerPhrase', '', '')
                configure addHiddenParameter('commentFilePath', '', '')
                configure addHiddenParameter('msgSuccess', '', '')
                configure addHiddenParameter('msgFailure', '', '')
                configure addHiddenParameter('commitStatusContext', '', '')
            }
            scm {
                git {
                    remote {
                        url(scmRepoUrl)
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