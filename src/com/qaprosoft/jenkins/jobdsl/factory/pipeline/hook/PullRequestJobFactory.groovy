package com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook

import groovy.transform.InheritConstructors
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.PipelineFactory

@InheritConstructors
public class PullRequestJobFactory extends PipelineFactory {

	def organization
	def repo
	def branch
    def scmProjectUrl

    public PullRequestJobFactory(folder, pipelineScript, jobName, jobDesc, organization, repo, branch, scmProjectUrl) {

        this.folder = folder
        this.pipelineScript = pipelineScript
        this.name = jobName
        this.description = jobDesc
		this.organization = organization
		this.repo = repo
		this.branch = branch
        this.scmProjectUrl = scmProjectUrl
    }

	def create() {
		def pipelineJob = super.create()
		pipelineJob.with {
            parameters {
                stringParam('repo', repo, 'Your GitHub repository for scanning')
            }
            scm {
                git {
                    remote {
                        url(scmProjectUrl)
                    }
                }
            }
			properties {
				githubProjectUrl(scmProjectUrl)
				//TODO: test with removed "cron('H/5 * * * *')"
				pipelineTriggers {
					triggers {
						ghprbTrigger {
							gitHubAuthId(getGitHubAuthId(repo))
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

	protected def getGitHubAuthId(repo) {
		return "https://api.github.com : ${repo}-token"
	}
}