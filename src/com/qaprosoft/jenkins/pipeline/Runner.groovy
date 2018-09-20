package com.qaprosoft.jenkins.pipeline

public class Runner {
	protected def context
	
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
