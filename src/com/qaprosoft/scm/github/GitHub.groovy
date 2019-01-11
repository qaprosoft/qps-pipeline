package com.qaprosoft.scm.github

import com.qaprosoft.Logger
import com.qaprosoft.scm.ISCM
import com.qaprosoft.jenkins.pipeline.Configuration

class GitHub implements ISCM {

    private def context
    private def gitHtmlUrl
    private def gitSshUrl
	private def credentialsId
	private Logger logger

	public GitHub(context) {
		this.context = context
        logger = new Logger(context)
		gitHtmlUrl = "https://\${GITHUB_HOST}/\${GITHUB_ORGANIZATION}/${Configuration.get("repo")}"
		gitSshUrl = "git@\${GITHUB_HOST}:\${GITHUB_ORGANIZATION}/${Configuration.get("project")}"
		credentialsId = "${Configuration.get("organization")}-${Configuration.get("repo")}"
    }

    public def clone() {
        clone(true)
    }

	public def clone(isShallow) {
		context.stage('Checkout GitHub Repository') {
            logger.info("GitHub->clone")

            def fork = parseFork(Configuration.get("fork"))
            def branch = Configuration.get("branch")
            def repo = Configuration.get("repo")
            def userId = Configuration.get("BUILD_USER_ID")
			Configuration.set(Configuration.Parameter.GITHUB_ORGANIZATION, Configuration.get("organization"))
			logger.info("GITHUB_HOST: " + Configuration.get("GITHUB_HOST"))
			logger.info("GITHUB_ORGANIZATION: " + Configuration.get("GITHUB_ORGANIZATION"))
			logger.info("gitHtmlUrl: " + gitHtmlUrl)

            def gitUrl = Configuration.resolveVars(gitHtmlUrl)

            logger.info("GIT_URL: " + gitUrl)
            logger.debug("forked_repo: " + fork)

			Map scmVars
			if (!fork) {
                scmVars = context.checkout getCheckoutParams(gitUrl, branch, null, isShallow, true, '', credentialsId)
			} else {
				def token_name = 'token_' + "${userId}"
                logger.debug("token_name: " + token_name)

				//register into the Configuration.vars personal token of the current user
				def token_value = context.env.getEnvironment().get(token_name)

				//if token_value contains ":" as delimiter then redefine build_user_id using the 1st part
				if (token_value != null && token_value.contains(":")) {
					def (tempUserId, tempToken) = token_value.tokenize( ':' )
					userId = tempUserId
					token_value =  tempToken
				}
                if (token_value != null) {
                    def GITHUB_HOST = Configuration.get(Configuration.Parameter.GITHUB_HOST)
					gitUrl = "https://${token_value}@${GITHUB_HOST}/${userId}/${repo}"
                    logger.info("fork repo url: ${gitUrl}")
                    scmVars = context.checkout getCheckoutParams(gitUrl, branch, null, isShallow, true, '', credentialsId)
				} else {
					throw new RuntimeException("Unable to run from fork repo as ${token_name} token is not registered on CI!")
				}
			}

            //TODO: remove git_branch after update ZafiraListener: https://github.com/qaprosoft/zafira/issues/760
            Configuration.set("scm_url", scmVars.GIT_URL)
            Configuration.set("scm_branch", branch)
            Configuration.set("scm_commit", scmVars.GIT_COMMIT)
        }
	}


	public def clone(gitUrl, branch, subFolder) {
		context.stage('Checkout GitHub Repository') {
            logger.info("GitHub->clone\nGIT_URL: ${gitUrl}\nbranch: ${branch}")
            context.checkout getCheckoutParams(gitUrl, branch, subFolder, true, false, '', credentialsId)
		}
	}

	public def clonePR(){
		context.stage('Checkout GitHub Repository') {
			Configuration.set(Configuration.Parameter.GITHUB_ORGANIZATION, Configuration.get("organization"))
			def branch  = Configuration.get("sha1")
			def gitUrl = Configuration.resolveVars(gitHtmlUrl)
            logger.info("GitHub->clonePR\nGIT_URL: ${gitUrl}\nbranch: ${branch}")
			context.checkout getCheckoutParams(gitUrl, branch, ".", true, false, '+refs/pull/*:refs/remotes/origin/pr/*', credentialsId)
		}
	}

    public def clonePush() {
        context.stage('Checkout GitHub Repository') {
			Configuration.set(Configuration.Parameter.GITHUB_ORGANIZATION, Configuration.get("organization"))
            def branch = Configuration.get("branch")
			def gitUrl = Configuration.resolveVars(gitHtmlUrl)
            logger.info("GitHub->clone\nGIT_URL: ${gitUrl}\nbranch: ${branch}")
            context.checkout getCheckoutParams(gitUrl, branch, null, false, true, '', credentialsId)
        }
    }

    private def getCheckoutParams(gitUrl, branch, subFolder, shallow, changelog, refspecValue, credentialsIdValue) {
        def checkoutParams = [scm: [$class: 'GitSCM',
                                    branches: [[name: branch]],
                                    doGenerateSubmoduleConfigurations: false,
                                    extensions: [[$class: 'CheckoutOption', timeout: 15],
                                                 [$class: 'CloneOption', noTags: true, reference: '', shallow: shallow, timeout: 15]],
                                    submoduleCfg: [],
                                    userRemoteConfigs: [[url: gitUrl, refspec: refspecValue, credentialsId: credentialsIdValue]]],
                              changelog: changelog,
                              poll: false]
        if (subFolder != null) {
            def subfolderExtension = [[$class: 'RelativeTargetDirectory', relativeTargetDir: subFolder]]
            checkoutParams.get("scm")["extensions"] = subfolderExtension
        }
        return checkoutParams
    }

    public def mergePR(){
        //merge pull request
        def org = Configuration.get("GITHUB_ORGANIZATION")
        def repo = Configuration.get("repo")
        def ghprbPullId = Configuration.get("ghprbPullId")

        def ghprbCredentialsId = Configuration.get("ghprbCredentialsId")
        logger.info("ghprbCredentialsId: " + ghprbCredentialsId)
        context.withCredentials([context.usernamePassword(credentialsId: "${ghprbCredentialsId}", usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            logger.debug("USERNAME: ${context.env.USERNAME}")
            logger.debug("PASSWORD: ${context.env.PASSWORD}")
            logger.debug("curl -u ${context.env.USERNAME}:${context.env.PASSWORD} -X PUT -d '{\"commit_title\": \"Merge pull request\"}'  https://api.github.com/repos/${org}/${repo}/pulls/${ghprbPullId}/merge")
            //context.sh "curl -X PUT -d '{\"commit_title\": \"Merge pull request\"}'  https://api.github.com/repos/${org}/${repo}/pulls/${ghprbPullId}/merge?access_token=${context.env.PASSWORD}"
            context.sh "curl -u ${context.env.USERNAME}:${context.env.PASSWORD} -X PUT -d '{\"commit_title\": \"Merge pull request\"}'  https://api.github.com/repos/${org}/${repo}/pulls/${ghprbPullId}/merge"
        }
    }

    private boolean parseFork(fork) {
        boolean booleanFork = false
        if (fork != null && !fork.isEmpty()) {
            booleanFork = fork.toBoolean()
        }
        return booleanFork
    }
}