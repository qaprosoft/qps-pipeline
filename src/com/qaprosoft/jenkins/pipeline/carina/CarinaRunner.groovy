package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.Runner

class CarinaRunner extends Runner {

    public CarinaRunner(context) {
        super(context)
    }
	
	public void onPush() {
		context.println("CarinaRunner->onPush")
		// handle each push/merge operation
		// execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
		context.println("TODO: implement snapshot build generation and emailing build number...")
	}
	

}