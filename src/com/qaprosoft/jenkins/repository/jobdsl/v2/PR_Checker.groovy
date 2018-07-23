package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.pipeline.v2.Configurator

createPRCheckerJob()

void createPRCheckerJob() {
  def repositoryName = "${project}"
  def repositorySubName = "${sub_project}"
  
  println "repositoryName: ${repositoryName}"
  println "repositorySubName: ${repositorySubName}"

  def htmlUrl = Configurator.resolveVars("${GITHUB_HTML_URL}/${project}")
  def sshUrl = Configurator.resolveVars("${GITHUB_SSH_URL}/${project}.git")
  def apiUrl = Configurator.resolveVars("${GITHUB_API_URL}")
  def organization = Configurator.resolveVars("${GITHUB_ORGANIZATION}")
  
  println "htmlUrl: ${htmlUrl}"
  println "sshUrl: ${sshUrl}"
  println "apiUrl: ${apiUrl}"
  println "organization: ${organization}"


  def jobFolder = "Pull-Request-Checkers"
  folder(jobFolder) {
    displayName(jobFolder)
  }
  
  def jobName = "${jobFolder}/${repositoryName}"
    job(jobName) {
            logRotator(-1, 100)
      		concurrentBuild()
            properties {
              githubProjectUrl("${htmlUrl}")
            }
            scm {
                git {
                            remote {
                                url("${sshUrl}")
                                refspec('+refs/pull/*:refs/remotes/origin/pr/*')
                            }
                            branch('${sha1}')
		            extensions {
		                cloneOptions {
					shallow(true)
				}
		            }
                }
            }
            triggers {
                ghprbTrigger {
                	gitHubAuthId("${apiUrl} : jenkins_su - PR Checker")
                	adminlist('')
			useGitHubHooks(true)
			triggerPhrase('')
			autoCloseFailedPullRequests(false)
                	skipBuildPhrase('.*\\[skip\\W+ci\\].*')
			displayBuildErrorsOnDownstreamBuilds(false)
			cron('H/5 * * * *')
			whitelist('')
			orgslist("${organization}")
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
            wrappers {
                timestamps()
                buildName('#${BUILD_NUMBER}|${ghprbPullId}')
            }
            steps {
                maven('clean \ncompile \ntest-compile \n-f ' + "./${repositorySubName}" + '/pom.xml \n-Dmaven.test.failure.ignore=true \n-Dcom.qaprosoft.carina-core.version=${CARINA_CORE_VERSION}')
                sonarRunnerBuilder {
                  properties('sonar.github.endpoint=' + "${apiUrl}" + ' \nsonar.analysis.mode=preview \nsonar.github.pullRequest=${ghprbPullId} \nsonar.github.repository=' + "${organization}/${repositoryName} \nsonar.projectKey=${repositoryName} \nsonar.projectName=${repositoryName} " + ' \nsonar.projectVersion=1.${BUILD_NUMBER} \nsonar.github.oauth=${GITHUB_OAUTH_TOKEN} \nsonar.sources=.')
                }
            }
            publishers {
                cleanWs {}
            }
    }
}