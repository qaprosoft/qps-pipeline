package com.qaprosoft.jenkins.pipeline


public class Runner extends Executor {
	
	public Runner(context) {
		super(context)
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
