package com.qaprosoft.jenkins.pipeline

public class Runner {
	protected def context
	protected Configuration configuration = new Configuration(context)
	
	public Runner(context) {
		this.context = context
	}
	
	//Events
	public void onPush() {
//		context.println("Runner->onPush")
	}

	public void onPullRequest() {
//		context.println("Runner->onPullRequest")
    }
}
