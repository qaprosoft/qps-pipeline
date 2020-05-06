package com.qaprosoft.jenkins.pipeline.runner.maven

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner

//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.maven.Maven
import com.qaprosoft.jenkins.pipeline.tools.maven.sonar.Sonar

@Mixin([Maven, Sonar])
public class Runner extends AbstractRunner {

    Logger logger

    public Runner(context) {
        super(context)
        scmClient = new GitHub(context)
        logger = new Logger(context)
    }

    //Events
    public void onPush() {
        context.node("master") {
            logger.info("Runner->onPush")

            boolean shallowClone = !Configuration.get("onlyUpdated").toBoolean()
            logger.info("shallowClone: " + shallowClone)
            scmClient.clone(shallowClone)
            //TODO: implement Sonar scan for full reposiory

            def project = Configuration.get("repo")
            executeSonarFullScan(project, project, "")
        }

        context.node("master") {
            jenkinsFileScan()
        }
    }

    public void onPullRequest() {
        context.node("master") {
            logger.info("Runner->onPullRequest")
            scmClient.clonePR()

            context.stage('Maven Compile') {
                def goals = "clean compile test-compile -f pom.xml"

                executeMavenGoals(goals)
            }
            context.stage('Sonar Scanner') {
                executeSonarPRScan()
            }

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
            throw new RuntimeException("Not implemented yet!")
        }
    }

}
