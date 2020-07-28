package com.qaprosoft.jenkins.pipeline.runner.maven

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner

//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration

import static com.qaprosoft.jenkins.Utils.*

public class Runner extends AbstractRunner {

    public Runner(context) {
        super(context)
        
        setDisplayNameTemplate('#${BUILD_NUMBER}|${branch}')
    }

    //Events
    public void onPush() {
        context.node("maven") {
            logger.info("Runner->onPush")
            getScm().clonePush()
            // [VD] don't remove -U otherwise latest dependencies are not downloaded
            compile("-U clean compile test -DskipTests", false)
            
            //TODO: test if we can execute Jenkinsfile jobdsl on maven node 
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
        context.stage("Maven Compile") {
            for (pomFile in context.getPomFiles()) {
                logger.debug("pomFile: " + pomFile)
                def sonarGoals = getSonarGoals(isPullRequest)
                context.mavenBuild("-f ${pomFile} ${goals} ${sonarGoals}")
            }
        }
    }
    
    protected def getSonarGoals(isPullRequest=false) {
        def sonarGoals = sc.getGoals(isPullRequest)
        if (!isParamEmpty(sonarGoals)) {
            //added maven specific goal
            sonarGoals += " sonar:sonar"
        }
        
        return sonarGoals
    }

}
