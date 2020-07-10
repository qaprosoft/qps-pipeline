package com.qaprosoft.jenkins.jobdsl.factory.pipeline

@Grab('org.testng:testng:6.8.8')

import static com.qaprosoft.jenkins.Utils.*
import org.testng.xml.XmlSuite
import groovy.transform.InheritConstructors

@InheritConstructors
public class TestJobFactory extends PipelineFactory {

    def host
    def repo
    def organization
    def branch
    def sub_project
    def zafira_project
    def suitePath
    def suiteName
    def orgRepoScheduling

    def threadCount
    def dataProviderThreadCount
    Map parametersMap = new LinkedHashMap()

    public TestJobFactory(folder, pipelineScript, host, repo, organization, branch,
                          sub_project, zafira_project, suitePath, suiteName, jobDesc, orgRepoScheduling, threadCount, dataProviderThreadCount) {
        this.folder = folder
        this.description = jobDesc
        this.pipelineScript = pipelineScript
        this.host = host
        this.repo = repo
        this.organization = organization
        this.branch = branch
        this.sub_project = sub_project
        this.zafira_project = zafira_project
        this.suitePath = suitePath
        this.suiteName = suiteName
        this.orgRepoScheduling = orgRepoScheduling
        this.threadCount = threadCount
        this.dataProviderThreadCount = dataProviderThreadCount
    }

    def registerParameter(parameterName, value) {
        parametersMap.put(parameterName, value)
    }

    def setParameter(param, value) {
        // read each param and parse for generating custom project fields
        //	<parameter name="stringParam::name::desc" value="value" />
        //	<parameter name="stringParam::name" value="value" />
        logger.debug("Parameter: ${param}")
        def delimiter = "::"
        if (param.key.contains(delimiter)) {
            def (type, name, desc) = param.key.split(delimiter)
            switch (type.toLowerCase()) {
                case "hiddenparam":
                    configure addHiddenParameter(name, desc, value)
                    break
                case "stringparam":
                    stringParam(name, value, desc)
                    break
                case "choiceparam":
                    choiceParam(name, Arrays.asList(value.split(',')), desc)
                    break
                case "booleanparam":
                    booleanParam(name, value.toBoolean(), desc)
                    break
                default:
                    break
            }
        }
    }

    def create() {
        logger.info("TestJobFactory->create")

        XmlSuite currentSuite = parseSuite(suitePath)

        this.name = !isParamEmpty(currentSuite.getParameter("jenkinsJobName")) ? currentSuite.getParameter("jenkinsJobName") : currentSuite.getName()
        name = replaceSpecialSymbols(name)
        logger.info("JenkinsJobName: ${name}")

        def pipelineJob = super.create()
        pipelineJob.with {
            def maxNumberKeepBuilds = getSuiteParameter("30", "maxNumberKeepBuilds", currentSuite).toInteger()
            logRotator {
                numToKeep maxNumberKeepBuilds
            }

            //** Triggers **//*
            def scheduling = currentSuite.getParameter("scheduling")
            if (scheduling != null && orgRepoScheduling) {
                triggers {
                    cron(parseSheduling(scheduling))
                }
            }

            //** Properties & Parameters Area **//*
            parameters {
                concurrentBuild(getSuiteParameter(true, "jenkinsConcurrentBuild", currentSuite).toBoolean())
                extensibleChoiceParameterDefinition {
                    name('env')
                    choiceListProvider {
                        textareaChoiceListProvider {
                            choiceListText(getEnvironments(currentSuite))
                            defaultChoice(getDefaultChoiceValue(currentSuite))
                            addEditedValue(false)
                            whenToAdd('Triggered')
                        }
                    }
                    editable(true)
                    description('Environment to test against')
                }

                booleanParam('fork', false, "Reuse forked repository for ${repo}.")
                //booleanParam('debug', false, 'Check to start tests in remote debug mode.')

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
                if (currentSuite.getParameter("jenkinsJobDisabled")?.toBoolean()) {
                    disabled()
                }
                def defaultMobilePool = getSuiteParameter("ANY", "jenkinsMobileDefaultPool", currentSuite)
                def autoScreenshot = getSuiteParameter("false", "jenkinsAutoScreenshot", currentSuite).toBoolean()
                def enableVideo = getSuiteParameter("true", "jenkinsEnableVideo", currentSuite).toBoolean()

                def jobType = getSuiteParameter("api", "jenkinsJobType", currentSuite).toLowerCase()
                // TODO: add ios_web, android_web if needed
                switch (jobType) {
                    case "api":
                        // API tests specific
                        registerParameter('stringparam::capabilities::Reserved for any semicolon separated W3C driver capabilities.', getSuiteParameter("platformName=API", "capabilities", currentSuite))
                        break
                    case "web":
                        // WEB tests specific
                        registerParameter('stringparam::capabilities::Provide semicolon separated W3C driver capabilities.', getSuiteParameter("browserName=chrome", "capabilities", currentSuite))
                        configure addExtensibleChoice('custom_capabilities', 'gc_CUSTOM_CAPABILITIES', "Set to NULL to run against Selenium Grid on Jenkin's Slave else, select an option for Browserstack.", 'NULL')
                        registerParameter('booleanparam::auto_screenshot::Generate screenshots automatically during the test', autoScreenshot)
                        registerParameter('booleanparam::enableVideo::Enable video recording', enableVideo)
                        break
                    case "android":
                        registerParameter('booleanparam::auto_screenshot::Generate screenshots automatically during the test', autoScreenshot)
                        registerParameter('booleanparam::enableVideo::Enable video recording', enableVideo)
                        registerParameter('stringparam::capabilities::Reserved for any semicolon separated W3C driver capabilities.', getSuiteParameter("platformName=ANDROID;deviceName=" + defaultMobilePool, "capabilities", currentSuite))
                        break
                    case "android-web":
                        registerParameter('booleanparam::auto_screenshot::Generate screenshots automatically during the test', autoScreenshot)
                        registerParameter('booleanparam::enableVideo::Enable video recording', enableVideo)
                        registerParameter('stringparam::capabilities::Reserved for any semicolon separated W3C driver capabilities.', getSuiteParameter("platformName=ANDROID;browserName=chrome;deviceName=" + defaultMobilePool, "capabilities", currentSuite))
                        break
                    case "ios":
                        registerParameter('booleanparam::auto_screenshot::Generate screenshots automatically during the test', autoScreenshot)
                        registerParameter('booleanparam::enableVideo::Enable video recording', enableVideo)
                        registerParameter('stringparam::capabilities::Reserved for any semicolon separated W3C driver capabilities.', getSuiteParameter("platformName=iOS;deviceName=" + defaultMobilePool, "capabilities", currentSuite))
                        break
                    case "ios-web":
                        registerParameter('booleanparam::auto_screenshot::Generate screenshots automatically during the test', autoScreenshot)
                        registerParameter('booleanparam::enableVideo::Enable video recording', enableVideo)
                        registerParameter('stringparam::capabilities::Reserved for any semicolon separated W3C driver capabilities.', getSuiteParameter("platformName=iOS;browserName=safari;deviceName=" + defaultMobilePool, "capabilities", currentSuite))
                        break
                // web ios: capabilities: browserName=safari, deviceName=ANY
                // web android: capabilities: browserName=chrome, deviceName=ANY
                    default:
                        registerParameter('stringparam::capabilities::Reserved for any semicolon separated W3C driver capabilities.', getSuiteParameter("platformName=*", "capabilities", currentSuite))
                        registerParameter('booleanparam::auto_screenshot::Generate screenshots automatically during the test', false)
                        break
                }

                registerParameter('hiddenparam::job_type::', jobType)

                def hubProvider = getSuiteParameter("", "provider", currentSuite)
                if (!isParamEmpty(hubProvider)) {
                    registerParameter('hiddenparam::capabilities.provider::hub provider name', hubProvider)
                }

                def nodeLabel = getSuiteParameter("", "jenkinsNodeLabel", currentSuite)
                if (!isParamEmpty(nodeLabel)) {
                    registerParameter('hiddenparam::node_label::customized node label', nodeLabel)
                }
                registerParameter('stringparam::branch::SCM repository branch to run against', this.branch)
                registerParameter('hiddenparam::repo::', repo)
                registerParameter('hiddenparam::GITHUB_HOST::', host)
                registerParameter('hiddenparam::GITHUB_ORGANIZATION::', organization)
                registerParameter('hiddenparam::sub_project::', sub_project)
                registerParameter('hiddenparam::zafira_project::', zafira_project)
                registerParameter('hiddenparam::suite::', suiteName)
                registerParameter('hiddenparam::ci_parent_url::', '')
                registerParameter('hiddenparam::ci_parent_build::', '')
                registerParameter('hiddenparam::slack_channels::', getSuiteParameter("", "jenkinsSlackChannels", currentSuite))
                configure addExtensibleChoice('ci_run_id', '', 'import static java.util.UUID.randomUUID\nreturn [randomUUID()]')
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")
                registerParameter('hiddenparam::queue_registration::', getSuiteParameter("true", "jenkinsQueueRegistration", currentSuite))
                // TODO: #711 completely remove custom jenkinsDefaultThreadCount parameter logic
                registerParameter('stringparam::thread_count::number of threads, number', getSuiteParameter(this.threadCount, "jenkinsDefaultThreadCount", currentSuite))
                if (!"1".equals(this.dataProviderThreadCount)) {
                    registerParameter('stringparam::data_provider_thread_count::number of threads for data provider, number', this.dataProviderThreadCount)
                }
                registerParameter('stringparam::email_list::List of Users to be emailed after the test', getSuiteParameter("", "jenkinsEmail", currentSuite))
                registerParameter('hiddenparam::failure_email_list::', getSuiteParameter("", "jenkinsFailedEmail", currentSuite))
                registerParameter('choiceparam::retry_count::Number of Times to Retry a Failed Test', getRetryCountArray(currentSuite))
                registerParameter('booleanparam::rerun_failures::During "Rebuild" pick it to execute only failed cases', false)
                registerParameter('hiddenparam::overrideFields::', getSuiteParameter("", "overrideFields", currentSuite))
                registerParameter('hiddenparam::zafiraFields::', getSuiteParameter("", "zafiraFields", currentSuite))

                //set necessary parameters
                for (key in parametersMap) {
                    logger.info("KEY_VAL: ${key}, ${parametersMap.get(key)}")
                    setParameter(key, parametersMap.get(key))
                }

                //set parameters from suite
                Map paramsMap = currentSuite.getAllParameters()
                logger.info("ParametersMap: ${paramsMap}")
                for (param in paramsMap) {
                    set_parameter(param)
                }
            }
        }
        return pipelineJob
    }

    protected def getRetryCountArray(currentSuite) {
        def retryCount = getSuiteParameter(0, "jenkinsDefaultRetryCount", currentSuite).toInteger()
        List retryCountList = new ArrayList(Arrays.asList(0, 1, 2, 3))
        if (retryCount != 0) {
            retryCountList.add(0, retryCount)
        }
        return retryCountList
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