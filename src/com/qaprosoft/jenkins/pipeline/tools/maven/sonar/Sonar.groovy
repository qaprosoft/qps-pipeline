package com.qaprosoft.jenkins.pipeline.tools.maven.sonar

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.Configuration
import hudson.plugins.sonar.SonarGlobalConfiguration
import com.qaprosoft.jenkins.pipeline.tools.maven.Maven

@Mixin(Maven)
public class Sonar {
    private static final String SONARQUBE = ".sonarqube"


    protected void executePRScan(){
        executePRScan(null)
    }

	protected void executePRScan(mavenSettingsConfig){

		boolean isSonarAvailable = false
		def sonarQubeEnv = getSonarEnv()
		def sonarConfigFileExists = context.fileExists "${SONARQUBE}"
		if (!sonarQubeEnv.isEmpty() && sonarConfigFileExists) {
			isSonarAvailable = true
		} else {
			logger.warn("Sonarqube is not configured correctly! Follow documentation Sonar integration steps to enable it.")
		}

		def pomFiles = getProjectPomFiles()
		pomFiles.each {
			logger.debug(it)
      compile()

      if (!isSonarAvailable) {
        continue
      }
      //do compile and scanner for all high level pom.xml files
      def jacocoEnable = Configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean()
      def (jacocoReportPath, jacocoReportPaths) = getJacocoReportPaths(jacocoEnable)

            context.env.sonarHome = context.tool name: 'sonar-ci-scanner', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
            context.withSonarQubeEnv('sonar-ci') {
                //TODO: [VD] find a way for easier env getter. how about making Configuration syncable with current env as well...
                def sonarHome = context.env.getEnvironment().get("sonarHome")
                logger.debug("sonarHome: " + sonarHome)

        def BUILD_NUMBER = Configuration.get("BUILD_NUMBER")
                // execute sonar scanner
        context.sh "${sonarHome}/bin/sonar-scanner -Dsonar.projectVersion=${BUILD_NUMBER} \
                   -Dproject.settings=${SONARQUBE} ${jacocoReportPath} ${jacocoReportPaths} \
                   -Dsonar.github.endpoint=${Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_API_URL)}")} \
                   -Dsonar.github.pullRequest=${Configuration.get("ghprbPullId")} \
                   -Dsonar.github.repository=${Configuration.get("ghprbGhRepository")} \
                   -Dsonar.github.oauth=${Configuration.get(Configuration.Parameter.GITHUB_OAUTH_TOKEN)} \
                   -Dsonar.sourceEncoding=UTF-8 \
                   -Dsonar.analysis.mode=preview"
            }

			// if (!isSonarAvailable) {
			// 	compile()
			// } else {
			// 	// [VD] do not remove "-U" arg otherwise fresh dependencies are not downloaded
			// 	context.stage('Sonar Scanner') {
			// 		context.withSonarQubeEnv(sonarQubeEnv) {
			// 			def goals = "-U -f ${it} \
			// 				clean compile test-compile package sonar:sonar -DskipTests=true \
			// 				-Dsonar.github.endpoint=${Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_API_URL)}")} \
			// 				-Dsonar.github.pullRequest=${Configuration.get("ghprbPullId")} \
			// 				-Dsonar.github.repository=${Configuration.get("ghprbGhRepository")} \
			// 				-Dsonar.projectVersion=${Configuration.get("BUILD_NUMBER")} \
			// 				-Dproject.settings=${SONARQUBE} \
			// 				-Dsonar.github.oauth=${Configuration.get(Configuration.Parameter.GITHUB_OAUTH_TOKEN)} \
			// 				-Dsonar.analysis.mode=preview"
			// 			if (mavenSettingsConfig != null) {
			// 				executeMavenGoals(goals, mavenSettingsConfig)
			// 			} else {
			// 				executeMavenGoals(goals)
			// 			}
			// 		}
			// 	}
			// }


		}
	}

	protected void executeFullScan() {
		context.stage('Sonar Scanner') {
			boolean isSonarAvailable = false
			def sonarQubeEnv = getSonarEnv()
			def sonarConfigFileExists = context.fileExists "${SONARQUBE}"
			if (!sonarQubeEnv.isEmpty() && sonarConfigFileExists) {
				isSonarAvailable = true
			} else {
				logger.warn("Sonarqube is not configured correctly! Follow documentation Sonar integration steps to enable it.")
			}

			def pomFiles = getProjectPomFiles()
			pomFiles.each {
				logger.debug(it)
				compile()

				if (!isSonarAvailable) {
					continue
				}
				//do compile and scanner for all high level pom.xml files
				def jacocoEnable = Configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean()
				def (jacocoReportPath, jacocoReportPaths) = getJacocoReportPaths(jacocoEnable)

        logger.debug("jacocoReportPath: " + jacocoReportPath)
        logger.debug("jacocoReportPaths: " + jacocoReportPaths)

              context.env.sonarHome = context.tool name: 'sonar-ci-scanner', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
	            context.withSonarQubeEnv('sonar-ci') {
	                //TODO: [VD] find a way for easier env getter. how about making Configuration syncable with current env as well...
	                def sonarHome = context.env.getEnvironment().get("sonarHome")
	                logger.debug("sonarHome: " + sonarHome)

					def BUILD_NUMBER = Configuration.get("BUILD_NUMBER")
	                // execute sonar scanner
					context.sh "${sonarHome}/bin/sonar-scanner -Dsonar.projectVersion=${BUILD_NUMBER} -Dproject.settings=${SONARQUBE} ${jacocoReportPath} ${jacocoReportPaths}"
	            }
			}
        }
    }

	protected def getJacocoReportPaths(boolean jacocoEnable) {
		def jacocoReportPath = ""
		def jacocoReportPaths = ""

		if (jacocoEnable) {
			def jacocoItExec = 'jacoco-it.exec'

			def jacocoBucket = Configuration.get(Configuration.Parameter.JACOCO_BUCKET)
			def jacocoRegion = Configuration.get(Configuration.Parameter.JACOCO_REGION)

			// download combined integration testing coverage report: jacoco-it.exec
			// TODO: test if aws cli is installed on regular jenkins slaves as we are going to run it on each onPush event starting from 5.0
			context.withAWS(region: "${jacocoRegion}", credentials:'aws-jacoco-token') {
				def copyOutput = context.sh script: "aws s3 cp s3://${jacocoBucket}/${jacocoItExec} /tmp/${jacocoItExec}", returnStdout: true
				logger.info("copyOutput: " + copyOutput)
			}


			if (context.fileExists("/tmp/${jacocoItExec}")) {
				jacocoReportPath = ""
				jacocoReportPaths = "-Dsonar.jacoco.reportPaths='/tmp/${jacocoItExec}'"
			}
		}

		return [jacocoReportPath, jacocoReportPaths]
	}

    protected String getSonarEnv() {
      def sonarQubeEnv = ''
      Jenkins.getInstance().getDescriptorByType(SonarGlobalConfiguration.class).getInstallations().each { installation ->
          sonarQubeEnv = installation.getName()
      }
      return sonarQubeEnv
    }

	protected def getProjectPomFiles() {
		def pomFiles = []
		def files = context.findFiles(glob: "**/pom.xml")

		if (files.length > 0) {
			logger.info("Number of pom.xml files to analyze: " + files.length)

			int curLevel = 5 //do not analyze projects where highest pom.xml level is lower or equal 5
			for (pomFile in files) {
				def path = pomFile.path
				int level = path.count("/")
				logger.debug("file: " + path + "; level: " + level + "; curLevel: " + curLevel)
				if (level < curLevel) {
					curLevel = level
					pomFiles.clear()
					pomFiles.add(pomFile.path)
				} else if (level == curLevel) {
					pomFiles.add(pomFile.path)
				}
			}
			logger.info("PROJECT POMS: " + pomFiles)
		}
		return pomFiles
	}
}
