package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.ISCM
import com.qaprosoft.scm.github.GitHub;

import com.cloudbees.groovy.cps.NonCPS

class Runner implements IRunner {
	public Runner(context) {
		this.context = context
		configuration = new Configuration(context)
		scmClient = new GitHub(context)
	}

	//Events
	public void onPush() {
		context.node("master") {
			//TODO: incorporate onlyUpdated
			scmClient.clone()
			context.println("Runner->onPush")
		}
	}

	public void onPullRequest() {
		context.node("master") {
			scmClient.clonePR()
			context.println("Runner->onPullRequest")
		}
	}
}
