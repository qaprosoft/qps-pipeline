package com.qaprosoft.jenkins.repository.pipeline

import static java.util.UUID.randomUUID


def runJob() {
    //assign initial node detection logic onto the ec2-fleet to
    // a) minimize calls on master node
    // b) keep job in queue if everything is busy without starting job timer
    def jobParameters = null
    def mobileGoals = ""

    def uuid = "${ci_run_id}"
    echo "uuid: " + uuid
    if (uuid.isEmpty()) {
            uuid = randomUUID() as String
    }
    echo "uuid: " + uuid

    try {
      def response = httpRequest \
	    contentType: 'APPLICATION_JSON', \
	    httpMode: 'POST', \
	    requestBody: "{\"refreshToken\": \"${ZAFIRA_ACCESS_TOKEN}\"}", \
            url: "${ZAFIRA_SERVICE_URL}/api/auth/refresh"

      // reread new acccetToken and type
      def properties = (Map) new groovy.json.JsonSlurper().parseText(response.getContent())
      
      def token = properties.get("accessToken")
      def type = properties.get("type")

      echo "token: ${token}"
      echo "type: ${type}"

      httpRequest customHeaders: [[name: 'Authorization', \
            value: "${type} ${token}"]], \
	    contentType: 'APPLICATION_JSON', \
	    httpMode: 'POST', \
	    requestBody: "{\"jobName\": \"${JOB_BASE_NAME}\", \"branch\": \"${branch}\", \"ciRunId\": \"${uuid}\", \"id\": \"0\"}", \
            url: "${ZAFIRA_SERVICE_URL}/api/tests/runs/queue"

    } catch (Throwable thr) {
      echo "Throwable: " + thr.getMessage()
    }

    jobParameters = setJobType("${platform}", "${browser}")

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

	    def timeoutValue = "${env.JOB_MAX_RUN_TIME}"
	    timeout(time: timeoutValue.toInteger(), unit: 'MINUTES') {
                this.runTests(jobParameters)
	    }

            this.reportingResults()

//            this.cleanWorkSpace()
          }
        } catch (Exception ex) {
            scanConsoleLogs()
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

    if (params["zafira_project"].equals("SING")) {
    echo "ENV: " +  params["env"]
    switch(params["env"]) {
	  case "BETA":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SingAndroidQaReleaseBeta/sing_android-playstore-release-beta-.*${build}.*.apk")
	    break
	  case "MASTER_BETA":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SingAndroidQaMasterBeta/sing_android-playstore-master-beta-.*${build}.*.apk")
	    break
	  case "MASTER_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt81/sing_android-playstore-master-int-.*.apk")
	    break
	  case "MASTER_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt83/sing_android-playstore-master-prod-.*.apk")
	    break
	  case "MASTER_STG":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt82/sing_android-playstore-master-stg-.*.apk")
	    break
	  case "INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt84/sing_android-playstore-release-int-.*.apk")
	    break
	  case "PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt85/sing_android-playstore-release-prod-.*.apk")
	    break
	  case "STG":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt86/sing_android-playstore-release-stg-.*.apk")
	    break
	  case "QA_SUPERPOWERED_PROD_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SuperpoweredProdBuilds_SingAndroidQaSuperpoweredProdInt/sing_android-playstore-superpowered_prod-int-.*.apk")
	    break
	  case "QA_SUPERPOWERED_REC_TYPE_BETA":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SuperpoweredRecTypeBuilds_SingAndroidQaSuperpoweredRecTypeBeta/sing_android-playstore-superpowered_rec_type-beta-.*.apk")
	    break
	  case "QA_DEV_DSHARE_CONTROL_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_DevDshareControlBuilds_SingAndroidQaDevDshareControlInt/sing_android-playstore-dev_dshare_control-int-.*.apk")
	    break
	  case "QA_PERFORMANCE_UPLOAD_MANAGER_V2_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_PerformanceUploadManagerV2builds_SingAndroidQaPerformanceUploadManag/sing_android-playstore-performance_upload_manager_v2-prod-.*.apk")
	    break
	  case "QA_FIND_FRIENDS_PHASE1_BETA":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_FindFriendsPhase1builds_SingAndroidQaFindFriendsPhase1beta/sing_android-playstore-find_friends_phase_1-beta-.*.apk")
	    break
	  case "QA_FIND_FRIENDS_PHASE1_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_FindFriendsPhase1builds_SingAndroidQaFindFriendsPhase1prod/sing_android-playstore-find_friends_phase_1-prod-.*.apk")
	    break
	  case "QA_ARMSTRONG_AUTO_LOGIN_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_ArmstrongAutoLoginBuilds_SingAndroidQaArmstrongAutoLoginProd/sing_android-playstore-armstrong_auto_login-prod-.*.apk")
	    break
	  case "QA_CONTINUOUS_PLAY_PHASE3_BETA":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_ContinuousPlayPhase3builds_SingAndroidQaContinuousPlayPhase3beta/sing_android-playstore-continuous_play_phase_3-beta-.*.apk")
	    break
	  case "QA_SINGLE_EGL_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SingleEGLBuilds_SingAndroidQaSingleEGLInt/sing_android-playstore-singleEGL-int-.*.apk")
	    break
	  case "QA_FRAUDJUST_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_FraudjustBuilds_SingAndroidQaFraudjustInt/sing_android-playstore-fraudjust-int-.*.apk")
	    break
	  case "QA_JOINERS_CHOICE_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_JoinersChoiceBuilds_SingAndroidQaJoinersChoiceInt/sing_android-playstore-joiners_choice-int-.*.apk")
	    break
	  case "QA_APPEVENTS_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_AppeventsBuilds_SingAndroidQaAppeventsProd/sing_android-playstore-appevents-prod-.*.apk")
	    break
	  case "QA_RENDERED_FILED_DELETION_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_Sa15040builds_SingAndroidQaSa15040int/sing_android-playstore-sa_15040-int-.*.apk")
	    break
	  case "QA_RENDERED_FILED_DELETION_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_Sa15040builds_SingAndroidQaSa15040prod/sing_android-playstore-sa_15040-prod-.*.apk")
	    break
	  case "QA_SA15312_INDIAN_LANG_BATCH1_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_Sa15312indianLangBatch1builds_SingAndroidQaSa15312indianLangBatch1pr/sing_android-playstore-SA_15312_indian_lang_batch1-prod-.*.apk")
	    break
	  case "QA_NO_SKIP_TOPIC_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_NoSkipTopicBuilds_SingAndroidQaNoSkipTopicProd/sing_android-playstore-no_skip_topic-prod-.*.apk")
	    break
	  case "QA_SOFT_TRIALS_CLIENT_DEV_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SoftTrialsClientDevBuilds_SingAndroidQaSoftTrialsClientDevInt/sing_android-playstore-soft_trials_client_dev-int-.*.apk")
	    break
	  case "QA_SA_14894_HOT_INVITES_BY_SONG_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_Sa14894HotInvitesBySongBuilds_SingAndroidQaSa14894HotInvitesBySongPr/sing_android-playstore-SA_14894_Hot_Invites_By_Song-prod-.*.apk")
	    break
	  case "QA_HTTPS_V2_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_HttpsV2builds_SingAndroidQaHttpsV2prod/sing_android-playstore-https_v2-prod-.*.apk")
	    break
	  case "QA_FREEFORM_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_FreeformBuilds_SingAndroidQaFreeformProd/sing_android-playstore-freeform-prod-.*.apk")
	    break	    
	  default:
	    throw new RuntimeException("Unknown env: " + params["env"]);
	    break
    }
    }

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

    if (params["zafira_project"].equals("SING")) {
    echo "ENV: " +  params["env"]
    switch(params["env"]) {
	  case "DEV_BETA":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_QaAutomationBuilds_SingEnterpriseDistQaAutomationBeta/sing-enterprise_dev-qa_automation-beta-.*${build}.*.ipa")
	    break
	  case "DEV_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_QaAutomationBuilds_SingEnterpriseDistQaAutomationInt/sing-enterprise_dev-qa_automation-int-.*${build}.*.ipa")
	    break
	  case "DEV_PROD":
	    goalMap.put("capabilities.app", "s3://SingIos_QaAutomationBuilds_SingEnterpriseDistQaAutomationProd/sing-enterprise_dev-qa_automation-prod-.*${build}.*.ipa")
	    break
	  case "DEV_STG":
	    goalMap.put("capabilities.app", "s3://SingIos_QaAutomationBuilds_SingEnterpriseDistQaAutomationStg/sing-enterprise_dev-qa_automation-stg-.*${build}.*.ipa")
	    break
	  case "UI_AUTOMATION_BETA":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_QaAutomationBuilds_SingUiAutomationQaAutomationBeta/sing-ui_automation-qa_automation-beta-.*${build}.*.ipa")
	    break
	  case "UI_AUTOMATION_RELEASE_BETA":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_QaAutomationBuilds_SingUiAutomationQaReleaseBeta/sing-ui_automation-qa_release-beta-.*${build}.*.ipa")
	    break
	  case "UI_AUTOMATION_FASTDESIGN_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_QaAutomationBuilds_SingUiAutomationQaFastDesignBeta/sing-ui_automation-qa_fast_design-prod-.*${build}.*.ipa")
	    break
	  case "IOS_MASTER_DEV_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_MasterBuilds_SingEnterpriseDevMasterInt/sing-enterprise_dev-master-int-.*${build}.*.ipa")
	    break
	  case "IOS_MASTER_BETA":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_EnterpriseDist_SingEnterpriseDistMasterBeta/sing-enterprise_dist-master-beta-.*${build}.*.ipa")
	    break
	  case "IOS_MASTER_INT":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_EnterpriseDist_SingEnterpriseDistMasterInt/sing-enterprise_dist-master-int-.*${build}.*.ipa")
	    break
	  case "IOS_MASTER_PROD":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_EnterpriseDist_SingEnterpriseDistMasterProd/sing-enterprise_dist-master-prod-.*${build}.*.ipa")
	    break
	  case "IOS_MASTER_STG":
	    goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_EnterpriseDist_SingEnterpriseDistMasterStg/sing-enterprise_dist-master-stg-.*${build}.*.ipa")
	    break
	  default:
	    throw new RuntimeException("Unknown env: " + params["env"]);
	    break
    }
    }


    return goalMap
}


def runTests(Map jobParameters) {
    stage('Run Test Suite') {
//        def goalMap = [:]
	def goalMap = jobParameters

	//TODO: investigate how user timezone can be declared on qps-infra level
	def pomFile = getSubProjectFolder() + "/pom.xml"
	def DEFAULT_BASE_MAVEN_GOALS = "-Dcarina-core_version=$CARINA_CORE_VERSION -f $pomFile \
			-Dcore_log_level=$CORE_LOG_LEVEL -Dmaven.test.failure.ignore=true -Dselenium_host=$SELENIUM_HOST -Dmax_screen_history=1 \
			-Dinit_retry_count=0 -Dinit_retry_interval=10 $ZAFIRA_BASE_CONFIG -Duser.timezone=PST -Ds3_local_storage=/opt/apk clean test"


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
        goalMap.put("ci_url", "$JOB_URL")
        goalMap.put("ci_build", "$BUILD_NUMBER")
//        goalMap.put("platform", jobParameters.get("platform"))

        def mvnBaseGoals = DEFAULT_BASE_MAVEN_GOALS + buildOutGoals(goalMap, currentBuild)
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
            bat "mvn ${mvnBaseGoals} -Dsuite=${suiteNameForWindows} -Dzafira_report_folder=./reports/qa -Dreport_url=$JOB_URL$BUILD_NUMBER/eTAF_Report"
        }

	this.publishJacocoReport();
        this.setTestResults()
    }
}

@NonCPS
def buildOutGoals(Map<String, String> goalMap, currentBuild) {
    def goals = ""

    goalMap.each { k, v -> goals = goals + " -D${k}=${v}"}

    def myparams = currentBuild.rawBuild.getAction(ParametersAction)
    for( p in myparams ) {
        goals = goals + " -D${p.name.toString()}=\"${p.value.toString()}\""
    }

    return goals
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

def scanConsoleLogs() {
        currentBuild.result = 'FAILURE'

	def body = "Unable to execute tests due to the unrecognized failure: ${JOB_URL}${BUILD_NUMBER}"
	def subject = "UNRECOGNIZED FAILURE: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"

	if (currentBuild.rawBuild.log.contains("COMPILATION ERROR : ")) {
	    echo "found compilation failure error in log!"	    
	    body = "Unable to execute tests due to the compilation failure. ${JOB_URL}${BUILD_NUMBER}"
	    subject = "COMPILATION FAILURE: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
	}

	if (currentBuild.rawBuild.log.contains("Aborted by ") || currentBuild.rawBuild.log.contains("Sending interrupt signal to process")) {
	    currentBuild.result = 'ABORTED'
	    echo "found Aborted by message in log!"	    
	    body = "Unable to continue tests due to the abort action. It could be aborted due to the default ${env.JOB_MAX_RUN_TIME} minutes timeout! ${JOB_URL}${BUILD_NUMBER}"
	    subject = "ABORTED: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
	}

	emailext attachLog: true, body: "${body}", recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']], subject: "${subject}", to: "${email_list}"
}


def reportingResults() {
    stage('Results') {
	if (!publishReport('**/reports/qa/zafira-report.html', 'eTAF_Report')) {
		publishReport('**/reports/qa/emailable-report.html', 'eTAF_Report')
	}
	publishReport('**/target/surefire-reports/index.html', 'Full TestNG HTML Report')
	publishReport('**/target/surefire-reports/emailable-report.html', 'TestNG Summary HTML Report')

	publishReport('**/artifacts/**', 'eTAF_Artifacts')
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
