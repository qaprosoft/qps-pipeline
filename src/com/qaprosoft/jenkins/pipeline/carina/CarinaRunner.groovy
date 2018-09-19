package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.Runner
import groovy.transform.InheritConstructors

@InheritConstructors
class CarinaRunner extends Runner {

    public CarinaRunner(context) {
        super(context)
    }
	
	//Events
	@Override
	public void onPush() {
		context.println("CarinaRunner->onPush")
		// handle each push/merge operation
		// execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
		context.println("TODO: implement snapshot build generation and emailing build number...")
	}
	
}