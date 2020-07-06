package com.qaprosoft.jenkins.pipeline.tools

import com.qaprosoft.jenkins.pipeline.integration.HttpClient
import com.qaprosoft.jenkins.pipeline.Configuration

class SonarClient extends HttpClient {

	private String serviceUrl

	SonarClient(context) {
		super(context)
		serviceUrl = context.env.getEnvironment().get("SONAR_URL")
	}

	public void scan(isPullRequest=false) {
        //TODO: verify preliminary if "maven" nodes available
        context.node("maven") {
            context.stage('Sonar Scanner') {
                if (isPullRequest) {
                    getScm().clonePR()
                } else {
                    // it should be non shallow clone anyway to support full static code analysis
                    getScm().clonePush()
                }

                def isSonarAvailable = "UP".equals(getServerStatus()?.get("status"))
                def isJacocoEnabled = Configuration.get(Configuration.Parameter.JACOCO_ENABLE)?.toBoolean()

                if (!isSonarAvailable) {
                    logger.warn("The sonarqube ${this.serviceUrl()} server is not available, sonarqube scan will be skipped!")
                }
                            
                for (pomFile in context.getPomFiles()) {
                    logger.debug("pomFile: " + pomFile)
                    //do compile and scanner for all high level pom.xml files
                    // [VD] don't remove -U otherwise latest dependencies are not downloaded
                    def goals = "-U clean compile test -f ${pomFile}"
                    def sonarGoals = isSonarAvailable ? getGoals() : ''
                	def extraGoals = isJacocoEnabled ? getJacocoGoals() : ''

                    if (isPullRequest) {
                        // no need to run unit tests for PR analysis
                        goals += " -DskipTests"

                        if (isSonarAvailable) {
                            // such param should be remove to decorate pr
                            sonarGoals = sonarGoals.minus("-Dsonar.branch.name=${Configuration.get("branch")}")
                            sonarGoals += getGoals(true)
                        } 
                        
                    } else {
                        //run unit tests to detect code coverage but don't fail the build in case of any failure
                        //TODO: for build process we can't use below goal!
                        extraGoals += " -Dmaven.test.failure.ignore=true"
                    }

                    context.mavenBuild("${goals} ${extraGoals} ${sonarGoals}")
                }
            }
        }
    }

    protected String getGoals(isPullRequest=false) {
    	if (isPullRequest) {
    		// goals needed to decorete pr with sonar analysis
    		return " -Dsonar.verbose=true \
                    -Dsonar.pullrequest.key=${Configuration.get("ghprbPullId")} \
                    -Dsonar.pullrequest.branch=${Configuration.get("ghprbSourceBranch")} \
                    -Dsonar.pullrequest.base=${Configuration.get("ghprbTargetBranch")} \
                    -Dsonar.pullrequest.github.repository=${Configuration.get("ghprbGhRepository")}"
    	}

    	return " sonar:sonar \
	             -Dsonar.host.url=${this.serviceUrl} \
	             -Dsonar.log.level=${this.logger.pipelineLogLevel} \
	             -Dsonar.branch.name=${Configuration.get("branch")}"
    }

    protected def getServerStatus() {
        def parameters = [contentType        : 'APPLICATION_JSON',
                          httpMode           : 'GET',
                          validResponseCodes : '200',
                          url                : serviceUrl + '/api/system/status']
		return sendRequestFormatted(parameters)
	}

    protected String getJacocoGoals(jacocoEnable) {
    	def (jacocoReportPath, jacocoReportPaths) = getJacocoReportPaths(jacocoEnable)
    	return 'jacoco:report-aggregate ${jacocoReportPaths} ${jacocoReportPath}'
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
            context.withAWS(region: "${jacocoRegion}", credentials: 'aws-jacoco-token') {
                def copyOutput = context.sh script: "aws s3 cp s3://${jacocoBucket}/${jacocoItExec} /tmp/${jacocoItExec}", returnStdout: true
                logger.info("copyOutput: " + copyOutput)
            }

            if (context.fileExists("/tmp/${jacocoItExec}")) {
                jacocoReportPath = "-Dsonar.jacoco.reportPath=/target/jacoco.exec" //this for unit tests code coverage
                jacocoReportPaths = "-Dsonar.jacoco.reportPaths=/tmp/${jacocoItExec}" // this one is for integration testing coverage
            }

            logger.debug("jacocoReportPath: " + jacocoReportPath)
            logger.debug("jacocoReportPaths: " + jacocoReportPaths)
        }

        return [jacocoReportPath, jacocoReportPaths]
    }

}