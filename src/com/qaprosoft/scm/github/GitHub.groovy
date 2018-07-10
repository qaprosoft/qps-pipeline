package com.qaprosoft.scm.github


import com.qaprosoft.scm.ISCM
import com.qaprosoft.jenkins.repository.pipeline.v2.Configurator

class GitHub implements ISCM {
	private def context;
	
	public GitHub(context) {
		this.context = context
	}
	
	public void clone(params, vars) {
		context.stage('Checkout GitHub Repository') {
			context.println("GitHub->clone")

			boolean fork = Configurator.get("fork").toBoolean()
			def branch = Configurator.get("branch")
			def project = Configurator.get("project")

			def GITHUB_SSH_URL = Configurator.get("GITHUB_SSH_URL")
			def userId = Configurator.get("BUILD_USER_ID")
			//context.println("userId: ${userId}")
			def GITHUB_HOST = Configurator.get("GITHUB_HOST")

			def gitUrl = "${GITHUB_SSH_URL}/${project}"
			context.println("gitUrl: " + gitUrl)
			context.println("forked_repo: " + fork)
			if (!fork) {
				context.println("I SHOULDN BE HERE")
				context.checkout scm: [$class: 'GitSCM', branches: [[name: '${branch}']], \
						doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CheckoutOption', timeout: 15], [$class: 'CloneOption', noTags: true, reference: '', shallow: true, timeout: 15]], \
						submoduleCfg: [], userRemoteConfigs: [[url: gitUrl]]], \
						changelog: false, poll: false
			} else {
				context.println("I SHOULDN'T BE HERE")
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
	
}