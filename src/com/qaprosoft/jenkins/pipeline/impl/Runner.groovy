package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.scm.github.GitHub;
import com.qaprosoft.jenkins.pipeline.AbstractRunner

import hudson.plugins.sonar.SonarGlobalConfiguration

public class Runner extends AbstractRunner {
	
	public Runner(context) {
		super(context)
		scmClient = new GitHub(context)
	}

	//Events
	public void onPush() {
		context.node("master") {
			context.println("Runner->onPush")
			boolean shadowClone = !Configuration.get("onlyUpdated").toBoolean()
			context.println("shadowClone: " + shadowClone)
			scmClient.clone(shadowClone)
			//TODO: implement Sonar scan for full reposiory 
		}
	}

	public void onPullRequest() {
		context.node("master") {
			context.println("Runner->onPullRequest")
			scmClient.clonePR()

			context.stage('Maven Compile') {
				def goals = "clean compile test-compile \
						 -f pom.xml -Dmaven.test.failure.ignore=true"

				executeMavenGoals(goals)
			}
			context.stage('Sonar Scanner') { 
				performSonarQubeScan() 
			}

			//TODO: investigate whether we need this piece of code
			//            if (Configuration.get("ghprbPullTitle").contains("automerge")) {
			//                scmClient.mergePR()
			//            }
		}
	}
	
	protected def void executeMavenGoals(goals){
		if (context.isUnix()) {
			context.sh "'mvn' -B ${goals}"
		} else {
			context.bat "mvn -B ${goals}"
		}
	}
	
	protected void performSonarQubeScan(){
		def sonarQubeEnv = ''
		Jenkins.getInstance().getDescriptorByType(SonarGlobalConfiguration.class).getInstallations().each { installation ->
			sonarQubeEnv = installation.getName()
		}
		if(sonarQubeEnv.isEmpty()){
			context.println "There is no SonarQube server configured. Please, configure Jenkins for performing SonarQube scan."
			return
		}
		//TODO: find a way to get somehow 2 below hardcoded string values
		context.stage('SonarQube analysis') {
			context.withSonarQubeEnv(sonarQubeEnv) {
				context.sh "mvn clean package sonar:sonar -DskipTests \
				 -Dsonar.github.endpoint=${Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_API_URL)}")} \
				 -Dsonar.analysis.mode=preview  \
				 -Dsonar.github.pullRequest=${Configuration.get("ghprbPullId")} \
				 -Dsonar.github.repository=${Configuration.get("ghprbGhRepository")} \
				 -Dsonar.projectKey=${Configuration.get("project")} \
				 -Dsonar.projectName=${Configuration.get("project")} \
				 -Dsonar.projectVersion=1.${Configuration.get(Configuration.Parameter.BUILD_NUMBER)} \
				 -Dsonar.github.oauth=${Configuration.get(Configuration.Parameter.GITHUB_OAUTH_TOKEN)} \
				 -Dsonar.sources=. \
				 -Dsonar.tests=. \
				 -Dsonar.inclusions=**/src/main/java/** \
				 -Dsonar.test.inclusions=**/src/test/java/** \
				 -Dsonar.java.source=1.8"
			}
		}
	}


}
