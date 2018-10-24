package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.Utils
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.ISCM

public abstract class AbstractRunner {
	protected def context
	protected ISCM scmClient

	protected final def FACTORY_TARGET = "qps-pipeline/src/com/qaprosoft/jenkins/jobdsl/Factory.groovy"
	protected def additionalClasspath = "qps-pipeline/src"
    protected def logLevel

	//this is very important line which should be declared only as a class member!
	protected Configuration configuration = new Configuration(context)
	
	public AbstractRunner(context) {
		this.context = context
        this.logLevel = Configuration.get(Configuration.Parameter.PIPELINE_LOG_LEVEL)
	}

	//Methods
	abstract public void build()
	
	//Events
	abstract public void onPush()
	abstract public void onPullRequest()


	public debug(String message){
		context.printf Utils.debug(logLevel, message)
	}

	public info(String message){
		context.printf Utils.info(logLevel, message)
	}

	public warn(String message){
		context.printf Utils.warn(logLevel, message)
	}

	public error(String message){
		context.printf Utils.error(logLevel, message)
	}

}
