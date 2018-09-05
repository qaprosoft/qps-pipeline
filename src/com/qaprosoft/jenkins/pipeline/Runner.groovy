package com.qaprosoft.jenkins.pipeline

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.XmlSuite;

import static java.util.UUID.randomUUID
import com.qaprosoft.zafira.ZafiraClient

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
		
        uuid = getUUID()
        String nodeName = "master"
        String emailList = Configurator.get("email_list")
        String failureEmailList = Configurator.get("failure_email_list")
        String ZAFIRA_SERVICE_URL = Configurator.get(Configurator.Parameter.ZAFIRA_SERVICE_URL)
        String ZAFIRA_ACCESS_TOKEN = Configurator.get(Configurator.Parameter.ZAFIRA_ACCESS_TOKEN)
        boolean DEVELOP = false
        if (Configurator.get("develop")) {
            DEVELOP = Configurator.get("develop").toBoolean()
        }
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
                    //TODO: send notification via email, slack, hipchat and whatever... based on subscription rules
                    this.sendTestRunResultsEmail(emailList, failureEmailList)
                    this.clean()
                }
			}
		}

	}

    public void rerunJobs(){

        String ZAFIRA_SERVICE_URL = Configurator.get(Configurator.Parameter.ZAFIRA_SERVICE_URL)
        String ZAFIRA_ACCESS_TOKEN = Configurator.get(Configurator.Parameter.ZAFIRA_ACCESS_TOKEN)
        boolean DEVELOP = false
        if (Configurator.get("develop")) {
            DEVELOP = Configurator.get("develop").toBoolean()
        }
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
		String devicePool = Configurator.get("devicePool")
		String browser = Configurator.get("browser")

		//TODO: improve carina to detect browser_version on the fly
		String browser_version = Configurator.get("browser_version")

		context.stage('Preparation') {
			currentBuild.displayName = "#${BUILD_NUMBER}|${suite}|${env}|${branch}"
			if (!isParamEmpty("${CARINA_CORE_VERSION}")) {
				currentBuild.displayName += "|" + "${CARINA_CORE_VERSION}"
			}
			if (!isParamEmpty(devicePool)) {
				currentBuild.displayName += "|${devicePool}"
			}
			if (!isParamEmpty(Configurator.get("browser"))) {
				currentBuild.displayName += "|${browser}"
			}
			if (!isParamEmpty(Configurator.get("browser_version"))) {
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
		def platform = Configurator.get("platform")
		return platform.equalsIgnoreCase("android") || platform.equalsIgnoreCase("ios")
	}
	
	protected void prepareForMobile() {
		context.println("Runner->prepareForMobile")
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
		context.println("Runner->prepareForAndroid")
		Configurator.set("mobile_app_clear_cache", "true")

		Configurator.set("capabilities.platformName", "ANDROID")

		Configurator.set("capabilities.autoGrantPermissions", "true")
		Configurator.set("capabilities.noSign", "true")
		Configurator.set("capabilities.STF_ENABLED", "true")

		Configurator.set("capabilities.appWaitDuration", "270000")
		Configurator.set("capabilities.androidInstallTimeout", "270000")

	}

	protected void prepareForiOS() {
		context.println("Runner->prepareForiOS")
		Configurator.set("capabilities.platform", "IOS")
		Configurator.set("capabilities.platformName", "IOS")
		Configurator.set("capabilities.deviceName", "*")

		Configurator.set("capabilities.appPackage", "")
		Configurator.set("capabilities.appActivity", "")

		Configurator.set("capabilities.autoAcceptAlerts", "true")

		Configurator.set("capabilities.STF_ENABLED", "false")

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
-Detaf.carina.core.version=${Configurator.get(Configurator.Parameter.CARINA_CORE_VERSION)} \
-f ${POM_FILE} \
-Dmaven.test.failure.ignore=true \
-Dcore_log_level=${Configurator.get(Configurator.Parameter.CORE_LOG_LEVEL)} \
-Dselenium_host=${Configurator.get(Configurator.Parameter.SELENIUM_URL)} \
-Dmax_screen_history=1 -Dinit_retry_count=0 -Dinit_retry_interval=10 \
-Dzafira_enabled=true \
-Dzafira_rerun_failures=${Configurator.get("rerun_failures")} \
-Dzafira_service_url=${Configurator.get(Configurator.Parameter.ZAFIRA_SERVICE_URL)} \
-Dzafira_access_token=${Configurator.get(Configurator.Parameter.ZAFIRA_ACCESS_TOKEN)} \
-Dzafira_report_folder=\"${etafReportFolder}\" \
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

			if (Configurator.get("node").equalsIgnoreCase("web") || Configurator.get("node").equalsIgnoreCase("android")) {
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
			
			//browserstack goals
			if (isBrowserStackRun()) {
				def uniqueBrowserInstance = "\"#${BUILD_NUMBER}-" + Configurator.get("suite") + "-" +
						Configurator.get("browser") + "-" + Configurator.get("env") + "\""
				uniqueBrowserInstance = uniqueBrowserInstance.replace("/", "-").replace("#", "")
				startBrowserStackLocal(uniqueBrowserInstance)
				goals += " -Dcapabilities.project=" + Configurator.get("project")
				goals += " -Dcapabilities.build=" + uniqueBrowserInstance
				goals += " -Dcapabilities.browserstack.localIdentifier=" + uniqueBrowserInstance
				goals += " -Dapp_version=browserStack"
			}

			//append again overrideFields to make sure they are declared at the end
			goals = goals + " " + Configurator.get("overrideFields")

			//context.echo "goals: ${goals}"

			//TODO: adjust etafReportFolder correctly
			if (context.isUnix()) {
				def suiteNameForUnix = Configurator.get("suite").replace("\\", "/")
				context.echo "Suite for Unix: ${suiteNameForUnix}"
				context.sh "'mvn' -B -U ${goals} -Dsuite=${suiteNameForUnix}"
			} else {
				def suiteNameForWindows = Configurator.get("suite").replace("/", "\\")
				context.echo "Suite for Windows: ${suiteNameForWindows}"
				context.bat "mvn -B -U ${goals} -Dsuite=${suiteNameForWindows}"
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
		
		def nodeLabel = Configurator.get("node_label")
		context.println("nodeLabel: " + nodeLabel)
		if (!isParamEmpty(nodeLabel)) {
			context.println("overriding default node to: " + nodeLabel)
			Configurator.set("node", nodeLabel)
		}

		context.echo "node: " + Configurator.get("node")
		return Configurator.get("node")
	}

	protected String getUUID() {
		def ci_run_id = Configurator.get("ci_run_id")
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

		String JOB_URL = Configurator.get(Configurator.Parameter.JOB_URL)
		String BUILD_NUMBER = Configurator.get(Configurator.Parameter.BUILD_NUMBER)
		String JOB_NAME = Configurator.get(Configurator.Parameter.JOB_NAME)
		String ADMIN_EMAILS = Configurator.get(Configurator.Parameter.ADMIN_EMAILS)

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
		context.emailext attachLog: true, body: "${body}", recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']], subject: "${subject}", to: "${email_list},${ADMIN_EMAILS}"
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

	protected void sendTestRunResultsEmail(String emailList, String failureEmailList) {
		if (emailList != null && !emailList.isEmpty()) {
			zc.sendTestRunResultsEmail(uuid, emailList, "all")
		}
		if (isFailure(context.currentBuild.rawBuild) && failureEmailList != null && !failureEmailList.isEmpty()) {
			zc.sendTestRunResultsEmail(uuid, failureEmailList, "failures")
		}
	}

    protected void publishReports(String pattern, String reportName) {
        def reports = context.findFiles(glob: pattern)
        for (int i = 0; i < reports.length; i++) {
			def parentFile = new File(reports[i].path).getParentFile()
			if (parentFile == null) {
				context.println "ERROR! Parent report is null! for " + reports[i].path
				continue
			}
            def reportDir = parentFile.getPath()
            context.println "Report File Found, Publishing " + reports[i].path
            if (i > 0){
                def reportIndex = "_" + i
                reportName = reportName + reportIndex
            }
            context.publishHTML getReportParameters(reportDir, reports[i].name, reportName )
        }
    }

    protected def getReportParameters(reportDir, reportFiles, reportName) {
        def reportParameters = [allowMissing: false,
                                alwaysLinkToLastBuild: false,
                                keepAll: true,
                                reportDir: reportDir,
                                reportFiles: reportFiles,
                                reportName: reportName]
        return reportParameters
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
		def pipelineJobName = Configurator.get(Configurator.Parameter.JOB_BASE_NAME)

		// override suite email_list from params if defined
		def emailList = currentSuite.getParameter("jenkinsEmail").toString()
		def paramEmailList = Configurator.get("email_list")
		if (paramEmailList != null && !paramEmailList.isEmpty()) {
			emailList = paramEmailList
		}
		
		def priorityNum = "5"
		def curPriorityNum = Configurator.get("BuildPriority")
		if (curPriorityNum != null && !curPriorityNum.isEmpty()) {
			priorityNum = curPriorityNum //lowest priority for pipeline/cron jobs. So manually started jobs has higher priority among CI queue
		}

		//def overrideFields = currentSuite.getParameter("overrideFields").toString()
		def overrideFields = Configurator.get("overrideFields")

        String supportedBrowsers = currentSuite.getParameter("jenkinsPipelineBrowsers").toString()
		String logLine = "pipelineJobName: ${pipelineJobName};\n	supportedPipelines: ${supportedPipelines};\n	jobName: ${jobName};\n	orderNum: ${orderNum};\n	email_list: ${emailList};\n	supportedEnvs: ${supportedEnvs};\n	currentEnv(s): ${currentEnvs};\n	supportedBrowsers: ${supportedBrowsers};\n"
		
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
		return Configurator.get("env")
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
			
			def email_list = entry.get("email_list")
			def ADMIN_EMAILS = Configurator.get("email_list")

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
				
				context.emailext attachLog: true, body: "Unable to start job via cron! " + ex.getMessage(), recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']], subject: "JOBSTART FAILURE: ${entry.get("jobName")}", to: "${email_list},${ADMIN_EMAILS}"
			}

		}
	}
	
	protected void startBrowserStackLocal(String uniqueBrowserInstance) {
		def browserStackUrl = "https://www.browserstack.com/browserstack-local/BrowserStackLocal"
		def accessKey = Configurator.get("BROWSERSTACK_ACCESS_KEY")
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
		def customCapabilities = Configurator.get("custom_capabilities")
		if (!isParamEmpty(customCapabilities)) {
			if (customCapabilities.toLowerCase().contains("browserstack")) {
				res = true
			}
		}
		return res
	}
}
