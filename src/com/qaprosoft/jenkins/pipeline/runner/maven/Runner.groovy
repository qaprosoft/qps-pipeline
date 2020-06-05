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
        logger.info("Runner->onPush")
        sonar.scan()
        Maven([
            node:"master",
            methods:[
                jenkinsFileScan()
                ]
            ]
        )
    }

	public void onPullRequest() {
        logger.info("Runner->onPullRequest"),
        sonar.setToken(getToken(Configuration.CREDS_SONAR_GITHUB_OAUTH_TOKEN)),
        Maven([
            node:"master",
            methods:[
                sonar.scan(true)
                ]
            ]
        )
	}

    //Methods
    public void build() {
        //TODO: verify if any maven nodes are available
        logger.info("Runner->build")
        scmClient.clone()
        context.stage("Maven Build") {
            Maven([
                    node   : "maven",
                    methods: [
                            executeMavenGoals(Configuration.get("maven_goals"))

                    ]
                ]
            )
        }
    }
}
