package com.qaprosoft.jenkins.pipeline.maven

import static com.qaprosoft.jenkins.pipeline.Executor.*
import static com.qaprosoft.Utils.*
import com.qaprosoft.integration.testrail.TestRailUpdater
import com.qaprosoft.integration.qtest.QTestUpdater
import com.qaprosoft.integration.zafira.ZafiraUpdater
import com.qaprosoft.jenkins.pipeline.browserstack.OS
//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.AbstractRunner
import com.qaprosoft.jenkins.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.TestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.CronJobFactory
import com.qaprosoft.jenkins.pipeline.maven.sonar.Sonar
import com.qaprosoft.scm.github.GitHub
import org.testng.xml.XmlSuite
import groovy.json.JsonOutput
import java.nio.file.Path
import java.nio.file.Paths


@Grab('org.testng:testng:6.8.8')

@Mixin([Maven, Sonar])
public class QARunner extends AbstractRunner {

    protected Map dslObjects = new HashMap()
    protected def pipelineLibrary = "QPS-Pipeline"
    protected def runnerClass = "com.qaprosoft.jenkins.pipeline.maven.QARunner"
    protected def onlyUpdated = false
    protected def currentBuild
    protected def uuid
    protected ZafiraUpdater zafiraUpdater
    protected TestRailUpdater testRailUpdater
    protected QTestUpdater qTestUpdater

    //CRON related vars
    protected def listPipelines = []
    protected JobType jobType = JobType.JOB
    protected Map pipelineLocaleMap = [:]
    protected orderedJobExecNum = 0
    protected boolean multilingualMode = false

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
        zafiraUpdater = new ZafiraUpdater(context)
        testRailUpdater = new TestRailUpdater(context)
        qTestUpdater = new QTestUpdater(context)

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
        logger.info("QARunner->build")
        if(!isParamEmpty(Configuration.get("scmURL"))){
            scmClient.setUrl(Configuration.get("scmURL"))
        }
        if (jobType.equals(JobType.JOB)) {
            runJob()
        }
        if (jobType.equals(JobType.CRON)) {
            runCron()
        }
    }


    //Events
    public void onPush() {
        context.node("master") {
            context.timestamps {
                logger.info("QARunner->onPush")
                prepare()
                if (!isUpdated(currentBuild,"**.xml,**/zafira.properties") && onlyUpdated) {
                    logger.warn("do not continue scanner as none of suite was updated ( *.xml )")
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
            logger.info("QARunner->onPullRequest")
            scmClient.clonePR()

			def pomFiles = getProjectPomFiles()
			pomFiles.each {
				logger.debug(it)
				//do compile and scanner for all hogh level pom.xml files
				
				// [VD] integrated compilation as part of the sonar PR checker maven goal
				//compile(it.value)
				executeSonarPRScan(it.value)
			}

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
			// [VD] don't remove -U otherwise latest dependencies are not downloaded
			// and PR can be marked as fail due to the compilation failure!
			def goals = "-U clean compile test-compile \
					-f ${pomFile} \
					-Dcom.qaprosoft.carina-core.version=${Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)} \
					-Dcarina-core.version=${Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)}"

			executeMavenGoals(goals)
		}
	}

    protected void prepare() {
        scmClient.clone(!onlyUpdated)
        String QPS_PIPELINE_GIT_URL = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_URL)
        String QPS_PIPELINE_GIT_BRANCH = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_BRANCH)
        scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")
    }

    protected void scan() {

        context.stage("Scan Repository") {
            def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
            def repo = Configuration.get("repo")
            def repoFolder = parseFolderName(getWorkspace())
            def branch = Configuration.get("branch")

            currentBuild.displayName = "#${buildNumber}|${repo}|${branch}"

            def workspace = getWorkspace()
            logger.info("WORKSPACE: ${workspace}")
//
//            // Support DEV related CI workflow
//            //TODO: analyze if we need 3 system object declarations
//
//            def jenkinsFileOrigin = "Jenkinsfile"
//            if (context.fileExists("${workspace}/${jenkinsFileOrigin}")) {
//                //TODO: figure our howto work with Jenkinsfile
//                // this is the repo with already available pipeline script in Jenkinsfile
//                // just create a job
//            }

            def pomFiles = getProjectPomFiles()
            for(pomFile in pomFiles){
                // Ternary operation to get subproject path. "." means that no subfolder is detected
                def subProject = Paths.get(pomFile).getParent()?Paths.get(pomFile).getParent().toString():"."
                def subProjectFilter = subProject.equals(".")?"**":subProject
                def testNGFolderName = searchTestNgFolderName(subProject)
                def zafiraProject = getZafiraProject(subProjectFilter)
                generateDslObjects(repoFolder, testNGFolderName, zafiraProject, subProject, subProjectFilter)

                // put into the factories.json all declared jobdsl factories to verify and create/recreate/remove etc
                context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)
                logger.info("factoryTarget: " + FACTORY_TARGET)
                //TODO: test carefully auto-removal for jobs/views and configs
                context.jobDsl additionalClasspath: additionalClasspath,
                        removedConfigFilesAction: Configuration.get("removedConfigFilesAction"),
                        removedJobAction: Configuration.get("removedJobAction"),
                        removedViewAction: Configuration.get("removedViewAction"),
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

    protected String getWorkspace() {
        return context.pwd()
    }

    protected def getProjectPomFiles() {
        def pomFiles = []
        def files = context.findFiles(glob: "**/pom.xml")

        if(files.length > 0) {
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

    protected def getSubProjectPomFiles(subDirectory) {
        if(".".equals(subDirectory)){
            subDirectory = ""
        }
        return context.findFiles(glob: subDirectory + "**/pom.xml")
    }

    def parseTestNgFolderName(pomFile) {
        def testNGFolderName = null
        def pom = context.readMavenPom file: pomFile
        for (plugin in pom.build.plugins){
            if (plugin.artifactId.contains("surefire")) {
                if(isParamEmpty(plugin.configuration)){
                    break
                }
                def suiteXmlFiles = plugin.configuration.getChild("suiteXmlFiles")
                if(isParamEmpty(suiteXmlFiles)){
                    break
                }
                def suiteXmlFile = suiteXmlFiles.getChild("suiteXmlFile")
                Path suitePath = Paths.get(suiteXmlFile.value).getParent()
                testNGFolderName = suitePath.getName(suitePath.getNameCount() - 1)
                logger.info(testNGFolderName)
            }
        }
        return testNGFolderName
    }

    def searchTestNgFolderName(subProject) {
        def testNGFolderName = "testng_suites"
        def poms = getSubProjectPomFiles(subProject)
        logger.info("SUBPROJECT POMS: " + poms)
        for(pom in poms){
            testNGFolderName = parseTestNgFolderName(pom.path)
            if(!isParamEmpty(testNGFolderName)){
                break
            }
        }
        return testNGFolderName
    }

    def getZafiraProject(subProjectFilter){
        def zafiraProject = "unknown"
        def zafiraProperties = context.findFiles glob: subProjectFilter + "/**/zafira.properties"
        zafiraProperties.each {
            Map properties  = context.readProperties file: it.path
            if(!isParamEmpty(properties.zafira_project)){
                zafiraProject = properties.zafira_project
                logger.info("ZafiraProject: " + zafiraProject)
            }
        }
        return zafiraProject
    }

    def generateDslObjects(repoFolder, testNGFolderName, zafiraProject, subProject, subProjectFilter){
        def host = Configuration.get(Configuration.Parameter.GITHUB_HOST)
        def organization = Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)
        def repo = Configuration.get("repo")

        // VIEWS
        registerObject("cron", new ListViewFactory(repoFolder, 'CRON', '.*cron.*'))
        //registerObject(project, new ListViewFactory(jobFolder, project.toUpperCase(), ".*${project}.*"))

        //TODO: create default personalized view here
        def suites = context.findFiles glob: subProjectFilter + "/**/" + testNGFolderName + "/**"
        logger.info("SUITES: " + suites)
        // find all tetsng suite xml files and launch dsl creator scripts (views, folders, jobs etc)
        for (File suite : suites) {
            Path suitePath = Paths.get(suite.path)
            def suiteName = suitePath.getFileName().toString()
            if (!suiteName.contains(".xml")) {
                continue
            }
            def currentSuitePath = workspace + "/" + suitePath.toString()
            XmlSuite currentSuite = parsePipeline(currentSuitePath)
            if (getBooleanParameter("jenkinsJobCreation", currentSuite)) {

                logger.info("suite name: " + suiteName)
                logger.info("suite path: " + suitePath)

                def suiteOwner = setSuiteParameter("anonymous", "suiteOwner", currentSuite)
                def currentZafiraProject = setSuiteParameter(zafiraProject, "zafira_project", currentSuite)

                // put standard views factory into the map
                registerObject(currentZafiraProject, new ListViewFactory(repoFolder, currentZafiraProject.toUpperCase(), ".*${currentZafiraProject}.*"))
                registerObject(suiteOwner, new ListViewFactory(repoFolder, suiteOwner, ".*${suiteOwner}"))

                //pipeline job
                //TODO: review each argument to TestJobFactory and think about removal
                //TODO: verify suiteName duplication here and generate email failure to the owner and admin_emails
                def jobDesc = "project: ${repo}; zafira_project: ${currentZafiraProject}; owner: ${suiteOwner}"
                registerObject(suiteName, new TestJobFactory(repoFolder, getPipelineScript(), host, repo, organization, subProject, currentZafiraProject, currentSuitePath, suiteName, jobDesc))

                //cron job
                if (!isParamEmpty(currentSuite.getParameter("jenkinsRegressionPipeline"))) {
                    def cronJobNames = currentSuite.getParameter("jenkinsRegressionPipeline")
                    for (def cronJobName : cronJobNames.split(",")) {
                        cronJobName = cronJobName.trim()
                        def cronDesc = "project: ${repo}; type: cron"
                        registerObject(cronJobName, new CronJobFactory(repoFolder, getCronPipelineScript(), cronJobName, host, repo, organization, currentSuitePath, cronDesc))
                    }
                }
            }
        }
    }

    protected String getPipelineScript() {
        return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
    }

    protected String getCronPipelineScript() {
        return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this, 'CRON').build()"
    }

    protected void registerObject(name, object) {
        if (dslObjects.containsKey(name)) {
            logger.warn("WARNING! key ${name} already defined and will be replaced!")
            logger.info("Old Item: ${dslObjects.get(name).dump()}")
            logger.info("New Item: ${object.dump()}")
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
        logger.info("QARunner->runJob")
        uuid = getUUID()
        logger.info("UUID: " + uuid)
        def isRerun = isRerun()
        String nodeName = "master"
        context.node(nodeName) {
            zafiraUpdater.queueZafiraTestRun(uuid)
            initJobParams()
            nodeName = chooseNode()
        }
        context.node(nodeName) {

            context.wrap([$class: 'BuildUser']) {
                try {
                    context.timestamps {

                        prepareBuild(currentBuild)
                        scmClient.clone()

                        downloadResources()

                        context.timeout(time: Integer.valueOf(Configuration.get(Configuration.Parameter.JOB_MAX_RUN_TIME)), unit: 'MINUTES') {
                            buildJob()
                        }
                        zafiraUpdater.sendZafiraEmail(uuid, overrideRecipients(Configuration.get("email_list")))
                        sendCustomizedEmail()
						//TODO: think about seperate stage for uploading jacoco reports
                        publishJacocoReport()
                    }
                } catch (Exception e) {
                    logger.error(printStackTrace(e))
                    zafiraUpdater.abortTestRun(uuid, currentBuild)
                    throw e
                } finally {
                    //TODO: send notification via email, slack, hipchat and whatever... based on subscription rules
                    qTestUpdater.updateTestRun(uuid)
                    testRailUpdater.updateTestRun(uuid, isRerun)
                    zafiraUpdater.exportZafiraReport(uuid, getWorkspace())
                    zafiraUpdater.setBuildResult(uuid, currentBuild)
                    publishJenkinsReports()
                    clean()
                }
            }
        }
    }

    protected def initJobParams(){
        if(isParamEmpty(Configuration.get("platform"))){
            Configuration.set("platform", "*") //init default platform for launcher
        }
        if(isParamEmpty(Configuration.get("browser"))){
            Configuration.set("browser", "NULL") //init default platform for launcher
        }
    }

    // Possible to override in private pipelines
    protected boolean isRerun(){
        return zafiraUpdater.isZafiraRerun(uuid)
    }

    // Possible to override in private pipelines
    protected def sendCustomizedEmail(){
        //Do nothing in default implementation
    }

    protected String chooseNode() {

        String defaultNode = "qa"
        Configuration.set("node", defaultNode) //master is default node to execute job

        //TODO: handle browserstack etc integration here?
        switch(Configuration.get("platform").toLowerCase()) {
            case "api":
                logger.info("Suite Type: API")
                Configuration.set("node", "api")
                Configuration.set("browser", "NULL")
                break;
            case "android":
                logger.info("Suite Type: ANDROID")
                Configuration.set("node", "android")
                break;
            case "ios":
                //TODO: Need to improve this to be able to handle where emulator vs. physical tests should be run.
                logger.info("Suite Type: iOS")
                Configuration.set("node", "ios")
                break;
            default:
                if ("NULL".equals(Configuration.get("browser"))) {
                    logger.info("Suite Type: Default")
                    Configuration.set("node", "master")
                } else {
                    logger.info("Suite Type: Web")
                    Configuration.set("node", "web")
                }
        }

        def nodeLabel = Configuration.get("node_label")
        logger.info("nodeLabel: " + nodeLabel)
        if (!isParamEmpty(nodeLabel)) {
            logger.info("overriding default node to: " + nodeLabel)
            Configuration.set("node", nodeLabel)
        }
        logger.info("node: " + Configuration.get("node"))
        return Configuration.get("node")
    }

    //TODO: moved almost everything into argument to be able to move this methoud outside of the current class later if necessary
    protected void prepareBuild(currentBuild) {

        Configuration.set("BUILD_USER_ID", getBuildUser(currentBuild))

        String buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
        String carinaCoreVersion = getCarinaCoreVersion()
        String suite = Configuration.get("suite")
        String branch = Configuration.get("branch")
        String env = Configuration.get("env")
        String devicePool = Configuration.get("devicePool")
        String browser = Configuration.get("browser")
        //TODO: improve carina to detect browser_version on the fly
        String browserVersion = Configuration.get("browser_version")

        context.stage('Preparation') {
            currentBuild.displayName = "#${buildNumber}|${suite}|${branch}"
            if (!isParamEmpty(env)) {
                currentBuild.displayName += "|" + "${env}"
            }
            if (!isParamEmpty(carinaCoreVersion)) {
                currentBuild.displayName += "|" + "${carinaCoreVersion}"
            }
            if (!isParamEmpty(devicePool)) {
                currentBuild.displayName += "|${devicePool}"
            }
            if (!isParamEmpty(browser)) {
                currentBuild.displayName += "|${browser}"
            }
            if (!isParamEmpty(browserVersion)) {
                currentBuild.displayName += "|${browserVersion}"
            }
            currentBuild.description = "${suite}"
            if (isMobile()) {
                //this is mobile test
                prepareForMobile()
            }
        }
    }

    protected String getCarinaCoreVersion() {
        def carinaCoreVersion = Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)
        def overrideFields = Configuration.get("overrideFields").toLowerCase()

        if (overrideFields.contains("carina_core_version")) {
            def findCoreVersion = overrideFields.toLowerCase().find(/(?<=carina_core_version=)([^,]*)/)
            if (!isParamEmpty(findCoreVersion)) {
                carinaCoreVersion = findCoreVersion
            }
        }
        return carinaCoreVersion
    }

    protected void prepareForMobile() {
        logger.info("Runner->prepareForMobile")
        def devicePool = Configuration.get("devicePool")
        def defaultPool = Configuration.get("DefaultPool")
        def platform = Configuration.get("platform")

        if (platform.equalsIgnoreCase("android")) {
            prepareForAndroid()
        } else if (platform.equalsIgnoreCase("ios")) {
            prepareForiOS()
        } else {
            logger.warn("Unable to identify mobile platform: ${platform}")
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
        logger.info("Runner->prepareForAndroid")
        Configuration.set("mobile_app_clear_cache", "true")
        Configuration.set("capabilities.platformName", "ANDROID")
        Configuration.set("capabilities.autoGrantPermissions", "true")
        Configuration.set("capabilities.noSign", "true")
        Configuration.set("capabilities.STF_ENABLED", "true")
        Configuration.set("capabilities.appWaitDuration", "270000")
        Configuration.set("capabilities.androidInstallTimeout", "270000")
    }

    protected void prepareForiOS() {
        logger.info("Runner->prepareForiOS")
        Configuration.set("capabilities.platform", "IOS")
        Configuration.set("capabilities.platformName", "IOS")
        Configuration.set("capabilities.deviceName", "*")
        Configuration.set("capabilities.appPackage", "")
        Configuration.set("capabilities.appActivity", "")
        Configuration.set("capabilities.STF_ENABLED", "false")
    }

    protected void downloadResources() {
        //DO NOTHING as of now

/*		def CARINA_CORE_VERSION = Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)
		context.stage("Download Resources") {
		def pomFile = getSubProjectFolder() + "/pom.xml"
		logger.info("pomFile: " + pomFile)
		
		executeMavenGoals("-B -U -f ${pomFile} clean process-resources process-test-resources -Dcarina-core_version=$CARINA_CORE_VERSION")
*/	}

	protected String getMavenGoals() {
		def buildUserEmail = Configuration.get("BUILD_USER_EMAIL")
		if(buildUserEmail == null) {
			//override "null" value by empty to be able to register in cloud version of Zafira
			buildUserEmail = ""
		}
		def defaultBaseMavenGoals = "-Dcarina-core_version=${Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)} \
				-Detaf.carina.core.version=${Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)} \
		-Ds3_save_screenshots=${Configuration.get(Configuration.Parameter.S3_SAVE_SCREENSHOTS)} \
		-Dcore_log_level=${Configuration.get(Configuration.Parameter.CORE_LOG_LEVEL)} \
		-Dselenium_host=${Configuration.get(Configuration.Parameter.SELENIUM_URL)} \
		-Dmax_screen_history=1 -Dinit_retry_count=0 -Dinit_retry_interval=10 \
		-Dzafira_enabled=true \
		-Dzafira_service_url=${Configuration.get(Configuration.Parameter.ZAFIRA_SERVICE_URL)} \
		-Dzafira_access_token=${Configuration.get(Configuration.Parameter.ZAFIRA_ACCESS_TOKEN)} \
		-Dreport_url=\"${Configuration.get(Configuration.Parameter.JOB_URL)}${Configuration.get(Configuration.Parameter.BUILD_NUMBER)}/eTAFReport\" \
				-Dgit_branch=${Configuration.get("branch")} \
		-Dgit_commit=${Configuration.get("scm_commit")} \
		-Dgit_url=${Configuration.get("scm_url")} \
		-Dci_url=${Configuration.get(Configuration.Parameter.JOB_URL)} \
		-Dci_build=${Configuration.get(Configuration.Parameter.BUILD_NUMBER)} \
				  -Doptimize_video_recording=${Configuration.get(Configuration.Parameter.OPTIMIZE_VIDEO_RECORDING)} \
		-Duser.timezone=${Configuration.get(Configuration.Parameter.TIMEZONE)} \
		clean test -Dqueue_registration=false"

        def rerunFailures = Configuration.get("rerun_failures")
        if(!isParamEmpty(rerunFailures)){
            defaultBaseMavenGoals = defaultBaseMavenGoals + " -Dzafira_rerun_failures=${rerunFailures}"
        }

		Configuration.set("ci_build_cause", getBuildCause((Configuration.get(Configuration.Parameter.JOB_NAME)), currentBuild))

		def goals = Configuration.resolveVars(defaultBaseMavenGoals)

		//register all obligatory vars
		Configuration.getVars().each { k, v -> goals = goals + " -D${k}=\"${v}\""}

		//register all params after vars to be able to override
		Configuration.getParams().each { k, v -> goals = goals + " -D${k}=\"${v}\""}

		goals = enableVideoStreaming(Configuration.get("node"), "Video streaming was enabled.", " -Dcapabilities.enableVNC=true ", goals)
		goals = addOptionalParameter("enableVideo", "Video recording was enabled.", " -Dcapabilities.enableVideo=true ", goals)
		goals = addOptionalParameter(Configuration.get(Configuration.Parameter.JACOCO_ENABLE), "Jacoco tool was enabled.", " jacoco:instrument ", goals)

		//TODO: move 8000 port into the global var
		def mavenDebug=" -Dmaven.surefire.debug=\"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -Xnoagent -Djava.compiler=NONE\" "

		if (Configuration.get("debug") && Configuration.get("debug").toBoolean()) {
			// [VD] getting debug host works only on specific nodes which are detecetd by chooseNode.
			// on this stage this method is not fucntion properly!
			goals = addOptionalParameter("debug", "Enabling remote debug on ${getDebugHost()}:${getDebugPort()}", mavenDebug, goals)
		}
		goals = addOptionalParameter("deploy_to_local_repo", "Enabling deployment of tests jar to local repo.", " install", goals)

		//browserstack goals
		if (isBrowserStackRun()) {
			def uniqueBrowserInstance = "\"#${Configuration.get(Configuration.Parameter.BUILD_NUMBER)}-" + Configuration.get("suite") + "-" +
					Configuration.get("browser") + "-" + Configuration.get("env") + "\""
			uniqueBrowserInstance = uniqueBrowserInstance.replace("/", "-").replace("#", "")
			startBrowserStackLocal(uniqueBrowserInstance)
			goals += " -Dcapabilities.project=" + Configuration.get("repo")
			goals += " -Dcapabilities.build=" + uniqueBrowserInstance
			goals += " -Dcapabilities.browserstack.localIdentifier=" + uniqueBrowserInstance
			goals += " -Dapp_version=browserStack"
		}

		//append again overrideFields to make sure they are declared at the end
		goals = goals + " " + Configuration.get("overrideFields")

		logger.debug("goals: ${goals}")

		def suiteName = null
		if (context.isUnix()) {
			suiteName = Configuration.get("suite").replace("\\", "/")
		} else {
			suiteName = Configuration.get("suite").replace("/", "\\")
		}

		return "${goals} -Dsuite=${suiteName}"
	}

	protected String getMavenPomFile() {
		return getSubProjectFolder() + "/pom.xml"
	}

    protected void buildJob() {
        context.stage('Run Test Suite') {
			def goals = getMavenGoals()
			def pomFile = getMavenPomFile()
			executeMavenGoals("-U ${goals} -f ${pomFile}")
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
            //TODO: [VD] use valid status and stderr object after develping such functionality on pipeline level: https://issues.jenkins-ci.org/browse/JENKINS-44930
            def logFile = "/var/tmp/BrowserStackLocal.log"
            def browserStackLocalStart = browserStackLocation + " --key ${accessKey} --local-identifier ${uniqueBrowserInstance} --force-local > ${logFile} 2>&1 &"
            context.sh(browserStackLocalStart)
            context.sh("sleep 3")
            logger.info("BrowserStack Local proxy statrup output:\n" + context.readFile(logFile).trim())
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
            logger.warn("do not publish any content to AWS S3 if integration is disabled")
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

     //Overriden in private pipeline
    protected def overrideRecipients(emailList) {
        return emailList
    }

    protected void publishJenkinsReports() {
        context.stage('Results') {
            publishReport('**/zafira/report.html', "ZafiraReport")
            publishReport('**/artifacts/**', 'Artifacts')
            publishReport('**/target/surefire-reports/index.html', 'Full TestNG HTML Report')
            publishReport('**/target/surefire-reports/emailable-report.html', 'TestNG Summary HTML Report')
        }
    }

    protected void publishReport(String pattern, String reportName) {
        try {
            def reports = context.findFiles(glob: pattern)
            for (int i = 0; i < reports.length; i++) {
                def parentFile = new File(reports[i].path).getParentFile()
                if (parentFile == null) {
                    logger.error("ERROR! Parent report is null! for " + reports[i].path)
                    continue
                }
                def reportDir = parentFile.getPath()
                logger.info("Report File Found, Publishing " + reports[i].path)
                if (i > 0){
                    def reportIndex = "_" + i
                    reportName = reportName + reportIndex
                }
                context.publishHTML getReportParameters(reportDir, reports[i].name, reportName )
            }
        } catch (Exception e) {
            logger.error("Exception occurred while publishing Jenkins report.")
            logger.error(printStackTrace(e))
        }
     }

    protected void runCron() {
        logger.info("QARunner->runCron")
        context.node("master") {
            scmClient.clone()

            def workspace = getWorkspace()
            logger.info("WORKSPACE: " + workspace)
            def project = Configuration.get("repo")
            def jenkinsFile = ".jenkinsfile.json"

            if (!context.fileExists("${jenkinsFile}")) {
                logger.warn("no .jenkinsfile.json discovered! Scanner will use default qps-pipeline logic for project: ${project}")
            }

            def suiteFilter = "src/test/resources/**"
            Object subProjects = parseJSON(workspace + "/" + jenkinsFile).sub_projects
            subProjects.each {
                listPipelines = []
                suiteFilter = it.suite_filter
                def sub_project = it.name

                def subProjectFilter = sub_project
                if (sub_project.equals(".")) {
                    subProjectFilter = "**"
                }

                def files = context.findFiles(glob: subProjectFilter + "/" + suiteFilter + "/**")
                if(files.length > 0) {
                    logger.info("Number of Test Suites to Scan Through: " + files.length)
                    for (int i = 0; i < files.length; i++) {
                        XmlSuite currentSuite = parsePipeline(workspace + "/" + files[i].path)
                        if (currentSuite == null) {
                            currentBuild.result = BuildResult.FAILURE
                            continue
                        }
                        def supportedLocales = getPipelineLocales(currentSuite)
                        if (supportedLocales.size() > 0) {
                            multilingualMode = true
                            supportedLocales.each { locale ->
                                pipelineLocaleMap.put("locale", locale.key)
                                pipelineLocaleMap.put("language", locale.value)
                                generatePipeline(currentSuite)
                            }
                            pipelineLocaleMap.clear()
                        } else {
                            generatePipeline(currentSuite)
                        }
                    }
                    logger.info "Finished Dynamic Mapping:"
                    listPipelines.each { pipeline ->
                        logger.info(pipeline.toString())
                    }
                    listPipelines = sortPipelineList(listPipelines)
                    logger.debug("Finished Dynamic Mapping Sorted Order:")
                    listPipelines.each { pipeline ->
                        logger.debug(pipeline.toString())
                    }
                    executeStages()
                } else {
                    logger.error("No Test Suites Found to Scan...")
                }
            }
        }

    }

     protected XmlSuite parsePipeline(filePath){
        logger.debug("filePath: " + filePath)
        XmlSuite currentSuite = null
        try {
            currentSuite = parseSuite(filePath)
        } catch (FileNotFoundException e) {
            logger.error("ERROR! Unable to find suite: " + filePath)
            logger.error(printStackTrace(e))
        } catch (Exception e) {
            logger.error("ERROR! Unable to parse suite: " + filePath)
            logger.error(printStackTrace(e))
        }
        return currentSuite
    }

    protected void generatePipeline(XmlSuite currentSuite) {

        def jobName = currentSuite.getParameter("jenkinsJobName").toString()
        def jobCreated = currentSuite.getParameter("jenkinsJobCreation")
        if (jobCreated != null && !jobCreated.toBoolean()) {
            //no need to proceed as jenkinsJobCreation=false
            return
        }
        def supportedPipelines = currentSuite.getParameter("jenkinsRegressionPipeline").toString()
        def orderNum = getOrderNum(currentSuite)
        def executionMode = currentSuite.getParameter("jenkinsJobExecutionMode").toString()
        def supportedEnvs = currentSuite.getParameter("jenkinsPipelineEnvironments").toString()
		if (isParamEmpty(supportedEnvs)) {
			supportedEnvs = currentSuite.getParameter("jenkinsEnvironments").toString()
		}
        def queueRegistration = currentSuite.getParameter("jenkinsQueueRegistration")
        if(!isParamEmpty(queueRegistration)){
            logger.info("override queue_registration to: " + queueRegistration)
            Configuration.set("queue_registration", queueRegistration)
        }

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
        String logLine = "supportedPipelines: ${supportedPipelines};\n	jobName: ${jobName};\n	orderNum: ${orderNum};\n	email_list: ${emailList};\n	supportedEnvs: ${supportedEnvs};\n	currentEnv(s): ${currentEnvs};\n	supportedBrowsers: ${supportedBrowsers};\n"

        def currentBrowser = Configuration.get("browser")

        if (currentBrowser == null || currentBrowser.isEmpty()) {
            currentBrowser = "NULL"
        }

        logLine += "	currentBrowser: ${currentBrowser};\n"
        logger.info(logLine)

        if (!supportedPipelines.contains("null")) {
            for (def pipeName : supportedPipelines.split(",")) {
                if (!pipelineJobName.equals(pipeName)) {
                    //launch test only if current pipeName exists among supportedPipelines
                    continue
                }
                for (def currentEnv : currentEnvs.split(",")) {
                    for (def supportedEnv : supportedEnvs.split(",")) {
//                        logger.debug("supportedEnv: " + supportedEnv)
                        if (!currentEnv.equals(supportedEnv) && !currentEnv.toString().equals("null")) {
                            logger.info("Skip execution for env: ${supportedEnv}; currentEnv: ${currentEnv}")
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

//                            logger.debug("supportedBrowser: ${supportedBrowser}; currentBrowser: ${currentBrowser}; ")
                            if (!currentBrowser.equals(supportedBrowser) && !currentBrowser.toString().equals("NULL")) {
                                logger.info("Skip execution for browser: ${supportedBrowser}; currentBrowser: ${currentBrowser}")
                                continue
                            }

//                            logger.info("adding ${filePath} suite to pipeline run...")

                            def pipelineMap = [:]

                            // put all not NULL args into the pipelineMap for execution
                            putMap(pipelineMap, pipelineLocaleMap)
                            putNotNull(pipelineMap, "browser", browser)
                            putNotNull(pipelineMap, "browser_version", browserVersion)
                            putNotNull(pipelineMap, "os", os)
                            putNotNull(pipelineMap, "os_version", osVersion)
                            pipelineMap.put("name", pipeName)
                            pipelineMap.put("branch", Configuration.get("branch"))
                            pipelineMap.put("ci_parent_url", setDefaultIfEmpty("ci_parent_url", Configuration.Parameter.JOB_URL))
                            pipelineMap.put("ci_parent_build", setDefaultIfEmpty("ci_parent_build", Configuration.Parameter.BUILD_NUMBER))
                            pipelineMap.put("retry_count", Configuration.get("retry_count"))
                            putNotNull(pipelineMap, "thread_count", Configuration.get("thread_count"))
                            pipelineMap.put("jobName", jobName)
                            pipelineMap.put("env", supportedEnv)
                            pipelineMap.put("order", orderNum)
                            pipelineMap.put("BuildPriority", priorityNum)
                            putNotNullWithSplit(pipelineMap, "emailList", emailList)
                            putNotNullWithSplit(pipelineMap, "executionMode", executionMode)
                            putNotNull(pipelineMap, "overrideFields", overrideFields)
                            putNotNull(pipelineMap, "queue_registration", Configuration.get("queue_registration"))
//                                logger.debug("initialized ${filePath} suite to pipeline run...")
                            registerPipeline(currentSuite, pipelineMap)
                        }
                    }
                }
            }
        }
    }

    protected def getOrderNum(suite){
        def orderNum = suite.getParameter("jenkinsJobExecutionOrder").toString()
        if (orderNum.equals("null")) {
            orderNum = "0"
            logger.info("specify by default '0' order - start asap")
        } else if (orderNum.equals("ordered")) {
            orderedJobExecNum++
            orderNum = orderedJobExecNum.toString()
        }
        return orderNum
    }

    protected def getCronEnv(currentSuite) {
        //currentSuite is need to override action in private pipelines
        return Configuration.get("env")
    }

	// do not remove currentSuite from this method! It is available here to be override on customer level.
    protected def registerPipeline(currentSuite, pipelineMap) {
        listPipelines.add(pipelineMap)
    }

    protected def executeStages() {
        def mappedStages = [:]

        boolean parallelMode = true

        //combine jobs with similar priority into the single paralle stage and after that each stage execute in parallel
        String beginOrder = "0"
        String curOrder = ""
        for (Map entry : listPipelines) {
            def stageName
            if(multilingualMode && entry.get("locale")){
                stageName = String.format("Stage: %s Environment: %s Browser: %s Locale: %s", entry.get("jobName"), entry.get("env"), entry.get("browser"), entry.get("locale"))
            } else {
                stageName = String.format("Stage: %s Environment: %s Browser: %s", entry.get("jobName"), entry.get("env"), entry.get("browser"))
            }
            logger.info("stageName: ${stageName}")

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
            logger.debug("beginOrder: ${beginOrder}; curOrder: ${curOrder}")

            // do not wait results for jobs with default order "0". For all the rest we should wait results between phases
            boolean waitJob = false
            if (curOrder.toInteger() > 0) {
                waitJob = true
            }

            if (curOrder.equals(beginOrder)) {
                logger.debug("colect into order: ${curOrder}; job: ${stageName}")
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
            logger.debug("launch jobs with order: ${curOrder}")
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
            logger.debug("Dynamic Stage Created For: " + entry.get("jobName"))
            logger.debug("Checking EmailList: " + entry.get("emailList"))

            List jobParams = []

            //add current build params from cron
            for (param in Configuration.getParams()) {
                if (!isParamEmpty(param.getValue())) {
                    if ("false".equalsIgnoreCase(param.getValue().toString()) || "true".equalsIgnoreCase(param.getValue().toString())) {
                        jobParams.add(context.booleanParam(name: param.getKey(), value: param.getValue()))
                    } else {
                        jobParams.add(context.string(name: param.getKey(), value: param.getValue()))
                    }
                }
            }

            for (param in entry) {
                jobParams.add(context.string(name: param.getKey(), value: param.getValue()))
            }
            logger.info(jobParams.dump())

            try {
                context.build job: parseFolderName(getWorkspace()) + "/" + entry.get("jobName"),
                        propagate: propagateJob,
                        parameters: jobParams,
                        wait: waitJob
            } catch (Exception e) {
                logger.error(printStackTrace(e))
                def body = "Unable to start job via cron! " + e.getMessage()
                def subject = "JOBSTART FAILURE: " + entry.get("jobName")
                def to = entry.get("email_list") + "," + Configuration.get("email_list")

                context.emailext getEmailParams(body, subject, to)
            }
        }
    }

    public void rerunJobs(){
        context.stage('Rerun Tests'){
            zafiraUpdater.smartRerun()
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
		if (!isParamEmpty(customCapabilities)) {
			if (customCapabilities.toLowerCase().contains("browserstack")) {
				res = true
			}
		}
		return res
	}

    protected def addOptionalParameter(parameter, message, capability, goals) {
        if (Configuration.get(parameter) && Configuration.get(parameter).toBoolean()) {
            logger.debug(message)
            goals += capability
        }
        return goals
    }

    // Possible to override in private pipelines
    protected def getDebugHost() {
       return context.env.getEnvironment().get("QPS_HOST")
    }

    // Possible to override in private pipelines
    protected def getDebugPort() {
        def port = "8000"
        return port
    }
	
}
