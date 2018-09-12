package com.qaprosoft.jenkins.pipeline

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.XmlSuite;

import static java.util.UUID.randomUUID
import com.qaprosoft.zafira.ZafiraClient
import com.qaprosoft.jenkins.pipeline.browserstack.OS

import com.qaprosoft.scm.github.GitHub

class Runner extends Executor {
	//ci_run_id  param for unique test run identification
	protected def uuid
	
	protected def zc

	//using constructor it will be possible to redefine this folder on pipeline/jobdsl level
	protected def folderName = "Automation"

	protected static final String etafReport = "eTAF_Report"
	//TODO: /api/test/runs/{id}/export should use encoded url value as well
	protected static final String etafReportEncoded = "eTAF_5fReport"
	
	protected static String etafReportFolder = "./reports/qa"

	//CRON related vars
	protected def listPipelines = []
	
	
	public Runner(context) {
		super(context)
		scmClient = new GitHub(context)
        zc = new ZafiraClient(context)
	}
	
	public void runCron() {
		def nodeName = "master"
		//TODO: remove master node assignment
		context.node(nodeName) {
			scmClient.clone()

			def WORKSPACE = this.getWorkspace()
			context.println("WORKSPACE: " + WORKSPACE)
			def project = Configuration.get("project")
			def sub_project = Configuration.get("sub_project")
			def jenkinsFile = ".jenkinsfile.json"

			if (!context.fileExists("${jenkinsFile}")) {
				context.println("no .jenkinsfile.json discovered! Scanner will use default qps-pipeline logic for project: ${project}")
			}

			def suiteFilter = "src/test/resources/**"
			Object subProjects = this.parseJSON(WORKSPACE + "/" + jenkinsFile).sub_projects
			subProjects.each {
				listPipelines = []
				suiteFilter = it.suite_filter
				sub_project = it.name

				def subProjectFilter = sub_project
				if (sub_project.equals(".")) {
					subProjectFilter = "**"
				}

				def files = context.findFiles(glob: subProjectFilter + "/" + suiteFilter + "/**")
				if(files.length > 0) {
					context.println("Number of Test Suites to Scan Through: " + files.length)
					for (int i = 0; i < files.length; i++) {
						this.parsePipeline(WORKSPACE + "/" + files[i].path)
					}

					listPipelines = sortPipelineList(listPipelines)

                    setFolderName(parseFolderName())

					this.executeStages(folderName)
				} else {
					context.println("No Test Suites Found to Scan...")
				}
			}
		}
	}

	protected void beforeRunJob() {
		// do nothing
	}

    protected void setFolderName(folderName){
        this.folderName = folderName
    }

    protected def parseFolderName() {
	def folderName = ""
	def workspace = this.getWorkspace();
	if (workspace.contains("jobs/")) {
		def array = workspace.split("jobs/")
		for (def i = 1; i < array.size() - 1; i++){
			folderName  = folderName + array[i]
		}
		folderName = folderName.replaceAll(".\$","")
	} else {
		def array = workspace.split("/")
		folderName = array[array.size() - 2]
	}

        return folderName
    }

	public void runJob() {
		context.println("Runner->runJob")
		//use this method to override any beforeRunJob logic
		beforeRunJob()


        context.withCredentials([context.usernamePassword(credentialsId:'gpg_token', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            context.echo "USERNAME: ${context.env.USERNAME}"
        }

//        context.environment {
//            GPG_TOKEN = context.credentials("gpg_token")
//            context.echo context.env.getEnvironment().dump()
//            context.println("GPG: " + context.env.getEnvironment().get("GPG_TOKEN_PSW") )
//        }
//        context.echo context.env.getEnvironment().dump()
//        context.println("GPG2: " + context.env.getEnvironment().get("GPG_TOKEN_PSW") )

        uuid = getUUID()
        String nodeName = "master"

        //TODO: remove master node assignment
		context.node(nodeName) {
            zc.queueZafiraTestRun(uuid)
            nodeName = chooseNode()
		}

		context.node(nodeName) {
			context.wrap([$class: 'BuildUser']) {
				try {
					context.timestamps {

						this.prepare(context.currentBuild)
						scmClient.clone()

						this.downloadResources()

						def timeoutValue = Configuration.get(Configuration.Parameter.JOB_MAX_RUN_TIME)
						context.timeout(time: timeoutValue.toInteger(), unit: 'MINUTES') {
							  this.build()
						}

						//TODO: think about seperate stage for uploading jacoco reports
						this.publishJacocoReport()
					}
					
				} catch (Exception ex) {
					printStackTrace(ex)
					String failureReason = getFailure(context.currentBuild)
					context.echo "failureReason: ${failureReason}"
					//explicitly execute abort to resolve anomalies with in_progress tests...
                    zc.abortZafiraTestRun(uuid, failureReason)
					throw ex
				} finally {
                    this.exportZafiraReport()
                    this.sendTestRunResultsEmail()
                    this.reportingResults()
                    //TODO: send notification via email, slack, hipchat and whatever... based on subscription rules
                    this.clean()
                }
			}
		}

	}

    public void rerunJobs(){
        context.stage('Rerun Tests'){
            zc.smartRerun()
        }
    }

	//TODO: moved almost everything into argument to be able to move this methoud outside of the current class later if necessary
	protected void prepare(currentBuild) {

        Configuration.set("BUILD_USER_ID", getBuildUser())
		
		String BUILD_NUMBER = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
		String CARINA_CORE_VERSION = Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)
		String suite = Configuration.get("suite")
		String branch = Configuration.get("branch")
		String env = Configuration.get("env")
        //TODO: rename to devicePool
		String devicePool = Configuration.get("devicePool")
		String browser = Configuration.get("browser")

		//TODO: improve carina to detect browser_version on the fly
		String browser_version = Configuration.get("browser_version")

		context.stage('Preparation') {
			currentBuild.displayName = "#${BUILD_NUMBER}|${suite}|${env}|${branch}"
			if (!isParamEmpty("${CARINA_CORE_VERSION}")) {
				currentBuild.displayName += "|" + "${CARINA_CORE_VERSION}"
			}
			if (!isParamEmpty(devicePool)) {
				currentBuild.displayName += "|${devicePool}"
			}
			if (!isParamEmpty(Configuration.get("browser"))) {
				currentBuild.displayName += "|${browser}"
			}
			if (!isParamEmpty(Configuration.get("browser_version"))) {
				currentBuild.displayName += "|${browser_version}"
			}
			currentBuild.description = "${suite}"
			
			if (isMobile()) {
				//this is mobile test
				this.prepareForMobile()
			}
		}
	}

	protected boolean isMobile() {
		def platform = Configuration.get("platform")
		return platform.equalsIgnoreCase("android") || platform.equalsIgnoreCase("ios")
	}
	
	protected void prepareForMobile() {
		context.println("Runner->prepareForMobile")
		def devicePool = Configuration.get("devicePool")
		def defaultPool = Configuration.get("DefaultPool")
		def platform = Configuration.get("platform")

		if (platform.equalsIgnoreCase("android")) {
			prepareForAndroid()
		} else if (platform.equalsIgnoreCase("ios")) {
			prepareForiOS()
		} else {
			context.echo "Unable to identify mobile platform: ${platform}"
		}

		//geeral mobile capabilities
		//TODO: find valid way for naming this global "MOBILE" quota
		Configuration.set("capabilities.deviceName", "QPS-HUB")
		if ("DefaultPool".equalsIgnoreCase(devicePool)) {
			//reuse list of devices from hidden parameter DefaultPool
			Configuration.set("capabilities.devicePool", defaultPool)
		} else {
			Configuration.set("capabilities.devicePool", devicePool)
		}
		
		// ATTENTION! Obligatory remove device from the params otherwise
		// hudson.remoting.Channel$CallSiteStackTrace: Remote call to JNLP4-connect connection from qpsinfra_jenkins-slave_1.qpsinfra_default/172.19.0.9:39487
		// Caused: java.io.IOException: remote file operation failed: /opt/jenkins/workspace/Automation/<JOB_NAME> at hudson.remoting.Channel@2834589:JNLP4-connect connection from
		Configuration.remove("device")

		//TODO: move it to the global jenkins variable
		Configuration.set("capabilities.newCommandTimeout", "180")
		Configuration.set("java.awt.headless", "true")

	}

	protected void prepareForAndroid() {
		context.println("Runner->prepareForAndroid")
		Configuration.set("mobile_app_clear_cache", "true")

		Configuration.set("capabilities.platformName", "ANDROID")

		Configuration.set("capabilities.autoGrantPermissions", "true")
		Configuration.set("capabilities.noSign", "true")
		Configuration.set("capabilities.STF_ENABLED", "true")

		Configuration.set("capabilities.appWaitDuration", "270000")
		Configuration.set("capabilities.androidInstallTimeout", "270000")

	}

	protected void prepareForiOS() {
		context.println("Runner->prepareForiOS")
		Configuration.set("capabilities.platform", "IOS")
		Configuration.set("capabilities.platformName", "IOS")
		Configuration.set("capabilities.deviceName", "*")

		Configuration.set("capabilities.appPackage", "")
		Configuration.set("capabilities.appActivity", "")

		Configuration.set("capabilities.autoAcceptAlerts", "true")

		Configuration.set("capabilities.STF_ENABLED", "false")

	}

	protected void downloadResources() {
		//DO NOTHING as of now

/*		def CARINA_CORE_VERSION = Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)
		context.stage("Download Resources") {
		def pomFile = getSubProjectFolder() + "/pom.xml"
		context.echo "pomFile: " + pomFile
			if (context.isUnix()) {
				context.sh "'mvn' -B -U -f ${pomFile} clean process-resources process-test-resources -Dcarina-core_version=$CARINA_CORE_VERSION"
			} else {
				//TODO: verify that forward slash is ok for windows nodes.
				context.bat(/"mvn" -B -U -f ${pomFile} clean process-resources process-test-resources -Dcarina-core_version=$CARINA_CORE_VERSION/)
			}
		}
*/	}


	protected void getResources() {
		context.echo "Do nothing in default implementation"
	}

	protected void build() {
		context.stage('Run Test Suite') {

            def pomFile = getSubProjectFolder() + "/pom.xml"
            def BUILD_USER_EMAIL = Configuration.get("BUILD_USER_EMAIL")
            if (BUILD_USER_EMAIL == null) {
                //override "null" value by empty to be able to register in cloud version of Zafira
                BUILD_USER_EMAIL = ""
            }
			def DEFAULT_BASE_MAVEN_GOALS = "-Dcarina-core_version=${Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)} \
                                            -Detaf.carina.core.version=${Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)} \
											-Ds3_save_screenshots_v2=${Configuration.get(Configuration.Parameter.S3_SAVE_SCREENSHOTS_V2)} \
                                            -f ${pomFile} \
                                            -Dmaven.test.failure.ignore=true \
                                            -Dcore_log_level=${Configuration.get(Configuration.Parameter.CORE_LOG_LEVEL)} \
                                            -Dselenium_host=${Configuration.get(Configuration.Parameter.SELENIUM_URL)} \
                                            -Dmax_screen_history=1 -Dinit_retry_count=0 -Dinit_retry_interval=10 \
                                            -Dzafira_enabled=true \
                                            -Dzafira_rerun_failures=${Configuration.get("rerun_failures")} \
                                            -Dzafira_service_url=${Configuration.get(Configuration.Parameter.ZAFIRA_SERVICE_URL)} \
                                            -Dzafira_access_token=${Configuration.get(Configuration.Parameter.ZAFIRA_ACCESS_TOKEN)} \
                                            -Dzafira_report_folder=\"${etafReportFolder}\" \
                                            -Dreport_url=\"${Configuration.get(Configuration.Parameter.JOB_URL)}${Configuration.get(Configuration.Parameter.BUILD_NUMBER)}/${etafReportEncoded}\" \
                                            -Dgit_branch=${Configuration.get("branch")} \
                                            -Dgit_commit=${Configuration.get("scm_commit")} \
                                            -Dgit_url=${Configuration.get("scm_url")} \
                                            -Dci_url=${Configuration.get(Configuration.Parameter.JOB_URL)} \
                                            -Dci_build=${Configuration.get(Configuration.Parameter.BUILD_NUMBER)} \
                                            -Dci_user_id=${Configuration.get("BUILD_USER_ID")} \
                                            -Dci_user_first_name=${Configuration.get("BUILD_USER_FIRST_NAME")} \
                                            -Dci_user_last_name=${Configuration.get("BUILD_USER_LAST_NAME")} \
                                            -Dci_user_email=${BUILD_USER_EMAIL} \
                                            -Duser.timezone=${Configuration.get(Configuration.Parameter.TIMEZONE)} \
                                            clean test"

			//TODO: move 8000 port into the global var
			def mavenDebug=" -Dmaven.surefire.debug=\"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -Xnoagent -Djava.compiler=NONE\" "

			Configuration.set("ci_build_cause", getBuildCause(Configuration.get(Configuration.Parameter.JOB_NAME)))

			def goals = Configuration.resolveVars(DEFAULT_BASE_MAVEN_GOALS)

			//register all obligatory vars
			Configuration.getVars().each { k, v -> goals = goals + " -D${k}=\"${v}\""}

			//register all params after vars to be able to override
            Configuration.getParams().each { k, v -> goals = goals + " -D${k}=\"${v}\""}

            goals = enableVideoStreaming(Configuration.get("node"), "Video streaming was enabled.", " -Dcapabilities.enableVNC=true ", goals)
            goals = addOptionalParameter("enableVideo", "Video recording was enabled.", " -Dcapabilities.enableVideo=true ", goals)
            goals = addOptionalParameter(Configuration.get(Configuration.Parameter.JACOCO_ENABLE), "Jacoco tool was enabled.", " jacoco:instrument ", goals)
            goals = addOptionalParameter("debug", "Enabling remote debug...", mavenDebug, goals)
            goals = addOptionalParameter("deploy_to_local_repo", "Enabling deployment of tests jar to local repo.", " install", goals)

			//browserstack goals
			if (isBrowserStackRun()) {
				def uniqueBrowserInstance = "\"#${BUILD_NUMBER}-" + Configuration.get("suite") + "-" +
						Configuration.get("browser") + "-" + Configuration.get("env") + "\""
				uniqueBrowserInstance = uniqueBrowserInstance.replace("/", "-").replace("#", "")
				startBrowserStackLocal(uniqueBrowserInstance)
				goals += " -Dcapabilities.project=" + Configuration.get("project")
				goals += " -Dcapabilities.build=" + uniqueBrowserInstance
				goals += " -Dcapabilities.browserstack.localIdentifier=" + uniqueBrowserInstance
				goals += " -Dapp_version=browserStack"
			}

			//append again overrideFields to make sure they are declared at the end
			goals = goals + " " + Configuration.get("overrideFields")

			//context.echo "goals: ${goals}"

			//TODO: adjust etafReportFolder correctly
			if (context.isUnix()) {
				def suiteNameForUnix = Configuration.get("suite").replace("\\", "/")
				context.echo "Suite for Unix: ${suiteNameForUnix}"
				context.sh "'mvn' -B -U ${goals} -Dsuite=${suiteNameForUnix}"
			} else {
				def suiteNameForWindows = Configuration.get("suite").replace("/", "\\")
				context.echo "Suite for Windows: ${suiteNameForWindows}"
				context.bat "mvn -B -U ${goals} -Dsuite=${suiteNameForWindows}"
			}

		}
	}

    protected def enableVideoStreaming(node, message, capability, goals) {
        if ("web".equalsIgnoreCase(node) || "android".equalsIgnoreCase(node)) {
            context.println message
            goals += capability
        }
        return goals
    }

    protected def addOptionalParameter(parameter, message, capability, goals) {
        if (Configuration.get(parameter) && Configuration.get(parameter).toBoolean()) {
            context.println message
            goals += capability
        }
        return goals
    }

	protected String chooseNode() {

        Configuration.set("node", "master") //master is default node to execute job

		//TODO: handle browserstack etc integration here?
		switch(Configuration.get("platform").toLowerCase()) {
			case "api":
				context.println("Suite Type: API")
				Configuration.set("node", "api")
				Configuration.set("browser", "NULL")
				break;
			case "android":
				context.println("Suite Type: ANDROID")
				Configuration.set("node", "android")
				break;
			case "ios":
				//TODO: Need to improve this to be able to handle where emulator vs. physical tests should be run.
				context.println("Suite Type: iOS")
				Configuration.set("node", "ios")
				break;
			default:
				if ("NULL".equals(Configuration.get("browser"))) {
					context.println("Suite Type: Default")
					Configuration.set("node", "master")
				} else {
					context.println("Suite Type: Web")
					Configuration.set("node", "web")
				}
		}
		
		def nodeLabel = Configuration.get("node_label")
		context.println("nodeLabel: " + nodeLabel)
		if (!isParamEmpty(nodeLabel)) {
			context.println("overriding default node to: " + nodeLabel)
			Configuration.set("node", nodeLabel)
		}

		context.println "node: " + Configuration.get("node")
		return Configuration.get("node")
	}

	protected String getUUID() {
		def ci_run_id = Configuration.get("ci_run_id")
		context.echo "uuid from jobParams: " + ci_run_id
		if (ci_run_id == null || ci_run_id.isEmpty()) {
				ci_run_id = randomUUID() as String
		}
		context.echo "final uuid: " + ci_run_id
		return ci_run_id
	}

	protected String getFailure(currentBuild) {
		//TODO: move string constants into object/enum if possible
		currentBuild.result = 'FAILURE'
		def failureReason = "undefined failure"

		String JOB_URL = Configuration.get(Configuration.Parameter.JOB_URL)
		String BUILD_NUMBER = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
		String JOB_NAME = Configuration.get(Configuration.Parameter.JOB_NAME)

		def bodyHeader = "<p>Unable to execute tests due to the unrecognized failure: ${JOB_URL}${BUILD_NUMBER}</p>"
		def subject = "UNRECOGNIZED FAILURE: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"

		if (currentBuild.rawBuild.log.contains("COMPILATION ERROR : ")) {
			failureReason = "COMPILATION ERROR"
			bodyHeader = "<p>Unable to execute tests due to the compilation failure. ${JOB_URL}${BUILD_NUMBER}</p>"
			subject = "COMPILATION FAILURE: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
		} else if (currentBuild.rawBuild.log.contains("BUILD FAILURE")) {
			failureReason = "BUILD FAILURE"
			bodyHeader = "<p>Unable to execute tests due to the build failure. ${JOB_URL}${BUILD_NUMBER}</p>"
			subject = "BUILD FAILURE: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
		} else  if (currentBuild.rawBuild.log.contains("Aborted by ")) {
			currentBuild.result = 'ABORTED'
			failureReason = "Aborted by " + getAbortCause(currentBuild)
			bodyHeader = "<p>Unable to continue tests due to the abort by " + getAbortCause(currentBuild) + " ${JOB_URL}${BUILD_NUMBER}</p>"
			subject = "ABORTED: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
		} else  if (currentBuild.rawBuild.log.contains("Cancelling nested steps due to timeout")) {
			currentBuild.result = 'ABORTED'
			failureReason = "Aborted by timeout"
			bodyHeader = "<p>Unable to continue tests due to the abort by timeout ${JOB_URL}${BUILD_NUMBER}</p>"
			subject = "TIMED OUT: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
		}


		def body = bodyHeader + """<br>Rebuild: ${JOB_URL}${BUILD_NUMBER}/rebuild/parameterized<br>
					${etafReport}: ${JOB_URL}${BUILD_NUMBER}/${etafReportEncoded}<br>
					Console: ${JOB_URL}${BUILD_NUMBER}/console"""

//        def to = Configuration.get("email_list") + "," + Configuration.get(Configuration.Parameter.ADMIN_EMAILS)
        def to = Configuration.get(Configuration.Parameter.ADMIN_EMAILS)
        //TODO: enable emailing but seems like it should be moved to the notification code
        context.emailext getEmailParams(body, subject, to)
		return failureReason
	}

	protected String getAbortCause(currentBuild)
	{
		def causee = ''
		def actions = currentBuild.getRawBuild().getActions(jenkins.model.InterruptedBuildAction)
		for (action in actions) {
			def causes = action.getCauses()

			// on cancellation, report who cancelled the build
			for (cause in causes) {
				causee = cause.getUser().getDisplayName()
				cause = null
			}
			causes = null
			action = null
		}
		actions = null

		return causee
	}

	protected boolean isFailure(currentBuild) {
		boolean failure = false
		if (currentBuild.result) {
			failure = "FAILURE".equals(currentBuild.result.name)
		}
		return failure
	}

	protected boolean isParamEmpty(String value) {
		if (value == null || value.isEmpty() || value.equals("NULL")) {
			return true
		} else {
			return false
		}
	}

	protected String getSubProjectFolder() {
		//specify current dir as subProject folder by default
		def subProjectFolder = "."
		if (!isParamEmpty(Configuration.get("sub_project"))) {
			subProjectFolder = "./" + Configuration.get("sub_project")
		}
		return subProjectFolder
	}

	//TODO: move into valid jacoco related package
	protected void publishJacocoReport() {
		def JACOCO_ENABLE = Configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean()
		if (!JACOCO_ENABLE) {
			context.println("do not publish any content to AWS S3 if integration is disabled")
			return
		}

		def JACOCO_BUCKET = Configuration.get(Configuration.Parameter.JACOCO_BUCKET)
		def JOB_NAME = Configuration.get(Configuration.Parameter.JOB_NAME)
		def BUILD_NUMBER = Configuration.get(Configuration.Parameter.BUILD_NUMBER)

		def files = context.findFiles(glob: '**/jacoco.exec')
		if(files.length == 1) {
			context.archiveArtifacts artifacts: '**/jacoco.exec', fingerprint: true, allowEmptyArchive: true
			// https://github.com/jenkinsci/pipeline-aws-plugin#s3upload
			//TODO: move region 'us-west-1' into the global var 'JACOCO_REGION'
			context.withAWS(region: 'us-west-1',credentials:'aws-jacoco-token') {
				context.s3Upload(bucket:"$JACOCO_BUCKET", path:"$JOB_NAME/$BUILD_NUMBER/jacoco-it.exec", includePathPattern:'**/jacoco.exec')
			}
		}
	}
	
	protected void reportingResults() {
		context.stage('Results') {
            publishReports('**/reports/qa/emailable-report.html', "${etafReport}")
            publishReports('**/artifacts/**', 'eTAF_Artifacts')
            publishReports('**/target/surefire-reports/index.html', 'Full TestNG HTML Report')
            publishReports('**/target/surefire-reports/emailable-report.html', 'TestNG Summary HTML Report')
		}
	}
	
	protected void exportZafiraReport() {
		//replace existing local emailable-report.html by Zafira content
		def zafiraReport = zc.exportZafiraReport(uuid)
		if (!zafiraReport.isEmpty()) {
			context.writeFile file: "${etafReportFolder}/emailable-report.html", text: zafiraReport
		}
		
		//TODO: think about method renaming because in additions it also could redefin job status in Jenkins.
		// or move below code into another method
		
		// set job status based on zafira report
		if (!zafiraReport.contains("PASSED:") && !zafiraReport.contains("PASSED (known issues):") && !zafiraReport.contains("SKIP_ALL:")) {
			context.echo "Unable to Find (Passed) or (Passed Known Issues) within the eTAF Report."
			context.currentBuild.result = 'FAILURE'
		} else if (zafiraReport.contains("SKIP_ALL:")) {
			context.currentBuild.result = 'UNSTABLE'
		}
	}

	protected void sendTestRunResultsEmail() {
        String emailList = Configuration.get("email_list")
        emailList = overrideRecipients(emailList)
        String failureEmailList = Configuration.get("failure_email_list")

        if (emailList != null && !emailList.isEmpty()) {
			zc.sendTestRunResultsEmail(uuid, emailList, "all")
		}
		if (isFailure(context.currentBuild.rawBuild) && failureEmailList != null && !failureEmailList.isEmpty()) {
			zc.sendTestRunResultsEmail(uuid, failureEmailList, "failures")
		}
	}

	@NonCPS
	protected def sortPipelineList(List pipelinesList) {
		context.println("Finished Dynamic Mapping: " + pipelinesList.dump())
		pipelinesList = pipelinesList.sort { map1, map2 -> !map1.order ? !map2.order ? 0 : 1 : !map2.order ? -1 : map1.order.toInteger() <=> map2.order.toInteger() }
		context.println("Finished Dynamic Mapping Sorted Order: " + pipelinesList.dump())
		return pipelinesList
		
	}

	protected void parsePipeline(String filePath) {
		//context.println("filePath: " + filePath)
		def XmlSuite currentSuite = null
		try {
			currentSuite = parseSuite(filePath)
		} catch (FileNotFoundException e) {
			context.println("ERROR! Unable to find suite: " + filePath)
			return
		} catch (Exception e) {
			context.println("ERROR! Unable to parse suite: " + filePath)
			context.println(e.printStackTrace())
			return
		}

		def jobName = currentSuite.getParameter("jenkinsJobName").toString()
		def supportedPipelines = currentSuite.getParameter("jenkinsRegressionPipeline").toString() 
		def orderNum = currentSuite.getParameter("jenkinsJobExecutionOrder").toString()
		if (orderNum.equals("null")) {
			orderNum = "0"
			context.println("specify by default '0' order - start asap")
		}
		def executionMode = currentSuite.getParameter("jenkinsJobExecutionMode").toString()

		def supportedEnvs = currentSuite.getParameter("jenkinsPipelineEnvironments").toString()
		
		def currentEnvs = getCronEnv(currentSuite)
		def pipelineJobName = Configuration.get(Configuration.Parameter.JOB_BASE_NAME)

		// override suite email_list from params if defined
		def emailList = currentSuite.getParameter("jenkinsEmail").toString()
		def paramEmailList = Configuration.get("email_list")
		if (paramEmailList != null && !paramEmailList.isEmpty()) {
			emailList = paramEmailList
		}
		
		def priorityNum = "5"
		def curPriorityNum = Configuration.get("BuildPriority")
		if (curPriorityNum != null && !curPriorityNum.isEmpty()) {
			priorityNum = curPriorityNum //lowest priority for pipeline/cron jobs. So manually started jobs has higher priority among CI queue
		}

		//def overrideFields = currentSuite.getParameter("overrideFields").toString()
		def overrideFields = Configuration.get("overrideFields")

        String supportedBrowsers = currentSuite.getParameter("jenkinsPipelineBrowsers").toString()
		String logLine = "pipelineJobName: ${pipelineJobName};\n	supportedPipelines: ${supportedPipelines};\n	jobName: ${jobName};\n	orderNum: ${orderNum};\n	email_list: ${emailList};\n	supportedEnvs: ${supportedEnvs};\n	currentEnv(s): ${currentEnvs};\n	supportedBrowsers: ${supportedBrowsers};\n"
		
		def currentBrowser = Configuration.get("browser")

		if (currentBrowser == null || currentBrowser.isEmpty()) {
			currentBrowser = "NULL"
		}

		logLine += "	currentBrowser: ${currentBrowser};\n"
		context.println(logLine)
		
		if (!supportedPipelines.contains("null")) {
			for (def pipeName : supportedPipelines.split(",")) {
				if (!pipelineJobName.equals(pipeName)) {
					//launch test only if current pipeName exists among supportedPipelines 
					continue;
				}

				
				for (def currentEnv : currentEnvs.split(",")) {
					for (def supportedEnv : supportedEnvs.split(",")) {
						//context.println("supportedEnv: " + supportedEnv)
						if (!currentEnv.equals(supportedEnv) && !currentEnv.toString().equals("null")) {
							//context.println("Skip execution for env: ${supportedEnv}; currentEnv: ${currentEnv}")
							//launch test only if current suite support cron regression execution for current env
							continue;
						}
	
						for (def supportedBrowser : supportedBrowsers.split(",")) {
							// supportedBrowsers - list of supported browsers for suite which are declared in testng suite xml file
							// supportedBrowser - splitted single browser name from supportedBrowsers
							def browser = currentBrowser
							def browserVersion = null
							def os = null
							def osVersion = null
	
							String browserInfo = supportedBrowser
							if (supportedBrowser.contains("-")) {
								def systemInfoArray = supportedBrowser.split("-")
								String osInfo = systemInfoArray[0]
								os = OS.getName(osInfo)
								osVersion = OS.getVersion(osInfo)
								browserInfo = systemInfoArray[1]
							}
							def browserInfoArray = browserInfo.split(" ")
							browser = browserInfoArray[0]
							if (browserInfoArray.size() > 1) {
								browserVersion = browserInfoArray[1]
							}
	
	                        // currentBrowser - explicilty selected browser on cron/pipeline level to execute tests
	
							//context.println("supportedBrowser: ${supportedBrowser}; currentBrowser: ${currentBrowser}; ")
							if (!currentBrowser.equals(supportedBrowser) && !currentBrowser.toString().equals("NULL")) {
								//context.println("Skip execution for browser: ${supportedBrowser}; currentBrowser: ${currentBrowser}")
								continue;
							}
							
							//context.println("adding ${filePath} suite to pipeline run...")
	
							def pipelineMap = [:]
	
							def branch = Configuration.get("branch")
							def ci_parent_url = Configuration.get("ci_parent_url")
							if (ci_parent_url.isEmpty()) {
								ci_parent_url = Configuration.get(Configuration.Parameter.JOB_URL)
							}
							def ci_parent_build = Configuration.get("ci_parent_build")
							if (ci_parent_build.isEmpty()) {
								ci_parent_build = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
							}
							def retry_count = Configuration.get("retry_count")
							def thread_count = Configuration.get("thread_count")
							// put all not NULL args into the pipelineMap for execution
	                        putNotNull(pipelineMap, "browser", browser)
	                        putNotNull(pipelineMap, "browser_version", browserVersion)
	                        putNotNull(pipelineMap, "os", os)
	                        putNotNull(pipelineMap, "os_version", osVersion)
							pipelineMap.put("name", pipeName)
							pipelineMap.put("branch", branch)
							pipelineMap.put("ci_parent_url", ci_parent_url)
							pipelineMap.put("ci_parent_build", ci_parent_build)
							pipelineMap.put("retry_count", retry_count)
	                        putNotNull(pipelineMap, "thread_count", thread_count)
							pipelineMap.put("jobName", jobName)
							pipelineMap.put("env", supportedEnv)
							pipelineMap.put("order", orderNum)
							pipelineMap.put("BuildPriority", priorityNum)
	                        putNotNullWithSplit(pipelineMap, "emailList", emailList)
	                        putNotNullWithSplit(pipelineMap, "executionMode", executionMode)
	                        putNotNull(pipelineMap, "overrideFields", overrideFields)
	
							//context.println("initialized ${filePath} suite to pipeline run...")
							registerPipeline(currentSuite, pipelineMap)
						}
	
					}
				}
			}
		}
	}

	protected def getCronEnv(currentSuite) {
		//currentSuite is need to override action in private pipelines
		return Configuration.get("env")
	}

	protected def registerPipeline(currentSuite, pipelineMap) {
		listPipelines.add(pipelineMap)
	}


    protected void putNotNull(pipelineMap, key, value) {
        if (value != null && !value.equalsIgnoreCase("null")) {
            pipelineMap.put(key, value)
        }
    }

    protected void putNotNullWithSplit(pipelineMap, key, value) {
        if (value != null) {
            pipelineMap.put(key, value.replace(", ", ","))
        }
    }

	protected def executeStages(String folderName) {
		def mappedStages = [:]

		boolean parallelMode = true

		//combine jobs with similar priority into the single paralle stage and after that each stage execute in parallel
		String beginOrder = "0"
		String curOrder = ""
		for (Map entry : listPipelines) {
			def stageName = String.format("Stage: %s Environment: %s Browser: %s", entry.get("jobName"), entry.get("env"), entry.get("browser"))
			context.println("stageName: ${stageName}")

			boolean propagateJob = true
			if (entry.get("executionMode").toString().contains("continue")) {
				//do not interrupt pipeline/cron if any child job failed
				propagateJob = false
			}
			if (entry.get("executionMode").toString().contains("abort")) {
				//interrupt pipeline/cron and return fail status to piepeline if any child job failed
				propagateJob = true
			}

			curOrder = entry.get("order")
			//context.println("beginOrder: ${beginOrder}; curOrder: ${curOrder}")
			
			// do not wait results for jobs with default order "0". For all the rest we should wait results between phases
			boolean waitJob = false
			if (curOrder.toInteger() > 0) {
				waitJob = true
			}
			
			if (curOrder.equals(beginOrder)) {
				//context.println("colect into order: ${curOrder}; job: ${stageName}")
				mappedStages[stageName] = buildOutStages(folderName, entry, waitJob, propagateJob)
			} else {
				context.parallel mappedStages
				
				//reset mappedStages to empty after execution
				mappedStages = [:]
				beginOrder = curOrder
				
				//add existing pipeline as new one in the current stage
				mappedStages[stageName] = buildOutStages(folderName, entry, waitJob, propagateJob)
			}
		}
		
		if (!mappedStages.isEmpty()) {
			//context.println("launch jobs with order: ${curOrder}")
			context.parallel mappedStages
		}

	}
	
	def buildOutStages(String folderName, Map entry, boolean waitJob, boolean propagateJob) {
		return {
			buildOutStage(folderName, entry, waitJob, propagateJob)
		}
	}
	
	protected def buildOutStage(String folderName, Map entry, boolean waitJob, boolean propagateJob) {
		context.stage(String.format("Stage: %s Environment: %s Browser: %s", entry.get("jobName"), entry.get("env"), entry.get("browser"))) {
			//context.println("Dynamic Stage Created For: " + entry.get("jobName"))
			//context.println("Checking EmailList: " + entry.get("emailList"))

			List jobParams = []
			for (param in entry) {
				jobParams.add(context.string(name: param.getKey(), value: param.getValue()))
			}
			context.println(jobParams.dump())
            //context.println("propagate: " + propagateJob)
			try {
				context.build job: folderName + "/" + entry.get("jobName"),
				propagate: propagateJob,
				parameters: jobParams,
				wait: waitJob
			} catch (Exception ex) {
                printStackTrace(ex)

                def body = "Unable to start job via cron! " + ex.getMessage()
                def subject = "JOBSTART FAILURE: " + entry.get("jobName")
                def to = entry.get("email_list") + "," + Configuration.get("email_list")

                context.emailext getEmailParams(body, subject, to)
            }
        }
	}

    protected def getEmailParams(body, subject, to) {
        def params = [attachLog: true,
                      body: body,
                      recipientProviders: [[$class: 'DevelopersRecipientProvider'],
                                           [$class: 'RequesterRecipientProvider']],
                      subject: subject,
                      to: to]
        return params
    }

	protected void startBrowserStackLocal(String uniqueBrowserInstance) {
		def browserStackUrl = "https://www.browserstack.com/browserstack-local/BrowserStackLocal"
		def accessKey = Configuration.get("BROWSERSTACK_ACCESS_KEY")
		if (context.isUnix()) {
			def browserStackLocation = "/var/tmp/BrowserStackLocal"
			if (!context.fileExists(browserStackLocation)) {
				context.sh "curl -sS " + browserStackUrl + "-linux-x64.zip > " + browserStackLocation + ".zip"
				context.unzip dir: "/var/tmp", glob: "", zipFile: browserStackLocation + ".zip"
				context.sh "chmod +x " + browserStackLocation
			}
			context.sh browserStackLocation + " --key " + accessKey + " --local-identifier " + uniqueBrowserInstance + " --force-local " + " &"
		} else {
			def browserStackLocation = "C:\\tmp\\BrowserStackLocal"
			if (!context.fileExists(browserStackLocation + ".exe")) {
				context.powershell(returnStdout: true, script: """[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Invoke-WebRequest -Uri \'${browserStackUrl}-win32.zip\' -OutFile \'${browserStackLocation}.zip\'""")
				context.unzip dir: "C:\\tmp", glob: "", zipFile: "${browserStackLocation}.zip"
			}
			context.powershell(returnStdout: true, script: "Start-Process -FilePath '${browserStackLocation}.exe' -ArgumentList '--key ${accessKey} --local-identifier ${uniqueBrowserInstance} --force-local'")
		}
	}

	protected void setZafiraReportFolder(folder) {
		etafReportFolder = folder
	}

	protected boolean isBrowserStackRun() {
		boolean res = false
		def customCapabilities = Configuration.get("custom_capabilities")
		if (!isParamEmpty(customCapabilities)) {
			if (customCapabilities.toLowerCase().contains("browserstack")) {
				res = true
			}
		}
		return res
	}

	protected def overrideRecipients(emailList) {
		return emailList
	}
}
