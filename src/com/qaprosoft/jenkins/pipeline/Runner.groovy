package com.qaprosoft.jenkins.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
class Runner extends Executor {
	public Runner(context) {
		super(context)
	}
	
	//Events
	public void onPush() {
		context.println("Runner->onPush")
		// handle each push/merge operation
	}

	public void onPullRequest() {
		context.println("Runner->onPullRequest")

    }
}
