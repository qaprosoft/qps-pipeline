package com.qaprosoft.jenkins.repository.pipeline

import static java.util.UUID.randomUUID


def runJob() {
    def jobParameters = setJobType("${platform}", "${browser}")
    def mobileGoals = ""
    node(jobParameters.get("node")) {
      wrap([$class: 'BuildUser']) {
        try {
          timestamps {
            this.prepare(jobParameters)

            this.repoClone()

            this.getResources()

	    if (params["device"] != null && !params["device"].isEmpty() && !params["device"].equals("NULL")) {
		//TODO: test mobile goals appending as this risky change can't be tested now!
                jobParameters += this.setupForMobile(jobParameters)
            }

            this.runTests(jobParameters)

            this.reportingResults()

//            this.cleanWorkSpace()
          }
        } catch (Exception ex) {
            currentBuild.result = 'FAILURE'
            throw ex
        } finally {
	  //do nothing for now
        }
      }
    }
}

def setJobType(String platform, String browser) {
    def jobProperties = [:]
    jobProperties.put("platform", platform)
    println "platform: ${platform}";	

    switch(platform.toLowerCase()) {
        case "api":
            println "Suite Type: API";
	    jobProperties.put("node", "api")
	    jobProperties.put("browser", "NULL")
            break;
        case "android":
            println "Suite Type: ANDROID";
	    jobProperties.put("node", "android")
            break;
        case "ios":
            //TODO: Need to improve this to be able to handle where emulator vs. physical tests should be run.
            println "Suite Type: iOS";
	    jobProperties.put("node", "ios")
            break;
        default:
	    if ("NULL".equals(browser)) {
                println "Suite Type: Default";
	        jobProperties.put("node", "master")
	    } else {
                println "Suite Type: Web";
	        jobProperties.put("node", "web")
	    }
    }
    return jobProperties
}

def prepare(Map jobParameters) {
    stage('Preparation') {
        currentBuild.displayName = "#${BUILD_NUMBER}|${suite}|${env.env}|${branch}"
	if (!isParamEmpty("${CARINA_CORE_VERSION}")) {
	    currentBuild.displayName += "|" + "${CARINA_CORE_VERSION}" 
	}
	if (!isParamEmpty(params["device"])) {
	    currentBuild.displayName += "|${device}"
	}
	if (!isParamEmpty(params["browser"])) {
	    currentBuild.displayName += "|${browser}"
	}
	if (!isParamEmpty(params["browser_version"])) {
	    currentBuild.displayName += "|${browser_version}"
	}
	
        currentBuild.description = "${suite}"
    }
}

def repoClone() {
    stage('Checkout GitHub Repository') {
	def fork = params["fork"]
	println "forked_repo: " + fork
	if (!fork) {
	        git branch: '${branch}', url: '${GITHUB_SSH_URL}/${project}', changelog: false, poll: false, shallow: true
	} else {
		def token_name = 'token_' + "${BUILD_USER_ID}"
		println "token_name: ${token_name}"
		def token_value = env[token_name]
		//println "token_value: ${token_value}" 
		if (token_value != null) {
			def forkUrl = "https://${token_value}@${GITHUB_HOST}/${BUILD_USER_ID}/${project}"
			println "fork repo url: ${forkUrl}"
		        git branch: '${branch}', url: "${forkUrl}", changelog: false, poll: false, shallow: true
		} else {
			throw new RuntimeException("Unable to run from fork repo as ${token_name} token is not registered on CI! Visit wiki article for details: AUTO-3636")
		}
	}
    }
}

def getResources() {
    stage("Download Resources") {
	def pomFile = getSubProjectFolder() + "/pom.xml"
	echo "pomFile: " + pomFile
        if (isUnix()) {
            sh "'mvn' -B -U -f $pomFile clean process-resources process-test-resources -Dcarina-core_version=$CARINA_CORE_VERSION"
        } else {
	    //TODO: verify that forward slash is ok for windows nodes.
            bat(/"mvn" -B -U -f $pomFile clean process-resources process-test-resources -Dcarina-core_version=$CARINA_CORE_VERSION/)
        }
    }
}

def setupForMobile(Map jobParameters) {

    def goalMap = [:]

    stage("Mobile Preparation") {
        if (jobParameters.get("platform").toString().equalsIgnoreCase("android")) {
            goalMap = setupGoalsForAndroid(goalMap)
        } else {
            goalMap = setupGoalsForiOS(goalMap)
        }

        if ("DefaultPool".equalsIgnoreCase(params["device"])) {
            //reuse list of devices from hidden parameter DefaultPool
            goalMap.put("capabilities.deviceName", params["DefaultPool"])
        } else {
            goalMap.put("capabilities.deviceName", params["device"])
        }


        goalMap.put("capabilities.newCommandTimeout", "180")

        goalMap.put("retry_count", "${retry_count}")
        goalMap.put("thread_count", "${thread_count}")
        goalMap.put("retry_interval", "1000")
        goalMap.put("implicit_timeout", "30")
        goalMap.put("explicit_timeout", "60")
        goalMap.put("java.awt.headless", "true")

        goalMap.put("recovery_mode", "${recoveryMode}")

    }
    return goalMap
}

def setupGoalsForAndroid(Map<String, String> goalMap) {

    goalMap.put("mobile_app_clear_cache", "true")

    goalMap.put("capabilities.platformName", "ANDROID")

    goalMap.put("capabilities.autoGrantPermissions", "true")
    goalMap.put("capabilities.noSign", "true")
    goalMap.put("capabilities.STF_ENABLED", "true")

    return goalMap
}


def setupGoalsForiOS(Map<String, String> goalMap) {


    goalMap.put("capabilities.platform", "IOS")
    goalMap.put("capabilities.platformName", "IOS")
    goalMap.put("capabilities.deviceName", "*")

    goalMap.put("capabilities.appPackage", "")
    goalMap.put("capabilities.appActivity", "")

    goalMap.put("capabilities.noSign", "false")
    goalMap.put("capabilities.autoGrantPermissions", "false")
    goalMap.put("capabilities.autoAcceptAlerts", "true")

    goalMap.put("capabilities.STF_ENABLED", "false")

    // remove after fixing
    goalMap.put("capabilities.automationName", "XCUITest")

    return goalMap
}


def buildOutGoals(Map<String, String> goalMap) {
    def goals = ""

    goalMap.each { k, v -> goals = goals + " -D${k}=${v}"}

    return goals
}

def runTests(Map jobParameters) {
    stage('Run Test Suite') {
//        def goalMap = [:]
	def goalMap = jobParameters

	//TODO: investigate how user timezone can be declared on qps-infra level
	def pomFile = getSubProjectFolder() + "/pom.xml"
	def DEFAULT_BASE_MAVEN_GOALS = "-Dcarina-core_version=$CARINA_CORE_VERSION -f $pomFile \
			-Dci_run_id=$ci_run_id -Dcore_log_level=$CORE_LOG_LEVEL -Demail_list=$email_list \
			-Dmaven.test.failure.ignore=true -Dselenium_host=$SELENIUM_HOST -Dmax_screen_history=1 \
			-Dinit_retry_count=0 -Dinit_retry_interval=10 $ZAFIRA_BASE_CONFIG -Duser.timezone=PST clean test"


	uuid = "${ci_run_id}"
	echo "uuid: " + uuid
        if (uuid.isEmpty()) {
            uuid = randomUUID() as String
        }
	echo "uuid: " + uuid

        def zafiraEnabled = "false"
        if ("${DEFAULT_BASE_MAVEN_GOALS}".contains("zafira_enabled=true")) {
            zafiraEnabled = "true"
        }

        if ("${develop}".contains("true")) {
            echo "Develop Flag has been Set, disabling interaction with Zafira Reporting."
            zafiraEnabled = "false"
        }

        goalMap.put("env", params["env"])

	if (params["browser"] != null && !params["browser"].isEmpty()) {
            goalMap.put("browser", params["browser"])
	}

	if (params["auto_screenshot"] != null) {
            goalMap.put("auto_screenshot", params["auto_screenshot"])
	}

	if (params["keep_all_screenshots"] != null) {
            goalMap.put("keep_all_screenshots", params["keep_all_screenshots"])
	}

	if (params["enableVNC"] != null) {
            goalMap.put("capabilities.enableVNC", params["enableVNC"])
	}


	goalMap.put("zafira_enabled", "${zafiraEnabled}")
	goalMap.put("zafira_project", "${zafira_project}")
        goalMap.put("ci_run_id", "${uuid}")
        goalMap.put("ci_url", "$JOB_URL")
        goalMap.put("ci_build", "$BUILD_NUMBER")
//        goalMap.put("platform", jobParameters.get("platform"))

        def mvnBaseGoals = DEFAULT_BASE_MAVEN_GOALS + buildOutGoals(goalMap)
        if ("${JACOCO_ENABLE}".equalsIgnoreCase("true")) {
            echo "Enabling jacoco report generation goals."
            mvnBaseGoals += " jacoco:instrument"
        }

        mvnBaseGoals += " ${overrideFields}"
        mvnBaseGoals = mvnBaseGoals.replace(", ", ",")

	echo "mvnBaseGoals: ${mvnBaseGoals}"

	//TODO: adjust zafira_report_folder correclty
        if (isUnix()) {
            suiteNameForUnix = "${suite}".replace("\\", "/")
            echo "Suite for Unix: ${suiteNameForUnix}"
            sh "'mvn' -B ${mvnBaseGoals} -Dsuite=${suiteNameForUnix} -Dzafira_report_folder=./reports/qa -Dreport_url=$JOB_URL$BUILD_NUMBER/eTAF_Report"
        } else {
            suiteNameForWindows = "${suite}".replace("/", "\\")
            echo "Suite for Windows: ${suiteNameForWindows}"
            bat(/"mvn" -B ${mvnBaseGoals} -Dsuite=${suiteNameForWindows} -Dzafira_report_folder=.\reports\qa -Dreport_url=$JOB_URL$BUILD_NUMBER\\/eTAF_Report/)
        }

	this.publishJacocoReport();
        this.setTestResults()
    }
}

def setTestResults() {
    //Need to do a forced failure here in case the report doesn't have PASSED or PASSED KNOWN ISSUES in it.
    //TODO: hardoced path here!
    checkReport = readFile("./reports/qa/emailable-report.html")
    if (!checkReport.contains("PASSED:") && !checkReport.contains("PASSED (known issues):") && !checkReport.contains("SKIP_ALL:")) {
        echo "Unable to Find (Passed) or (Passed Known Issues) within the eTAF Report."
        currentBuild.result = 'FAILURE'
    } else if (checkReport.contains("SKIP_ALL:")) {
        currentBuild.result = 'UNSTABLE'
    }
}

def reportingResults() {
    stage('Results') {
	if (!publishReport('**/reports/qa/zafira-report.html', 'eTAF_Report')) {
		publishReport('**/reports/qa/emailable-report.html', 'eTAF_Report')
	}
	publishReport('**/tests/target/surefire-reports/index.html', 'Full TestNG HTML Report')
	publishReport('**/tests/target/surefire-reports/emailable-report.html', 'TestNG Summary HTML Report')

    }
}

def publishJacocoReport() {
        def files = findFiles(glob: '**/jacoco.exec')
        if(files.length == 1) {
	        archiveArtifacts artifacts: '**/jacoco.exec', fingerprint: true, allowEmptyArchive: true
	        // https://github.com/jenkinsci/pipeline-aws-plugin#s3upload
	        withAWS(region: 'us-west-1',credentials:'aws-jacoco-token') {
	            s3Upload(bucket:"$JACOCO_BUCKET", path:"$JOB_NAME/$BUILD_NUMBER/jacoco-it.exec", includePathPattern:'**/jacoco.exec')
	        }
	}
}

def publishReport(String pattern, String reportName) {
        def files = findFiles(glob: "${pattern}")
        if(files.length == 1) {
	    def reportFile = files[0]
	    reportDir = new File(reportFile.path).getParentFile()
            echo "Report File Found, Publishing ${reportFile.path}"
            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: "${reportDir}", reportFiles: "${reportFile.name}", reportName: "${reportName}"])
	    return true;
        } else if (files.length > 1) {
	    echo  "ERROR: too many report file discovered! count: ${files.length}"
	    return false;
	} else {
	    echo  "ERROR: no report file discovered!"
	    return false;
	}
}

def cleanWorkSpace() {
    stage('Wipe out Workspace') {
        deleteDir()
    }
}

def isParamEmpty(String value) {
    if (value == null || value.isEmpty() || value.equals("NULL")) {
	return true
    } else {
	return false
    }
}


def runJacocoMergeJob() {
    node(jobParameters.get("node")) {
        timestamps {
            this.runMerge()
            this.cleanWorkSpace()
        }
    }
}

def runMerge() {
    stage('Run Test Suite') {
        // download jacoco-it.exec coverage reports
        withAWS(region: 'us-west-1',credentials:'aws-jacoco-token') {
            files = s3FindFiles(bucket:"$JACOCO_BUCKET", path:'**/**/jacoco-it.exec')
            files.each (files) { file ->
              println "file: " + file
            }

            //s3Download(bucket:"$JACOCO_BUCKET", path:"$JOB_NAME/$BUILD_NUMBER/jacoco-it.exec", includePathPattern:'**/jacoco.exec')
        }

        // merge all reports into the single file

        // upload merged file into another bucket/key

        // remove old binary reports

    }
}

def getSubProjectFolder() {
	//specify current dir as subProject folder by default
	def subProjectFolder = "."
	if (!isParamEmpty(params["sub_project"])) {
	    subProjectFolder = "./" + params["sub_project"]
	}
	return subProjectFolder
}