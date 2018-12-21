package com.qaprosoft.jenkins.pipeline.maven

public class Maven {
	//TODO: migreate to traits as only it is supported in pipelines
	// https://issues.jenkins-ci.org/browse/JENKINS-46145

	def MAVEN_TOOL='M3'

	public void executeMavenGoals(goals) {
		logger.debug("Maven mixing->executeMavenGoals")
		context.withMaven(
			// Maven installation declared in the Jenkins "Global Tool Configuration"
			maven: "${MAVEN_TOOL}",
			// Maven settings.xml file defined with the Jenkins Config File Provider Plugin
			// Maven settings and global settings can also be defined in Jenkins Global Tools Configuration
			//mavenSettingsConfig: 'settings',
			//mavenLocalRepo: ".repository"
			) {
			// Run the maven build
			buildGoals(goals)
		}
	}

	public void executeMavenGoals(goals, mavenSettingsConfig) {
		logger.info("Maven mixing->executeMavenGoals")
		context.withMaven(
			// Maven installation declared in the Jenkins "Global Tool Configuration"
			maven: "${MAVEN_TOOL}",
			// Maven settings.xml file defined with the Jenkins Config File Provider Plugin
			// Maven settings and global settings can also be defined in Jenkins Global Tools Configuration
			mavenSettingsConfig: "${mavenSettingsConfig}") {
			// Run the maven build
			buildGoals(goals)
		}
	}
	
	public void executeMavenGoals(goals, mavenSettingsConfig, mavenLocalRepo) {
		logger.info("Maven mixing->executeMavenGoals")
		context.withMaven(
			// Maven installation declared in the Jenkins "Global Tool Configuration"
			maven: "${MAVEN_TOOL}",
			// Maven settings.xml file defined with the Jenkins Config File Provider Plugin
			// Maven settings and global settings can also be defined in Jenkins Global Tools Configuration
			mavenSettingsConfig: "${mavenSettingsConfig}",
			mavenLocalRepo: "${mavenLocalRepo}") {
			// Run the maven build
			buildGoals(goals)
		}
	}

	private def buildGoals(goals) {
		if (context.isUnix()) {
			context.sh "'mvn' -B ${goals}"
		} else {
			context.bat "mvn -B ${goals}"
		}
	}
}
