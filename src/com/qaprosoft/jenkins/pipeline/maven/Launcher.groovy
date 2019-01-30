package com.qaprosoft.jenkins.pipeline.maven

@Grab('org.testng:testng:6.8.8')
import com.qaprosoft.Logger
import com.qaprosoft.Utils
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.maven.QARunner
import com.qaprosoft.selenium.grid.ProxyInfo
import org.testng.xml.Parser

import static com.qaprosoft.jenkins.pipeline.Executor.*
import com.qaprosoft.scm.ISCM
import com.qaprosoft.scm.github.GitHub
import org.testng.xml.XmlSuite
import static java.util.UUID.randomUUID

class Launcher {

    protected def context
    protected ISCM scmClient
    protected Logger logger
    protected def currentBuild

    public Launcher(context) {
        this.context = context
        this.logger = new Logger(context)
        scmClient = new GitHub(context)
        currentBuild = context.currentBuild
    }

    public def runSuite() {
        context.node('master') {
            context.timestamps {
                prepare()
                scan()
                new QARunner().runJob()
                clean()
            }
        }

    }

    protected void prepare() {
        scmClient.clone()
        String QPS_PIPELINE_GIT_URL = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_URL)
        String QPS_PIPELINE_GIT_BRANCH = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_BRANCH)
        scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")
    }

    protected void scan() {

        context.stage("Scan Repository") {
            def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
            def organization = Configuration.get("organization")
            def repo = Configuration.get("repo")
            def branch = Configuration.get("branch")
            def suiteName = Configuration.get("suite")
            currentBuild.displayName = "#${buildNumber}|${repo}|${branch}"

            def workspace = getWorkspace()
            logger.info("WORKSPACE: ${workspace}")

            def pomFiles = getProjectPomFiles()
            pomFiles.each {
                logger.info(it.dump())
            }

            def jenkinsFile = ".jenkinsfile.json"
            if (!context.fileExists("${workspace}/${jenkinsFile}")) {
                logger.warn("Skip repository scan as no .jenkinsfile.json discovered! Project: ${repo}")
                currentBuild.result = BuildResult.UNSTABLE
                return
            }

            Object subProjects = parseJSON("${workspace}/${jenkinsFile}").sub_projects

            logger.info("PARSED: " + subProjects)
            subProjects.each {
                logger.info("sub_project: " + it)

                def sub_project = it.name

                def subProjectFilter = it.name
                if (sub_project.equals(".")) {
                    subProjectFilter = "**"
                }

                def zafiraFilter = it.zafira_filter
                def suiteFilter = it.suite_filter

                if (suiteFilter.isEmpty()) {
                    logger.warn("Skip repository scan as no suiteFilter identified! Project: ${repo}")
                    return
                }

                def zafira_project = 'unknown'
                def zafiraProperties = context.findFiles(glob: subProjectFilter + "/" + zafiraFilter)
                for (File file : zafiraProperties) {
                    def props = context.readProperties file: file.path
                    if (props['zafira_project'] != null) {
                        zafira_project = props['zafira_project']
                    }
                }
                logger.info("zafira_project: ${zafira_project}")

                if (suiteFilter.endsWith("/")) {
                    //remove last character if it is slash
                    suiteFilter = suiteFilter[0..-2]
                }
                def testngFolder = suiteFilter.substring(suiteFilter.lastIndexOf("/"), suiteFilter.length()) + "/"
                logger.info("testngFolder: " + testngFolder)

                // find all tetsng suite xml files and launch dsl creator scripts (views, folders, jobs etc)
                def suites = context.findFiles(glob: subProjectFilter + "/" + suiteFilter + "/**")
                for (File suite : suites) {
                    if (!suite.path.endsWith(".xml") || !suite.path.contains(suiteName + ".xml")) {
                        continue
                    }
                    logger.info("suite: " + suite.path)
                    try {
                        def suitePath = getWorkspace() + "/" + suite.path
                        initRun(suitePath, suiteName, repo, organization, sub_project, zafira_project)
                    } catch (FileNotFoundException e) {
                        logger.error("ERROR! Unable to find suite: " + suite.path)
                        logger.error(Utils.printStackTrace(e))
                    } catch (Exception e) {
                        logger.error("ERROR! Unable to parse suite: " + suite.path)
                        logger.error(Utils.printStackTrace(e))
                    }
                }
            }
        }
    }

    def initRun(suitePath, suiteName, repo, organization, sub_project, zafira_project) {
        logger.info("Extracting parameters from xml suite...")
        def xmlFile = new Parser(suitePath)
        xmlFile.setLoadClasses(false)

        List<XmlSuite> suiteXml = xmlFile.parseToList()
        XmlSuite currentSuite = suiteXml.get(0)

        def env = currentSuite.getParameter("jenkinsEnvironments")
        if (env != null) {
            Configuration.set("env", env)
        } else {
            Configuration.set("env", "DEMO")
        }

        Configuration.set("fork", "false")
        Configuration.set("debug", "false")

        def defaultMobilePool = currentSuite.getParameter("jenkinsMobileDefaultPool")
        if (defaultMobilePool == null) {
            defaultMobilePool = "ANY"
        }

        def autoScreenshot = false
        if (currentSuite.getParameter("jenkinsAutoScreenshot") != null) {
            autoScreenshot = currentSuite.getParameter("jenkinsAutoScreenshot").toBoolean()
        }

        def enableVideo = true
        if (currentSuite.getParameter("jenkinsEnableVideo") != null) {
            enableVideo = currentSuite.getParameter("jenkinsEnableVideo").toBoolean()
        }

        def jobType = suiteName
        if (currentSuite.getParameter("jenkinsJobType") != null) {
            jobType = currentSuite.getParameter("jenkinsJobType")
        }
        logger.info("JobType: ${jobType}")
        switch(jobType.toLowerCase()) {
            case ~/^(?!.*web).*api.*$/:
                // API tests specific
                Configuration.set("platform", "API")
                break
            case ~/^.*web.*$/:
            case ~/^.*gui.*$/:
                // WEB tests specific
                //TODO: extract castom capabilities from global choice and not to add ef it is NULL
                Configuration.set("custom_capabilities", "NULL")
                def browser = 'chrome'
                if (currentSuite.getParameter("jenkinsDefaultBrowser") != null) {
                    browser = currentSuite.getParameter("jenkinsDefaultBrowser")
                }
                Configuration.set("browser", browser)
                Configuration.set("browser_version", "*")
                Configuration.set("os", 'NULL')
                Configuration.set("os_version", "*")
                Configuration.set("auto_screenshot", autoScreenshot)
                Configuration.set("enableVideo", enableVideo)
                Configuration.set("platform", "*")
                break
//            case ~/^.*android.*$/:
//                choiceParam('devicePool', getDevices('ANDROID'), "Select the Device a Test will run against.  ALL - Any available device, PHONE - Any available phone, TABLET - Any tablet")
//                //TODO: Check private repositories for parameter use and fix possible problems using custom pipeline
//                //stringParam('build', '.*', ".* - use fresh build artifact from S3 or local storage;\n2.2.0.3741.45 - exact version you would like to use")
//                booleanParam('recoveryMode', false, 'Restart application between retries')
//                booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
//                booleanParam('enableVideo', enableVideo, 'Enable video recording')
//                configure addHiddenParameter('DefaultPool', '', defaultMobilePool)
//                configure addHiddenParameter('platform', '', 'ANDROID')
//                break
//            case ~/^.*ios.*$/:
//                //TODO:  Need to adjust this for virtual as well.
//                choiceParam('devicePool', getDevices('iOS'), "Select the Device a Test will run against.  ALL - Any available device, PHONE - Any available phone, TABLET - Any tablet")
//                //TODO: Check private repositories for parameter use and fix possible problems using custom pipeline
//                //stringParam('build', '.*', ".* - use fresh build artifact from S3 or local storage;\n2.2.0.3741.45 - exact version you would like to use")
//                booleanParam('recoveryMode', false, 'Restart application between retries')
//                //TODO: hardcode auto_screenshots=true for iOS until we fix video recording
//                booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
//                //TODO: enable video as only issue with Appiym and xrecord utility is fixed
//                booleanParam('enableVideo', enableVideo, 'Enable video recording')
//                configure addHiddenParameter('DefaultPool', '', defaultMobilePool)
//                configure addHiddenParameter('platform', '', 'iOS')
//                break
            default:
                Configuration.set("auto_screenshot", "false")
                Configuration.set("platform", "*")
                break
        }

        def nodeLabel
        if (currentSuite.toXml().contains("jenkinsNodeLabel")) {
            nodeLabel = currentSuite.getParameter("jenkinsNodeLabel")
            Configuration.set("node_label", nodeLabel)
        }

        def gitBranch = "master"
        if (currentSuite.getParameter("jenkinsDefaultGitBranch") != null) {
            gitBranch = currentSuite.getParameter("jenkinsDefaultGitBranch")
        }
        Configuration.set("branch", gitBranch)
        Configuration.set("repo", repo)
        Configuration.set("GITHUB_ORGANIZATION", organization)
        Configuration.set("sub_project", sub_project)
        Configuration.set("zafira_project", zafira_project)
        Configuration.set("suite", suiteName)
        Configuration.set("ci_parent_url", '')
        Configuration.set("ci_parent_build", '')
        Configuration.set("ci_run_id", randomUUID())
        Configuration.set("BuildPriority", "3")

        def queue_registration = "true"
        if (currentSuite.getParameter("jenkinsQueueRegistration") != null) {
            queue_registration = currentSuite.getParameter("jenkinsQueueRegistration")
        }

        Configuration.set("queue_registration", queue_registration)

        def threadCount = '1'
        if (currentSuite.toXml().contains("jenkinsDefaultThreadCount")) {
            threadCount = currentSuite.getParameter("jenkinsDefaultThreadCount")
        }

        Configuration.set("thread_count", threadCount)
        Configuration.set("email_list", currentSuite.getParameter("jenkinsEmail").toString())

        if (currentSuite.toXml().contains("jenkinsFailedEmail")) {
            Configuration.set("failure_email_list", currentSuite.getParameter("jenkinsFailedEmail").toString())
        } else {
            Configuration.set("failure_email_list", '')
        }

        def retryCount = 0
        if (currentSuite.getParameter("jenkinsDefaultRetryCount") != null) {
            retryCount = currentSuite.getParameter("jenkinsDefaultRetryCount").toInteger()
        }

        Configuration.set("retry_count", retryCount)

//        if (retryCount != 0) {
//            Configuration.set("retry_count", 0)
//            choiceParam('retry_count', [retryCount, 0, 1, 2, 3], 'Number of Times to Retry a Failed Test')
//        } else {
//            choiceParam('retry_count', [0, 1, 2, 3], 'Number of Times to Retry a Failed Test')
//        }

        Configuration.set("rerun_failures", "false")
//        def customFields = getCustomFields(currentSuite)
//        Configuration.set("overrideFields", customFields)

//        def paramsMap = currentSuite.getAllParameters()
//        logger.info("ParametersMap: ${paramsMap}")
//        for (param in paramsMap) {
//            logger.debug("Parameter: ${param}")
//            def delimiter = "::"
//            if (param.key.contains(delimiter)) {
//                def (type, name, desc) = param.key.split(delimiter)
//                switch(type.toLowerCase()) {
//                    case "hiddenparam":
//                        configure addHiddenParameter(name, desc, param.value)
//                        break
//                    case "stringparam":
//                        stringParam(name, param.value, desc)
//                        break
//                    case "choiceparam":
//                        choiceParam(name, Arrays.asList(param.value.split(',')), desc)
//                        break
//                    case "booleanparam":
//                        booleanParam(name, param.value.toBoolean(), desc)
//                        break
//                    default:
//                        break
//                }
//            }
//        }
    }

    protected String getCustomFields(currentSuite) {
        def overrideFields = getGenericSplit(currentSuite, "overrideFields")
        def prepCustomFields = ""

        if (!overrideFields.isEmpty()) {
            for (String customField : overrideFields) {
                prepCustomFields = prepCustomFields + " -D" + customField
            }
        }

        return prepCustomFields
    }

    protected def getDevices(String platform) {
        def proxyInfo = new ProxyInfo(_dslFactory)
        return proxyInfo.getDevicesList(platform)
    }

    protected String listToString(currentSuite, parameterName) {
        def list = getGenericSplit(currentSuite, parameterName)
        def prepList = 'return ['

        if (!list.isEmpty()) {
            for (String l : list) {
                prepList = prepList + '"' + l + '", '
            }
            prepList = prepList.take(prepList.length() - 2)
        }

        prepList = prepList + ']'

        return prepList
    }
    protected def getProjectPomFiles() {
        def pomFiles = []
        def files = context.findFiles(glob: "**/pom.xml")

        if(files.length > 0) {
            logger.info("Number of pom.xml files to analyze: " + files.length)

            int curLevel = 5 //do not analyze projects where highest pom.xml level is lower or equal 5
            for (int i = 0; i < files.length; i++) {
                def path = files[i].path
                int level = path.count("/")
                logger.debug("file: " + path + "; level: " + level + "; curLevel: " + curLevel)
                if (level < curLevel) {
                    curLevel = level
                    pomFiles.clear()
                    pomFiles.add(files[i].path)
                } else if (level == curLevel) {
                    pomFiles.add(files[i].path)
                }
            }
        }
        logger.info(pomFiles.dump())
        return pomFiles
    }

    protected String getWorkspace() {
        return context.pwd()
    }

    protected clean() {
        context.stage('Wipe out Workspace') {
            context.deleteDir()
        }
    }
}
