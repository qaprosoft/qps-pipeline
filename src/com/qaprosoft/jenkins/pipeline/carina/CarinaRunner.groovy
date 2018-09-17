package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Runner

class CarinaRunner extends Runner {

    public CarinaRunner(context) {
        super(context)
        this.context = context
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

			executeMavenGoals("-U clean process-resources process-test-resources")
			//TODO: think about using deploy to produce snapshot build for PR as well
			executeMavenGoals("-Dcobertura.report.format=xml clean test cobertura:cobertura")

			context.sh("find . -name \"*cobertura*\"")
			context.sh("find . -name coverage.xml")
			
            publishUnitTestResults()

            //TODO: implement below code
			// produce snapshot build if ghprbPullTitle contains 'build-snapshot'
			
            if (Configuration.get("ghprbPullTitle").contains("build-snapshot")) {
				executeMavenGoals("versions:set -DnewVersion=${context.env.getEnvironment().get("CARINA_RELEASE")}.${context.env.getEnvironment().get("BUILD_NUMBER")}-SNAPSHOT")
				executeMavenGoals("-Dcobertura.report.format=xml cobertura:cobertura clean deploy javadoc:javadoc")
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