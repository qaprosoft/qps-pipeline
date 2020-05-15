package com.qaprosoft.jenkins.pipeline.runner.gradle

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner
import com.qaprosoft.jenkins.pipeline.tools.gradle.Sonar

//import com.qaprosoft.jenkins.pipeline.tools.maven.Maven

import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub

//[VD] do not remove this important import!

//@Mixin([Maven])
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
        context.node("maven") {
            logger.info("Runner->onPush")
            sonar.scan()
        }

        context.node("maven") {
            jenkinsFileScan()
        }
    }

    public void onPullRequest() {
        context.node("maven") {
            logger.info("Runner->onPullRequest")
            sonar.scan(true)
        }
    }

    //Methods
    public void build() {
        context.node("maven") {
            logger.info("Runner->build")
            throw new RuntimeException("Not implemented yet!")
        }
    }

}
