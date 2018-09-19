package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.scm.github.GitHub
import groovy.transform.InheritConstructors

@InheritConstructors
public class Runner extends Executor {
	
	public Runner(context) {
		super(context)
		scmClient = new GitHub(context)
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
