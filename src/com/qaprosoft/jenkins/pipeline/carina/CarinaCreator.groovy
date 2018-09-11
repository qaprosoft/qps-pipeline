package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Creator
import com.qaprosoft.jenkins.pipeline.Scanner
import com.qaprosoft.scm.github.GitHub;

class CarinaCreator extends Creator {

    public CarinaCreator(context) {
        super(context)
        this.context = context

        scmClient = new GitHub(context)
        scanner = new Scanner(context)
    }

    //Events
    @Override
    protected void onUpdate() {
        context.println("CarinaCreator->onUpdate")
        // handle each push/merge operation
        // execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
        context.println("TODO: implement snapshot build generation and emailing build number...")
    }

    @Override
    protected void onPullRequest() {
        context.println("CarinaCreator->onPullRequest")
        context.node("master") {
            scmClient.clonePR()
            def goals = "-Dcobertura.report.format=xml cobertura:cobertura clean test javadoc:javadoc"
            executeMavenGoals(goals)
            context.junit '**/target/surefire-reports/junitreports/*.xml'

            //TODO: implement below code
            // produce snapshot build if ghprbPullTitle contains 'build-snapshot'
            if (Configuration.get("ghprbPullTitle").contains("build-snapshot")) {
                executeMavenGoals("mvn versions:set -DnewVersion=${Configuration.get("CARINA_RELEASE")}.${Configuration.get("BUILD_NUMBER")}-SNAPSHOT")
                executeMavenGoals("mvn -Dgpg.passphrase=${Configuration.get("GPG_PASSWORD")} -Dcobertura.report.format=xml cobertura:cobertura clean deploy javadoc:javadoc")
            }
            //email notification
        }
        //TODO: publish cobertura report
        //TODO: send email about unit testing results
    }

    def void executeMavenGoals(goals){
        if (context.isUnix()) {
            context.sh "'mvn' -B ${goals}"
        } else {
            context.bat "mvn -B ${goals}"
        }

    }

}