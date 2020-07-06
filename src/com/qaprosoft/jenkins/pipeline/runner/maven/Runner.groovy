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
        context.node("maven") {
            logger.info("Runner->onPush")
            getScm().clonePush()
            // [VD] don't remove -U otherwise latest dependencies are not downloaded
            compile("-U clean compile test -Dmaven.test.failure.ignore=true", false)
            
            jenkinsFileScan()
        }
    }

    public void onPullRequest() {
        context.node("maven") {
            logger.info("Runner->onPullRequest")
            
            getScm().clonePR()
            compile("-U clean compile test -DskipTests", true)
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
    
    protected void compile(goals, isPullRequest=false) {
        for (pomFile in context.getPomFiles()) {
            logger.debug("pomFile: " + pomFile)
            //do compilation icluding sonar/jacoco goals if needed
            def sonarGoals = sc.getGoals(isPullRequest)
            context.mavenBuild("-f ${pomFile} ${goals} ${sonarGoals}")
        }
    }

    @NonCPS
    public def setSshClient() {
        sc.setSshClient()
        super.setSshClient()
    }
}
