package com.qaprosoft.jenkins.jobdsl.factory.pipeline

@Grab('org.testng:testng:6.8.8')

import static com.qaprosoft.jenkins.Utils.*
import org.testng.xml.XmlSuite
import com.qaprosoft.jenkins.jobdsl.selenium.grid.ProxyInfo
import groovy.transform.InheritConstructors

@InheritConstructors
public class TestJobFactory extends PipelineFactory {

    def host
    def repo
    def organization
    def sub_project
    def zafira_project
    def suitePath
    def suiteName

    public TestJobFactory(folder, pipelineScript, host, repo, organization, sub_project, zafira_project, suitePath, suiteName, jobDesc) {
        this.folder = folder
        this.description = jobDesc
        this.pipelineScript = pipelineScript
        this.host = host
        this.repo = repo
        this.organization = organization
        this.sub_project = sub_project
        this.zafira_project = zafira_project
        this.suitePath = suitePath
        this.suiteName = suiteName
    }

    def create() {
        logger.info("TestJobFactory->create")

        XmlSuite currentSuite = parseSuite(suitePath)

        this.name = currentSuite.getParameter("jenkinsJobName")
        logger.info("JenkinsJobName: ${name}")

        def pipelineJob = super.create()
        pipelineJob.with {

            //** Triggers **//*
            def scheduling = currentSuite.getParameter("scheduling")
            if (scheduling != null) {
                triggers {
                    cron(parseSheduling(scheduling))
                }
            }

            //** Properties & Parameters Area **//*
            parameters {
                choiceParam('env', getEnvironments(currentSuite), 'Environment to test against.')
                booleanParam('fork', false, "Reuse forked repository for ${repo} repository.")
                booleanParam('debug', false, 'Check to start tests in remote debug mode.')

                //** Requires Active Choices Plug-in v1.2+ **//*
                //** Currently renders with error: https://issues.jenkins-ci.org/browse/JENKINS-42655 **//*
                if (!isParamEmpty(currentSuite.getParameter("jenkinsGroups"))) {
                    activeChoiceParam("groups") {
                        description("Please select test group(s) to run")
                        filterable()
                        choiceType("MULTI_SELECT")
                        groovyScript {
                            script(this.listToString(currentSuite, "jenkinsGroups"))
                            fallbackScript("return ['error']")
                        }
                    }
                }
                if (currentSuite.getParameter("jenkinsJobDisabled")?.toBoolean()){
                    disabled()
                }
                def defaultMobilePool = getSuiteParameter("ANY", "jenkinsMobileDefaultPool", currentSuite)
                def autoScreenshot = getSuiteParameter("false", "jenkinsAutoScreenshot", currentSuite).toBoolean()
                def enableVideo = getSuiteParameter("true", "jenkinsEnableVideo", currentSuite).toBoolean()

                switch(getSuiteParameter(suiteName, "jenkinsJobType", currentSuite).toLowerCase()) {
                    case ~/^(?!.*web).*api.*$/:
                        // API tests specific
                        configure addHiddenParameter('platform', '', 'API')
                        break
                    case ~/^.*web.*$/:
                    case ~/^.*gui.*$/:
                        // WEB tests specific
                        configure addExtensibleChoice('custom_capabilities', 'gc_CUSTOM_CAPABILITIES', "Set to NULL to run against Selenium Grid on Jenkin's Slave else, select an option for Browserstack.", 'NULL')
                        def browser = 'chrome'
                        if (currentSuite.getParameter("jenkinsDefaultBrowser") != null) {
                            browser = currentSuite.getParameter("jenkinsDefaultBrowser")
                        }
                        configure addExtensibleChoice('browser', 'gc_BROWSER', 'Select a browser to run tests against.', browser)
                        configure addHiddenParameter('browser_version', '', '*')
                        configure addHiddenParameter('os', '', 'NULL')
                        configure addHiddenParameter('os_version', '', '*')
                        booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
                        booleanParam('enableVideo', enableVideo, 'Enable video recording')
                        configure addHiddenParameter('platform', '', '*')
                        break
                    case ~/^.*android.*$/:
                        choiceParam('devicePool', getDevices('ANDROID'), "Select the Device a Test will run against.  ANY - Any available device or exact device.")
                        if (getSuiteParameter("false", "jenkinsMobileWeb", currentSuite).toBoolean()) {
                            choiceParam('deviceBrowser', ["chrome"], "Select the mobile browser a Test will run against.")
                        }
                        //TODO: Check private repositories for parameter use and fix possible problems using custom pipeline
                        //stringParam('build', '.*', ".* - use fresh build artifact from S3 or local storage\n2.2.0.3741.45 - exact version you would like to use")
                        booleanParam('recoveryMode', false, 'Restart application between retries')
                        booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
                        booleanParam('enableVideo', enableVideo, 'Enable video recording')
                        configure addHiddenParameter('DefaultPool', '', defaultMobilePool)
                        configure addHiddenParameter('platform', '', 'ANDROID')
                        break
                    case ~/^.*ios.*$/:
                        //TODO:  Need to adjust this for virtual as well.
                        choiceParam('devicePool', getDevices('iOS'), "Select the Device a Test will run against.  ANY - Any available device or exact device.")
                        if (getSuiteParameter("false", "jenkinsMobileWeb", currentSuite).toBoolean()) {
                            choiceParam('deviceBrowser', ["safari"], "Select the mobile browser a Test will run against.")
                        }
                        //TODO: Check private repositories for parameter use and fix possible problems using custom pipeline
                        //stringParam('build', '.*', ".* - use fresh build artifact from S3 or local storage\n2.2.0.3741.45 - exact version you would like to use")
                        booleanParam('recoveryMode', false, 'Restart application between retries')
                        //TODO: hardcode auto_screenshots=true for iOS until we fix video recording
                        booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
                        //TODO: enable video as only issue with Appiym and xrecord utility is fixed
                        booleanParam('enableVideo', enableVideo, 'Enable video recording')
                        configure addHiddenParameter('DefaultPool', '', defaultMobilePool)
                        configure addHiddenParameter('platform', '', 'iOS')
                        break
                    default:
                        booleanParam('auto_screenshot', false, 'Generate screenshots automatically during the test')
                        configure addHiddenParameter('platform', '', '*')
                        break
                }

                def nodeLabel = getSuiteParameter("", "jenkinsNodeLabel", currentSuite)
                if (!isParamEmpty(nodeLabel)){
                    configure addHiddenParameter('node_label', 'customized node label', nodeLabel)
                }

                configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", getSuiteParameter("master", "jenkinsDefaultGitBranch", currentSuite))
                configure addHiddenParameter('repo', '', repo)
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                configure addHiddenParameter('sub_project', '', sub_project)
                configure addHiddenParameter('zafira_project', '', zafira_project)
                configure addHiddenParameter('suite', '', suiteName)
                configure addHiddenParameter('ci_parent_url', '', '')
                configure addHiddenParameter('ci_parent_build', '', '')
                configure addHiddenParameter('slack_channels', '', getSuiteParameter("", "jenkinsSlackChannels", currentSuite))
                configure addExtensibleChoice('ci_run_id', '', 'import static java.util.UUID.randomUUID\nreturn [randomUUID()]')
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")
                configure addHiddenParameter('queue_registration', '', getSuiteParameter("true", "jenkinsQueueRegistration", currentSuite))
                stringParam('thread_count', getSuiteParameter("1", "jenkinsDefaultThreadCount", currentSuite), 'number of threads, number')
                stringParam('email_list',  getSuiteParameter("", "jenkinsEmail", currentSuite), 'List of Users to be emailed after the test')
                configure addHiddenParameter('failure_email_list', '', getSuiteParameter("", "jenkinsFailedEmail", currentSuite))
                choiceParam('retry_count', getRetryCountArray(currentSuite), 'Number of Times to Retry a Failed Test')
                booleanParam('rerun_failures', false, 'During \"Rebuild\" pick it to execute only failed cases')
                stringParam('test_run_rules', '', 'Ex. PRIORITY=>P1&&P2;;OWNER=>user;;\nIf not empty, adding queued tests will be disabled.')
                configure addHiddenParameter('overrideFields', '' , getSuiteParameter("", "overrideFields", currentSuite))

                Map paramsMap = currentSuite.getAllParameters()
                logger.info("ParametersMap: ${paramsMap}")
                for (param in paramsMap) {
                    // read each param and parse for generating custom project fields
                    //	<parameter name="stringParam::name::desc" value="value" />
                    //	<parameter name="stringParam::name" value="value" />
                    logger.debug("Parameter: ${param}")
                    def delimiter = "::"
                    if (param.key.contains(delimiter)) {
                        def (type, name, desc) = param.key.split(delimiter)
                        switch(type.toLowerCase()) {
                            case "hiddenparam":
                                configure addHiddenParameter(name, desc, param.value)
                                break
                            case "stringparam":
                                stringParam(name, param.value, desc)
                                break
                            case "choiceparam":
                                choiceParam(name, Arrays.asList(param.value.split(',')), desc)
                                break
                            case "booleanparam":
                                booleanParam(name, param.value.toBoolean(), desc)
                                break
                            default:
                                break
                        }
                    }
                }
            }
        }
        return pipelineJob
    }

    protected def parseSheduling(scheduling){
        if (scheduling.contains("::")){
            def multilineArray = scheduling.split("::")
            def multilineValue = ""
            multilineArray.each { value ->
                multilineValue = multilineValue + value + "\n"
            }
            scheduling = multilineValue
        }
        return scheduling
    }

    protected def getRetryCountArray(currentSuite){
        def retryCount = getSuiteParameter(0, "jenkinsDefaultRetryCount", currentSuite).toInteger()
        List retryCountList = new ArrayList(Arrays.asList(0, 1, 2, 3))
        if (retryCount != 0) {
            retryCountList.add(0, retryCount)
        }
        return retryCountList
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
}