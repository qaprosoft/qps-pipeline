package com.qaprosoft.jenkins.pipeline.tools.maven.sonar

import com.qaprosoft.jenkins.pipeline.Configuration
import hudson.plugins.sonar.SonarGlobalConfiguration
import com.qaprosoft.jenkins.pipeline.tools.maven.Maven

@Mixin(Maven)
public class Sonar {

    protected void executeSonarPRScan(){
        executeSonarPRScan("pom.xml")
    }

    protected boolean executeSonarPRScan(pomFile){
        return executeSonarPRScan(pomFile, null)
    }

    protected boolean executeSonarPRScan(pomFile, mavenSettingsConfig){
        def sonarQubeEnv = ''
        Jenkins.getInstance().getDescriptorByType(SonarGlobalConfiguration.class).getInstallations().each { installation ->
            sonarQubeEnv = installation.getName()
        }
        if(sonarQubeEnv.isEmpty()){
			//TODO: add link to the doc about howto configur it correctly
            logger.warn("There is no SonarQube server configured. Please, configure Jenkins for performing SonarQube scan otherwise only compilation will be verified!")
			// [VD] do not remove "-U" arg otherwise fresh dependencies are not downloaded
			return false
        }

        // [VD] do not remove "-U" arg otherwise fresh dependencies are not downloaded
        context.stage('Sonar Scanner') {
            context.withSonarQubeEnv(sonarQubeEnv) {
                def goals = "-U -f ${pomFile} \
					clean compile test-compile package sonar:sonar -DskipTests=true \
					-Dsonar.github.endpoint=${Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_API_URL)}")} \
					-Dsonar.github.pullRequest=${Configuration.get("ghprbPullId")} \
					-Dsonar.github.repository=${Configuration.get("ghprbGhRepository")} \
					-Dsonar.projectKey=${Configuration.get("repo")} \
					-Dsonar.projectName=${Configuration.get("repo")} \
					-Dsonar.projectVersion=1.${Configuration.get(Configuration.Parameter.BUILD_NUMBER)} \
					-Dsonar.github.oauth=${Configuration.get(Configuration.Parameter.GITHUB_OAUTH_TOKEN)} \
					-Dsonar.sources=. \
					-Dsonar.tests=. \
					-Dsonar.inclusions=**/src/main/java/** \
					-Dsonar.test.inclusions=**/src/test/java/** \
					-Dsonar.java.source=1.8"
                /** **/
                if (mavenSettingsConfig != null) {
                    executeMavenGoals(goals, mavenSettingsConfig)
                } else {
                    executeMavenGoals(goals)
                }
            }
        }

		return true

    }

    protected void executeSonarFullScan(String projectName, String projectKey, String modules) {
        executeSonarFullScan(".", projectName, projectKey, modules)
    }

    protected void executeSonarFullScan(String projectBaseDir, String projectName, String projectKey, String modules) {
        context.stage('Sonar Scanner') {
            def sonarQubeEnv = ''
            Jenkins.getInstance().getDescriptorByType(SonarGlobalConfiguration.class).getInstallations().each { installation ->
                sonarQubeEnv = installation.getName()
            }
            if(sonarQubeEnv.isEmpty()){
                logger.warn("There is no SonarQube server configured. Please, configure Jenkins for performing SonarQube scan.")
                return
            }


            //download combined integration testing coverage report: jacoco-it.exec
            def jacocoEnable = Configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean()
            def jacocoItExec = 'jacoco-it.exec'
            def jacocoBucket = Configuration.get(Configuration.Parameter.JACOCO_BUCKET)
            def jacocoRegion = Configuration.get(Configuration.Parameter.JACOCO_REGION)
            if (jacocoEnable) {
                context.withAWS(region: "${jacocoRegion}",credentials:'aws-jacoco-token') {
                    def copyOutput = context.sh script: "aws s3 cp s3://${jacocoBucket}/${jacocoItExec} /tmp/${jacocoItExec}", returnStdout: true
                    logger.info("copyOutput: " + copyOutput)
                }
            }


            context.env.sonarHome = context.tool name: 'sonar-ci-scanner', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
            context.withSonarQubeEnv('sonar-ci') {
                //TODO: [VD] find a way for easier env getter. how about making Configuration syncable with current env as well...
                def sonarHome = context.env.getEnvironment().get("sonarHome")
                def BUILD_NUMBER = Configuration.get("BUILD_NUMBER")

                // execute sonar scanner
                context.sh "${sonarHome}/bin/sonar-scanner \
					-Dsonar.projectBaseDir=${projectBaseDir} \
					-Dsonar.projectName=${projectName} \
					-Dsonar.projectKey=${projectKey} \
					-Dsonar.projectVersion=1.${BUILD_NUMBER} \
					-Dsonar.java.source=1.8 \
					-Dsonar.sources='src/main' \
					-Dsonar.tests='src/test' \
					-Dsonar.java.binaries='target/classes' \
					-Dsonar.junit.reportsPath='target/surefire-reports' \
					-Dsonar.modules=${modules} \
					-Dsonar.jacoco.ReportPath='target/jacoco.exec' \
					-Dsonar.jacoco.itReportPath='/tmp/${jacocoItExec}'"
            }
        }
    }

}
