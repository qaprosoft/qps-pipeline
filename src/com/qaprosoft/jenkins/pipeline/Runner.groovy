package com.qaprosoft.jenkins.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
public class Runner extends Executor {
	protected def uuid
	
	public Runner(context) {
		super(context)
		uuid = "qwe"
	}
	
	//Events
	public void onPush() {
		context.println(uuid)
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
