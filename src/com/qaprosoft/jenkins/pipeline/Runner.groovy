package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Configuration
import com.cloudbees.groovy.cps.NonCPS

public class Runner {
	protected def context
	protected Configuration configuration = new Configuration(context)
	
	public Runner(context) {
		this.context = context
	}
	
	//Events
	public void onPush() {
		context.stage("Runner->onPush") {
			context.println("Runner->onPush")
		}
	}

	public void onPullRequest() {
		context.stage("Runner->onPullRequest") {
			context.println("Runner->onPullRequest")
		}
    }
}
