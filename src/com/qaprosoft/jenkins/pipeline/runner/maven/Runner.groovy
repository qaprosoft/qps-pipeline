package com.qaprosoft.jenkins.pipeline.runner.maven

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner

//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.maven.sonar.Sonar

public class Runner extends AbstractRunner {
    Sonar sonar

    public Runner(context) {
        super(context)
        sonar = new Sonar(context)
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
        //TODO: verify if any maven nodes are available
        context.node("maven") {
            logger.info("Runner->build")
            scmClient.clone()
            setDisplayNameTemplate("#${BUILD_NUMBER}|${branch}")
            context.stage("Maven Build") {
                context.mavenBuild(Configuration.get("maven_goals"))
            }
        }
    }

    @NonCPS
    public def setSshClient() {
        sonar.setSshClient()
        super.setSshClient()
    }
}
