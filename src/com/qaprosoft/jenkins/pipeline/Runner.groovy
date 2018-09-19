package com.qaprosoft.jenkins.pipeline

public class abstract Runner {
	protected def context
	
	public Runner(context) {
		this.context = context
	}
	
	//Events
	public void onPush() {
		context.println("Runner->onPush")
	}

	public void onPullRequest() {
		context.println("Runner->onPullRequest")
    }
}
