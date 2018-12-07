package com.qaprosoft.jenkins.pipeline.sonar

import hudson.plugins.sonar.SonarGlobalConfiguration
import com.qaprosoft.jenkins.pipeline.maven.Maven

//** IMPORTANT Add @Mixin(Sonar) together with @Mixin(Maven) **//
public class Sonar {

	protected void performSonarQubeScan(){
		performSonarQubeScan("pom.xml")
	}

	protected void performSonarQubeScan(pomFile){
		def sonarQubeEnv = ''
		Jenkins.getInstance().getDescriptorByType(SonarGlobalConfiguration.class).getInstallations().each { installation ->
			sonarQubeEnv = installation.getName()
		}
		if(sonarQubeEnv.isEmpty()){
			logger.warn("There is no SonarQube server configured. Please, configure Jenkins for performing SonarQube scan.")
			return
		}
		
		context.stage('Sonar Scanner') {
			context.withSonarQubeEnv(sonarQubeEnv) {
				def goals = "-f ${pomFile} \
					clean package sonar:sonar -DskipTests \
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
			/** **/
			executeMavenGoals(goals)
			}
		}
	}

 
}
