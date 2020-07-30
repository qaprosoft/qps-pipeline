package com.qaprosoft.jenkins.pipeline.runner.gradle

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
        context.node("gradle") {
            logger.info("Runner->onPush")
            getScm().clonePush()
            compile("./gradlew clean")
            jenkinsFileScan()
        }
    }

    public void onPullRequest() {
        context.node("gradle") {
            logger.info("Runner->onPullRequest")
            getScm().clonePR()
            compile("./gradlew clean", true)
        }
    }

    //Methods
    public void build() {
        context.node("gradle") {
            logger.info("Runner->build")
            scmClient.clone()
            context.stage("Gradle Build") {
                //TODO: finish implementation later as we have for maven
                context.sh "./gradlew clean"
            }
        }
    }
    
    protected void compile(goals, isPullRequest=false) {
        def sonarGoals = getSonarGoals(isPullRequest)
        
        if (!context.fileExists('gradlew')) {
            goals = goals.replace("./gradlew", "gradle")
        }

        context.withGradle {
            sh "${goals} ${sonarGoals}"
        }
    }
    
    protected def getSonarGoals(isPullRequest=false) {
        def sonarGoals = sc.getGoals(isPullRequest)
        if (!isParamEmpty(sonarGoals)) {
            //added gradle specific goal
            sonarGoals += " sonarqube"
        }
        
        return sonarGoals
    }

}
