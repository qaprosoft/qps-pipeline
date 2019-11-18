package com.qaprosoft.jenkins.jobdsl.factory.job.hook

import com.qaprosoft.jenkins.jobdsl.factory.pipeline.PipelineFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class PullRequestJobFactory extends PipelineFactory {

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
        return freestyleJob
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}