package com.qaprosoft.jenkins.pipeline.tools.scm

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.Configuration

import static com.qaprosoft.jenkins.pipeline.Executor.*
import static com.qaprosoft.jenkins.Utils.*


abstract class Scm implements ISCM {

	protected def context
	protected def logger

	protected def prRefSpec
	protected def branchSpec
	
	protected def host
	protected def org
	protected def repo
	protected def apiUrl
	protected def sshUrl
	protected def oauthToken

	Scm(context) { 
		this.context = context
		this.loger = new Logger(context)
	}

	Scm(context, host, org, repo, oauthToken="") {
		this.context = context
		this.host = host
		this.org = org
		this.repo = repo
		this.oauthToken = oauthToken
	}

	protected abstract String getHtmlUrl()
	protected abstract String getBranchSpec(branch)

	public def clone() {
        clone(true)
    }

    public def clone(isShallow) {
        context.stage('Checkout Scm Repository') {
            logger.info("Scm->clone")
            def fork = !isParamEmpty(Configuration.get("fork")) ? Configuration.get("fork").toBoolean() : false
            def branch = Configuration.get("branch")
            def repo = Configuration.get("repo")
            def userId = Configuration.get("BUILD_USER_ID")
            def gitUrl = Configuration.resolveVars(scmUrl)
            def credentialsId = Configuration.get("GITHUB_ORGANIZATION") + "-" + repo
            logger.info("SCM_HOST: " + Configuration.get("GITHUB_HOST"))
            logger.info("SCM_ORGANIZATION: " + Configuration.get("GITHUB_ORGANIZATION"))
            logger.info("SCM_URL: " + gitUrl)
            logger.info("CREDENTIALS_ID: " + credentialsId)
            if (fork) {
                def tokenName = 'token_' + "${userId}"
                def userCredentials = getCredentials(tokenName)
                if (userCredentials) {
                    def userName = ""
                    def userPassword = ""
                    context.withCredentials([context.usernamePassword(credentialsId: tokenName, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        gitUrl = "https://${scmHost}/${context.env.USERNAME}/${repo}"
                        credentialsId = tokenName
                        userName = context.env.USERNAME
                        userPassword = context.env.PASSWORD
                    }
                    logger.debug("tokenName: ${tokenName}; name: ${userName}; password: ${userPassword}")
                } else {
                    throw new RuntimeException("Unable to run from fork repo as ${tokenName} token is not registered on CI!")
                }

            }
            Map scmVars = context.checkout getCheckoutParams(gitUrl, branch, null, isShallow, true, "+refs/heads/${branch}:refs/remotes/origin/${branch}", credentialsId)
            Configuration.set("scm_url", scmVars.GIT_URL)
            Configuration.set("scm_branch", branch)
            Configuration.set("scm_commit", scmVars.GIT_COMMIT)
        }
    }


    public def clone(gitUrl, branch, subFolder) {
        context.stage('Checkout GitHub Repository') {
            logger.info("GitHub->clone\nGIT_URL: ${gitUrl}\nbranch: ${branch}")
            context.checkout getCheckoutParams(gitUrl, branch, subFolder, true, false, "+refs/heads/${branch}:refs/remotes/origin/${branch}", credentialsId)
        }
    }

    public def clonePR() {
        context.stage('Checkout GitHub Repository') {
            def branch = Configuration.get("pr_source_branch")
            def prNumber = Configuration.get('pr_number')
            def gitUrl = Configuration.resolveVars(scmUrl)
            logger.info("GitHub->clonePR\nGIT_URL: ${gitUrl}\nbranch: ${branch}")
            context.checkout getCheckoutParams(gitUrl, branchSpec, ".", true, false, prRefSpec, credentialsId)
        }
    }

    public def clonePush() {
        context.stage('Checkout GitHub Repository') {
            def branch = Configuration.get("branch")
            def gitUrl = Configuration.resolveVars(scmUrl)
            logger.info("GitHub->clone\nGIT_URL: ${gitUrl}\nbranch: ${branch}")
            context.checkout getCheckoutParams(gitUrl, branch, null, false, true, "+refs/heads/${branch}:refs/remotes/origin/${branch}", credentialsId)
        }
    }

    protected def getCheckoutParams(gitUrl, branch, subFolder, shallow, changelog, refspecValue, credentialsIdValue) {
        def checkoutParams = [scm      : [$class                           : 'GitSCM',
                                          branches                         : [[name: branch]],
                                          doGenerateSubmoduleConfigurations: false,
                                          extensions                       : [[$class: 'CheckoutOption', timeout: 15],
                                                                              [$class: 'CloneOption', noTags: true, reference: '', shallow: shallow, timeout: 15]],
                                          submoduleCfg                     : [],
                                          userRemoteConfigs                : [[url: gitUrl, refspec: refspecValue, credentialsId: credentialsIdValue]]],
                              changelog: changelog,
                              poll     : false]
        if (subFolder != null) {
            def subfolderExtension = [[$class: 'RelativeTargetDirectory', relativeTargetDir: subFolder]]
            checkoutParams.get("scm")["extensions"] = subfolderExtension
        }
        return checkoutParams
    }

    public def mergePR() {
        //merge pull request
        def org = Configuration.get("GITHUB_ORGANIZATION")
        def repo = Configuration.get("repo")
        def ghprbPullId = Configuration.get("ghprbPullId")

        def ghprbCredentialsId = Configuration.get("ghprbCredentialsId")
        logger.info("ghprbCredentialsId: " + ghprbCredentialsId)
        context.withCredentials([context.usernamePassword(credentialsId: "${ghprbCredentialsId}", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            logger.debug("USERNAME: ${context.env.USERNAME}")
            logger.debug("PASSWORD: ${context.env.PASSWORD}")
            logger.debug("curl -u ${context.env.USERNAME}:${context.env.PASSWORD} -X PUT -d '{\"commit_title\": \"Merge pull request\"}'  https://api.github.com/repos/${org}/${repo}/pulls/${ghprbPullId}/merge")
            //context.sh "curl -X PUT -d '{\"commit_title\": \"Merge pull request\"}'  https://api.github.com/repos/${org}/${repo}/pulls/${ghprbPullId}/merge?access_token=${context.env.PASSWORD}"
            context.sh "curl -u ${context.env.USERNAME}:${context.env.PASSWORD} -X PUT -d '{\"commit_title\": \"Merge pull request\"}'  https://api.github.com/repos/${org}/${repo}/pulls/${ghprbPullId}/merge"
        }
    }


}