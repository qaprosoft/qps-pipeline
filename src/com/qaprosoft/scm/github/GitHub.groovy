package com.qaprosoft.scm.github


import com.qaprosoft.scm.ISCM

class GitHub implements ISCM {
	private def context;
	
	public GitHub(context) {
		this.context = context
	}
	
	public void clone(params, vars) {
		context.stage('Checkout GitHub Repository') {
			context.println("GitHub->clone")
			def fork = params.get("fork")
			def branch = params.get("branch")
			def project = params.get("project")

			def GITHUB_SSH_URL = vars.get("GITHUB_SSH_URL")
			def userId = vars.get("BUILD_USER_ID")
			context.println("userId: ${userId}")
			userId = vars.get("ci_user_id")
			context.println("userId: ${userId}")
			def GITHUB_HOST = vars.get("GITHUB_HOST")

			def gitUrl = "${GITHUB_SSH_URL}/${project}"
			context.println("gitUrl: " + gitUrl)
			context.println("forked_repo: " + fork)
			if (!fork) {
				context.checkout scm: [$class: 'GitSCM', branches: [[name: '${branch}']], \
						doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CheckoutOption', timeout: 15], [$class: 'CloneOption', noTags: true, reference: '', shallow: true, timeout: 15]], \
						submoduleCfg: [], userRemoteConfigs: [[url: gitUrl]]], \
						changelog: false, poll: false
			} else {
				def token_name = 'token_' + "${userId}"
				context.println("token_name: ${token_name}")
				def token_value = vars.get(token_name)
				//if token_value contains ":" as delimiter then redefine build_user_id using the 1st part
				if (token_value.contans(":")) {
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
			params.put("git_url", gitUrl)
			params.put("scm_url", gitUrl)
			//TODO: init git_commit as well
		}
	}
	
}