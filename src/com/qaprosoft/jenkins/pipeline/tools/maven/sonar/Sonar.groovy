package com.qaprosoft.jenkins.pipeline.tools.maven.sonar

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.Configuration
import hudson.plugins.sonar.SonarGlobalConfiguration
import com.qaprosoft.jenkins.pipeline.tools.maven.Maven
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub

@Mixin(Maven)
public class Sonar {
    private static final String SONARQUBE = ".sonarqube"
	private static boolean isSonarAvailable

	protected def context
	protected Logger logger
	protected ISCM scmClient

	public Sonar(context) {
		this.context = context
		this.logger = new Logger(context)
		this.scmClient = new GitHub(context)
	}

	public void scan(isPrClone=false) {
		//TODO: verify preliminary if "maven" nodes available
		context.node("maven") {
			context.stage('Sonar Scanner') {

				if (isPrClone) {
					scmClient.clonePR()
				} else {
					// it should be non shallow clone anyway to support full static code analysis
					scmClient.clonePush()
				}

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
						return
					}
					//do compile and scanner for all high level pom.xml files
					def jacocoEnable = Configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean()
					def (jacocoReportPath, jacocoReportPaths) = getJacocoReportPaths(jacocoEnable)

					logger.debug("jacocoReportPath: " + jacocoReportPath)
					logger.debug("jacocoReportPaths: " + jacocoReportPaths)

					context.env.sonarHome = context.tool name: 'sonar-ci-scanner', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
		            	context.withSonarQubeEnv('sonar-ci') {
							// execute sonar scanner
							context.sh scannerScript(isPrClone, jacocoReportPaths, jacocoReportPath)
		            }
				}
	        }
		}
    }

	private def getJacocoReportPaths(boolean jacocoEnable) {
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
				jacocoReportPath = "-Dsonar.jacoco.reportPath=/target/jacoco.exec" //this for unit tests code coverage
				jacocoReportPaths = "-Dsonar.jacoco.reportPaths=/tmp/${jacocoItExec}" // this one is for integration testing coverage
			}
		}

		return [jacocoReportPath, jacocoReportPaths]
	}

  private def scannerScript(isPrClone, jacocoReportPaths, jacocoReportPath) {
    //TODO: [VD] find a way for easier env getter. how about making Configuration syncable with current env as well...
    def sonarHome = context.env.getEnvironment().get("sonarHome")
    logger.debug("sonarHome: " + sonarHome)

    def BUILD_NUMBER = Configuration.get("BUILD_NUMBER")
    def SONAR_LOG_LEVEL = context.env.getEnvironment().get("QPS_PIPELINE_LOG_LEVEL").equals(Logger.LogLevel.DEBUG.name()) ?  "DEBUG" : "INFO"

    def script = "${sonarHome}/bin/sonar-scanner \
                  -Dsonar.projectVersion=${BUILD_NUMBER} \
                  -Dproject.settings=${SONARQUBE} \
                  -Dsonar.log.level=${SONAR_LOG_LEVEL} ${jacocoReportPaths} ${jacocoReportPath}"

    if (isPrClone) {
      script += " -Dsonar.github.endpoint=${Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_API_URL)}")} \
                  -Dsonar.github.pullRequest=${Configuration.get("ghprbPullId")} \
                  -Dsonar.github.repository=${Configuration.get("ghprbGhRepository")} \
                  -Dsonar.github.oauth=${Configuration.get(Configuration.Parameter.SONAR_GITHUB_OAUTH_TOKEN)} \
                  -Dsonar.sourceEncoding=UTF-8 \
                  -Dsonar.analysis.mode=preview"
    }
    return script
  }

    private String getSonarEnv() {
      def sonarQubeEnv = ''
      Jenkins.getInstance().getDescriptorByType(SonarGlobalConfiguration.class).getInstallations().each { installation ->
          sonarQubeEnv = installation.getName()
      }
      return sonarQubeEnv
    }

}
