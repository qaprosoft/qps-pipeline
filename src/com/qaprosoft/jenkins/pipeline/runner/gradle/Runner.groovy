package com.qaprosoft.jenkins.pipeline.runner.gradle

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner
import com.qaprosoft.jenkins.pipeline.tools.gradle.sonar.Sonar

import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub

class Runner extends AbstractRunner {
    Logger logger
    Sonar sonar

    Runner(context) {
        super(context)
        scmClient = new GitHub(context)
        sonar = new Sonar(context)
        logger = new Logger(context)
    }

    //Events
    void onPush() {
        context.node("gradle") {
            logger.info("Runner->onPush")
            sonar.scan()
        }

        context.node("gradle") {
            jenkinsFileScan()
        }
    }

    void onPullRequest() {
        context.node("gradle") {
            logger.info("Runner->onPullRequest")
            sonar.scan(true)
        }
    }

    //Methods
    void build() {
        context.node("gradle") {
            logger.info("Runner->build")
            throw new RuntimeException("Not implemented yet!")
        }
    }

}
