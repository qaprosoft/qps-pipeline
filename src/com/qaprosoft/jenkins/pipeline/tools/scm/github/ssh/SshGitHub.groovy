package com.qaprosoft.jenkins.pipeline.tools.scm.github.ssh

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.Configuration
import static com.qaprosoft.jenkins.pipeline.Executor.*
import static com.qaprosoft.jenkins.Utils.*
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub

class SshGitHub extends GitHub {

    public SshGitHub(context) {
        super(context)
        gitHtmlUrl = "git@\${GITHUB_HOST}:\${GITHUB_ORGANIZATION}/${Configuration.get("repo")}"
    }

    public def push(source, target, isForce) {
        def organization = Configuration.get("GITHUB_ORGANIZATION")
        def host = Configuration.get("GITHUB_HOST")
        def repo = Configuration.get("repo")
        def credentialsId = organization + "-" + repo
        logger.info("credentialsId: " + credentialsId)
        context.withCredentials([context.usernamePassword(credentialsId: credentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            logger.debug("USERNAME: ${context.env.USERNAME}")
            logger.debug("PASSWORD: ${context.env.PASSWORD}")
            context.sh "git remote set-url origin https://${context.env.USERNAME}:${context.env.PASSWORD}@${host}/${organization}/${repo}.git"
            context.sh "git checkout -B ${source}"
            context.sh "git gc"
            context.sh "git pull -v --progress origin ${source}"
            def forceArg = isForce ? "--force" : ""
            context.sh "git push ${forceArg} --progress origin HEAD:${target}"
        }
    }	

    public def setUrl(url) {
        gitHtmlUrl = url
    }

    @Override
    public def clone(isShallow) {
        context.stage('Checkout GitHub Repository') {
            logger.info("GitHub->clone")

            def branch = Configuration.get("branch")
            def repo = Configuration.get("repo")
            def gitUrl = Configuration.resolveVars(gitHtmlUrl)
            logger.info("GITHUB_HOST: " + Configuration.get("GITHUB_HOST"))
            logger.info("GITHUB_ORGANIZATION: " + Configuration.get("GITHUB_ORGANIZATION"))
            logger.info("GIT_URL: " + gitUrl)

            Map scmVars = context.checkout getCheckoutParams(gitUrl, branch, null, isShallow, true, '', '')
            Configuration.set("scm_url", scmVars.GIT_URL)
            Configuration.set("scm_branch", branch)
            Configuration.set("scm_commit", scmVars.GIT_COMMIT)
        }
    }

}
