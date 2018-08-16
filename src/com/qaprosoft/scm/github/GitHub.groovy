package com.qaprosoft.scm.github

import com.qaprosoft.scm.ISCM
import com.qaprosoft.jenkins.repository.pipeline.v2.Configurator

class GitHub implements ISCM {
	private def context;
	
	public GitHub(context) {
		this.context = context
	}


	public def clone() {
		context.stage('Checkout GitHub Repository') {
			context.println("GitHub->clone")

			def fork = parseFork(Configurator.get("fork"))
            def branch = Configurator.get("branch")
			def project = Configurator.get("project")
            def userId = Configurator.get("BUILD_USER_ID")
			def GITHUB_SSH_URL = Configurator.get(Configurator.Parameter.GITHUB_SSH_URL)
			def GITHUB_HOST = Configurator.get(Configurator.Parameter.GITHUB_HOST)

			def gitUrl = Configurator.resolveVars("${GITHUB_SSH_URL}/${project}")

			context.println("GIT_URL: " + gitUrl)
			context.println("forked_repo: " + fork)
			if (!fork) {
				def checkoutResult = context.checkout scm: [$class: 'GitSCM', branches: [[name: '${branch}']], \
						doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CheckoutOption', timeout: 15], [$class: 'CloneOption', depth: 2, noTags: true, reference: '', shallow: false, timeout: 15]], \
						submoduleCfg: [], userRemoteConfigs: [[url: gitUrl]]], \
						changelog: true, poll: false
				context.println("CHECKOUT RESULT: " + checkoutResult)
			} else {
				def token_name = 'token_' + "${userId}"
				//context.println("token_name: ${token_name}")
				def token_value = Configurator.get(token_name)
				//context.println("token_value: ${token_value}")
				//if token_value contains ":" as delimiter then redefine build_user_id using the 1st part
				if (token_value != null && token_value.contains(":")) {
					def (tempUserId, tempToken) = token_value.tokenize( ':' )
					userId = tempUserId
					token_value =  tempToken
				}
				if (token_value != null) {
					gitUrl = "https://${token_value}@${GITHUB_HOST}/${userId}/${project}"
					context.println "fork repo url: ${gitUrl}"
					context.checkout scm: [$class: 'GitSCM', branches: [[name: '${branch}']], \
							doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CheckoutOption', timeout: 15], [$class: 'CloneOption', noTags: true, reference: '', shallow: true, timeout: 15]], \
							submoduleCfg: [], userRemoteConfigs: [[url: gitUrl]]], \
							changelog: false, poll: false

				} else {
					throw new RuntimeException("Unable to run from fork repo as ${token_name} token is not registered on CI!")
				}
			}
			//TODO: remove git_branch after update ZafiraListener: https://github.com/qaprosoft/zafira/issues/760
			Configurator.set("git_url", gitUrl)
			Configurator.set("scm_url", gitUrl)
			//TODO: init git_commit as well
		}
	}
	
	public def clone(gitUrl, branch, subFolder) {
		context.stage('Checkout GitHub Repository') {
			context.println("GitHub->clone")

			context.println("GIT_URL: " + gitUrl)
			context.println("branch: " + branch)
			if (subFolder != null) {
				context.checkout scm: [$class: 'GitSCM', branches: [[name: branch]], \
					doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: subFolder]], \
					submoduleCfg: [], \
					userRemoteConfigs: [[url: gitUrl]]], \
				changelog: false, poll: false
			} else {
				context.checkout scm: [$class: 'GitSCM', branches: [[name: branch]], \
						doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CheckoutOption', timeout: 15], \
							[$class: 'CloneOption', noTags: true, reference: '', shallow: true, timeout: 15]], \
						submoduleCfg: [], userRemoteConfigs: [[url: gitUrl]]], \
						changelog: false, poll: false
			}
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