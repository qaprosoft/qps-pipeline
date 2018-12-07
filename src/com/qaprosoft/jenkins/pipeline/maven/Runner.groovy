package com.qaprosoft.jenkins.pipeline.maven

import com.qaprosoft.Logger
import com.qaprosoft.scm.github.GitHub;
import com.qaprosoft.jenkins.pipeline.AbstractRunner

//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.maven.Maven

import com.qaprosoft.jenkins.pipeline.sonar.Sonar

@Mixin(Maven)
@Mixin(Sonar)
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
            boolean shadowClone = !Configuration.get("onlyUpdated").toBoolean()
            logger.info("shadowClone: " + shadowClone)
            scmClient.clone(shadowClone)
            //TODO: implement Sonar scan for full reposiory
        }
    }

    public void onPullRequest() {
        context.node("master") {
            logger.info("Runner->onPullRequest")
            scmClient.clonePR()

            context.stage('Maven Compile') {
                def goals = "clean compile test-compile \
						 -f pom.xml -Dmaven.test.failure.ignore=true"

                executeMavenGoals(goals)
            }
            context.stage('Sonar Scanner') {
                performSonarQubeScan()
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
            //TODO: implement Jenkinsfile pipeline execution from the repo
        }
    }

}
