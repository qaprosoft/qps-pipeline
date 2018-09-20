package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.ISCM
import com.qaprosoft.scm.github.GitHub;

public abstract class AbstractRunner {
	protected def context
	protected ISCM scmClient
	
	//this is very important line which should be declared only as a class member!
	protected Configuration configuration = new Configuration(context)

	//Events
	abstract public void onPush()
	abstract public void onPullRequest()
}
