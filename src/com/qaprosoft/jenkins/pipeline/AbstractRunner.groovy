package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.ISCM

public abstract class AbstractRunner {
	protected def context
	protected ISCM scmClient

	protected final def FACTORY_TARGET = "qps-pipeline/src/com/qaprosoft/jenkins/jobdsl/Factory.groovy"
	protected def additionalClasspath = "qps-pipeline/src"

	
	//this is very important line which should be declared only as a class member!
	protected Configuration configuration = new Configuration(context)
	
	public AbstractRunner(context) {
		this.context = context
	}

	//Methods
	abstract public void build()
	
	//Events
	abstract public void onPush()
	abstract public void onPullRequest()
	
}
