package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Scanner;

class Repository extends Executor {

	protected Scanner scanner;

	public Repository(context) {
		super(context)
		this.context = context

		scanner = new Scanner(context);
	}

	public void create() {
		context.println("Repository->create")
		// Create folder/views/jobs based on repo content

		// 1. Create pr_checker/merger job for concrete repo
		// 2. launch scanner for each merge/push operation

		scanner.scanRepository() //uncheck onlyUpdated here for execution
	}

	public void update() {
		context.println("Repository->update")
		//global runner after each GitHub Webhook trigger
		// try to define trigger reason and execute appropriate event handler, for example
		// if it was triggered by SCM_TRIGGER and pull request checker then onPullrequest should be executed

		//TODO: hardcoded onUpdate event call to test
		onUpdate()
	}

	//Events
	protected void onUpdate() {
		context.println("Repository->onUpdate")
		// handle each push/merge operation
		// execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
		scanner.scanRepository()
	}

	protected void onPullRequest() {
		context.println("Repository->onPullRequest")
		verify()
	}

	protected void verify() {
		context.println("Repository->verify")
	}
	
	protected void compile() {
		context.println("Repository->compile")
	}

	protected void deploy() {
		context.println("Repository->deploy")
	}

	protected void test() {
		context.println("Repository->test")
	}



}