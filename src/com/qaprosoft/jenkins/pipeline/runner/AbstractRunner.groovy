package com.qaprosoft.jenkins.pipeline.runner

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM

public abstract class AbstractRunner {
    protected def context
    protected ISCM scmClient
    protected ISCM scmSshClient
	
	protected FactoryRunner factoryRunner // object to be able to start JobDSL anytime we need
    protected Logger logger
	
    //this is very important line which should be declared only as a class member!
    protected Configuration configuration = new Configuration(context)

    public AbstractRunner(context) {
        this.context = context
        this.logger = new Logger(context)
		this.factoryRunner = new FactoryRunner(context)
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
	
	protected void setDslClasspath(additionalClasspath) {
		factoryRunner.setDslClasspath(additionalClasspath)
	}

}
