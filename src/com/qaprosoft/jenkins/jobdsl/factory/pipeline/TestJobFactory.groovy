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

    def jobParameter = new JobParameter()

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

    def create() {
        logger.info("TestJobFactory->create")

        setDefaultParams()
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

                // generateParams

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
                        setParameterToMap('capabilities', jobParameter.set('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=API", "capabilities", currentSuite)))
                        break
                    case "web":
                        // WEB tests specific
                        setParameterToMap('capabilities', jobParameter.set('stringparam', 'Provide semicolon separated W3C driver capabilities', getSuiteParameter("browserName=chrome", "capabilities", currentSuite)))
                        configure addExtensibleChoice('custom_capabilities', 'gc_CUSTOM_CAPABILITIES', "Set to NULL to run against Selenium Grid on Jenkin's Slave else, select an option for Browserstack.", 'NULL')
                        setParameterToMap('auto_screenshot', jobParameter.set('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        setParameterToMap('enableVideo', jobParameter.set('booleanparam', 'Enable video recording', enableVideo))
                        break
                    case "android":
                        setParameterToMap('auto_screenshot', jobParameter.set('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        setParameterToMap('enableVideo', jobParameter.set('booleanparam', 'Enable video recording', enableVideo))
                        setParameterToMap('capabilities', jobParameter.set('stringparam', 'Reserved for any semicolon separated W3C driver capabilities.', getSuiteParameter("platformName=ANDROID;deviceName=" + defaultMobilePool, "capabilities", currentSuite)))
                        break
                    case "android-tv":
                        setParameterToMap('auto_screenshot', jobParameter.set('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        setParameterToMap('enableVideo', jobParameter.set('booleanparam', 'Enable video recording', enableVideo))
                        setParameterToMap('capabilities', jobParameter.set('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=ANDROID;deviceName=" + defaultMobilePool, "capabilities", currentSuite)))
                        break
                    case "android-web":
                        setParameterToMap('auto_screenshot', jobParameter.set('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        setParameterToMap('enableVideo', jobParameter.set('booleanparam', 'Enable video recording', enableVideo))
                        setParameterToMap('capabilities', jobParameter.set('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=ANDROID;browserName=chrome;deviceName=" + defaultMobilePool, "capabilities", currentSuite)))
                        break
                    case "ios":
                        setParameterToMap('auto_screenshot', jobParameter.set('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        setParameterToMap('enableVideo', jobParameter.set('booleanparam', 'Enable video recording', enableVideo))
                        setParameterToMap('capabilities', jobParameter.set('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=iOS;deviceName=" + defaultMobilePool, "capabilities", currentSuite)))
                        break
                    case "ios-web":
                        setParameterToMap('auto_screenshot', jobParameter.set('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        setParameterToMap('enableVideo', jobParameter.set('booleanparam', 'Enable video recording', enableVideo))
                        setParameterToMap('capabilities', jobParameter.set('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=iOS;browserName=safari;deviceName=" + defaultMobilePool, "capabilities", currentSuite)))
                        break
                // web ios: capabilities: browserName=safari, deviceName=ANY
                // web android: capabilities: browserName=chrome, deviceName=ANY
                    default:
                        setParameterToMap('capabilities', jobParameter.set('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=*", "capabilities", currentSuite)))
                        setParameterToMap('auto_screenshot', jobParameter.set('booleanparam', 'Generate screenshots automatically during the test', false))
                        break
                }

                setParameterToMap('job_type', jobParameter.set('hiddenparam', "", jobType))

                def hubProvider = getSuiteParameter("", "provider", currentSuite)
                if (!isParamEmpty(hubProvider)) {
                    setParameterToMap('capabilities.provider', jobParameter.set('hiddenparam', 'hub provider name', hubProvider))
                }

                def nodeLabel = getSuiteParameter("", "jenkinsNodeLabel", currentSuite)
                if (!isParamEmpty(nodeLabel)) {
                    setParameterToMap('node_label', jobParameter.set('hiddenparam', 'customized node label', nodeLabel))
                }
                setParameterToMap('branch', jobParameter.set('stringparam', 'SCM repository branch to run against', this.branch))
                setParameterToMap('repo', jobParameter.set('hiddenparam', '', repo))
                setParameterToMap('GITHUB_HOST', jobParameter.set('hiddenparam', '', host))
                setParameterToMap('GITHUB_ORGANIZATION', jobParameter.set('hiddenparam', '', organization))
                setParameterToMap('sub_project', jobParameter.set('hiddenparam', '', sub_project))
                setParameterToMap('zafira_project', jobParameter.set('hiddenparam', '', zafira_project))
                setParameterToMap('suite', jobParameter.set('hiddenparam', '', suiteName))
                setParameterToMap('ci_parent_url', jobParameter.set('hiddenparam', '', ''))
                setParameterToMap('ci_parent_build', jobParameter.set('hiddenparam', '', ''))
                setParameterToMap('slack_channels', jobParameter.set('hiddenparam', '', getSuiteParameter("", "jenkinsSlackChannels", currentSuite)))
                configure addExtensibleChoice('ci_run_id', '', 'import static java.util.UUID.randomUUID\nreturn [randomUUID()]')
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")
                setParameterToMap('queue_registration', jobParameter.set('hiddenparam', '', getSuiteParameter("true", "jenkinsQueueRegistration", currentSuite)))
                // TODO: #711 completely remove custom jenkinsDefaultThreadCount parameter logic
                setParameterToMap('thread_count', jobParameter.set('stringparam', 'number of threads, number', getSuiteParameter(this.threadCount, "jenkinsDefaultThreadCount", currentSuite)))
                if (!"1".equals(this.dataProviderThreadCount)) {
                    setParameterToMap('data_provider_thread_count', jobParameter.set('stringparam', 'number of threads for data provider, number', this.dataProviderThreadCount))
                }
                setParameterToMap('email_list', jobParameter.set('stringparam', 'List of Users to be emailed after the test', getSuiteParameter("", "jenkinsEmail", currentSuite)))
                setParameterToMap('failure_email_list', jobParameter.set('hiddenparam', '', getSuiteParameter("", "jenkinsFailedEmail", currentSuite)))
                setParameterToMap('retry_count', jobParameter.set('choiceparam', 'Number of Times to Retry a Failed Test', getRetryCountArray(currentSuite)))
                setParameterToMap('rerun_failures', jobParameter.set('booleanparam', 'During "Rebuild" pick it to execute only failed cases', false))
                setParameterToMap('overrideFields', jobParameter.set('hiddenparam', '', getSuiteParameter("", "overrideFields", currentSuite)))
                setParameterToMap('zafiraFields', jobParameter.set('hiddenparam', '', getSuiteParameter("", "zafiraFields", currentSuite)))

                //set parameters to map from suite
                parseSuiteParametersToMap()

                logger.info("ParametersMap: ${parametersMap}")
                for (name in parametersMap.keySet()) {
                    def paramContent = parametersMap.get(name)
                    def type = paramContent.paramType
                    def desc = paramContent.paramDescription
                    def value = paramContent.paramValue

                    switch (type.toLowerCase()) {
                        case "hiddenparam":
                            configure addHiddenParameter(name, desc, value)
                            break
                        case "stringparam":
                            stringParam(name, value, desc)
                            break
                        case "choiceparam":
                            if (value instanceof String) {
                                choiceParam(name, Arrays.asList(value.split(',')), desc)
                            } else {
                                choiceParam(name, value, desc)
                            }
                            break
                        case "booleanparam":
                            booleanParam(name, value.toBoolean(), desc)
                            break
                        default:
                            break
                        }
                    }
                }
            }
        return pipelineJob
    }

    def setParameterToMap(parameterName, value) {
        parametersMap.put(parameterName, value)
    }

    def parseSuiteParametersToMap() {
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
                setParameterToMap(name, jobParameter.set(type.toLowerCase(), desc, name))
            }
        }
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