package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.Runner
import groovy.transform.InheritConstructors

@InheritConstructors
class CarinaRunner extends Runner{
	protected def context

    public CarinaRunner(context) {
        super(context)
        this.context = context
    }

    @Override
	public void onPush() {
		context.println("CarinaRunner->onPush")
        scanner.updateRepository()
		// handle each push/merge operation
		// execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
		context.println("TODO: implement snapshot build generation and emailing build number...")
	}
}