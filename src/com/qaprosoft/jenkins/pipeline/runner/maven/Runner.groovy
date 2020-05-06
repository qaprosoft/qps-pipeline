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

            def sonarPropsFileExists = context.fileExists ".sonarqube"
            compile()
            sonarFullScan(sonarPropsFileExists)
			//TODO: decentralize sonar properties declaration
			// 1. declare "executeSonarFullScan()" with no args ?!
			// 2. organize reading project name and key, modules and all possible args from ".sonarqube" property file
			// 3. if no .sonarqube detected then project name and key equals to Configuration.get("repo"), modules are empty
			// 4. switch QARunner as well to use simple executeSonarFullScan() call
			// 5. send PR into the carina putting into this repo new ".sonarqube" file with
			    // sonar.modules=carina-api,carina-aws-s3,carina-commons,carina-core,carina-crypto,carina-dataprovider,carina-appcenter,carina-proxy,carina-reporting,carina-utils,carina-webdriver
			    // sonar.java.source=1.8


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
