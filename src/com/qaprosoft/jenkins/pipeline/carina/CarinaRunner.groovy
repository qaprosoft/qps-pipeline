package com.qaprosoft.jenkins.pipeline.carina

class CarinaRunner {
	protected def context

    public CarinaRunner(context) {
		this.context = context
    }
	
	//Events
	//@Override
	public void onPush() {
		context.println("CarinaRunner->onPush")
		// handle each push/merge operation
		// execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
		context.println("TODO: implement snapshot build generation and emailing build number...")
	}
	
}