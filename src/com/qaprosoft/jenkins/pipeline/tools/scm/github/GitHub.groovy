package com.qaprosoft.jenkins.pipeline.tools.scm.github

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.Configuration
import static com.qaprosoft.jenkins.pipeline.Executor.*
import static com.qaprosoft.jenkins.Utils.*

class GitHub implements ISCM {

    protected def context
    protected def scmHtmlUrl
    protected def credentialsId
    protected Logger logger
    protected def scmHost
    protected def prRefSpec

    public GitHub(context) {
        this.context = context
        logger = new Logger(context)
        scmHost = Configuration.get(Configuration.Parameter.GITHUB_HOST)

        switch (scmHost) {
            case ~/.*upnetix.*/:
                scmHtmlUrl = "https://\${GITHUB_HOST}/scm/\${GITHUB_ORGANIZATION}/${Configuration.get("repo")}"
                break;
            case ~/.*gitlab.*/:
                scmHtmlUrl = "https://\${GITHUB_HOST}/\${GITHUB_ORGANIZATION}/${Configuration.get("repo")}"
                prRefSpec = "+refs/merge-requests/*:refs/remotes/origin/merge-requests/*"
                break;
            case ~/.*io.*/:
                scmHtmlUrl = "https://\${GITHUB_HOST}/scm/\${GITHUB_ORGANIZATION}/${Configuration.get("repo")}"
                break;
            default: //github by default
                scmHtmlUrl = "https://\${GITHUB_HOST}/\${GITHUB_ORGANIZATION}/${Configuration.get("repo")}"
                prRefSpec = "+refs/pull/*:refs/remotes/origin/pr/*"
                break;
        }

        //TODO: remove credentialsId setup here or replace by scmOrg after final migration
        //this.credentialsId = "${Configuration.get("GITHUB_ORGANIZATION")}-${Configuration.get("repo")}"
        if (Configuration.get("repo") != null) {
            this.credentialsId = "${Configuration.get("GITHUB_ORGANIZATION")}-${Configuration.get("repo")}"
        } else {
            this.credentialsId = null
        }

        if (Configuration.get("scmURL") != null) {
            scmHtmlUrl = Configuration.get("scmURL")
            credentialsId = ''
        }
    }

    public def clone() {
        clone(true)
    }

    public def clone(isShallow) {
        context.stage('Checkout GitHub Repository') {
            logger.info("GitHub->clone")
            def fork = !isParamEmpty(Configuration.get("fork")) ? Configuration.get("fork").toBoolean() : false
            def branch = Configuration.get("SCM_BRANCH")
            def repo = Configuration.get("SCM_REPO")
            def userId = Configuration.get("BUILD_USER_ID")
            def gitUrl = Configuration.resolveVars(scmHtmlUrl)
            def credentialsId = Configuration.get("SCM_ORG") + "-" + repo
            logger.info("GITHUB_HOST: " + Configuration.get("SCM_HOST"))
            logger.info("GITHUB_ORGANIZATION: " + Configuration.get("SCM_ORG"))
            logger.info("GIT_URL: " + gitUrl)
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
            def gitUrl = Configuration.resolveVars(scmHtmlUrl)
            logger.info("GitHub->clonePR\nGIT_URL: ${gitUrl}\nbranch: ${branch}")
            context.checkout getCheckoutParams(gitUrl, "origin/pr/${prNumber}/merge", ".", true, false, prRefSpec, credentialsId)
        }
    }

    public def clonePush() {
        context.stage('Checkout GitHub Repository') {
            def branch = Configuration.get("SCM_BRANCH")
            def gitUrl = Configuration.resolveVars(scmHtmlUrl)
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
        def org = Configuration.get("SCM_ORG")
        def repo = Configuration.get("SCM_REPO")
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

    public def setUrl(url) {
        scmHtmlUrl = url
    }
}
