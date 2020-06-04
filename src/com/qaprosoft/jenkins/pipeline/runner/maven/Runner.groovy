package com.qaprosoft.jenkins.pipeline.runner.maven

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner

//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.maven.sonar.Sonar

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
        Maven([
            node:"master",
            methods:[
                logger.info("Runner->onPush"),
                sonar.scan(),
                jenkinsFileScan()
                ]
            ]
        )
    }

	public void onPullRequest() {
        Maven([
            node:"master",
            methods:[
                logger.info("Runner->onPullRequest"),
                sonar.setToken(getToken(Configuration.CREDS_SONAR_GITHUB_OAUTH_TOKEN)),
                sonar.scan(true)
                ]
            ]
        )
	}

    //Methods
    public void build() {
        //TODO: verify if any maven nodes are available
        Maven([
            node:"maven",
            methods:[
                logger.info("Runner->build"),
                scmClient.clone(),
                context.stage("Maven Build") {
                    executeMavenGoals(Configuration.get("maven_goals"))
                }
                ]
            ]
        )
    }
}
