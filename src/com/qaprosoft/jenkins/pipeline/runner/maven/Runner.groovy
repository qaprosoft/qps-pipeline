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

        //TODO: reorganize all credentials etc init based on approach below!
        setOrgFolderName()
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
	
	@NonCPS
	private void setOrgFolderName() {
		String jobName = Configuration.get(Configuration.Parameter.JOB_NAME)
		int nameCount = Paths.get(jobName).getNameCount()

		def orgFolderName = ""
		if (nameCount == 1 && (jobName.contains("qtest-updater") || jobName.contains("testrail-updater"))) {
			// testrail-updater - i.e. stage
			orgFolderName = ""
		} else if (nameCount == 2 && (jobName.contains("qtest-updater") || jobName.contains("testrail-updater"))) {
			// stage/testrail-updater - i.e. stage
			orgFolderName = Paths.get(jobName).getName(0).toString()
		} else if (nameCount == 2) {
			// carina-demo/API_Demo_Test - i.e. empty orgFolderName
			orgFolderName = ""
		} else if (nameCount == 3) { //TODO: need to test use-case with views!
			// qaprosoft/carina-demo/API_Demo_Test - i.e. orgFolderName=qaprosoft
			orgFolderName = Paths.get(jobName).getName(0).toString()
		} else {
			throw new RuntimeException("Invalid job organization structure: '${jobName}'!" )
		}
		
		Configuration.set(Configuration.Parameter.ORG_FOLDER, orgFolderName)
		
	}
	
	private void setSonarGithubToken() {
		def orgFolderName = Configuration.get(Configuration.Parameter.ORG_FOLDER)

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
