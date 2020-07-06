package com.qaprosoft.jenkins.pipeline.runner.maven

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner

//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.SonarClient

public class Runner extends AbstractRunner {
    SonarClient sc

    public Runner(context) {
        super(context)
        sc = new SonarClient(context)
        setDisplayNameTemplate('#${BUILD_NUMBER}|${branch}')
    }

    //Events
    public void onPush() {
        context.node("master") {
            logger.info("Runner->onPush")
            sc.scan()
        }
        context.node("master") {
            jenkinsFileScan()
        }
    }

    public void onPullRequest() {
        context.node("master") {
            logger.info("Runner->onPullRequest")
            sc.scan(true)
        }
    }

    //Methods
    public void build() {
        //TODO: verify if any maven nodes are available
        context.node("maven") {
            logger.info("Runner->build")
            scmClient.clone()
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
