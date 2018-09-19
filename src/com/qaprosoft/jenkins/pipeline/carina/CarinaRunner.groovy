package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.Runner
import com.qaprosoft.jenkins.pipeline.Configuration
import groovy.transform.InheritConstructors

@InheritConstructors
class CarinaRunner extends Runner {

    public CarinaRunner(context) {
        super(context)
    }
	
	//Events
	@Override
	public void onPush() {
		context.println("CarinaRunner->onPush")
		// handle each push/merge operation
		// execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
		context.println("TODO: implement snapshot build generation and emailing build number...")
	}
	
	@Override
	public void onPullRequest() {
		context.println("CarinaRunner->onPullRequest")

		context.node("master") {
			scmClient.clonePR()

			executeMavenGoals("-U process-resources process-test-resources -Dcobertura.report.format=xml clean install cobertura:cobertura")
			publishUnitTestResults()
			performSonarQubeScan()

			// produce snapshot build if ghprbPullTitle contains 'build-snapshot'
			if (Configuration.get("ghprbPullTitle").contains("build-snapshot")) {
				//versions:set -DnewVersion=${CARINA_RELEASE}.${BUILD_NUMBER}-SNAPSHOT
				//-Dgpg.passphrase=<pswd> -Dcobertura.report.format=xml cobertura:cobertura clean deploy javadoc:javadoc
				executeMavenGoals("versions:set -DnewVersion=${context.env.getEnvironment().get("CARINA_RELEASE")}.${context.env.getEnvironment().get("BUILD_NUMBER")}-SNAPSHOT")
				executeMavenGoals("clean deploy javadoc:javadoc")
/*                context.withCredentials([context.usernamePassword(credentialsId:'gpg_token', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
					context.echo "USERNAME: ${context.env.USERNAME}"
					context.echo "PASSWORD: ${context.env.PASSWORD}"
					executeMavenGoals("-Dgpg.passphrase=${context.env.PASSWORD} -Dcobertura.report.format=xml cobertura:cobertura clean deploy javadoc:javadoc")
				}
*/
			}
			//TODO: email notification
		}
	}
	
}