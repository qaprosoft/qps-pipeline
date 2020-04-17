package com.qaprosoft.jenkins

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.Configuration

/*
 * BaseObject to operate with pipeline context, loggers and runners
 */
public abstract class BaseObject {
	protected def context
	protected Logger logger
	protected FactoryRunner factoryRunner // object to be able to start JobDSL anytime we need

	//this is very important line which should be declared only as a class member!
	protected Configuration configuration = new Configuration(context)

	public BaseObject(context) {
		this.context = context
		this.logger = new Logger(context)
		this.factoryRunner = new FactoryRunner(context)
	}

	//TODO: [VD] think about moving into AbstractRunner!
	protected void setDslClasspath(additionalClasspath) {
		factoryRunner.setDslClasspath(additionalClasspath)
	}

}
