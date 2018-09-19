package com.qaprosoft.jenkins.pipeline



@InheritConstructors
public class Runner {
	protected def context
	
	//Events
	public void onPush() {
//		context.println("Runner->onPush")
	}

	public void onPullRequest() {
//		context.println("Runner->onPullRequest")
    }
}
