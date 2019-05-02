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
            }
            scm {
                git {
                    remote {
                        url(scmRepoUrl)
                    }
                }
            }
            properties {
                githubProjectUrl(scmRepoUrl)
                //TODO: test with removed "cron('H/5 * * * *')"
                pipelineTriggers {
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
                }
            }

        }
        return pipelineJob
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}