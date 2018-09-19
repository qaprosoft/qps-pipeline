package com.qaprosoft.jenkins.pipeline

import com.cloudbees.groovy.cps.NonCPS

public class Runner {
	protected def context
	protected Configuration configuration
	
	public Runner(context) {
		this.context = context
		this.configuration = new Configuration(context)
	}
	
	//Events
	public void onPush() {
		context.println("Runner->onPush")
	}

	public void onPullRequest() {
		context.println("Runner->onPullRequest")
    }
}
