package com.qaprosoft.jenkins.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
public class Runner extends Executor {
	
	public Runner(context) {
		super(context)
	}
	
	//Events
	public void onPush() {
		context.println("core: " + Configuration.get("CARINA_CORE_VERSION"))
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
