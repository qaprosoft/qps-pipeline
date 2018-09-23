package com.qaprosoft.jenkins.pipeline.impl

@Grab('org.testng:testng:6.8.8')
import com.qaprosoft.jenkins.pipeline.Executor

import com.qaprosoft.jenkins.pipeline.browserstack.OS
import com.qaprosoft.zafira.ZafiraClient
import org.testng.xml.XmlSuite;
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.AbstractRunner
import com.qaprosoft.jenkins.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.TestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.CronJobFactory
import com.qaprosoft.scm.github.GitHub;


import hudson.plugins.sonar.SonarGlobalConfiguration


public class QARunner extends AbstractRunner {

    protected Map dslObjects = [:]
    protected static final String zafiraReport = "ZafiraReport"
    protected def pipelineLibrary = "QPS-Pipeline"
    protected def runnerClass = "com.qaprosoft.jenkins.pipeline.impl.QARunner"
    protected def folderName = "Automation"
    protected def onlyUpdated = false
    protected def currentBuild
    protected def uuid
    protected def zc
    //CRON related vars
    protected def listPipelines = []
    protected JobType jobType = JobType.JOB

    public enum JobType {
        JOB("JOB"),
        CRON("CRON")
        String type
        JobType(String type) {
            this.type = type
        }
    }

    public QARunner(context) {
        super(context)
        scmClient = new GitHub(context)
        zc = new ZafiraClient(context)
        currentBuild = context.currentBuild
        if (Configuration.get("onlyUpdated") != null) {
            onlyUpdated = Configuration.get("onlyUpdated").toBoolean()
        }
    }

    public QARunner(context, jobType) {
        this (context)
        this.jobType = jobType
    }

    //Methods
    public void build() {
        context.node("master") {
            context.println("QARunner->build")
            if (jobType.equals(JobType.JOB)) {
                runJob()
            }
            if (jobType.equals(JobType.CRON)) {
                runCron()
            }
            //TODO: identify if it is job or cron and execute appropriate protected method
        }
    }


    //Events
    public void onPush() {
        context.node("master") {
            context.timestamps {
                context.println("QARunner->onPush")
                prepare()
                if (!isUpdated("**.xml,**/zafira.properties") && onlyUpdated) {
                    context.println("do not continue scanner as none of suite was updated ( *.xml )")
                    return
                }
                //TODO: implement repository scan and QA jobs recreation
                scan()
                clean()
            }
        }
    }

    public void onPullRequest() {
        context.node("master") {
            context.println("QARunner->onPullRequest")
            scmClient.clonePR()

			compile()
            performSonarQubeScan()

            //TODO: investigate whether we need this piece of code
            //            if (Configuration.get("ghprbPullTitle").contains("automerge")) {
            //                scmClient.mergePR()
            //            }
        }
    }
	
	protected void compile() {
		compile("pom.xml")
	}
	
	protected void compile(pomFile) {
		context.stage('Maven Compile') {
			def goals = "clean compile test-compile \
					-f ${pomFile} \
					-Dmaven.test.failure.ignore=true \
					-Dcom.qaprosoft.carina-core.version=${Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)}"

			executeMavenGoals(goals)
		}
	}

    protected void prepare() {
        scmClient.clone(!onlyUpdated)
        String QPS_PIPELINE_GIT_URL = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_URL)
        String QPS_PIPELINE_GIT_BRANCH = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_BRANCH)
        scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")
    }


    protected def void executeMavenGoals(goals){
        if (context.isUnix()) {
            context.sh "'mvn' -B ${goals}"
        } else {
            context.bat "mvn -B ${goals}"
        }
    }

	protected void performSonarQubeScan(){
		performSonarQubeScan("pom.xml")
	}
	
    protected void performSonarQubeScan(pomFile){
		context.stage('Sonar Scanner') {
	        def sonarQubeEnv = ''
	        Jenkins.getInstance().getDescriptorByType(SonarGlobalConfiguration.class).getInstallations().each { installation ->
	            sonarQubeEnv = installation.getName()
	        }
	        if(sonarQubeEnv.isEmpty()){
	            context.println "There is no SonarQube server configured. Please, configure Jenkins for performing SonarQube scan."
	            return
	        }
            context.withSonarQubeEnv(sonarQubeEnv) {
                context.sh "mvn \
				 -f ${pomFile} \
				 clean package sonar:sonar -DskipTests \
				 -Dsonar.github.endpoint=${Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_API_URL)}")} \
				 -Dsonar.analysis.mode=preview  \
				 -Dsonar.github.pullRequest=${Configuration.get("ghprbPullId")} \
				 -Dsonar.github.repository=${Configuration.get("ghprbGhRepository")} \
				 -Dsonar.projectKey=${Configuration.get("project")} \
				 -Dsonar.projectName=${Configuration.get("project")} \
				 -Dsonar.projectVersion=1.${Configuration.get(Configuration.Parameter.BUILD_NUMBER)} \
				 -Dsonar.github.oauth=${Configuration.get(Configuration.Parameter.GITHUB_OAUTH_TOKEN)} \
				 -Dsonar.sources=. \
				 -Dsonar.tests=. \
				 -Dsonar.inclusions=**/src/main/java/** \
				 -Dsonar.test.inclusions=**/src/test/java/** \
				 -Dsonar.java.source=1.8"
            }
		}
    }
    /** **/

    protected void scan() {

        context.stage("Scan Repository") {
            def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
            def project = Configuration.get("project")
            def jobFolder = Configuration.get("project")

            def branch = Configuration.get("branch")
            currentBuild.displayName = "#${buildNumber}|${project}|${branch}"

            def workspace = getWorkspace()
            context.println("WORKSPACE: ${workspace}")

            def removedConfigFilesAction = Configuration.get("removedConfigFilesAction")
            def removedJobAction = Configuration.get("removedJobAction")
            def removedViewAction = Configuration.get("removedViewAction")

            // Support DEV related CI workflow
            //TODO: analyze if we need 3 system object declarations

            def jenkinsFileOrigin = "Jenkinsfile"
            if (context.fileExists("${workspace}/${jenkinsFileOrigin}")) {
                //TODO: figure our howto work with Jenkinsfile
                // this is the repo with already available pipeline script in Jenkinsfile
                // just create a job
            }


            def jenkinsFile = ".jenkinsfile.json"
            if (!context.fileExists("${workspace}/${jenkinsFile}")) {
                context.println("Skip repository scan as no .jenkinsfile.json discovered! Project: ${project}")
                currentBuild.result = 'UNSTABLE'
                return
            }

            Object subProjects = Executor.parseJSON("${workspace}/${jenkinsFile}").sub_projects

            context.println "PRSED: " + subProjects
            subProjects.each {
                context.println("sub_project: " + it)

                def sub_project = it.name

                def subProjectFilter = it.name
                if (sub_project.equals(".")) {
                    subProjectFilter = "**"
                }

                def prChecker = it.pr_checker
                def zafiraFilter = it.zafira_filter
                def suiteFilter = it.suite_filter

                def zafira_project = 'unknown'
                def zafiraProperties = context.findFiles(glob: subProjectFilter + "/" + zafiraFilter)
                for (File file : zafiraProperties) {
                    def props = context.readProperties file: file.path
                    if (props['zafira_project'] != null) {
                        zafira_project = props['zafira_project']
                    }
                }
                context.println("zafira_project: ${zafira_project}")

                if (suiteFilter.endsWith("/")) {
                    //remove last character if it is slash
                    suiteFilter = suiteFilter[0..-2]
                }
                def testngFolder = suiteFilter.substring(suiteFilter.lastIndexOf("/"), suiteFilter.length()) + "/"
                context.println("testngFolder: " + testngFolder)

                // VIEWS
                registerObject("cron", new ListViewFactory(jobFolder, 'CRON', '.*cron.*'))
                //registerObject(project, new ListViewFactory(jobFolder, project.toUpperCase(), ".*${project}.*"))

                //TODO: create default personalized view here

                // find all tetsng suite xml files and launch dsl creator scripts (views, folders, jobs etc)
                def suites = context.findFiles(glob: subProjectFilter + "/" + suiteFilter + "/**")
                for (File suite : suites) {
                    if (!suite.path.endsWith(".xml")) {
                        continue
                    }
                    context.println("suite: " + suite.path)
                    def suiteOwner = "anonymous"

                    def suiteName = suite.path
                    suiteName = suiteName.substring(suiteName.lastIndexOf(testngFolder) + testngFolder.length(), suiteName.indexOf(".xml"))

                    try {
                        XmlSuite currentSuite = Executor.parseSuite(workspace + "/" + suite.path)
                        if (currentSuite.toXml().contains("jenkinsJobCreation") && currentSuite.getParameter("jenkinsJobCreation").contains("true")) {
                            context.println("suite name: " + suiteName)
                            context.println("suite path: " + suite.path)

                            if (currentSuite.toXml().contains("suiteOwner")) {
                                suiteOwner = currentSuite.getParameter("suiteOwner")
                            }
                            if (currentSuite.toXml().contains("zafira_project")) {
                                zafira_project = currentSuite.getParameter("zafira_project")
                            }

                            // put standard views factory into the map
                            registerObject(zafira_project, new ListViewFactory(jobFolder, zafira_project.toUpperCase(), ".*${zafira_project}.*"))
                            registerObject(suiteOwner, new ListViewFactory(jobFolder, suiteOwner, ".*${suiteOwner}"))

                            //pipeline job
                            //TODO: review each argument to TestJobFactory and think about removal
                            //TODO: verify suiteName duplication here and generate email failure to the owner and admin_emails
                            def jobDesc = "project: ${project}; zafira_project: ${zafira_project}; owner: ${suiteOwner}"
                            registerObject(suiteName, new TestJobFactory(jobFolder, getPipelineScript(), project, sub_project, zafira_project, getWorkspace() + "/" + suite.path, suiteName, jobDesc))

                            //cron job
                            if (!currentSuite.getParameter("jenkinsRegressionPipeline").toString().contains("null")
                                    && !currentSuite.getParameter("jenkinsRegressionPipeline").toString().isEmpty()) {
                                def cronJobNames = currentSuite.getParameter("jenkinsRegressionPipeline").toString()
                                for (def cronJobName : cronJobNames.split(",")) {
                                    cronJobName = cronJobName.trim()
                                    def cronDesc = "project: ${project}; type: cron"
                                    registerObject(cronJobName, new CronJobFactory(jobFolder, getCronPipelineScript(), cronJobName, project, sub_project, getWorkspace() + "/" + suite.path, cronDesc))
                                }
                            }
                        }

                    } catch (FileNotFoundException e) {
                        context.println("ERROR! Unable to find suite: " + suite.path)
                        printStackTrace(e)
                    } catch (Exception e) {
                        context.println("ERROR! Unable to parse suite: " + suite.path)
                        printStackTrace(e)
                    }

                }

                // put into the factories.json all declared jobdsl factories to verify and create/recreate/remove etc
                context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)

                context.println("factoryTarget: " + FACTORY_TARGET)
                //TODO: test carefully auto-removal for jobs/views and configs
                context.jobDsl additionalClasspath: additionalClasspath,
                        removedConfigFilesAction: removedConfigFilesAction,
                        removedJobAction: removedJobAction,
                        removedViewAction: removedViewAction,
                        targets: FACTORY_TARGET,
                        ignoreExisting: false
            }
        }
    }


    protected clean() {
        context.stage('Wipe out Workspace') {
            context.deleteDir()
        }
    }


    /** Detects if any changes are present in files matching patterns  */
    @NonCPS
    protected boolean isUpdated(String patterns) {
        def isUpdated = false
        def changeLogSets = currentBuild.rawBuild.changeSets
        changeLogSets.each { changeLogSet ->
            /* Extracts GitChangeLogs from changeLogSet */
            for (entry in changeLogSet.getItems()) {
                /* Extracts paths to changed files */
                for (path in entry.getPaths()) {
                    context.println("UPDATED: " + path.getPath())
                    Path pathObject = Paths.get(path.getPath())
                    /* Checks whether any changed file matches one of patterns */
                    for (pattern in patterns.split(",")){
                        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern)
                        /* As only match is found stop search*/
                        if (matcher.matches(pathObject)){
                            isUpdated = true
                            return
                        }
                    }
                }
            }
        }
        return isUpdated
    }

    protected String getWorkspace() {
        return context.pwd()
    }

    protected String getPipelineScript() {
        return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
    }

    protected String getCronPipelineScript() {
        return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this, 'CRON').build()"
    }

    protected void registerObject(name, object) {
        if (dslObjects.containsKey(name)) {
            context.println("WARNING! key '" + name + "' already defined and will be replaced!")
            context.println("old item: " + dslObjects.get(name).dump())
            context.println("new item: " + object.dump())
        }
        dslObjects.put(name, object)
    }

    protected void setDslTargets(targets) {
        this.factoryTarget = targets
    }

    protected void setDslClasspath(additionalClasspath) {
        this.additionalClasspath = additionalClasspath
    }

    protected void runJob() {
        context.println("QARunner->runJob")

        uuid = Executor.getUUID()
        context.println "UUID: " + uuid
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

                        prepareBuild(currentBuild)
                        scmClient.clone()

                        downloadResources()

                        def timeoutValue = Configuration.get(Configuration.Parameter.JOB_MAX_RUN_TIME)
                        context.timeout(time: timeoutValue.toInteger(), unit: 'MINUTES') {
                            buildJob()
                        }

                        //TODO: think about seperate stage for uploading jacoco reports
                        publishJacocoReport()
                    }

                } catch (Exception e) {
                    printStackTrace(e)
                    String failureReason = getFailure(currentBuild)
                    context.println "failureReason: ${failureReason}"
                    //explicitly execute abort to resolve anomalies with in_progress tests...
                    zc.abortZafiraTestRun(uuid, failureReason)
                    throw e
                } finally {
                    exportZafiraReport()
                    sendTestRunResultsEmail()
                    reportingResults()
                    //TODO: send notification via email, slack, hipchat and whatever... based on subscription rules
                    clean()
                }
            }
        }
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
        if (!Executor.isParamEmpty(nodeLabel)) {
            context.println("overriding default node to: " + nodeLabel)
            Configuration.set("node", nodeLabel)
        }

        context.println "node: " + Configuration.get("node")
        return Configuration.get("node")
    }

    //TODO: moved almost everything into argument to be able to move this methoud outside of the current class later if necessary
    protected void prepareBuild(currentBuild) {

        Configuration.set("BUILD_USER_ID", Executor.getBuildUser(currentBuild))

        String buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
        String carinaCoreVersion = Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)
        String suite = Configuration.get("suite")
        String branch = Configuration.get("branch")
        String env = Configuration.get("env")
        String devicePool = Configuration.get("devicePool")
        String browser = Configuration.get("browser")

        //TODO: improve carina to detect browser_version on the fly
        String browserVersion = Configuration.get("browser_version")

        context.stage('Preparation') {
            currentBuild.displayName = "#${buildNumber}|${suite}|${env}|${branch}"
            if (!Executor.isParamEmpty("${carinaCoreVersion}")) {
                currentBuild.displayName += "|" + "${carinaCoreVersion}"
            }
            if (!Executor.isParamEmpty(devicePool)) {
                currentBuild.displayName += "|${devicePool}"
            }
            if (!Executor.isParamEmpty(Configuration.get("browser"))) {
                currentBuild.displayName += "|${browser}"
            }
            if (!Executor.isParamEmpty(Configuration.get("browser_version"))) {
                currentBuild.displayName += "|${browserVersion}"
            }
            currentBuild.description = "${suite}"

            if (Executor.isMobile()) {
                //this is mobile test
                prepareForMobile()
            }
        }
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
            context.println "Unable to identify mobile platform: ${platform}"
        }

        //general mobile capabilities
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
		def pomFile = Executor.getSubProjectFolder() + "/pom.xml"
		context.println "pomFile: " + pomFile
			if (context.isUnix()) {
				context.sh "'mvn' -B -U -f ${pomFile} clean process-resources process-test-resources -Dcarina-core_version=$CARINA_CORE_VERSION"
			} else {
				//TODO: verify that forward slash is ok for windows nodes.
				context.bat(/"mvn" -B -U -f ${pomFile} clean process-resources process-test-resources -Dcarina-core_version=$CARINA_CORE_VERSION/)
			}
		}
*/	}

    protected void buildJob() {
        context.stage('Run Test Suite') {

            def pomFile = Executor.getSubProjectFolder() + "/pom.xml"
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
			-Dreport_url=\"${Configuration.get(Configuration.Parameter.JOB_URL)}${Configuration.get(Configuration.Parameter.BUILD_NUMBER)}/${zafiraReport}\" \
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

            Configuration.set("ci_build_cause", Executor.getBuildCause((Configuration.get(Configuration.Parameter.JOB_NAME)), currentBuild))

            def goals = Configuration.resolveVars(DEFAULT_BASE_MAVEN_GOALS)

            //register all obligatory vars
            Configuration.getVars().each { k, v -> goals = goals + " -D${k}=\"${v}\""}

            //register all params after vars to be able to override
            Configuration.getParams().each { k, v -> goals = goals + " -D${k}=\"${v}\""}

            goals = Executor.enableVideoStreaming(Configuration.get("node"), "Video streaming was enabled.", " -Dcapabilities.enableVNC=true ", goals)
            goals = Executor.addOptionalParameter("enableVideo", "Video recording was enabled.", " -Dcapabilities.enableVideo=true ", goals)
            goals = Executor.addOptionalParameter(Configuration.get(Configuration.Parameter.JACOCO_ENABLE), "Jacoco tool was enabled.", " jacoco:instrument ", goals)
            goals = Executor.addOptionalParameter("debug", "Enabling remote debug...", mavenDebug, goals)
            goals = Executor.addOptionalParameter("deploy_to_local_repo", "Enabling deployment of tests jar to local repo.", " install", goals)

            //browserstack goals
            if (isBrowserStackRun()) {
                def uniqueBrowserInstance = "\"#${Configuration.get(Configuration.Parameter.BUILD_NUMBER)}-" + Configuration.get("suite") + "-" +
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

            //context.println "goals: ${goals}"

            if (context.isUnix()) {
                def suiteNameForUnix = Configuration.get("suite").replace("\\", "/")
                context.println "Suite for Unix: ${suiteNameForUnix}"
                context.sh "'mvn' -B -U ${goals} -Dsuite=${suiteNameForUnix}"
            } else {
                def suiteNameForWindows = Configuration.get("suite").replace("/", "\\")
                context.println "Suite for Windows: ${suiteNameForWindows}"
                context.bat "mvn -B -U ${goals} -Dsuite=${suiteNameForWindows}"
            }

        }
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
            failureReason = "Aborted by " + Executor.getAbortCause(currentBuild)
            bodyHeader = "<p>Unable to continue tests due to the abort by " + Executor.getAbortCause(currentBuild) + " ${JOB_URL}${BUILD_NUMBER}</p>"
            subject = "ABORTED: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
        } else  if (currentBuild.rawBuild.log.contains("Cancelling nested steps due to timeout")) {
            currentBuild.result = 'ABORTED'
            failureReason = "Aborted by timeout"
            bodyHeader = "<p>Unable to continue tests due to the abort by timeout ${JOB_URL}${BUILD_NUMBER}</p>"
            subject = "TIMED OUT: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
        }


        def body = bodyHeader + """<br>Rebuild: ${JOB_URL}${BUILD_NUMBER}/rebuild/parameterized<br>
		${zafiraReport}: ${JOB_URL}${BUILD_NUMBER}/${zafiraReport}<br>
				Console: ${JOB_URL}${BUILD_NUMBER}/console"""

        //        def to = Configuration.get("email_list") + "," + Configuration.get(Configuration.Parameter.ADMIN_EMAILS)
        def to = Configuration.get(Configuration.Parameter.ADMIN_EMAILS)
        //TODO: enable emailing but seems like it should be moved to the notification code
        context.emailext Executor.getEmailParams(body, subject, to)
        return failureReason
    }


    protected void exportZafiraReport() {
        //replace existing local emailable-report.html by Zafira content
        def zafiraReport = zc.exportZafiraReport(uuid)
        //context.println(zafiraReport)
        if (!zafiraReport.isEmpty()) {
            context.writeFile file: getWorkspace() + "/zafira/report.html", text: zafiraReport
        }

        //TODO: think about method renaming because in additions it also could redefine job status in Jenkins.
        // or move below code into another method

        // set job status based on zafira report
        if (!zafiraReport.contains("PASSED:") && !zafiraReport.contains("PASSED (known issues):") && !zafiraReport.contains("SKIP_ALL:")) {
            //context.println "Unable to Find (Passed) or (Passed Known Issues) within the eTAF Report."
            currentBuild.result = 'FAILURE'
        } else if (zafiraReport.contains("SKIP_ALL:")) {
            currentBuild.result = 'UNSTABLE'
        }
    }

    protected void sendTestRunResultsEmail() {
        String emailList = Configuration.get("email_list")
        emailList = overrideRecipients(emailList)
        String failureEmailList = Configuration.get("failure_email_list")

        if (emailList != null && !emailList.isEmpty()) {
            zc.sendTestRunResultsEmail(uuid, emailList, "all")
        }
        if (Executor.isFailure(currentBuild.rawBuild) && failureEmailList != null && !failureEmailList.isEmpty()) {
            zc.sendTestRunResultsEmail(uuid, failureEmailList, "failures")
        }
    }

    //Overrided in private pipeline
    protected def overrideRecipients(emailList) {
        return emailList
    }

    protected void reportingResults() {
        context.stage('Results') {
            publishReports('**/zafira/report.html', "${zafiraReport}")
            publishReports('**/artifacts/**', 'eTAF_Artifacts')
            publishReports('**/target/surefire-reports/index.html', 'Full TestNG HTML Report')
            publishReports('**/target/surefire-reports/emailable-report.html', 'TestNG Summary HTML Report')
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
            context.publishHTML Executor.getReportParameters(reportDir, reports[i].name, reportName )
        }
    }

    protected void runCron() {
        context.println("QARunner->runCron")
        def nodeName = "master"
        //TODO: remove master node assignment
        context.node(nodeName) {
            scmClient.clone()

            def workspace = getWorkspace()
            context.println("WORKSPACE: " + workspace)
            def project = Configuration.get("project")
            def sub_project = Configuration.get("sub_project")
            def jenkinsFile = ".jenkinsfile.json"

            if (!context.fileExists("${jenkinsFile}")) {
                context.println("no .jenkinsfile.json discovered! Scanner will use default qps-pipeline logic for project: ${project}")
            }

            def suiteFilter = "src/test/resources/**"
            Object subProjects = Executor.parseJSON(workspace + "/" + jenkinsFile).sub_projects
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
                        parsePipeline(workspace + "/" + files[i].path)
                    }

                    context.println("Finished Dynamic Mapping: " + listPipelines.dump())
                    listPipelines = Executor.sortPipelineList(listPipelines)
                    context.println("Finished Dynamic Mapping Sorted Order: " + listPipelines.dump())
                    folderName = Executor.parseFolderName(getWorkspace())
                    executeStages()
                } else {
                    context.println("No Test Suites Found to Scan...")
                }
            }
        }

    }

    protected void parsePipeline(String filePath) {
        //context.println("filePath: " + filePath)
        def XmlSuite currentSuite = null
        try {
            currentSuite = Executor.parseSuite(filePath)
        } catch (FileNotFoundException e) {
            context.println("ERROR! Unable to find suite: " + filePath)
            printStackTrace(e)
            return
        } catch (Exception e) {
            context.println("ERROR! Unable to parse suite: " + filePath)
            printStackTrace(e)
            return
        }

        def jobName = currentSuite.getParameter("jenkinsJobName").toString()
        def jobCreated = currentSuite.getParameter("jenkinsJobCreation")
        if (jobCreated != null && !jobCreated.toBoolean()) {
            //no need to proceed as jenkinsJobCreation=false
            return
        }
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
                    continue
                }
                for (def currentEnv : currentEnvs.split(",")) {
                    for (def supportedEnv : supportedEnvs.split(",")) {
                        //context.println("supportedEnv: " + supportedEnv)
                        if (!currentEnv.equals(supportedEnv) && !currentEnv.toString().equals("null")) {
                            //context.println("Skip execution for env: ${supportedEnv}; currentEnv: ${currentEnv}")
                            //launch test only if current suite support cron regression execution for current env
                            continue
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
                                continue
                            }

                            //context.println("adding ${filePath} suite to pipeline run...")

                            def pipelineMap = [:]

                            // put all not NULL args into the pipelineMap for execution
                            Executor.putNotNull(pipelineMap, "browser", browser)
                            Executor.putNotNull(pipelineMap, "browser_version", browserVersion)
                            Executor.putNotNull(pipelineMap, "os", os)
                            Executor.putNotNull(pipelineMap, "os_version", osVersion)
                            pipelineMap.put("name", pipeName)
                            pipelineMap.put("branch", Configuration.get("branch"))
                            pipelineMap.put("ci_parent_url", Executor.setDefaultIfEmpty("ci_parent_url", Configuration.Parameter.JOB_URL))
                            pipelineMap.put("ci_parent_build", Executor.setDefaultIfEmpty("ci_parent_build", Configuration.Parameter.BUILD_NUMBER))
                            pipelineMap.put("retry_count", Configuration.get("retry_count"))
                            Executor.putNotNull(pipelineMap, "thread_count", Configuration.get("thread_count"))
                            pipelineMap.put("jobName", jobName)
                            pipelineMap.put("env", supportedEnv)
                            pipelineMap.put("order", orderNum)
                            pipelineMap.put("BuildPriority", priorityNum)
                            Executor.putNotNullWithSplit(pipelineMap, "emailList", emailList)
                            Executor.putNotNullWithSplit(pipelineMap, "executionMode", executionMode)
                            Executor.putNotNull(pipelineMap, "overrideFields", overrideFields)

                            //context.println("initialized ${filePath} suite to pipeline run...")
                            registerPipeline(pipelineMap)
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

    protected def registerPipeline(pipelineMap) {
        listPipelines.add(pipelineMap)
    }

    protected def executeStages() {
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
                mappedStages[stageName] = buildOutStages(entry, waitJob, propagateJob)
            } else {
                context.parallel mappedStages

                //reset mappedStages to empty after execution
                mappedStages = [:]
                beginOrder = curOrder

                //add existing pipeline as new one in the current stage
                mappedStages[stageName] = buildOutStages(entry, waitJob, propagateJob)
            }
        }

        if (!mappedStages.isEmpty()) {
            //context.println("launch jobs with order: ${curOrder}")
            context.parallel mappedStages
        }

    }

    protected def buildOutStages(Map entry, boolean waitJob, boolean propagateJob) {
        return {
            buildOutStage(entry, waitJob, propagateJob)
        }
    }

    protected def buildOutStage(Map entry, boolean waitJob, boolean propagateJob) {
        context.stage(String.format("Stage: %s Environment: %s Browser: %s", entry.get("jobName"), entry.get("env"), entry.get("browser"))) {
            //context.println("Dynamic Stage Created For: " + entry.get("jobName"))
            //context.println("Checking EmailList: " + entry.get("emailList"))

            List jobParams = []

            //add current build params from cron
            for (param in Configuration.getParams()) {
                if ("false".equalsIgnoreCase(param.getValue().toString()) || "true".equalsIgnoreCase(param.getValue().toString())) {
                    jobParams.add(context.booleanParam(name: param.getKey(), value: param.getValue()))
                } else {
                    jobParams.add(context.string(name: param.getKey(), value: param.getValue()))
                }
            }

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
            } catch (Exception e) {
                printStackTrace(e)
                def body = "Unable to start job via cron! " + e.getMessage()
                def subject = "JOBSTART FAILURE: " + entry.get("jobName")
                def to = entry.get("email_list") + "," + Configuration.get("email_list")

                context.emailext Executor.getEmailParams(body, subject, to)
            }
        }
    }

    public void rerunJobs(){
        context.stage('Rerun Tests'){
            zc.smartRerun()
        }
    }

    public void publishUnitTestResults() {
        //publish junit/cobertura reports
        context.junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
        context.step([$class: 'CoberturaPublisher',
                      autoUpdateHealth: false,
                      autoUpdateStability: false,
                      coberturaReportFile: '**/target/site/cobertura/coverage.xml',
                      failUnhealthy: false,
                      failUnstable: false,
                      maxNumberOfBuilds: 0,
                      onlyStable: false,
                      sourceEncoding: 'ASCII',
                      zoomCoverageChart: false])
    }
	
	protected boolean isBrowserStackRun() {
		boolean res = false
		def customCapabilities = Configuration.get("custom_capabilities")
		if (!Executor.isParamEmpty(customCapabilities)) {
			if (customCapabilities.toLowerCase().contains("browserstack")) {
				res = true
			}
		}
		return res
	}

}
