package com.qaprosoft.jenkins.pipeline.runner.maven

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner

//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.maven.Maven
import com.qaprosoft.jenkins.pipeline.tools.maven.sonar.Sonar

@Mixin([Maven])
public class Runner extends AbstractRunner {
    Logger logger
    Sonar sonar

    public Runner(context) {
        super(context)
        scmClient = new GitHub(context)
        sonar = new Sonar(context)
        logger = new Logger(context)
    }

    //Events
    public void onPush() {
        context.node("master") {
            logger.info("Runner->onPush")
            sonar.scan()
        }

        context.node("master") {
            jenkinsFileScan()
        }
    }

	public void onPullRequest() {
		context.node("master") {
			logger.info("Runner->onPullRequest")
			sonar.setToken(getToken(Configuration.CREDS_SONAR_GITHUB_OAUTH_TOKEN))
			sonar.scan(true)
		}
	}

    //Methods
    public void build() {
        context.node("master") {
            logger.info("Runner->build")
            context.node("maven") {
                buildProject()
            }
            //TODO: we are ready to produce building for any maven project: this is maven compile install goals
            //throw new RuntimeException("Not implemented yet!")
        }
    }

}
