package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
public class TriggerJobFactory extends PipelineFactory {
	def project

    public TriggerJobFactory(folder, pipelineScript, jobName, jobDesc, project) {
        this.folder = folder
		this.pipelineScript = pipelineScript
		this.name = jobName
        this.description = jobDesc
		this.project = project
    }

    def create() {

        def pipelineJob = super.create()

        pipelineJob.with {
			scm {
				git {
					branch("master")
					remote {
						url("https://github.com/qaprosoft/carina-demo")
					}
				}
			}
			
            properties {
                githubProjectUrl('https://github.com/qaprosoft/carina/')
                pipelineTriggers {
                    triggers {
                        githubPush()
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
                            orgslist(getOrganization())
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

			//TODO: think about other parameters to support DevOps CI operations
            parameters {
				stringParam('project', project, 'Your GitHub repository for scanning')
				//TODO: analyze howto support several gc_GIT_BRACH basing on project
				configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", "master")
				booleanParam('onlyUpdated', true, '	If chosen, scan will be performed only in case of any change in *.xml suites.')
				
				choiceParam('removedConfigFilesAction', ['IGNORE', 'DELETE'], '')
				choiceParam('removedJobAction', ['IGNORE', 'DELETE'], '')
				choiceParam('removedViewAction', ['IGNORE', 'DELETE'], '')
            }

        }
        return pipelineJob
    }

    protected def getOrganization() {
        return 'qaprosoft'
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}