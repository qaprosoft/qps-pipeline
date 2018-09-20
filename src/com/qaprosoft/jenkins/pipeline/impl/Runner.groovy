package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.ISCM
import com.qaprosoft.scm.github.GitHub;
import com.qaprosoft.jenkins.pipeline.AbstractRunner

public class Runner extends AbstractRunner {
	
	public Runner(context) {
		super(context)
		scmClient = new GitHub(context)
	}

	//Events
	public void onPush() {
		context.node("master") {
			//TODO: incorporate onlyUpdated
			boolean shadowClone = !Configuration.get("onlyUpdated").toBoolean()
			context.println("shadowClone: " + shadowClone)
			scmClient.clone(shadowClone)
			context.println("Runner->onPush")
		}
	}

	public void onPullRequest() {
		context.node("master") {
			scmClient.clonePR()
			context.println("Runner->onPullRequest")
		}
	}
}
