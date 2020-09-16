package com.qaprosoft.jenkins.pipeline.runner.maven

import com.qaprosoft.jenkins.jobdsl.factory.pipeline.CronJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.TestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.integration.qtest.QTestUpdater
import com.qaprosoft.jenkins.pipeline.integration.testrail.TestRailUpdater
import com.qaprosoft.jenkins.pipeline.integration.zafira.StatusMapper
import com.qaprosoft.jenkins.pipeline.integration.zafira.ZafiraUpdater
import com.wangyin.parameter.WHideParameterDefinition
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import javaposse.jobdsl.plugin.actions.GeneratedJobsBuildAction
import jp.ikedam.jenkins.plugins.extensible_choice_parameter.ExtensibleChoiceParameterDefinition
import org.testng.xml.XmlSuite

import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Matcher
import java.util.regex.Pattern

import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

// #608 imports
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


@Grab('org.testng:testng:6.8.8')

public class TestNG extends Runner {

    protected def pipelineLibrary = "QPS-Pipeline"
    protected def runnerClass = "com.qaprosoft.jenkins.pipeline.runner.maven.TestNG"
    protected def onlyUpdated = false
    protected def uuid
    protected ZafiraUpdater zafiraUpdater
    protected TestRailUpdater testRailUpdater
    protected QTestUpdater qTestUpdater

    protected qpsInfraCrossBrowserMatrixName = "qps-infra-matrix"
    protected qpsInfraCrossBrowserMatrixValue = "browser: chrome; browser: firefox" // explicit versions removed as we gonna to deliver auto upgrade for browsers

    //CRON related vars
    protected def listPipelines = []
    protected Map pipelineLocaleMap = [:]
    protected orderedJobExecNum = 0
    protected boolean multilingualMode = false

    protected static final String JOB_TYPE = "job_type"
	protected static final String JENKINS_REGRESSION_MATRIX = "jenkinsRegressionMatrix"
	protected static final String JENKINS_REGRESSION_SCHEDULING = "jenkinsRegressionScheduling"
	
	protected static final String RESOURCES_PATH = "/resources/"
	protected static final String CARINA_SUITES_PATH = RESOURCES_PATH +  "testng_suites/"



    public TestNG(context) {
        super(context)
        onlyUpdated = Configuration.get("onlyUpdated")?.toBoolean()
        def locale = Configuration.get("locale")
        def language = Configuration.get("language")
        setDisplayNameTemplate('#${BUILD_NUMBER}|${suite}|${branch}|${env}|${browser}|${browserVersion}|${locale}|${language}')
    }

    //Events
    @Override
    public void onPush() {
        boolean isValid = false
        
        context.node("master") {
            context.timestamps {
                logger.info("TestNG->onPush")
                setReportingCreds()

                try {
                    getScm().clone(true)
                    if (isUpdated(currentBuild,"**.xml,**/zafira.properties") || !onlyUpdated) {
                        scan()
                        //TODO: move getJenkinsJobsScanResult to the end of the regular scan and removed from catch block!
                        getJenkinsJobsScanResult(currentBuild.rawBuild)
                    }

                    jenkinsFileScan()
                    isValid = true
                } catch (Exception e) {
                    logger.error("Scan failed.\n" + e.getMessage())
                    getJenkinsJobsScanResult(null)
                    this.currentBuild.result = BuildResult.FAILURE
                }
            }
        }
        
        context.node("maven") {
            context.timestamps {
                if (isValid) {
                    getScm().clonePush()
                    compile("-U clean compile test -DskipTests")
                }
                
                clean()
            }
        }
    }

	public void sendQTestResults() {
		// set all required integration at the beginning of build operation to use actual value and be able to override anytime later
		setReportingCreds()
		setQTestCreds()

		def ci_run_id = Configuration.get("ci_run_id")
		qTestUpdater.updateTestRun(ci_run_id)
	}

	public void sendTestRailResults() {
		// set all required integration at the beginning of build operation to use actual value and be able to override anytime later
		setReportingCreds()
		setTestRailCreds()

		testRailUpdater.updateTestRun(Configuration.get("ci_run_id"))
	}

	protected void scan() {

        context.stage("Scan Repository") {
            def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
            def repo = Configuration.get("repo")
            def repoFolder = parseFolderName(getWorkspace())
            def branch = Configuration.get("branch")

            setDisplayNameTemplate("#${buildNumber}|${repo}|${branch}")
            currentBuild.displayName = getDisplayName()

            def workspace = getWorkspace()
            logger.info("WORKSPACE: ${workspace}")

            for (pomFile in context.getPomFiles()) {
                // Ternary operation to get subproject path. "." means that no subfolder is detected
                def subProject = Paths.get(pomFile).getParent() ? Paths.get(pomFile).getParent().toString() : "."
                logger.debug("subProject: " + subProject)
                def subProjectFilter = subProject.equals(".") ? "**" : subProject
                def zafiraProject = getZafiraProject(subProjectFilter)
                generateDslObjects(repoFolder, zafiraProject, subProject, subProjectFilter, branch)

				factoryRunner.run(dslObjects, Configuration.get("removedConfigFilesAction"),
										Configuration.get("removedJobAction"),
										Configuration.get("removedViewAction"))
            }
        }
    }

    protected String getWorkspace() {
        return context.pwd()
    }

    protected def getSubProjectPomFiles(subDirectory) {
        if (".".equals(subDirectory)){
            subDirectory = ""
        } else {
            subDirectory = subDirectory + "/"
        }
        return context.findFiles(glob: subDirectory + "**/pom.xml")
    }

    def getZafiraProject(subProjectFilter){
        def zafiraProject = "unknown"
        def zafiraProperties = context.findFiles glob: subProjectFilter + "/**/zafira.properties"
        zafiraProperties.each {
            Map properties  = context.readProperties file: it.path
            if (!isParamEmpty(properties.zafira_project)){
                zafiraProject = properties.zafira_project
                logger.info("ZafiraProject: " + zafiraProject)
            }
        }
        return zafiraProject
    }

    def generateDslObjects(repoFolder, zafiraProject, subProject, subProjectFilter, branch){
        def host = Configuration.get(Configuration.Parameter.GITHUB_HOST)
        def organization = Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)
        def repo = Configuration.get("repo")
		
        // VIEWS
        registerObject("cron", new ListViewFactory(repoFolder, 'CRON', '.*cron.*'))
        //registerObject(project, new ListViewFactory(jobFolder, project.toUpperCase(), ".*${project}.*"))

        //TODO: create default personalized view here
        def suites = context.findFiles glob: subProjectFilter + "/**/*.xml"
        logger.info("SUITES: " + suites)
        // find all tetsng suite xml files and launch dsl creator scripts (views, folders, jobs etc)
        for (File suite : suites) {
            def suitePath = suite.path
			logger.debug("suitePath: " + suitePath)
			
			//verify if it is testNG suite xml file and continue scan only in this case!
			def currentSuitePath = workspace + "/" + suitePath
			
			if (!currentSuitePath.contains(RESOURCES_PATH) || !isTestNgSuite(currentSuitePath)) {
				logger.info("Skip from scanner as not a TestNG suite xml file: " + currentSuitePath)
				// not under /src/test/resources or not a TestNG suite file
				continue
			}

            XmlSuite currentSuite = parsePipeline(currentSuitePath)
			def suiteName = ""
			if (currentSuitePath.toLowerCase().contains(CARINA_SUITES_PATH) && currentSuitePath.endsWith(".xml")) {
				// carina core TestNG suite
				int testResourceIndex = currentSuitePath.toLowerCase().lastIndexOf(CARINA_SUITES_PATH)
				logger.debug("testResourceIndex : " + testResourceIndex)
				suiteName = currentSuitePath.substring(testResourceIndex + CARINA_SUITES_PATH.length(), currentSuitePath.length() - 4)
			} else {
				// external TestNG suite
				int testResourceIndex = currentSuitePath.lastIndexOf(RESOURCES_PATH)
				logger.debug("testResourceIndex : " + testResourceIndex)
				suiteName = currentSuitePath.substring(testResourceIndex + RESOURCES_PATH.length(), currentSuitePath.length())
			}
			
			if (suiteName.isEmpty()) {
				continue
			}

            logger.info("suite name: " + suiteName)
            logger.info("suite path: " + suitePath)

            def suiteThreadCount = getSuiteAttribute(currentSuite, "thread-count")
            logger.info("suite thread-count: " + suiteThreadCount)

            def suiteDataProviderThreadCount = getSuiteAttribute(currentSuite, "data-provider-thread-count")
            logger.info("suite data-provider-thread-count: " + suiteDataProviderThreadCount)

            def suiteOwner = getSuiteParameter("anonymous", "suiteOwner", currentSuite)
            if (suiteOwner.contains(",")) {
                // to workaround problem when multiply suiteowners are declared in suite xml file which is unsupported
                suiteOwner = suiteOwner.split(",")[0].trim()
            }

            def currentZafiraProject = getSuiteParameter(zafiraProject, "zafira_project", currentSuite)

            // put standard views factory into the map
            registerObject(currentZafiraProject, new ListViewFactory(repoFolder, currentZafiraProject.toUpperCase(), ".*${currentZafiraProject}.*"))
            registerObject(suiteOwner, new ListViewFactory(repoFolder, suiteOwner, ".*${suiteOwner}"))

            switch(suiteName.toLowerCase()){
                case ~/^.*api.*$/:
                    registerObject("API_VIEW", new ListViewFactory(repoFolder, "API", "", ".*(?i)api.*"))
                    break
                case ~/^.*web.*$/:
                    registerObject("WEB_VIEW", new ListViewFactory(repoFolder, "WEB", "", ".*(?i)web.*"))
                    break
                case ~/^.*android.*$/:
                    registerObject("ANDROID_VIEW", new ListViewFactory(repoFolder, "ANDROID", "", ".*(?i)android.*"))
                    break
                case ~/^.*ios.*$/:
                    registerObject("IOS_VIEW", new ListViewFactory(repoFolder, "IOS", "", ".*(?i)ios.*"))
                    break
            }

            def nameOrgRepoScheduling = (repoFolder.replaceAll('/', '-') + "-scheduling")
            def orgRepoScheduling = true
            if (!isParamEmpty(configuration.getGlobalProperty(nameOrgRepoScheduling)) && configuration.getGlobalProperty(nameOrgRepoScheduling).toBoolean() == false) {
                orgRepoScheduling = false
            }

            //pipeline job
            //TODO: review each argument to TestJobFactory and think about removal
            //TODO: verify suiteName duplication here and generate email failure to the owner and admin_emails
            def jobDesc = "project: ${repo}; zafira_project: ${currentZafiraProject}; owner: ${suiteOwner}"
            branch = getSuiteParameter(Configuration.get("branch"), "jenkinsDefaultGitBranch", currentSuite)
            registerObject(suitePath, new TestJobFactory(repoFolder, getPipelineScript(), host, repo, organization, branch, subProject, currentZafiraProject, currentSuitePath, suiteName, jobDesc, orgRepoScheduling, suiteThreadCount, suiteDataProviderThreadCount))

			//cron job
            if (!isParamEmpty(currentSuite.getParameter("jenkinsRegressionPipeline"))) {
                def cronJobNames = currentSuite.getParameter("jenkinsRegressionPipeline")
                for (def cronJobName : cronJobNames.split(",")) {
                    cronJobName = cronJobName.trim()
					def cronDesc = "project: ${repo}; type: cron"
					def cronJobFactory = new CronJobFactory(repoFolder, getCronPipelineScript(), cronJobName, host, repo, organization, branch, currentSuitePath, cronDesc, orgRepoScheduling)

					if (!dslObjects.containsKey(cronJobName)) {
						// register CronJobFactory only if its declaration is missed
						registerObject(cronJobName, cronJobFactory)
					} else {
						cronJobFactory = dslObjects.get(cronJobName)
					}

					// try to detect scheduling in current suite
					def scheduling = null
					if (!isParamEmpty(currentSuite.getParameter(JENKINS_REGRESSION_SCHEDULING))) {
						scheduling = currentSuite.getParameter(JENKINS_REGRESSION_SCHEDULING)
					}
					if (!isParamEmpty(currentSuite.getParameter(JENKINS_REGRESSION_SCHEDULING + "_" + cronJobName))) {
						scheduling = currentSuite.getParameter(JENKINS_REGRESSION_SCHEDULING + "_" + cronJobName)
					}

					if (!isParamEmpty(scheduling)) {
						logger.info("Setup scheduling for cron: ${cronJobName} value: ${scheduling}")
						cronJobFactory.setScheduling(scheduling)
					}
                }
            }
        }
    }

	protected def getSuiteAttribute(suite, attribute) {
		def res = "1"

		def file = new File(suite.getFileName())
		def documentBuilderFactory = DocumentBuilderFactory.newInstance()

		documentBuilderFactory.setValidating(false)
		documentBuilderFactory.setNamespaceAware(true)
		try {
			documentBuilderFactory.setFeature("http://xml.org/sax/features/namespaces", false)
			documentBuilderFactory.setFeature("http://xml.org/sax/features/validation", false)
			documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
			documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

			def documentBuilder = documentBuilderFactory.newDocumentBuilder()
			def document = documentBuilder.parse(file)

			for (int i = 0; i < document.getChildNodes().getLength(); i++) {
				def nodeMapAttributes = document.getChildNodes().item(i).getAttributes()
				if (nodeMapAttributes == null) {
					continue
				}

				// get "name" from suite element
				// <suite verbose="1" name="Carina Demo Tests - API Sample" thread-count="3" >
				Node nodeName = nodeMapAttributes.getNamedItem("name")
				if (nodeName == null) {
					continue
				}

				if (suite.getName().equals(nodeName.getNodeValue())) {
					// valid suite node detected
					Node nodeAttribute = nodeMapAttributes.getNamedItem(attribute)
					if (nodeAttribute != null) {
						res = nodeAttribute.getNodeValue()
						break
					}
				}
			}
		} catch (Exception e) {
			logger.error("Unable to get attribute '" + attribute +"' from suite: " + suite.getFileName() + "!")
			logger.error(e.getMessage())
			logger.error(printStackTrace(e))
		}

		return res
	}

	protected boolean isTestNgSuite(filePath){
		logger.debug("filePath: " + filePath)
		XmlSuite currentSuite = null
		boolean res = false
		try {
			currentSuite = parseSuite(filePath)
			res = true
		} catch (FileNotFoundException e) {
			logger.error("Unable to find suite: " + filePath)
			logger.error(printStackTrace(e))
		} catch (Exception e) {
			logger.debug("Unable to parse suite: " + filePath)
			logger.debug(printStackTrace(e))
		}
		return res
	}

    protected XmlSuite parsePipeline(filePath){
        logger.debug("filePath: " + filePath)
        XmlSuite currentSuite = null
        try {
            currentSuite = parseSuite(filePath)
        } catch (FileNotFoundException e) {
            logger.error("Unable to find suite: " + filePath)
            logger.error(printStackTrace(e))
        } catch (Exception e) {
            logger.error("Unable to parse suite: " + filePath)
            logger.error(printStackTrace(e))
        }
        return currentSuite
    }

    protected String getPipelineScript() {
        if ("QPS-Pipeline".equals(pipelineLibrary)) {
            return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).runJob()"
        } else {
            return "@Library(\'QPS-Pipeline\')\n@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).runJob()"
        }
    }

    protected String getCronPipelineScript() {
        if ("QPS-Pipeline".equals(pipelineLibrary)) {
            return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).runCron()"
        } else {
            return "@Library(\'QPS-Pipeline\')\n@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).runCron()"
        }
    }

    protected def getJenkinsJobsScanResult(build) {
        Map jenkinsJobsScanResult = [:]
        jenkinsJobsScanResult.success = false
        jenkinsJobsScanResult.repo = Configuration.get("repo")
        jenkinsJobsScanResult.userId = !isParamEmpty(Configuration.get("userId")) ? Long.valueOf(Configuration.get("userId")) : 2
        jenkinsJobsScanResult.jenkinsJobs = []
        try {
            if (build) {
                jenkinsJobsScanResult.jenkinsJobs = generateJenkinsJobs(build)
                jenkinsJobsScanResult.success = true
            }
            zafiraUpdater.createLaunchers(jenkinsJobsScanResult)
        } catch (Exception e) {
            throw new RuntimeException("Something went wrong during launchers creation", e)
        }
    }

    protected def generateJenkinsJobs(build){
        List jenkinsJobs = []
        build.getAction(GeneratedJobsBuildAction).modifiedObjects.each { job ->
            def jobFullName = replaceStartSlash(job.jobName)
            def jenkinsJob = generateJenkinsJob(jobFullName)
            jenkinsJobs.add(jenkinsJob)
        }
        logger.debug("jenkinsJobs for launchers: " + jenkinsJobs)
        return jenkinsJobs
    }

    protected def generateJenkinsJob(jobFullName){
        Map jenkinsJob = [:]

        def job = getItemByFullName(jobFullName)
        def jobUrl = getJobUrl(jobFullName)
        Map parameters = getParametersMap(job)

        if (!isParamEmpty(parameters.job_type)) {
            jenkinsJob.type = parameters.job_type
        } else {
            //TODO: contact with zebrunner insight team to confirm it is valid/expceted jobType for crons
            jenkinsJob.type = 'CRON'
        }

        parameters.remove("job_type")
        jenkinsJob.url = jobUrl
        jenkinsJob.parameters  = new JsonBuilder(parameters).toPrettyString()

        return jenkinsJob
    }

    protected def getObjectValue(obj) {
        def value
        if (obj instanceof ExtensibleChoiceParameterDefinition){
            value = obj.choiceListProvider.getChoiceList()
        } else if (obj instanceof ChoiceParameterDefinition) {
            value = obj.choices
        }  else {
            value = obj.defaultValue
        }
        return value
    }

    protected def getParametersMap(job) {
        def parameterDefinitions = job.getProperty('hudson.model.ParametersDefinitionProperty').parameterDefinitions
        Map parameters = [:]

        for (parameterDefinition in parameterDefinitions) {
            if (parameterDefinition.name == 'capabilities') {
                def value = getObjectValue(parameterDefinition).split(';')
                for (prm in value) {
                    if (prm.split('=').size() == 2) {
                        parameters.put("capabilities." + prm.split('=')[0], prm.split('=')[1])
                    } else {
                        logger.error("Invalid capability param: ${prm}" )
                    }
                }
            }
        }

        parameterDefinitions.each { parameterDefinition ->
            def value = getObjectValue(parameterDefinition)

            if (!(parameterDefinition instanceof WHideParameterDefinition) || JOB_TYPE.equals(parameterDefinition.name)) {
                if(isJobParameterValid(parameterDefinition.name)){
                    parameters.put(parameterDefinition.name, value)
                }
            }
        }
        logger.info(parameters)
        return parameters
    }

    public void runJob() {
        // set all required integration at the beginning of build operation to use actual value and be able to override anytime later
        setReportingCreds()
        setSeleniumUrl()
        
        //TODO: test if we should support scmURL
        if (!isParamEmpty(Configuration.get("scmURL"))){
            scmClient.setUrl(Configuration.get("scmURL"))
        }
        
        logger.info("TestNG->runJob")
        uuid = getUUID()
        logger.info("UUID: " + uuid)
        def testRun
        def isRerun = isRerun()
        String nodeName = "master"
        context.node(nodeName) {
            zafiraUpdater.queueZafiraTestRun(uuid)
            nodeName = chooseNode()
        }
        context.node(nodeName) {
            context.wrap([$class: 'BuildUser']) {
                try {
                    context.timestamps {
                        prepareBuild(currentBuild)
                        getScm().clone()

                        context.timeout(time: Integer.valueOf(Configuration.get(Configuration.Parameter.JOB_MAX_RUN_TIME)), unit: 'MINUTES') {
                            buildJob()
                        }
                        testRun = zafiraUpdater.getTestRunByCiRunId(uuid)
                        if(!isParamEmpty(testRun)){
                            zafiraUpdater.sendZafiraEmail(uuid, overrideRecipients(Configuration.get("email_list")))
                            
                            def channel = Configuration.get("slack_channels")
                            def failChannel = Configuration.get("failure_slack_channels")
                            if (!StatusMapper.ZafiraStatus.PASSED.name().equals(testRun.status)
                                && !isParamEmpty(failChannel)) {
                                //redirect message to failChannel for non PASSED test run
                                channel = failChannel
                            }
                            zafiraUpdater.sendSlackNotification(uuid, channel)
                        }
                        
                        //TODO: think about seperate stage for uploading jacoco reports
                        publishJacocoReport()
                    }
                } catch (Exception e) {
                    //TODO: [VD] think about making currentBuild.result as FAILURE
                    logger.error(printStackTrace(e))
                    testRun = zafiraUpdater.getTestRunByCiRunId(uuid)
                    if (!isParamEmpty(testRun)) {
                        def abortedTestRun = zafiraUpdater.abortTestRun(uuid, currentBuild)
                        if ((!isParamEmpty(abortedTestRun)
                                && !StatusMapper.ZafiraStatus.ABORTED.name().equals(abortedTestRun.status)
                                && !BuildResult.ABORTED.name().equals(currentBuild.result)) || Configuration.get("notify_slack_on_abort")?.toBoolean()) {

                            // send failure slack notification to "failure_slack_channels" if applicable otherwise send to default one
                            def channel = Configuration.get("failure_slack_channels")
                            if (isParamEmpty(channel)) {
                                // reuse default channel for negative notification only if failure_slack_channels absent  
                                channel = Configuration.get("slack_channels")
                            }
                            zafiraUpdater.sendSlackNotification(uuid, channel)
                        }
                    }
                    throw e
                } finally {
                    //TODO: send notification via email, slack, hipchat and whatever... based on subscription rules
                    if(!isParamEmpty(testRun)) {
                        zafiraUpdater.exportZafiraReport(uuid, getWorkspace())
                        zafiraUpdater.setBuildResult(uuid, currentBuild)
                    } else {
                        //try to find build result from CarinaReport if any
                    }
                    publishJenkinsReports()
                    sendCustomizedEmail()
                    clean()
                    customNotify()

                    if (Configuration.get("testrail_enabled")?.toBoolean() && !isParamEmpty(getCurrentFolderFullName(Configuration.TESTRAIL_UPDATER_JOBNAME))) {
                        String jobName = getCurrentFolderFullName(Configuration.TESTRAIL_UPDATER_JOBNAME)

                        // TODO: rename include_all to something testrail related
                        def includeAll = Configuration.get("include_all")?.toBoolean()
                        def milestoneName = !isParamEmpty(Configuration.get("testrail_milestone"))?Configuration.get("testrail_milestone"):""
                        def runName = !isParamEmpty(Configuration.get("testrail_run_name"))?Configuration.get("testrail_run_name"):""
                        def runExists = Configuration.get("run_exists")?.toBoolean()
                        def assignee = !isParamEmpty(Configuration.get("testrail_assignee"))?Configuration.get("testrail_assignee"):""

                        context.node("master") {
                            context.build job: jobName,
                                    propagate: false,
                                    wait: false,
                                    parameters: [
                                            context.string(name: 'ci_run_id', value: uuid),
                                            context.booleanParam(name: 'include_all', value: includeAll),
                                            context.string(name: 'milestone', value: milestoneName),
                                            context.string(name: 'run_name', value: runName),
                                            context.booleanParam(name: 'run_exists', value: runExists),
                                            context.string(name: 'assignee', value: assignee)
                                    ]
                        }
                    }
                    if (Configuration.get("qtest_enabled")?.toBoolean() && !isParamEmpty(getCurrentFolderFullName(Configuration.QTEST_UPDATER_JOBNAME))) {
                        String jobName = getCurrentFolderFullName(Configuration.QTEST_UPDATER_JOBNAME)
                        def os = !isParamEmpty(Configuration.get("capabilities.os"))?Configuration.get("capabilities.os"):""
                        def osVersion = !isParamEmpty(Configuration.get("capabilities.os_version"))?Configuration.get("capabilities.os_version"):""
                        def browser = getBrowser()
                        context.node("master") {
                            context.build job: jobName,
                                    propagate: false,
                                    wait: false,
                                    parameters: [
                                            context.string(name: 'ci_run_id', value: uuid),
                                            context.string(name: 'os', value: os),
                                            context.string(name: 'os_version', value: osVersion),
                                            context.string(name: 'browser', value: browser)
                                    ]
                        }
                    }
                }
            }
        }
    }

    protected String getCurrentFolderFullName(String jobName) {
        String baseJobName = jobName
        def fullJobName = Configuration.get(Configuration.Parameter.JOB_NAME)
        def fullJobNameArray = fullJobName.split("/")
        if (fullJobNameArray.size() == 3) {
            baseJobName = fullJobNameArray[0] + "/" + baseJobName
        }
        return baseJobName
    }

    // to be able to organize custom notifications on private pipeline layer
    protected void customNotify() {
        // do nothing
    }

    // Possible to override in private pipelines
    protected boolean isRerun() {
        return zafiraUpdater.isZafiraRerun(uuid)
    }

    // Possible to override in private pipelines
    protected def sendCustomizedEmail() {
        //Do nothing in default implementation
    }

    protected String chooseNode() {
        def jobType = !isParamEmpty(Configuration.get(JOB_TYPE)) ? Configuration.get(JOB_TYPE) : ""
        switch (jobType.toLowerCase()) {
            case "api":
            case "none":
                logger.info("Suite Type: API")
                Configuration.set("node", "api")
                //TODO: remove browser later. For now all API jobs marked as web vs chrome without below line
                Configuration.set("browser", "NULL")
                break;
            case "android":
            case "android-web":
                logger.info("Suite Type: ANDROID")
                Configuration.set("node", "android")
                break;
            case "android-tv":
                logger.info("Suite Type: ANDROID TV")
                Configuration.set("node", "android-tv")
                break;   
            case "ios":
            case "ios-web":
                logger.info("Suite Type: iOS")
                Configuration.set("node", "ios")
                break;
            case "web":
                logger.info("Suite Type: Web")
                Configuration.set("node", "web")
                break;
            default:
                logger.info("Suite Type: Default")
                Configuration.set("node", "default")
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

        context.stage('Preparation') {
            currentBuild.displayName = getDisplayName()
            if (isMobile()) {
                //this is mobile test
                prepareForMobile()
            }
        }
    }

    protected void prepareForMobile() {
        logger.info("Runner->prepareForMobile")
        def platform = Configuration.get("job_type").toLowerCase()

        if (platform.contains("android")) {
            prepareForAndroid()
        } else if (platform.equalsIgnoreCase("ios")) {
            prepareForiOS()
        } else {
            logger.warn("Unable to identify mobile platform: ${platform}")
        }

        //general mobile capabilities
        Configuration.set("capabilities.provider", "mcloud")


        // ATTENTION! Obligatory remove device from the params otherwise
        // hudson.remoting.Channel$CallSiteStackTrace: Remote call to JNLP4-connect connection from qpsinfra_jenkins-slave_1.qpsinfra_default/172.19.0.9:39487
        // Caused: java.io.IOException: remote file operation failed: /opt/jenkins/workspace/Automation/<JOB_NAME> at hudson.remoting.Channel@2834589:JNLP4-connect connection from
        Configuration.remove("device")
        //TODO: move it to the global jenkins variable
        
        // to fix "Session [9ed2aef1-8bd0-4151-a272-e5e869e0991a] was terminated due to TIMEOUT"
        Configuration.set("capabilities.newCommandTimeout", "120")
        Configuration.set("java.awt.headless", "true")
    }

    protected void prepareForAndroid() {
        logger.info("Runner->prepareForAndroid")
        Configuration.set("mobile_app_clear_cache", "true")
        Configuration.set("capabilities.autoGrantPermissions", "true")
        Configuration.set("capabilities.noSign", "true")
//        Configuration.set("capabilities.appWaitDuration", "270000")
//        Configuration.set("capabilities.androidInstallTimeout", "270000")
//        Configuration.set("capabilities.adbExecTimeout", "270000")
    }

    protected void prepareForiOS() {
        logger.info("Runner->prepareForiOS")
    }

    protected void buildJob() {
        context.stage('Run Test Suite') {
            def goals = getMavenGoals()
            def pomFile = getMavenPomFile()
            context.mavenBuild("-U ${goals} -f ${pomFile}")
        }
    }

	protected void setSeleniumUrl() {
		def seleniumUrl = Configuration.get(Configuration.Parameter.SELENIUM_URL)
		logger.info("seleniumUrl: ${seleniumUrl}")
		if (!isParamEmpty(seleniumUrl) && !Configuration.mustOverride.equals(seleniumUrl)) {
			// do not override from creds as looks like external service or user overrided this value
			return
		}

		// update SELENIUM_URL parameter based on capabilities.provider. Local "selenium" is default provider
		def provider = !isParamEmpty(Configuration.get("capabilities.provider")) ? Configuration.get("capabilities.provider") : "selenium"
		def hubUrl = "${provider}_hub"
        Configuration.set(Configuration.Parameter.SELENIUM_URL, getToken(hubUrl))

	}

    protected void setReportingCreds() {
        def zafiraFields = Configuration.get("zafiraFields")
        if (!isParamEmpty(zafiraFields) && zafiraFields.contains("zafira_service_url") && zafiraFields.contains("zafira_access_token")) {
            // init Zafira serviceUrl and accessToken parameter based on zafiraFields parameter
            logger.debug("init ZafiraUpdater from zafiraFields: " + zafiraFields)
            Configuration.set(Configuration.Parameter.REPORTING_SERVICE_URL, Configuration.get("zafira_service_url"))
            Configuration.set(Configuration.Parameter.REPORTING_ACCESS_TOKEN, Configuration.get("zafira_access_token"))
        } else {
            // init Zafira serviceUrl and accessToken parameter based on values from credentials
            logger.debug("init ZafiraUpdater from credentials")
            Configuration.set(Configuration.Parameter.REPORTING_SERVICE_URL, getToken(Configuration.CREDS_REPORTING_SERVICE_URL))
            Configuration.set(Configuration.Parameter.REPORTING_ACCESS_TOKEN, getToken(Configuration.CREDS_REPORTING_ACCESS_TOKEN))
        }

        // obligatory init zafiraUpdater after getting valid url and token
        zafiraUpdater = new ZafiraUpdater(context)
    }

	protected void setTestRailCreds() {
		// update testRail integration items from credentials
		Configuration.set(Configuration.Parameter.TESTRAIL_SERVICE_URL, getToken(Configuration.CREDS_TESTRAIL_SERVICE_URL))
        
        def (userName, userPassword) = getUserCreds(Configuration.CREDS_TESTRAIL)
        Configuration.set(Configuration.Parameter.TESTRAIL_USERNAME, userName)
        Configuration.set(Configuration.Parameter.TESTRAIL_PASSWORD, userPassword)
        
		// obligatory init testrailUpdater after getting valid url and creds reading
		testRailUpdater = new TestRailUpdater(context)
	}

	protected void setQTestCreds() {
		// update QTest serviceUrl and accessToken parameter based on values from credentials
		Configuration.set(Configuration.Parameter.QTEST_SERVICE_URL, getToken(Configuration.CREDS_QTEST_SERVICE_URL))
		Configuration.set(Configuration.Parameter.QTEST_ACCESS_TOKEN, getToken(Configuration.CREDS_QTEST_ACCESS_TOKEN))

		// obligatory init qtestUpdater after getting valid url and token
		qTestUpdater = new QTestUpdater(context)
	}

    protected String getMavenGoals() {
        // When zafira is disabled use Maven TestNG build status as job status. RetryCount can't be supported well!
        def zafiraGoals = "-Dzafira_enabled=false -Dmaven.test.failure.ignore=false"
        logger.debug("REPORTING_SERVICE_URL: " + Configuration.get(Configuration.Parameter.REPORTING_SERVICE_URL))
        logger.debug("REPORTING_ACCESS_TOKEN: " + Configuration.get(Configuration.Parameter.REPORTING_ACCESS_TOKEN))

        if (!Configuration.mustOverride.equals(Configuration.get(Configuration.Parameter.REPORTING_SERVICE_URL)) 
            && !Configuration.mustOverride.equals(Configuration.get(Configuration.Parameter.REPORTING_ACCESS_TOKEN))) {
            // Ignore maven build result if Zafira integration is enabled
            zafiraGoals = "-Dmaven.test.failure.ignore=true \
							-Dzafira_enabled=true \
							-Dzafira_service_url=${Configuration.get(Configuration.Parameter.REPORTING_SERVICE_URL)} \
							-Dzafira_access_token=${Configuration.get(Configuration.Parameter.REPORTING_ACCESS_TOKEN)}"
        }

        def buildUserEmail = Configuration.get("BUILD_USER_EMAIL") ? Configuration.get("BUILD_USER_EMAIL") : ""
        def defaultBaseMavenGoals = "-Dselenium_host=${Configuration.get(Configuration.Parameter.SELENIUM_URL)} \
        ${zafiraGoals} \
        -Ds3_save_screenshots=${Configuration.get(Configuration.Parameter.S3_SAVE_SCREENSHOTS)} \
        -Dcore_log_level=${Configuration.get(Configuration.Parameter.CORE_LOG_LEVEL)} \
        -Dmax_screen_history=1 \
        -Dreport_url=\"${Configuration.get(Configuration.Parameter.JOB_URL)}${Configuration.get(Configuration.Parameter.BUILD_NUMBER)}/CarinaReport\" \
        -Dgit_branch=${Configuration.get("branch")} \
        -Dgit_commit=${Configuration.get("scm_commit")} \
        -Dgit_url=${Configuration.get("scm_url")} \
        -Dci_url=${Configuration.get(Configuration.Parameter.JOB_URL)} \
        -Dci_build=${Configuration.get(Configuration.Parameter.BUILD_NUMBER)} \
        clean test"

        addCapability("ci_build_cause", getBuildCause((Configuration.get(Configuration.Parameter.JOB_NAME)), currentBuild))
        addCapability("suite", suiteName)
        addCapabilityIfPresent("rerun_failures", "zafira_rerun_failures")
        addCapabilityIfPresent("enableVideo", "capabilities.enableVideo")
        // [VD] getting debug host works only on specific nodes which are detecetd by chooseNode.
        // on this stage this method is not fucntion properly!
        //TODO: move 8000 port into the global var
        addOptionalCapability("debug", "Enabling remote debug on ${getDebugHost()}:${getDebugPort()}", "maven.surefire.debug",
                "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -Xnoagent -Djava.compiler=NONE")
        addVideoStreamingCapability("Video streaming was enabled.", "capabilities.enableVNC", "true")
        addBrowserStackGoals()

        def goals = Configuration.resolveVars(defaultBaseMavenGoals)

        goals += addMVNParams(Configuration.getVars())
        goals += addMVNParams(Configuration.getParams())

        goals += getOptionalCapability(Configuration.Parameter.JACOCO_ENABLE, " jacoco:instrument ")
        goals += getOptionalCapability("deploy_to_local_repo", " install")

        logger.debug("goals: ${goals}")
        return goals
    }
    protected def addMVNParams(params) {
        // This is an array of parameters, that we need to exclude from list of transmitted parameters to maven
        def necessaryMavenParams  = [
                "capabilities",
                "REPORTING_SERVICE_URL",
                "REPORTING_ACCESS_TOKEN",
                "zafiraFields",
                "CORE_LOG_LEVEL",
                "JACOCO_BUCKET",
                "JACOCO_REGION",
                "JACOCO_ENABLE",
                "JOB_MAX_RUN_TIME",
                "QPS_PIPELINE_GIT_BRANCH",
                "QPS_PIPELINE_GIT_URL",
                "ADMIN_EMAILS",
                "GITHUB_HOST",
                "GITHUB_API_URL",
                "GITHUB_ORGANIZATION",
                "GITHUB_HTML_URL",
                "GITHUB_OAUTH_TOKEN",
                "GITHUB_SSH_URL",
                "SELENIUM_URL",
                "TESTRAIL_SERVICE_URL",
                "TESTRAIL_USERNAME",
                "TESTRAIL_PASSWORD",
                "testrail_enabled",
                "QTEST_SERVICE_URL",
                "QTEST_ACCESS_TOKEN",
                "qtest_enabled",
                "job_type",
                "repo",
                "sub_project",
                "slack_channels",
                "BuildPriority",
                "queue_registration",
                "overrideFields",
                "fork"
        ]

        def goals = ''
        for (p in params) {
            if (!(p.getKey() in necessaryMavenParams)) {
                p.getKey()
                goals += " -D${p.getKey()}=\"${p.getValue()}\""
            }
        }
        return goals
    }

    protected def addVideoStreamingCapability(message, capabilityName, capabilityValue) {
        def node = Configuration.get("node").toLowerCase()
        if ("web".equalsIgnoreCase(node) || node.contains("android")) {
            logger.info(message)
            Configuration.set(capabilityName, capabilityValue)
        }
    }

    /**
     * Enables capability
     */
    protected def addCapability(capabilityName, capabilityValue) {
        Configuration.set(capabilityName, capabilityValue)
    }

    /**
     * Enables capability if its value is present in configuration and is true
     */
    protected def addOptionalCapability(parameterName, message, capabilityName, capabilityValue) {
        if (Configuration.get(parameterName)?.toBoolean()) {
            logger.info(message)
            Configuration.set(capabilityName, capabilityValue)
        }
    }

    /**
     * Enables capability if its value is present in configuration
     */
    protected def addCapabilityIfPresent(parameterName, capabilityName) {
        def capabilityValue = Configuration.get(parameterName)
        if(!isParamEmpty(capabilityValue))
            addCapability(capabilityName, capabilityValue)
    }

    /**
     * Returns capability value when it is enabled via parameterName in Configuration,
     * the other way returns empty line
     */
    protected def getOptionalCapability(parameterName, capabilityName) {
        return Configuration.get(parameterName)?.toBoolean() ? capabilityName : ""
    }

    protected def addBrowserStackGoals() {
        //browserstack goals
        if (isBrowserStackRunning()) {
            def uniqueBrowserInstance = "\"#${Configuration.get(Configuration.Parameter.BUILD_NUMBER)}-" + Configuration.get("suite") + "-" +
                    getBrowser() + "-" + Configuration.get("env") + "\""
            uniqueBrowserInstance = uniqueBrowserInstance.replace("/", "-").replace("#", "")
            startBrowserStackLocal(uniqueBrowserInstance)
            Configuration.set("capabilities.project", Configuration.get("repo"))
            Configuration.set("capabilities.build", uniqueBrowserInstance)
            Configuration.set("capabilities.browserstack.localIdentifier", uniqueBrowserInstance)
            Configuration.set("app_version", "browserStack")
        }
    }

    protected boolean isBrowserStackRunning() {
        def customCapabilities = Configuration.get("custom_capabilities") ? Configuration.get("custom_capabilities") : ""
        return customCapabilities.toLowerCase().contains("browserstack")
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

    protected def getSuiteName() {
        def suiteName
        if (context.isUnix()) {
            suiteName = Configuration.get("suite").replace("\\", "/")
        } else {
            suiteName = Configuration.get("suite").replace("/", "\\")
        }
        return suiteName
    }

    protected String getMavenPomFile() {
        return getSubProjectFolder() + "/pom.xml"
    }

    //TODO: move into valid jacoco related package
    protected void publishJacocoReport() {
        def jacocoEnable = Configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean()
        if (!jacocoEnable) {
            logger.warn("do not publish any content to AWS S3 if integration is disabled")
            return
        }

        def jacocoBucket = Configuration.get(Configuration.Parameter.JACOCO_BUCKET)
        def jacocoRegion = Configuration.get(Configuration.Parameter.JACOCO_REGION)
        def jobName = Configuration.get(Configuration.Parameter.JOB_NAME)
        def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)

        def files = context.findFiles(glob: '**/jacoco.exec')
        if (files.length == 1) {
            context.archiveArtifacts artifacts: '**/jacoco.exec', fingerprint: true, allowEmptyArchive: true
            // https://github.com/jenkinsci/pipeline-aws-plugin#s3upload
            context.withAWS(region: "$jacocoRegion", credentials: 'aws-jacoco-token') {
                context.s3Upload(bucket: "$jacocoBucket", path: "$jobName/$buildNumber/jacoco-it.exec", includePathPattern: '**/jacoco.exec')
            }
        }
    }

    //Overriden in private pipeline
    protected def overrideRecipients(emailList) {
        return emailList
    }

    protected void publishJenkinsReports() {
        context.stage('Results') {
            publishReport('**/reports/qa/emailable-report.html', "CarinaReport")
            publishReport('**/zafira/report.html', "ZafiraReport")
            //publishReport('**/artifacts/**', 'Artifacts')
            publishReport('**/*.dump', 'DumpReports')
            publishReport('**/*.har', 'HarReports')
            publishReport('**/target/surefire-reports/index.html', 'Full TestNG HTML Report')
            publishReport('**/target/surefire-reports/emailable-report.html', 'TestNG Summary HTML Report')
            publishReport('**/artifacts/**/feature-overview.html', 'CucumberReport')
        }
    }

    protected void publishReport(String pattern, String reportName) {
        try {
            def reports = context.findFiles(glob: pattern)
            def name = reportName
            for (int i = 0; i < reports.length; i++) {
                def parentFile = new File(reports[i].path).getParentFile()
                if (parentFile == null) {
                    logger.error("Parent report is null! for " + reports[i].path)
                    continue
                }
                def reportDir = parentFile.getPath()
                logger.info("Report File Found, Publishing " + reports[i].path)

                if (i > 0) {
                    name = reports[i].name.toString()
                }

                if (name.contains(".mp4")) {
                    // don't publish ".mp4" artifacts
                    continue
                }

                // TODO: remove below hotfix after resolving: https://github.com/qaprosoft/carina/issues/816
                if (reportName.equals("Artifacts") && reports[i].path.contains("CucumberReport")) {
                    // do not publish artifact as it is cucumber system item
                    continue
                }

                context.publishHTML getReportParameters(reportDir, reports[i].name, name)
            }
        } catch (Exception e) {
            logger.error("Exception occurred while publishing Jenkins report.")
            logger.error(printStackTrace(e))
        }
    }

    public void runCron() {
        logger.info("TestNG->runCron")
        context.node("master") {
            //TODO: test if we should support scmURL
            if (!isParamEmpty(Configuration.get("scmURL"))){
                scmClient.setUrl(Configuration.get("scmURL"))
            }

            getScm().clone()
            listPipelines = []
            def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
            def repo = Configuration.get("repo")
            def branch = Configuration.get("branch")

            setDisplayNameTemplate("#${buildNumber}|${repo}|${branch}")
            currentBuild.displayName = getDisplayName()

            for(pomFile in context.getPomFiles()){
                // clear list of pipelines for each sub-project
                listPipelines.clear()
                // Ternary operation to get subproject path. "." means that no subfolder is detected
                def subProject = Paths.get(pomFile).getParent()?Paths.get(pomFile).getParent().toString():"."
                def subProjectFilter = subProject.equals(".")?"**":subProject
                generatePipeLineList(subProjectFilter)
                logger.info "Finished Dynamic Mapping:"
                listPipelines = sortPipelineList(listPipelines)
                listPipelines.each { pipeline ->
                    logger.info(pipeline.toString())
                }
                executeStages()
            }
        }
    }

    protected def generatePipeLineList(subProjectFilter){
        def files = context.findFiles glob: subProjectFilter + "/**/*.xml"
        logger.info("Number of Test Suites to Scan Through: " + files.length)
        for (file in files){
            def currentSuitePath = workspace + "/" + file.path
            if (!currentSuitePath.contains(RESOURCES_PATH) || !isTestNgSuite(currentSuitePath)) {
                logger.info("Skip from scanner as not a TestNG suite xml file: " + currentSuitePath)
                // not under /src/test/resources or not a TestNG suite file
                continue
            }

            logger.info("Current suite path: " + currentSuitePath)
            XmlSuite currentSuite = parsePipeline(currentSuitePath)
            if (currentSuite == null) {
                logger.error("Unable to parse suite: " + currentSuitePath)
                currentBuild.result = BuildResult.FAILURE
                continue
            }
            generatePipeline(currentSuite)
        }
    }

    protected void generatePipeline(XmlSuite currentSuite) {
        if (getBooleanParameterValue("jenkinsJobDisabled", currentSuite)) {
            return
        }

        def jobName = !isParamEmpty(currentSuite.getParameter("jenkinsJobName"))?replaceSpecialSymbols(currentSuite.getParameter("jenkinsJobName")):replaceSpecialSymbols(currentSuite.getName())
        def regressionPipelines = !isParamEmpty(currentSuite.getParameter("jenkinsRegressionPipeline"))?currentSuite.getParameter("jenkinsRegressionPipeline"):""
        def orderNum = getJobExecutionOrderNumber(currentSuite)
        def executionMode = currentSuite.getParameter("jenkinsJobExecutionMode")
        def supportedEnvs = getSuiteParameter(currentSuite.getParameter("jenkinsEnvironments"), "jenkinsPipelineEnvironments", currentSuite)
        def currentEnvs = getCronEnv(currentSuite)
        def queueRegistration = !isParamEmpty(currentSuite.getParameter("jenkinsQueueRegistration"))?currentSuite.getParameter("jenkinsQueueRegistration"):Configuration.get("queue_registration")
        def emailList = !isParamEmpty(Configuration.get("email_list"))?Configuration.get("email_list"):currentSuite.getParameter("jenkinsEmail")
        def priorityNum = !isParamEmpty(Configuration.get("BuildPriority"))?Configuration.get("BuildPriority"):"5"
        def currentBrowser = !isParamEmpty(getBrowser())?getBrowser():"NULL"
        def logLine = "regressionPipelines: ${regressionPipelines};\n	jobName: ${jobName};\n	" +
                "jobExecutionOrderNumber: ${orderNum};\n	email_list: ${emailList};\n	" +
                "supportedEnvs: ${supportedEnvs};\n	currentEnv(s): ${currentEnvs};\n	" +
                "currentBrowser: ${currentBrowser};"
        logger.info(logLine)

        for (def regressionPipeline : regressionPipelines?.split(",")) {
			regressionPipeline = regressionPipeline.trim()
            if (!Configuration.get(Configuration.Parameter.JOB_BASE_NAME).equals(regressionPipeline)) {
                //launch test only if current regressionPipeline exists among regressionPipelines
                continue
            }

            for (def currentEnv : currentEnvs.split(",")) {
                currentEnv = currentEnv.trim()
                for (def supportedEnv : supportedEnvs.split(",")) {
                    supportedEnv = supportedEnv.trim()
//                  logger.debug("supportedEnv: " + supportedEnv)
                    if (!currentEnv.equals(supportedEnv) && !isParamEmpty(currentEnv)) {
                        logger.info("Skip execution for env: ${supportedEnv}; currentEnv: ${currentEnv}")
                        //launch test only if current suite support cron regression execution for current env
                        continue
                    }


					// organize children pipeline jobs according to the JENKINS_REGRESSION_MATRIX or execute at once with default params
					def supportedParamsMatrix = ""
					if (!isParamEmpty(currentSuite.getParameter(JENKINS_REGRESSION_MATRIX))) {
						supportedParamsMatrix = currentSuite.getParameter(JENKINS_REGRESSION_MATRIX)
						logger.info("Declared ${JENKINS_REGRESSION_MATRIX} detected!")
					}

					if (!isParamEmpty(currentSuite.getParameter(JENKINS_REGRESSION_MATRIX + "_" + regressionPipeline))) {
						// override default parameters matrix using concrete cron params
						supportedParamsMatrix = currentSuite.getParameter(JENKINS_REGRESSION_MATRIX + "_" + regressionPipeline)
						logger.info("Declared ${JENKINS_REGRESSION_MATRIX}_${regressionPipeline} detected!")
					}

					for (def supportedParams : supportedParamsMatrix.split(";")) {
						if (!isParamEmpty(supportedParams)) {
							supportedParams = supportedParams.trim()
							logger.info("supportedParams: ${supportedParams}")
						}

						Map supportedConfigurations = getSupportedConfigurations(supportedParams)
						logger.info("supportedConfigurations: ${supportedConfigurations}")
						def pipelineMap = [:]
						// put all not NULL args into the pipelineMap for execution
						putMap(pipelineMap, pipelineLocaleMap)
						pipelineMap.put("name", regressionPipeline)
						pipelineMap.put("params_name", supportedParams)
						pipelineMap.put("branch", Configuration.get("branch"))
						pipelineMap.put("ci_parent_url", setDefaultIfEmpty("ci_parent_url", Configuration.Parameter.JOB_URL))
						pipelineMap.put("ci_parent_build", setDefaultIfEmpty("ci_parent_build", Configuration.Parameter.BUILD_NUMBER))
						putNotNull(pipelineMap, "thread_count", Configuration.get("thread_count"))
						pipelineMap.put("jobName", jobName)
						pipelineMap.put("env", supportedEnv)
						pipelineMap.put("order", orderNum)
						pipelineMap.put("BuildPriority", priorityNum)
						putNotNullWithSplit(pipelineMap, "email_list", emailList)
						putNotNullWithSplit(pipelineMap, "executionMode", executionMode)
						putNotNull(pipelineMap, "overrideFields", Configuration.get("overrideFields"))
						putNotNull(pipelineMap, "zafiraFields", Configuration.get("zafiraFields"))
						putNotNull(pipelineMap, "queue_registration", queueRegistration)
						// supported config matrix should be applied at the end to be able to override default args like retry_count etc
						putMap(pipelineMap, supportedConfigurations)
						registerPipeline(currentSuite, pipelineMap)
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

    protected def getJobExecutionOrderNumber(suite){
        def orderNum = suite.getParameter("jenkinsJobExecutionOrder")
        if (isParamEmpty(orderNum)) {
            orderNum = 0
            logger.info("specify by default '0' order - start asap")
        } else if (orderNum.equals("ordered")) {
            orderedJobExecNum++
            orderNum = orderedJobExecNum
        }
        return orderNum.toString()
    }

    protected def getCronEnv(currentSuite) {
        //currentSuite is need to override action in private pipelines
        return Configuration.get("env")
    }

    // do not remove currentSuite from this method! It is available here to be override on customer level.
    protected def registerPipeline(currentSuite, pipelineMap) {
        listPipelines.add(pipelineMap)
    }

    protected getSupportedConfigurations(configDetails){
        def valuesMap = [:]
        // browser: chrome; browser: firefox;
        // browser: chrome, browser_version: 74;
        // os:Windows, os_version:10, browser:chrome, browser_version:72;
        // device:Samsung Galaxy S8, os_version:7.0
        // devicePool:Samsung Galaxy S8, platform: ANDROID, platformVersion: 9, deviceBrowser: chrome
        for (def config : configDetails.split(",")) {
            if (isParamEmpty(config)) {
                logger.warn("Supported config data is NULL!")
                continue
            }
            def nameValueArray = config.split(":");
            def name = nameValueArray[0]?.trim()
            logger.info("name: " + name)
            def value = ""
            if (nameValueArray.size() > 1) {
                value = nameValueArray[1]?.trim()
            }
            logger.info("value: " + value)
            valuesMap[name] = value
        }
        logger.info("valuesMap: " + valuesMap)
        return valuesMap
    }

    // do not remove unused crossBrowserSchema. It is declared for custom private pipelines to override default schemas
    @Deprecated
    protected getCrossBrowserConfigurations(configDetails) {
        return configDetails.replace(qpsInfraCrossBrowserMatrixName, qpsInfraCrossBrowserMatrixValue)
    }

    protected def executeStages() {
        def mappedStages = [:]

        boolean parallelMode = true
        //combine jobs with similar priority into the single parallel stage and after that each stage execute in parallel
        String beginOrder = "0"
        String curOrder = ""
        for (Map jobParams : listPipelines) {
            def stageName = getStageName(jobParams)
            boolean propagateJob = true
            if (!isParamEmpty(jobParams.get("executionMode"))) {
                if (jobParams.get("executionMode").contains("continue")) {
                    //do not interrupt pipeline/cron if any child job failed
                    propagateJob = false
                }
                if (jobParams.get("executionMode").contains("abort")) {
                    //interrupt pipeline/cron and return fail status to piepeline if any child job failed
                    propagateJob = true
                }
            }
            curOrder = jobParams.get("order")
            logger.debug("beginOrder: ${beginOrder}; curOrder: ${curOrder}")
            // do not wait results for jobs with default order "0". For all the rest we should wait results between phases
            boolean waitJob = false
            if (curOrder.toInteger() > 0) {
                waitJob = true
            }
            if (curOrder.equals(beginOrder)) {
                logger.debug("colect into order: ${curOrder}; job: ${stageName}")
                mappedStages[stageName] = buildOutStages(jobParams, waitJob, propagateJob)
            } else {
                context.parallel mappedStages
                //reset mappedStages to empty after execution
                mappedStages = [:]
                beginOrder = curOrder
                //add existing pipeline as new one in the current stage
                mappedStages[stageName] = buildOutStages(jobParams, waitJob, propagateJob)
            }
        }
        if (!isParamEmpty(mappedStages)) {
            logger.debug("launch jobs with order: ${curOrder}")
            context.parallel mappedStages
        }

    }

    protected def getStageName(jobParams) {
        // Put into this method all unique pipeline stage params otherwise less jobs then needed are launched!
        def stageName = ""
        String jobName = jobParams.get("jobName")
        String env = jobParams.get("env")
		String paramsName = jobParams.get("params_name")

        String browser = jobParams.get("browser")
        String browser_version = jobParams.get("browser_version")
        String custom_capabilities = jobParams.get("custom_capabilities")
        String locale = jobParams.get("locale")

        if (!isParamEmpty(jobName)) {
            stageName += "Stage: ${jobName} "
        }
		if (!isParamEmpty(env)) {
			stageName += "Environment: ${env} "
		}
		if (!isParamEmpty(paramsName)) {
			stageName += "Params: ${paramsName} "
		}
		//TODO: investigate if we can remove lower param for naming after adding "params_name"
        if (!isParamEmpty(browser)) {
            stageName += "Browser: ${browser} "
        }
        if (!isParamEmpty(browser_version)) {
            stageName += "Browser version: ${browser_version} "
        }
        if (!isParamEmpty(custom_capabilities)) {
            stageName += "Custom capabilities: ${custom_capabilities} "
        }
        if (!isParamEmpty(locale) && multilingualMode) {
            stageName += "Locale: ${locale} "
        }
        return stageName
    }

    protected def buildOutStages(Map entry, boolean waitJob, boolean propagateJob) {
        return {
            buildOutStage(entry, waitJob, propagateJob)
        }
    }

    protected def buildOutStage(Map entry, boolean waitJob, boolean propagateJob) {
        context.stage(getStageName(entry)) {
            logger.debug("Dynamic Stage Created For: " + entry.get("jobName"))
            logger.debug("Checking EmailList: " + entry.get("email_list"))

            List jobParams = []

            //add current build params from cron
            for (param in Configuration.getParams()) {
				if ("params_name".equals(param.getKey())) {
					//do not append params_name as it it used only for naming
					continue
				}

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
            //updates zafira credentials with values from Jenkins Credentials (if present)
			setReportingCreds()
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

    public void mergeBranch() {
        context.node("master") {
            logger.info("Runner->onBranchMerge")
            def sourceBranch = Configuration.get("branch")
            def targetBranch = Configuration.get("targetBranch")
            def forcePush = Configuration.get("forcePush").toBoolean()
            scmSshClient.clone()
            scmSshClient.push(sourceBranch, targetBranch, forcePush)
        }
    }

    def getSettingsFileProviderContent(fileId){
        context.configFileProvider([context.configFile(fileId: fileId, variable: "MAVEN_SETTINGS")]) {
            context.readFile context.env.MAVEN_SETTINGS
        }
    }

    // Possible to override in private pipelines
    protected def getDebugHost() {
        return context.env.getEnvironment().get("INFRA_HOST")
    }

    // Possible to override in private pipelines
    protected def getDebugPort() {
        def port = "8000"
        return port
    }

}
