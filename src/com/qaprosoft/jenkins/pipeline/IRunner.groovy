package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.ISCM
import com.qaprosoft.scm.github.GitHub;

trait IRunner {
	protected def context
	protected ISCM scmClient
	protected Configuration configuration = new Configuration(context)

	abstract void onPush()
	abstract void onPullRequest()
}
