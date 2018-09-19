package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.Runner
import groovy.transform.InheritConstructors

@InheritConstructors
class CarinaRunner extends Runner {

    def scanner

    public CarinaRunner(context) {
        super(context)
        scanner = new CarinaScanner(context)
    }

    @Override
	public void onPush() {
		context.println("CarinaRunner->onPush")
        scanner.scanRepository()
		// handle each push/merge operation
		// execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
		context.println("TODO: implement snapshot build generation and emailing build number...")
	}

    @Override
    public void onPullRequest() {
        context.println("CarinaRunner->onPullRequest")

        context.node("master") {
            scmClient.clonePR()

            executeMavenGoals("-U clean process-resources process-test-resources -Dcobertura.report.format=xml cobertura:cobertura clean deploy javadoc:javadoc")

            context.junit '**/target/surefire-reports/junitreports/*.xml'

            //TODO: test&fix cobertura report publishing
            context.step([$class: 'CoberturaPublisher',
                          autoUpdateHealth: false,
                          autoUpdateStability: false,
                          coberturaReportFile: '**/target/site/cobertura/coverage.xml',
                          failUnhealthy: false,
                          failUnstable: false,
                          maxNumberOfBuilds: 0,
                          onlyStable: false,
                          sourceEncoding: 'ASCII',
                          zoomCoverageChart: false])

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