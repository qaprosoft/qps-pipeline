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
                        configure stringParam('capabilities', getSuiteParameter("platformName=API", "capabilities", currentSuite), 'Reserved for any semicolon separated W3C driver capabilities.')
                        break
                    case "web":
                        // WEB tests specific
                        configure stringParam('capabilities', getSuiteParameter("browserName=chrome", "capabilities", currentSuite), 'Provide semicolon separated W3C driver capabilities.')
                        configure addExtensibleChoice('custom_capabilities', 'gc_CUSTOM_CAPABILITIES', "Set to NULL to run against Selenium Grid on Jenkin's Slave else, select an option for Browserstack.", 'NULL')
                        booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
                        booleanParam('enableVideo', enableVideo, 'Enable video recording')
                        break
                    case "android":
                        booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
                        booleanParam('enableVideo', enableVideo, 'Enable video recording')
                        configure stringParam('capabilities', getSuiteParameter("platformName=ANDROID;deviceName=" + defaultMobilePool, "capabilities", currentSuite), 'Reserved for any semicolon separated W3C driver capabilities.')
                        break
                    case "android-tv":
                        booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
                        booleanParam('enableVideo', enableVideo, 'Enable video recording')
                        configure stringParam('capabilities', getSuiteParameter("platformName=ANDROID;deviceName=" + defaultMobilePool, "capabilities", currentSuite), 'Reserved for any semicolon separated W3C driver capabilities.')
                        break
                    case "android-web":
                        booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
                        booleanParam('enableVideo', enableVideo, 'Enable video recording')
                        configure stringParam('capabilities', getSuiteParameter("platformName=ANDROID;browserName=chrome;deviceName=" + defaultMobilePool, "capabilities", currentSuite), 'Reserved for any semicolon separated W3C driver capabilities.')
                        break
                    case "ios":
                        booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
                        booleanParam('enableVideo', enableVideo, 'Enable video recording')
                        configure stringParam('capabilities', getSuiteParameter("platformName=iOS;deviceName=" + defaultMobilePool, "capabilities", currentSuite), 'Reserved for any semicolon separated W3C driver capabilities.')
                        break
                    case "ios-web":
                        booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
                        booleanParam('enableVideo', enableVideo, 'Enable video recording')
                        configure stringParam('capabilities', getSuiteParameter("platformName=iOS;browserName=safari;deviceName=" + defaultMobilePool, "capabilities", currentSuite), 'Reserved for any semicolon separated W3C driver capabilities.')
                        break
                // web ios: capabilities: browserName=safari, deviceName=ANY
                // web android: capabilities: browserName=chrome, deviceName=ANY
                    default:
                        configure stringParam('capabilities', getSuiteParameter("platformName=*", "capabilities", currentSuite), 'Reserved for any semicolon separated W3C driver capabilities.')
                        booleanParam('auto_screenshot', false, 'Generate screenshots automatically during the test')

                        break
                }
                configure addHiddenParameter('job_type', '', jobType)

                def hubProvider = getSuiteParameter("", "provider", currentSuite)
                if (!isParamEmpty(hubProvider)) {
                    configure addHiddenParameter('capabilities.provider', 'hub provider name', hubProvider)
                }

                def nodeLabel = getSuiteParameter("", "jenkinsNodeLabel", currentSuite)
                if (!isParamEmpty(nodeLabel)) {
                    configure addHiddenParameter('node_label', 'customized node label', nodeLabel)
                }
                configure stringParam('branch', this.branch, "SCM repository branch to run against")
                configure addHiddenParameter('repo', '', repo)
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                configure addHiddenParameter('sub_project', '', sub_project)
                configure addHiddenParameter('zafira_project', '', zafira_project)
                configure addHiddenParameter('suite', '', suiteName)
                configure addHiddenParameter('ci_parent_url', '', '')
                configure addHiddenParameter('ci_parent_build', '', '')
                configure addHiddenParameter('slack_channels', '', getSuiteParameter("", "jenkinsSlackChannels", currentSuite))
                configure addHiddenParameter('failure_slack_channels', '', getSuiteParameter("", "jenkinsFailedSlackChannels", currentSuite))
                configure addExtensibleChoice('ci_run_id', '', 'import static java.util.UUID.randomUUID\nreturn [randomUUID()]')
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")
                configure addHiddenParameter('queue_registration', '', getSuiteParameter("true", "jenkinsQueueRegistration", currentSuite))
                // TODO: #711 completely remove custom jenkinsDefaultThreadCount parameter logic
                stringParam('thread_count', getSuiteParameter(this.threadCount, "jenkinsDefaultThreadCount", currentSuite), 'number of threads, number')
                if (!"1".equals(this.dataProviderThreadCount)) {
                    stringParam('data_provider_thread_count', this.dataProviderThreadCount, 'number of threads for data provider, number')
                }
                stringParam('email_list', getSuiteParameter("", "jenkinsEmail", currentSuite), 'List of Users to be emailed after the test')
                configure addHiddenParameter('failure_email_list', '', getSuiteParameter("", "jenkinsFailedEmail", currentSuite))
                choiceParam('retry_count', getRetryCountArray(currentSuite), 'Number of Times to Retry a Failed Test')
                booleanParam('rerun_failures', false, 'During \"Rebuild\" pick it to execute only failed cases')
                configure addHiddenParameter('overrideFields', '', getSuiteParameter("", "overrideFields", currentSuite))
                configure addHiddenParameter('zafiraFields', '', getSuiteParameter("", "zafiraFields", currentSuite))

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
                        switch (type.toLowerCase()) {
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