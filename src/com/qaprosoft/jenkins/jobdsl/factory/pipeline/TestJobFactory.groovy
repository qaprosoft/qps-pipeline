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

        //TODO: refactor to remove below code line
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
                //TODO: move param to map
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
                        addParam('capabilities', new JobParam('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=API", "capabilities", currentSuite)))
                        break
                    case "web":
                        // WEB tests specific
                        addParam('capabilities', new JobParam('stringparam', 'Provide semicolon separated W3C driver capabilities', getSuiteParameter("browserName=chrome", "capabilities", currentSuite)))
                        addParam('auto_screenshot', new JobParam('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        addParam('enableVideo', new JobParam('booleanparam', 'Enable video recording', enableVideo))
                        break
                    case "android":
                        addParam('auto_screenshot', new JobParam('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        addParam('enableVideo', new JobParam('booleanparam', 'Enable video recording', enableVideo))
                        addParam('capabilities', new JobParam('stringparam', 'Reserved for any semicolon separated W3C driver capabilities.', getSuiteParameter("platformName=ANDROID;deviceName=" + defaultMobilePool, "capabilities", currentSuite)))
                        break
                    case "android-tv":
                        addParam('auto_screenshot', new JobParam('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        addParam('enableVideo', new JobParam('booleanparam', 'Enable video recording', enableVideo))
                        addParam('capabilities', new JobParam('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=ANDROID;deviceName=" + defaultMobilePool, "capabilities", currentSuite)))
                        break
                    case "android-web":
                        addParam('auto_screenshot', new JobParam('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        addParam('enableVideo', new JobParam('booleanparam', 'Enable video recording', enableVideo))
                        addParam('capabilities', new JobParam('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=ANDROID;browserName=chrome;deviceName=" + defaultMobilePool, "capabilities", currentSuite)))
                        break
                    case "ios":
                        addParam('auto_screenshot', new JobParam('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        addParam('enableVideo', new JobParam('booleanparam', 'Enable video recording', enableVideo))
                        addParam('capabilities', new JobParam('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=iOS;deviceName=" + defaultMobilePool, "capabilities", currentSuite)))
                        break
                    case "ios-web":
                        addParam('auto_screenshot', new JobParam('booleanparam', 'Generate screenshots automatically during the test', autoScreenshot))
                        addParam('enableVideo', new JobParam('booleanparam', 'Enable video recording', enableVideo))
                        addParam('capabilities', new JobParam('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=iOS;browserName=safari;deviceName=" + defaultMobilePool, "capabilities", currentSuite)))
                        break
                // web ios: capabilities: browserName=safari, deviceName=ANY
                // web android: capabilities: browserName=chrome, deviceName=ANY
                    default:
                        addParam('capabilities', new JobParam('stringparam', 'Reserved for any semicolon separated W3C driver capabilities', getSuiteParameter("platformName=*", "capabilities", currentSuite)))
                        addParam('auto_screenshot', new JobParam('booleanparam', 'Generate screenshots automatically during the test', false))
                        break
                }

                addParam('job_type', new JobParam('hiddenparam', "", jobType))

                def hubProvider = getSuiteParameter("", "provider", currentSuite)
                if (!isParamEmpty(hubProvider)) {
                    addParam('capabilities.provider', new JobParam('hiddenparam', 'hub provider name', hubProvider))
                }

                def nodeLabel = getSuiteParameter("", "jenkinsNodeLabel", currentSuite)
                if (!isParamEmpty(nodeLabel)) {
                    addParam('node_label', new JobParam('hiddenparam', 'customized node label', nodeLabel))
                }
                addParam('branch', new JobParam('stringparam', 'SCM repository branch to run against', this.branch))
                addParam('repo', new JobParam('hiddenparam', '', repo))
                addParam('GITHUB_HOST', new JobParam('hiddenparam', '', host))
                addParam('GITHUB_ORGANIZATION', new JobParam('hiddenparam', '', organization))
                addParam('sub_project', new JobParam('hiddenparam', '', sub_project))
                addParam('zafira_project', new JobParam('hiddenparam', '', zafira_project))
                addParam('suite', new JobParam('hiddenparam', '', suiteName))
                addParam('slack_channels', new JobParam('hiddenparam', '', getSuiteParameter("", "jenkinsSlackChannels", currentSuite)))
                addParam('ci_run_id', new JobParam('extensiblechoiceparam', '', 'import static java.util.UUID.randomUUID\nreturn [randomUUID()]'))
                addParam('BuildPriority', new JobParam('extensiblechoiceparam', "Priority of execution. Lower number means higher priority", "3", "gc_BUILD_PRIORITY"))
                addParam('queue_registration', new JobParam('hiddenparam', '', getSuiteParameter("true", "jenkinsQueueRegistration", currentSuite)))
                // TODO: #711 completely remove custom jenkinsDefaultThreadCount parameter logic
                addParam('thread_count', new JobParam('stringparam', 'number of threads, number', getSuiteParameter(this.threadCount, "jenkinsDefaultThreadCount", currentSuite)))
                if (!"1".equals(this.dataProviderThreadCount)) {
                    addParam('data_provider_thread_count', new JobParam('stringparam', 'number of threads for data provider, number', this.dataProviderThreadCount))
                }
                addParam('email_list', new JobParam('stringparam', 'List of Users to be emailed after the test', getSuiteParameter("", "jenkinsEmail", currentSuite)))
                addParam('failure_email_list', new JobParam('hiddenparam', '', getSuiteParameter("", "jenkinsFailedEmail", currentSuite)))
                addParam('retry_count', new JobParam('choiceparam', 'Number of Times to Retry a Failed Test', getRetryCountArray(currentSuite)))
                addParam('rerun_failures', new JobParam('booleanparam', 'During "Rebuild" pick it to execute only failed cases', false))
                addParam('overrideFields', new JobParam('hiddenparam', '', getSuiteParameter("", "overrideFields", currentSuite)))
                addParam('zafiraFields', new JobParam('hiddenparam', '', getSuiteParameter("", "zafiraFields", currentSuite)))

                //set parameters to map from suite
                parseSuiteParametersToMap(currentSuite)

                logger.info("ParametersMap: ${parametersMap}")
                for (name in parametersMap.keySet()) {
                    def paramContent = parametersMap.get(name)
                    if (paramContent != null) {
                        logger.info('111111 ' + name)
                        def type = paramContent.getType()
                        def desc = paramContent.getDesc()
                        def value = paramContent.getValue()
                        if (value != null) {
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
                                case "extensiblechoiceparam":
                                    if (isParamEmpty(paramContent.getGlobalName())) {
                                        configure addExtensibleChoice(name, desc, value)
                                    } else {
                                        configure addExtensibleChoice(name, paramContent.getGlobalName(), desc, value)
                                    }
                                    break
                                default:
                                    break
                                }
                            }
                        }
                    }
                }
            }
        return pipelineJob
    }

    def parseSuiteParametersToMap(currentSuite) {
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
                addParam(name, new JobParam(type.toLowerCase(), desc, name))
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