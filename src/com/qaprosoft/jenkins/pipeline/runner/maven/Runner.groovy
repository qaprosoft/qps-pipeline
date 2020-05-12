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
        context.node("maven") {
            logger.info("Runner->onPush")
            scmClient.clonePush()
            sonar.scan()
        }

        context.node("master") {
            jenkinsFileScan()
        }
    }

    public void onPullRequest() {
        context.node("maven") {
            logger.info("Runner->onPullRequest")
            scmClient.clonePR()
            sonar.scan(true)

            //TODO: investigate whether we need this piece of code
            //            if (Configuration.get("ghprbPullTitle").contains("automerge")) {
            //                scmClient.mergePR()
            //            }
        }
    }

    //Methods
    public void build() {
        context.node("master") {
            logger.info("Runner->build")
            //TODO: we are ready to produce building for any maven project: this is maven compile install goals
            throw new RuntimeException("Not implemented yet!")
        }
    }

}
