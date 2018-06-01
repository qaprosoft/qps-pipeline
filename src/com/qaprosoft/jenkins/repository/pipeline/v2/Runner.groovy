package com.qaprosoft.jenkins.repository.pipeline.v2

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.XmlSuite;

import static java.util.UUID.randomUUID
import com.qaprosoft.zafira.ZafiraClient

import com.qaprosoft.scm.github.GitHub;

import com.qaprosoft.jenkins.repository.pipeline.v2.Executor

class Runner extends Executor {
	//ci_run_id  param for unique test run identification
	protected def uuid
	
	protected def zc
	
	//using constructor it will be possible to redefine this folder on pipeline/jobdsl level
	protected def folderName = "Automation"
	
	//CRON related vars
	protected def listPipelines = []
	
	
	public Runner(context) {
		super(context)
		scmClient = new GitHub(context)
	}
	
	public void runCron() {
		jobParams = initParams(context.currentBuild)
		jobVars = initVars(context.env)

		def nodeName = "master"
		//TODO: remove master node assignment
		context.node(nodeName) {
			scmClient.clone(jobParams, jobVars)
		
			def WORKSPACE = this.getWorkspace()
			context.println("WORKSPACE: " + WORKSPACE)
			def project = jobParams.get("project")
			def sub_project = jobParams.get("sub_project")
			def jenkinsFile = ".jenkinsfile.json"
			
			if (!context.fileExists("${jenkinsFile}")) {
				context.println("no .jenkinsfile.json discovered! Scannr will use default qps-pipeline logic for project: ${project}")
			}

			def suiteFilter = "src/test/resources/**"
			Object subProjects = this.parseJSON(WORKSPACE + "/" + jenkinsFile).sub_projects
			subProjects.each {
				if (it.name.equals(sub_project)) {
					suiteFilter = it.suite_filter
				}
			}

			def subProjectFilter = sub_project
			if (sub_project.equals(".")) {
				subProjectFilter = "**"
			}

			def files = context.findFiles(glob: subProjectFilter + "/" + suiteFilter + "/**")
			if(files.length > 0) {
				context.println("Number of Test Suites to Scan Through: " + files.length)
				for (int i = 0; i < files.length; i++) {
					this.parsePipeline(jobVars, jobParams, WORKSPACE + "/" + files[i].path)
				}

				context.println("Finished Dynamic Mapping: " + listPipelines)
				sortPipelineList()
				context.println("Finished Dynamic Mapping Sorted Order: " + listPipelines)

				this.executeStages(folderName)
			} else {
				context.println("No Test Suites Found to Scan...")
			}
			
		}		
	}
	
	
	public void runJob() {
		jobParams = initParams(context.currentBuild)
		jobVars = initVars(context.env)
		uuid = getUUID()
		
		def nodeName = "master"
		//TODO: remove master node assignment
		context.node(nodeName) {
			// init ZafiraClient to register queued run and abort it at the end of the run pipeline
			try {
				zc = new ZafiraClient(context, jobVars.get("ZAFIRA_SERVICE_URL"), jobParams.get("develop"))
				def token = zc.getZafiraAuthToken(jobVars.get("ZAFIRA_ACCESS_TOKEN"))
				zc.queueZafiraTestRun(uuid, jobVars, jobParams)
			} catch (Exception ex) {
				printStackTrace(ex)
			}
	
			nodeName = chooseNode(jobParams)
		}
		
		context.node(nodeName) {
			context.wrap([$class: 'BuildUser']) {
				try {
					context.timestamps {
						
						this.prepare(context.currentBuild, jobParams, jobVars)
						scmClient.clone(jobParams, jobVars)


						this.downloadResources(jobParams, jobVars)

						def timeoutValue = jobVars.get("JOB_MAX_RUN_TIME")
						context.timeout(time: timeoutValue.toInteger(), unit: 'MINUTES') {
							  this.build(jobParams, jobVars)  
						}
						
						//TODO: think about seperate stage for uploading jacoco reports
						this.publishJacocoReport(jobVars);
					}
					
				} catch (Exception ex) {
					String failureReason = getFailure(context.currentBuild, jobParams, jobVars)
					context.echo "failureReason: ${failureReason}"
					//explicitly execute abort to resolve anomalies with in_progress tests...
					zc.abortZafiraTestRun(uuid, failureReason)
					throw ex
				} finally {
					this.reportingResults()
					//TODO: send notification via email, slack, hipchat and whatever... based on subscrpition rules
					this.clean()
				}
			}
		}

	}

    public void rerunJobs(){
        jobParams = initParams(context.currentBuild)
        context.println("asdasd")
        context.println(jobParams.get("hashcode"))
        context.println(jobParams.get("failurePercent"))
        context.println(jobParams.get("rerunFailures"))
        context.println(jobParams.get("doRebuild"))
    }

	//TODO: moved almost everything into argument to be able to move this methoud outside of the current class later if necessary
	protected void prepare(currentBuild, params, vars) {
		String BUILD_NUMBER = vars.get("BUILD_NUMBER")
		String CARINA_CORE_VERSION = vars.get("CARINA_CORE_VERSION")

		String suite = params.get("suite")
		String branch = params.get("branch")
		String _env = params.get("env")
		String device = params.get("device")
		String browser = params.get("browser")
		//TODO: improve carina to detect browser_version on the fly
		String browser_version = params.get("browser_version")

		context.stage('Preparation') {
			currentBuild.displayName = "#${BUILD_NUMBER}|${suite}|${_env}|${branch}"
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
			
			// identify if it is mobile test using "device" param. Don't reuse node as it can be changed based on client needs 
			if (device != null && !device.isEmpty() && !device.equalsIgnoreCase("NULL")) {
				//this is mobile test
				this.prepareForMobile(params)
			}
		}
	}

	protected void prepareForMobile(params) {
		def device = params.get("device")
		def defaultPool = params.get("DefaultPool")
		def platform = params.get("platform")

		if (platform.equalsIgnoreCase("android")) {
			prepareForAndroid(params)
		} else if (platform.equalsIgnoreCase("ios")) {
			prepareForiOS(params)
		} else {
			context.echo "Unable to identify mobile platform: ${platform}"
		}

		//general mobile capabilities
		if ("DefaultPool".equalsIgnoreCase(device)) {
			//reuse list of devices from hidden parameter DefaultPool
			params.put("capabilities.deviceName", defaultPool)
		} else {
			params.put("capabilities.deviceName", device)
		}

		// ATTENTION! Obligatory remove device from the params otherwise
		// hudson.remoting.Channel$CallSiteStackTrace: Remote call to JNLP4-connect connection from qpsinfra_jenkins-slave_1.qpsinfra_default/172.19.0.9:39487
		// Caused: java.io.IOException: remote file operation failed: /opt/jenkins/workspace/Automation/<JOB_NAME> at hudson.remoting.Channel@2834589:JNLP4-connect connection from
		params.remove("device")

		//TODO: move it to the global jenkins variable
		params.put("capabilities.newCommandTimeout", "180")
		params.put("java.awt.headless", "true")

	}
	
	protected void prepareForAndroid(params) {
		params.put("mobile_app_clear_cache", "true")

		params.put("capabilities.platformName", "ANDROID")

		params.put("capabilities.autoGrantPermissions", "true")
		params.put("capabilities.noSign", "true")
		params.put("capabilities.STF_ENABLED", "true")
		
		customPrepareForAndroid(params)
	}
	
	protected void customPrepareForAndroid(params) {
		//do nothing here
	}
		

	protected void prepareForiOS(params) {

		params.put("capabilities.platform", "IOS")
		params.put("capabilities.platformName", "IOS")
		params.put("capabilities.deviceName", "*")

		params.put("capabilities.appPackage", "")
		params.put("capabilities.appActivity", "")

		params.put("capabilities.autoAcceptAlerts", "true")

		params.put("capabilities.STF_ENABLED", "false")
		
		customPrepareForiOS(params)
	}
	
	protected void customPrepareForiOS(params) {
		//do nothing here
	}
		
	protected void downloadResources(params, vars) {
		//DO NOTHING as of now

/*		def CARINA_CORE_VERSION = vars.get("CARINA_CORE_VERSION")
		context.stage("Download Resources") {
		def pomFile = getSubProjectFolder(params) + "/pom.xml"
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
	
	protected void build(params, vars) {
		context.stage('Run Test Suite') {

			def pomFile = getSubProjectFolder(params) + "/pom.xml"
			
			def CARINA_CORE_VERSION = vars.get("CARINA_CORE_VERSION")
			def CORE_LOG_LEVEL = vars.get("CORE_LOG_LEVEL")
			def SELENIUM_URL = vars.get("SELENIUM_URL")
			def ZAFIRA_BASE_CONFIG = vars.get("ZAFIRA_BASE_CONFIG")
			
			
			def JOB_URL = vars.get("JOB_URL")
			def BUILD_NUMBER = vars.get("BUILD_NUMBER")

			def branch = params.get("branch")
			//TODO: remove git_branch after update ZafiraListener: https://github.com/qaprosoft/zafira/issues/760
			params.put("git_branch", branch)
			params.put("scm_branch", branch)
			
			//TODO: investigate how user timezone can be declared on qps-infra level
			def DEFAULT_BASE_MAVEN_GOALS = "-Dcarina-core_version=$CARINA_CORE_VERSION -f ${pomFile}" \
				" -Dcore_log_level=$CORE_LOG_LEVEL -Dmaven.test.failure.ignore=true -Dselenium_host=$SELENIUM_URL -Dmax_screen_history=1" \
				" -Dinit_retry_count=0 -Dinit_retry_interval=10 $ZAFIRA_BASE_CONFIG clean test" //-Duser.timezone=PST

			//TODO: move 8000 port into the global var
			def mavenDebug=" -Dmaven.surefire.debug=\"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -Xnoagent -Djava.compiler=NONE\" "

			params.put("zafira_enabled", zc.isAvailable())
			params.put("ci_url", JOB_URL)
			params.put("ci_build", BUILD_NUMBER)



			
			def goals = DEFAULT_BASE_MAVEN_GOALS
			//register all env variables
			vars.each { k, v -> goals = goals + " -D${k}=\"${v}\""}
			
			//register all params after vars to be able to override
			params.each { k, v -> goals = goals + " -D${k}=\"${v}\""}

			//TODO: make sure that jobdsl adds for UI tests boolean args: "capabilities.enableVNC and capabilities.enableVideo" 
			if (vars.get("enableVNC")) {
				goals += " -Dcapabilities.enableVNC=true "
			}
			
			if (vars.get("enableVideo")) {
				goals += " -Dcapabilities.enableVideo=true "
			}
			
			if (vars.get("JACOCO_ENABLE").toBoolean()) {
				goals += " jacoco:instrument "
			}
			
			if (params.get("debug")) {
				context.echo "Enabling remote debug..."
				goals += mavenDebug
			}
			
			//append again overrideFields to make sure they are declared at the end
			goals += params.get("overrideFields")
			
			context.echo "goals: ${goals}"
			
			//TODO: adjust zafira_report_folder correclty
			if (context.isUnix()) {
				def suiteNameForUnix = params.get("suite").replace("\\", "/")
				context.echo "Suite for Unix: ${suiteNameForUnix}"
				context.sh "'mvn' -B -U ${goals} -Dsuite=${suiteNameForUnix} -Dzafira_report_folder=./reports/qa -Dreport_url=$JOB_URL$BUILD_NUMBER/eTAF_Report"
			} else {
				def suiteNameForWindows = "${suite}".replace("/", "\\")
				context.echo "Suite for Windows: ${suiteNameForWindows}"
				context.bat "mvn -B -U ${mvnBaseGoals} -Dsuite=${suiteNameForWindows} -Dzafira_report_folder=./reports/qa -Dreport_url=$JOB_URL$BUILD_NUMBER/eTAF_Report"
			}

			this.setJobResults(context.currentBuild)

		}
	}
	
	protected String chooseNode(params) {
		def platform = params.get("platform")
		def browser = params.get("browser")

		params.put("node", "master") //master is default node to execute job

		//TODO: handle browserstack etc integration here?
		switch(platform.toLowerCase()) {
			case "api":
				context.println("Suite Type: API")
				params.put("node", "api")
				params.put("browser", "NULL")
				break;
			case "android":
				context.println("Suite Type: ANDROID")
				params.put("node", "android")
				break;
			case "ios":
				//TODO: Need to improve this to be able to handle where emulator vs. physical tests should be run.
				context.println("Suite Type: iOS")
				params.put("node", "ios")
				break;
			default:
				if ("NULL".equals(browser)) {
					context.println("Suite Type: Default")
					params.put("node", "master")
				} else {
					context.println("Suite Type: Web")
					params.put("node", "web")
				}
		}
		context.echo "node: " + params.get("node") 
		return params.get("node")
	}

	protected String getUUID() {
		def ci_run_id = jobParams.get("ci_run_id")
		context.echo "uuid from jobParams: " + ci_run_id
		if (ci_run_id.isEmpty()) {
				ci_run_id = randomUUID() as String
		}
		context.echo "final uuid: " + ci_run_id
		return ci_run_id
	}
	
	//TODO: investigate howto transfer jobVars
	protected String getFailure(currentBuild, params, vars) {
		//TODO: move string constants into object/enum if possible
		currentBuild.result = 'FAILURE'
		def failureReason = "undefined failure"
		
		String JOB_URL = vars.get("JOB_URL")
		String BUILD_NUMBER = vars.get("BUILD_NUMBER")
		String JOB_NAME = vars.get("JOB_NAME")
		
		String email_list = params.get("email_list")

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
					eTAF_Report: ${JOB_URL}${BUILD_NUMBER}/eTAF_Report<br>
					Console: ${JOB_URL}${BUILD_NUMBER}/console"""

		//TODO: enable emailing but seems like it should be moved to the notification code
		//context.emailext attachLog: true, body: "${body}", recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']], subject: "${subject}", to: "${email_list}"
		//	context.emailext attachLog: true, body: "${body}", recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']], subject: "${subject}", to: "${email_list},${ADMIN_EMAILS}"
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
	
	protected boolean isParamEmpty(String value) {
		if (value == null || value.isEmpty() || value.equals("NULL")) {
			return true
		} else {
			return false
		}
	}

	protected String getSubProjectFolder(params) {
		//specify current dir as subProject folder by default
		def subProjectFolder = "."
		if (!isParamEmpty(params.get("sub_project"))) {
			subProjectFolder = "./" + params.get("sub_project")
		}
		return subProjectFolder
	}

	//TODO: move into valid jacoco related package
	protected void publishJacocoReport(vars) {
		def JACOCO_BUCKET = vars.get("JACOCO_BUCKET")
		def JOB_NAME = vars.get("JOB_NAME")
		def BUILD_NUMBER = vars.get("BUILD_NUMBER")

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
	
	protected void setJobResults(currentBuild) {
		//Need to do a forced failure here in case the report doesn't have PASSED or PASSED KNOWN ISSUES in it.
		//TODO: hardoced path here! Update logic to find it across all sub-folders
		String checkReport = context.readFile("./reports/qa/emailable-report.html")

		if (!checkReport.contains("PASSED:") && !checkReport.contains("PASSED (known issues):") && !checkReport.contains("SKIP_ALL:")) {
			context.echo "Unable to Find (Passed) or (Passed Known Issues) within the eTAF Report."
			currentBuild.result = 'FAILURE'
		} else if (checkReport.contains("SKIP_ALL:")) {
			currentBuild.result = 'UNSTABLE'
		}
	}

	
	protected void reportingResults() {
		context.stage('Results') {
			if (!publishReport('**/reports/qa/zafira-report.html', 'eTAF_Report')) {
				publishReport('**/reports/qa/emailable-report.html', 'eTAF_Report')
			}
			
			publishReport('**/artifacts/**', 'eTAF_Artifacts')
			
			publishTestNgReports('**/target/surefire-reports/index.html', 'Full TestNG HTML Report')
			publishTestNgReports('**/target/surefire-reports/emailable-report.html', 'TestNG Summary HTML Report')

		}
	}
	
	protected void publishTestNgReports(String pattern, String reportName) {
		def reports = context.findFiles(glob: "${pattern}")
		for (int i = 0; i < reports.length; i++) {
			def reportDir = new File(reports[i].path).getParentFile()
			context.echo "Report File Found, Publishing ${reports[i].path}"
			def reportIndex = ""
			if (i > 0) {
				reportIndex = "_" + i
			}
			context.publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: "${reportDir}", reportFiles: "${reports[i].name}", reportName: "${reportName}${reportIndex}"])
		}
	}


	protected boolean publishReport(String pattern, String reportName) {
		def files = context.findFiles(glob: "${pattern}")
		if(files.length == 1) {
			def reportFile = files[0]
			def reportDir = new File(reportFile.path).getParentFile()
			context.echo "Report File Found, Publishing ${reportFile.path}"
			context.publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: "${reportDir}", reportFiles: "${reportFile.name}", reportName: "${reportName}"])
			return true;
		} else if (files.length > 1) {
			context.echo "ERROR: too many report file discovered! count: ${files.length}"
			return false;
		} else {
			context.echo "No report file discovered: ${reportName}"
			return false;
		}
	}
	
	
	protected void sortPipelineList() {
		listPipelines.sort { map1, map2 -> !map1.order ? !map2.order ? 0 : 1 : !map2.order ? -1 : map1.order.toInteger() <=> map2.order.toInteger() }
	}

	protected void parsePipeline(jobVars, jobParams, String filePath) {
		//context.println("filePath: " + filePath)
		XmlSuite currentSuite = parseSuite(filePath)
		
		def jobName = currentSuite.getParameter("jenkinsJobName").toString()
		def supportedPipelines = currentSuite.getParameter("jenkinsRegressionPipeline").toString() 
		def orderNum = currentSuite.getParameter("jenkinsJobExecutionOrder").toString()
		if (orderNum.equals("null")) {
			orderNum = "0"
			context.println("specify by default '0' order - start asap")
		}
		def executionMode = currentSuite.getParameter("jenkinsJobExecutionMode").toString()

		def supportedEnvs = currentSuite.getParameter("jenkinsPipelineEnvironments").toString()
		
		def currentEnv = jobParams.get("env")
		def pipelineJobName = jobVars.get("JOB_BASE_NAME")

		// override suite email_list from params if defined
		def emailList = currentSuite.getParameter("jenkinsEmail").toString()
		def paramEmailList = jobParams.get("email_list")
		if (!paramEmailList.isEmpty()) {
			emailList = paramEmailList
		}
		
		def priorityNum = "5"
		def curPriorityNum = jobParams.get("priority")
		if (curPriorityNum != null && !curPriorityNum.isEmpty()) {
			priorityNum = curPriorityNum //lowest priority for pipeline/cron jobs. So manually started jobs has higher priority among CI queue
		}
		
		
		def supportedBrowsers = currentSuite.getParameter("jenkinsPipelineBrowsers").toString()
		String logLine = "pipelineJobName: ${pipelineJobName};\n	supportedPipelines: ${supportedPipelines};\n	jobName: ${jobName};\n	orderNum: ${orderNum};\n	email_list: ${emailList};\n	supportedEnvs: ${supportedEnvs};\n	currentEnv: ${currentEnv};\n	supportedBrowsers: ${supportedBrowsers};\n"
		
		def currentBrowser = jobParams.get("browser")
		if (currentBrowser == null) {
			currentBrowser = "null"
		}
		logLine += "	currentBrowser: ${currentBrowser};\n"
		context.println(logLine)
		
		if (!supportedPipelines.contains("null")) {
			for (def pipeName : supportedPipelines.split(",")) {
				if (!pipelineJobName.equals(pipeName)) {
					//launch test only if current pipeName exists among supportedPipelines 
					continue;
				}

				for (def supportedEnv : supportedEnvs.split(",")) {
					context.println("supportedEnv: " + supportedEnv)
					if (!currentEnv.equals(supportedEnv) && !currentEnv.toString().equals("null")) {
						context.println("Skip execution for env: ${supportedEnv}; currentEnv: ${currentEnv}")
						//launch test only if current suite support cron regression execution for current env
						continue;
					}


					for (def supportedBrowser : supportedBrowsers.split(",")) {
						// supportedBrowsers - list of supported browsers for suite which are declared in testng suite xml file
						// supportedBrowser - splitted single browser name from supportedBrowsers

						// currentBrowser - explicilty selected browser on cron/pipeline level to execute tests

						context.println("supportedBrowser: ${supportedBrowser}; currentBrowser: ${currentBrowser}; ")
						if (!currentBrowser.equals(supportedBrowser) && !currentBrowser.toString().equals("null")) {
							context.println("Skip execution for browser: ${supportedBrowser}; currentBrowser: ${currentBrowser}")
							continue;
						}
						
						//context.println("adding ${filePath} suite to pipeline run...")

						def pipelineMap = [:]

						def branch = jobParams.get("branch")
						def ci_parent_url = jobParams.get("ci_parent_url")
						if (ci_parent_url.isEmpty()) {
							ci_parent_url = jobVars.get("JOB_URL")
						}
						def ci_parent_build = jobParams.get("ci_parent_build")
						if (ci_parent_build.isEmpty()) {
							ci_parent_build = jobVars.get("BUILD_NUMBER")
						}
						def retry_count = jobParams.get("retry_count")
						def thread_count = jobParams.get("thread_count")

						pipelineMap.put("browser", supportedBrowser)
						pipelineMap.put("name", pipeName)
						pipelineMap.put("branch", branch)
						pipelineMap.put("ci_parent_url", ci_parent_url)
						pipelineMap.put("ci_parent_build", ci_parent_build)
						pipelineMap.put("retry_count", retry_count)
						pipelineMap.put("thread_count", thread_count)
						pipelineMap.put("jobName", jobName)
						pipelineMap.put("environment", supportedEnv)
						pipelineMap.put("order", orderNum)
						pipelineMap.put("priority", priorityNum)
						pipelineMap.put("emailList", emailList.replace(", ", ","))
						pipelineMap.put("executionMode", executionMode.replace(", ", ","))

						//context.println("initialized ${filePath} suite to pipeline run...")
						//context.println("pipelines size1: " + listPipelines.size())
						listPipelines.add(pipelineMap)
						//context.println("pipelines size2: " + listPipelines.size())
					}

				}
			}
		}
	}

	protected def executeStages(String folderName) {
		//    for (Map entry : sortedPipeline) {
		//	buildOutStage(folderName, entry, false)
		//    }

		def mappedStages = [:]

		boolean parallelMode = true

		//combine jobs with similar priority into the single paralle stage and after that each stage execute in parallel
		String beginOrder = "0"
		String curOrder = ""
		for (Map entry : listPipelines) {
			def stageName = String.format("Stage: %s Environment: %s Browser: %s", entry.get("jobName"), entry.get("environment"), entry.get("browser"))
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
		context.stage(String.format("Stage: %s Environment: %s Browser: %s", entry.get("jobName"), entry.get("environment"), entry.get("browser"))) {
			//context.println("Dynamic Stage Created For: " + entry.get("jobName"))
			//context.println("Checking EmailList: " + entry.get("emailList"))
			
			def email_list = entry.get("email_list")
			def ADMIN_EMAILS = jobVars.get("email_list")

			//context.println("propagate: " + propagateJob)
			try {
				if (!entry.get("browser").isEmpty()) {
					context.build job: folderName + "/" + entry.get("jobName"),
						propagate: propagateJob,
						parameters: [context.string(name: 'branch', value: entry.get("branch")), context.string(name: 'env', value: entry.get("environment")), context.string(name: 'browser', value: entry.get("browser")), context.string(name: 'ci_parent_url', value: entry.get("ci_parent_url")), context.string(name: 'ci_parent_build', value: entry.get("ci_parent_build")), context.string(name: 'email_list', value: entry.get("emailList")), context.string(name: 'thread_count', value: entry.get("thread_count")), context.string(name: 'retry_count', value: entry.get("retry_count")), context.string(name: 'BuildPriority', value: entry.get("priority")),],
						wait: waitJob
				} else {
					context.build job: folderName + "/" + entry.get("jobName"),
						propagate: propagateJob,
						parameters: [context.string(name: 'branch', value: entry.get("branch")), context.string(name: 'env', value: entry.get("environment")), context.string(name: 'ci_parent_url', value: entry.get("ci_parent_url")), context.string(name: 'ci_parent_build', value: entry.get("ci_parent_build")), context.string(name: 'email_list', value: entry.get("emailList")), context.string(name: 'thread_count', value: entry.get("thread_count")), context.string(name: 'retry_count', value: entry.get("retry_count")), context.string(name: 'BuildPriority', value: entry.get("priority")),],
						wait: waitJob
				}
			} catch (Exception ex) {
				printStackTrace(ex)
				
				context.emailext attachLog: true, body: "Unable to start job via cron! " + ex.getMessage(), recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']], subject: "JOBSTART FAILURE: ${entry.get("jobName")}", to: "${email_list},${ADMIN_EMAILS}"
			}

		}
	}
}
