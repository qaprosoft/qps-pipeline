package com.qaprosoft.jenkins.pipeline.runner

import com.qaprosoft.jenkins.BaseObject
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM

public abstract class AbstractRunner extends BaseObject {
    protected ISCM scmClient
    protected ISCM scmSshClient
	
    public AbstractRunner(context) {
		super(context)
    }

    //Methods
    abstract public void build()

    //Events
    abstract public void onPush()
    abstract public void onPullRequest()

    protected void jenkinsFileScan() {
		if (!context.fileExists('Jenkinsfile')) {
			// do nothing
			return
		}
		
        context.stage('Jenkinsfile Stage') {
            context.script { 
                context.jobDsl targets: 'Jenkinsfile'
            }
        }
    }

}
