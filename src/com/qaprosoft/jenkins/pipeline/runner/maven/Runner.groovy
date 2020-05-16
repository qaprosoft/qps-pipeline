package com.qaprosoft.jenkins.pipeline.runner.maven

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner

//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.maven.Maven
import com.qaprosoft.jenkins.pipeline.tools.maven.sonar.Sonar

import java.nio.file.Paths

import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

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
        context.node("master") {
            logger.info("Runner->onPush")
            sonar.scan()
        }

        context.node("master") {
            jenkinsFileScan()
        }
    }

    public void onPullRequest() {
        context.node("master") {
            logger.info("Runner->onPullRequest")
			setSonarGithubToken()
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
	
	private void setSonarGithubToken() {
		def orgFolderName = getOrganization()
		logger.info("orgFolderName: " + orgFolderName)

		def sonarGithubToken = Configuration.CREDS_SONAR_GITHUB_OAUTH_TOKEN
		if (!isEmpty(orgFolderName)) {
			sonarGithubToken = "${orgFolderName}" + "-" + sonarGithubToken
		}
		if (getCredentials(sonarGithubToken)){
			context.withCredentials([context.usernamePassword(credentialsId:sonarGithubToken, usernameVariable:'KEY', passwordVariable:'VALUE')]) {
				Configuration.set(Configuration.Parameter.SONAR_GITHUB_OAUTH_TOKEN, context.env.VALUE)
			}
		}
	}
	
	@NonCPS
	static boolean isEmpty(value) {
		if (value == null) {
			return true
		}  else {
			return value.toString().isEmpty() || value.toString().equalsIgnoreCase("NULL")
		}
	}


}
