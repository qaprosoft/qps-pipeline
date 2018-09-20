package com.qaprosoft.jenkins.pipeline

public class Runner {
	protected def context
	protected ISCM scmClient
	protected Configuration configuration = new Configuration(context)

	public Runner(context) {
		this.context = context
		scmClient = new GitHub(context)
	}

	//Events
	public void onPush() {
		context.node("master") {
			context.stage("Runner->onPush") { context.println("Runner->onPush") }
		}
	}

	public void onPullRequest() {
		context.node("master") {
			context.stage("Runner->onPullRequest") { context.println("Runner->onPullRequest") }
		}
	}
}
