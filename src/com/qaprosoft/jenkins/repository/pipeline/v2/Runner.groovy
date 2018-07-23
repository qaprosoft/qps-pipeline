package com.qaprosoft.jenkins.repository.pipeline.v2

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.XmlSuite;

import static java.util.UUID.randomUUID
import com.qaprosoft.zafira.ZafiraClient

import com.qaprosoft.scm.github.GitHub;
import com.qaprosoft.jenkins.repository.pipeline.v2.Configurator
import com.qaprosoft.jenkins.repository.pipeline.v2.Executor

class Runner extends Executor {
	//ci_run_id  param for unique test run identification
	protected def uuid
	
	protected def zc
	
	//using constructor it will be possible to redefine this folder on pipeline/jobdsl level
	protected def folderName = "Automation"
	
	// with new Zafirta implementation it could be static and finalfor any project
	protected static final String ZAFIRA_REPORT_FOLDER = "./reports/qa"
	protected static final String etafReport = "eTAF_Report"
	//TODO: /api/test/runs/{id}/export should use encoded url value as well
	protected static final String etafReportEncoded = "eTAF_5fReport"
	
	//CRON related vars
	protected def listPipelines = []
	
	
	public Runner(context) {
		super(context)
		scmClient = new GitHub(context)
	}
	
	public void runCron() {
		def nodeName = "master"
		//TODO: remove master node assignment
		context.node(nodeName) {
			scmClient.clone()

			def WORKSPACE = this.getWorkspace()
			context.println("WORKSPACE: " + WORKSPACE)
			def project = Configurator.get("project")
			def sub_project = Configurator.get("sub_project")
			def jenkinsFile = ".jenkinsfile.json"

			if (!context.fileExists("${jenkinsFile}")) {
				context.println("no .jenkinsfile.json discovered! Scannr will use default qps-pipeline logic for project: ${project}")
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

					this.executeStages(folderName)
				} else {
					context.println("No Test Suites Found to Scan...")
				}
			}
		}
	}


	public void runJob() {
        uuid = getUUID()
        String nodeName = "master"
        String emailList = Configurator.get("email_list")
        String failureEmailList = Configurator.get("failure_email_list")
        String ZAFIRA_SERVICE_URL = Configurator.get(Configurator.Parameter.ZAFIRA_SERVICE_URL)
        String ZAFIRA_ACCESS_TOKEN = Configurator.get(Configurator.Parameter.ZAFIRA_ACCESS_TOKEN)
        boolean DEVELOP = Configurator.get("develop").toBoolean()

        //TODO: remove master node assignment
		context.node(nodeName) {
			// init ZafiraClient to register queued run and abort it at the end of the run pipeline
			try {
				zc = new ZafiraClient(context, ZAFIRA_SERVICE_URL, DEVELOP)
				def token = zc.getZafiraAuthToken(ZAFIRA_ACCESS_TOKEN)
                zc.queueZafiraTestRun(uuid)
			} catch (Exception ex) {
				printStackTrace(ex)
			}
			nodeName = chooseNode()
		}

		context.node(nodeName) {
			context.wrap([$class: 'BuildUser']) {
				try {
					context.timestamps {

						this.prepare(context.currentBuild)
						scmClient.clone()

						this.downloadResources()

						def timeoutValue = Configurator.get(Configurator.Parameter.JOB_MAX_RUN_TIME)
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
                    this.reportingResults()
                    //TODO: send notification via email, slack, hipchat and whatever... based on subscrpition rules
                    this.sendTestRunResultsEmail(emailList, failureEmailList)
                    this.clean()
                }
			}
		}

	}

    public void rerunJobs(){

        String ZAFIRA_SERVICE_URL = Configurator.get(Configurator.Parameter.ZAFIRA_SERVICE_URL)
        String ZAFIRA_ACCESS_TOKEN = Configurator.get(Configurator.Parameter.ZAFIRA_ACCESS_TOKEN)
        boolean DEVELOP = Configurator.get("develop").toBoolean()

        context.stage('Rerun Tests'){
            try {
                zc = new ZafiraClient(context, ZAFIRA_SERVICE_URL, DEVELOP)
                def token = zc.getZafiraAuthToken(ZAFIRA_ACCESS_TOKEN)
                zc.smartRerun()
            } catch (Exception ex) {
                printStackTrace(ex)
            }
        }
    }

	//TODO: moved almost everything into argument to be able to move this methoud outside of the current class later if necessary
	protected void prepare(currentBuild) {

        Configurator.set("BUILD_USER_ID", getBuildUser())
		
		String BUILD_NUMBER = Configurator.get(Configurator.Parameter.BUILD_NUMBER)
		String CARINA_CORE_VERSION = Configurator.get(Configurator.Parameter.CARINA_CORE_VERSION)
		String suite = Configurator.get("suite")
		String branch = Configurator.get("branch")
		String env = Configurator.get("env")
        //TODO: rename to devicePool
		String device = Configurator.get("DEVICE")
		String browser = Configurator.get("browser")

		//TODO: improve carina to detect browser_version on the fly
		String browser_version = Configurator.get("browser_version")

		context.stage('Preparation') {
			currentBuild.displayName = "#${BUILD_NUMBER}|${suite}|${env}|${branch}"
			if (!isParamEmpty("${CARINA_CORE_VERSION}")) {
				currentBuild.displayName += "|" + "${CARINA_CORE_VERSION}"
			}
			if (!isParamEmpty(Configurator.get("device"))) {
				currentBuild.displayName += "|${device}"
			}
			if (!isParamEmpty(Configurator.get("browser"))) {
				currentBuild.displayName += "|${browser}"
			}
			if (!isParamEmpty(Configurator.get("browser_version"))) {
				currentBuild.displayName += "|${browser_version}"
			}
			currentBuild.description = "${suite}"
			
			// identify if it is mobile test using "device" param. Don't reuse node as it can be changed based on client needs 
			if (isMobile()) {
				//this is mobile test
				this.prepareForMobile()
			}
		}
	}

	protected boolean isMobile() {
		def platform = Configurator.get("platform")
		return platform.equalsIgnoreCase("android") || platform.equalsIgnoreCase("ios")
	}
	
	protected void prepareForMobile(params) {
		def devicePool = Configurator.get("devicePool")
		def defaultPool = Configurator.get("DefaultPool")
		def platform = Configurator.get("platform")

		if (platform.equalsIgnoreCase("android")) {
			prepareForAndroid()
		} else if (platform.equalsIgnoreCase("ios")) {
			prepareForiOS()
		} else {
			context.echo "Unable to identify mobile platform: ${platform}"
		}

		//geeral mobile capabilities
		//TODO: find valid way for naming this global "MOBILE" quota
		Configurator.set("capabilities.deviceName", "QPS-HUB")
		if ("DefaultPool".equalsIgnoreCase(devicePool)) {
			//reuse list of devices from hidden parameter DefaultPool
			Configurator.set("capabilities.devicePool", defaultPool)
		} else {
			Configurator.set("capabilities.devicePool", devicePool)
		}
		
		// ATTENTION! Obligatory remove device from the params otherwise
		// hudson.remoting.Channel$CallSiteStackTrace: Remote call to JNLP4-connect connection from qpsinfra_jenkins-slave_1.qpsinfra_default/172.19.0.9:39487
		// Caused: java.io.IOException: remote file operation failed: /opt/jenkins/workspace/Automation/<JOB_NAME> at hudson.remoting.Channel@2834589:JNLP4-connect connection from
    Configurator.remove("device")

		//TODO: move it to the global jenkins variable
		Configurator.set("capabilities.newCommandTimeout", "180")
		Configurator.set("java.awt.headless", "true")

	}

	protected void prepareForAndroid() {
		Configurator.set("mobile_app_clear_cache", "true")

		Configurator.set("capabilities.platformName", "ANDROID")

		Configurator.set("capabilities.autoGrantPermissions", "true")
		Configurator.set("capabilities.noSign", "true")
		Configurator.set("capabilities.STF_ENABLED", "true")

		Configurator.set("capabilities.appWaitDuration", "270000")
		Configurator.set("capabilities.androidInstallTimeout", "270000")

		customPrepareForAndroid()
	}

	protected void customPrepareForAndroid() {
		//do nothing here
	}


	protected void prepareForiOS() {

		Configurator.set("capabilities.platform", "IOS")
		Configurator.set("capabilities.platformName", "IOS")
		Configurator.set("capabilities.deviceName", "*")

		Configurator.set("capabilities.appPackage", "")
		Configurator.set("capabilities.appActivity", "")

		Configurator.set("capabilities.autoAcceptAlerts", "true")

		Configurator.set("capabilities.STF_ENABLED", "false")

		customPrepareForiOS()
	}

	protected void customPrepareForiOS() {
		//do nothing here
	}

	protected void downloadResources() {
		//DO NOTHING as of now

/*		def CARINA_CORE_VERSION = Configurator.get(Configurator.Parameter.CARINA_CORE_VERSION)
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

			def POM_FILE = getSubProjectFolder() + "/pom.xml"

			def BRANCH = Configurator.get("branch")
            def BUILD_USER_ID = Configurator.get("BUILD_USER_ID")
            def BUILD_USER_FIRST_NAME = Configurator.get("BUILD_USER_FIRST_NAME")
            def BUILD_USER_LAST_NAME = Configurator.get("BUILD_USER_LAST_NAME")
            def BUILD_USER_EMAIL = Configurator.get("BUILD_USER_EMAIL")
			
			def JOB_URL = Configurator.get(Configurator.Parameter.JOB_URL)
			def BUILD_NUMBER = Configurator.get(Configurator.Parameter.BUILD_NUMBER)
			Configurator.set("ci_url", JOB_URL)
			Configurator.set("ci_build", BUILD_NUMBER)
			
            //TODO: remove git_branch after update ZafiraListener: https://github.com/qaprosoft/zafira/issues/760
			Configurator.set("git_branch", BRANCH)
			Configurator.set("scm_branch", BRANCH)

			def DEFAULT_BASE_MAVEN_GOALS = "-Dcarina-core_version=${Configurator.get(Configurator.Parameter.CARINA_CORE_VERSION)} \
-f ${POM_FILE} \
-Dmaven.test.failure.ignore=true \
-Dcore_log_level=${Configurator.get(Configurator.Parameter.CORE_LOG_LEVEL)} \
-Dselenium_host=${Configurator.get(Configurator.Parameter.SELENIUM_URL)} \
-Dmax_screen_history=1 -Dinit_retry_count=0 -Dinit_retry_interval=10 \
-Dzafira_enabled=true \
-Dzafira_rerun_failures=${Configurator.get("rerun_failures")} \
-Dzafira_service_url=${Configurator.get(Configurator.Parameter.ZAFIRA_SERVICE_URL)} \
-Dzafira_access_token=${Configurator.get(Configurator.Parameter.ZAFIRA_ACCESS_TOKEN)} \
-Dzafira_report_folder=\"${ZAFIRA_REPORT_FOLDER}\" \
-Dreport_url=\"$JOB_URL$BUILD_NUMBER/${etafReportEncoded}\" \
-Dgit_branch=$BRANCH \
-Dgit_commit=${Configurator.get("GIT_COMMIT")} \
-Dgit_url=${Configurator.get("git_url")} \
-Dci_url=\"${JOB_URL}\" \
-Dci_build=\"${BUILD_NUMBER}\" \
-Dci_user_id=\"$BUILD_USER_ID\" \
-Dci_user_first_name=\"$BUILD_USER_FIRST_NAME\" \
-Dci_user_last_name=\"$BUILD_USER_LAST_NAME\" \
-Dci_user_email=\"$BUILD_USER_EMAIL\" \
-Duser.timezone=${Configurator.get(Configurator.Parameter.TIMEZONE)} \
clean test"

			//TODO: move 8000 port into the global var
			def mavenDebug=" -Dmaven.surefire.debug=\"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -Xnoagent -Djava.compiler=NONE\" "

			Configurator.set("zafira_enabled", zc.isAvailable().toString())
			


			//TODO: determine correctly ci_build_cause (HUMAN, TIMER/SCHEDULE or UPSTREAM_JOB using jenkins pipeline functionality

			//for now register only UPSTREAM_JOB cause when ci_parent_url and ci_parent_build not empty
			if (!Configurator.get("ci_parent_url").isEmpty() && !Configurator.get("ci_parent_build").isEmpty()) {
				Configurator.set("ci_build_cause", "UPSTREAMTRIGGER")
			}

			def goals = Configurator.resolveVars(DEFAULT_BASE_MAVEN_GOALS)

			//register all obligatory vars
			Configurator.getVars().each { k, v -> goals = goals + " -D${k}=\"${v}\""}
			
			//register all params after vars to be able to override
            Configurator.getParams().each { k, v -> goals = goals + " -D${k}=\"${v}\""}

			//TODO: make sure that jobdsl adds for UI tests boolean args: "capabilities.enableVNC and capabilities.enableVideo"
			if (Configurator.get("enableVNC") && Configurator.get("enableVNC").toBoolean()) {
				goals += " -Dcapabilities.enableVNC=true "
			}

			if (Configurator.get("enableVideo") && Configurator.get("enableVideo").toBoolean()) {
				goals += " -Dcapabilities.enableVideo=true "
			}

			if (Configurator.get(Configurator.Parameter.JACOCO_ENABLE).toBoolean()) {
				goals += " jacoco:instrument "
			}

			if (Configurator.get("debug") && Configurator.get("debug").toBoolean()) {
				context.echo "Enabling remote debug..."
				goals += mavenDebug
			}
			
			if (Configurator.get("deploy_to_local_repo") && Configurator.get("deploy_to_local_repo").toBoolean()) {
				context.echo "Enabling deployment of tests jar to local repo."
				goals += " install"
			}
			
			//append again overrideFields to make sure they are declared at the end
			goals = goals + " " + Configurator.get("overrideFields")

			context.echo "goals: ${goals}"

			//TODO: adjust ZAFIRA_REPORT_FOLDER correclty
			if (context.isUnix()) {
				def suiteNameForUnix = Configurator.get("suite").replace("\\", "/")
				context.echo "Suite for Unix: ${suiteNameForUnix}"
				context.sh "'mvn' -B -U ${goals} -Dsuite=${suiteNameForUnix}"
			} else {
				def suiteNameForWindows = Configurator.get("suite").replace("/", "\\")
				context.echo "Suite for Windows: ${suiteNameForWindows}"
				context.bat "mvn -B -U ${mvnBaseGoals} -Dsuite=${suiteNameForWindows}"
			}

		}
	}

	protected String chooseNode() {
		def platform = Configurator.get("platform")
		def browser = Configurator.get("browser")

        Configurator.set("node", "master") //master is default node to execute job

		//TODO: handle browserstack etc integration here?
		switch(platform.toLowerCase()) {
			case "api":
				context.println("Suite Type: API")
				Configurator.set("node", "api")
				Configurator.set("browser", "NULL")
				break;
			case "android":
				context.println("Suite Type: ANDROID")
				Configurator.set("node", "android")
				break;
			case "ios":
				//TODO: Need to improve this to be able to handle where emulator vs. physical tests should be run.
				context.println("Suite Type: iOS")
				Configurator.set("node", "ios")
				break;
			default:
				if ("NULL".equals(browser)) {
					context.println("Suite Type: Default")
					Configurator.set("node", "master")
				} else {
					context.println("Suite Type: Web")
					Configurator.set("node", "web")
				}
		}
		context.echo "node: " + Configurator.get("node")
		return Configurator.get("node")
	}

	protected String getUUID() {
		def ci_run_id = Configurator.get("ci_run_id")
		context.echo "uuid from jobParams: " + ci_run_id
		if (ci_run_id.isEmpty()) {
				ci_run_id = randomUUID() as String
		}
		context.echo "final uuid: " + ci_run_id
		return ci_run_id
	}

	protected String getFailure(currentBuild) {
		//TODO: move string constants into object/enum if possible
		currentBuild.result = 'FAILURE'
		def failureReason = "undefined failure"

		String JOB_URL = Configurator.get(Configurator.Parameter.JOB_URL)
		String BUILD_NUMBER = Configurator.get(Configurator.Parameter.BUILD_NUMBER)
		String JOB_NAME = Configurator.get(Configurator.Parameter.JOB_NAME)

		String email_list = Configurator.get("email_list")

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
		if (!isParamEmpty(Configurator.get("sub_project"))) {
			subProjectFolder = "./" + Configurator.get("sub_project")
		}
		return subProjectFolder
	}

	//TODO: move into valid jacoco related package
	protected void publishJacocoReport() {
		def JACOCO_ENABLE = Configurator.get(Configurator.Parameter.JACOCO_ENABLE).toBoolean()
		if (!JACOCO_ENABLE) {
			context.println("do not publish any content to AWS S3 if integration is disabled")
			return
		}

		def JACOCO_BUCKET = Configurator.get(Configurator.Parameter.JACOCO_BUCKET)
		def JOB_NAME = Configurator.get(Configurator.Parameter.JOB_NAME)
		def BUILD_NUMBER = Configurator.get(Configurator.Parameter.BUILD_NUMBER)

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
			publishReport('**/reports/qa/emailable-report.html', "${etafReport}")
			
			publishReport('**/artifacts/**', 'eTAF_Artifacts')
			
			publishTestNgReports('**/target/surefire-reports/index.html', 'Full TestNG HTML Report')
			publishTestNgReports('**/target/surefire-reports/emailable-report.html', 'TestNG Summary HTML Report')

		}
	}
	
	protected void exportZafiraReport() {
		//replace existing local emailable-report.html by Zafira content
		def zafiraReport = zc.exportZafiraReport(uuid)
		if (!zafiraReport.isEmpty()) {
			context.writeFile file: "${ZAFIRA_REPORT_FOLDER}/emailable-report.html", text: zafiraReport
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

	protected void sendTestRunResultsEmail(String emailList, String failureEmailList) {
		if (emailList != null && !emailList.isEmpty()) {
			zc.sendTestRunResultsEmail(uuid, emailList, "all")
		}
		if (isFailure(context.currentBuild.rawBuild) && failureEmailList != null && !failureEmailList.isEmpty()) {
			zc.sendTestRunResultsEmail(uuid, failureEmailList, "failures")
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
	
	@NonCPS
	protected def sortPipelineList(List pipelinesList) {
		context.println("Finished Dynamic Mapping: " + pipelinesList.dump())
		pipelinesList = pipelinesList.sort { map1, map2 -> !map1.order ? !map2.order ? 0 : 1 : !map2.order ? -1 : map1.order.toInteger() <=> map2.order.toInteger() }
		context.println("Finished Dynamic Mapping Sorted Order: " + pipelinesList.dump())
		return pipelinesList
		
	}

	protected void parsePipeline(String filePath) {
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
		
		def currentEnv = Configurator.get("env")
		def pipelineJobName = Configurator.get(Configurator.Parameter.JOB_BASE_NAME)

		// override suite email_list from params if defined
		def emailList = currentSuite.getParameter("jenkinsEmail").toString()
		def paramEmailList = Configurator.get("email_list")
		if (paramEmailList != null && !paramEmailList.isEmpty()) {
			emailList = paramEmailList
		}
		
		def priorityNum = "5"
		def curPriorityNum = Configurator.get("priority")
		if (curPriorityNum != null && !curPriorityNum.isEmpty()) {
			priorityNum = curPriorityNum //lowest priority for pipeline/cron jobs. So manually started jobs has higher priority among CI queue
		}
		
		
		def supportedBrowsers = currentSuite.getParameter("jenkinsPipelineBrowsers").toString()
		String logLine = "pipelineJobName: ${pipelineJobName};\n	supportedPipelines: ${supportedPipelines};\n	jobName: ${jobName};\n	orderNum: ${orderNum};\n	email_list: ${emailList};\n	supportedEnvs: ${supportedEnvs};\n	currentEnv: ${currentEnv};\n	supportedBrowsers: ${supportedBrowsers};\n"
		
		def currentBrowser = Configurator.get("browser")
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

						// currentBrowser - explicilty selected browser on cron/pipeline level to execute tests

						//context.println("supportedBrowser: ${supportedBrowser}; currentBrowser: ${currentBrowser}; ")
						if (!currentBrowser.equals(supportedBrowser) && !currentBrowser.toString().equals("NULL")) {
							//context.println("Skip execution for browser: ${supportedBrowser}; currentBrowser: ${currentBrowser}")
							continue;
						}
						
						//context.println("adding ${filePath} suite to pipeline run...")

						def pipelineMap = [:]

						def branch = Configurator.get("branch")
						def ci_parent_url = Configurator.get("ci_parent_url")
						if (ci_parent_url.isEmpty()) {
							ci_parent_url = Configurator.get(Configurator.Parameter.JOB_URL)
						}
						def ci_parent_build = Configurator.get("ci_parent_build")
						if (ci_parent_build.isEmpty()) {
							ci_parent_build = Configurator.get(Configurator.Parameter.BUILD_NUMBER)
						}
						def retry_count = Configurator.get("retry_count")
						def thread_count = Configurator.get("thread_count")

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
			def ADMIN_EMAILS = Configurator.get("email_list")

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
