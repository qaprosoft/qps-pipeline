package com.qaprosoft.jenkins.repository.jobdsl

class Job {

    static void createPipeline(pipelineJob, org.testng.xml.XmlSuite currentSuite, String project, String sub_project, String suite, String suiteOwner, String zafira_project) {

        pipelineJob.with {
            description("project: ${project}; zafira_project: ${zafira_project}; owner: ${suiteOwner}")
            logRotator {
                numToKeep 100
            }

            authenticationToken('ciStart')

            /** Properties & Parameters Area **/
            parameters {
                choiceParam('env', getEnvironments(currentSuite), 'Environment to test against.')
                booleanParam('fork', false, "Reuse forked repository for ${project} project.")

                def defaultMobilePool = currentSuite.getParameter("jenkinsMobileDefaultPool")
                if (defaultMobilePool == null) {
                    defaultMobilePool = "ANY"
                }

		def jobType = suite
		if (currentSuite.getParameter("jenkinsJobType") != null) {
			jobType = currentSuite.getParameter("jenkinsJobType")
		}
                println("jobType: " + jobType)
                switch(jobType.toLowerCase()) {
                    case ~/^(?!.*web).*api.*$/:
			// API tests specific
                        configure addHiddenParameter('browser', '', 'NULL')
                        configure addHiddenParameter("keep_all_screenshots", '', 'false')
                        configure addHiddenParameter('platform', '', 'API')
                        break;
                    case ~/^.*web.*$/:
                    case ~/^.*gui.*$/:
			// WEB tests specific
                        configure addExtensibleChoice('custom_capabilities', 'gc_CUSTOM_CAPABILITIES', "Set to NULL to run against Selenium Grid on Jenkin's Slave else, select an option for Browserstack.", 'NULL')
                        configure addExtensibleChoice('browser', 'gc_BROWSER', 'Select a browser to run tests against.', 'chrome')
                        booleanParam('auto_screenshot', true, 'Generate screenshots automatically during the test')
                        booleanParam('keep_all_screenshots', true, 'Keep screenshots even if the tests pass')
                        booleanParam('enableVNC', true, 'Selenoid only to enable VNC sessions')
                        configure addHiddenParameter('platform', '', '*')
                        break;
                    case ~/^.*android.*$/:
                        choiceParam('device', getAndroidDeviceList(), "Select the Device a Test will run against.  ALL - Any available device, PHONE - Any available phone, TABLET - Any tablet")
                        stringParam('build', 'latest', "latest - use fresh build artifact from S3 or local storage;\n2.2.0.3741.45 - exact version you would like to use")
                        booleanParam('recoveryMode', false, 'Restart application between retries')
                        booleanParam('auto_screenshot', true, 'Generate screenshots automatically during the test')
                        booleanParam('keep_all_screenshots', true, 'Keep screenshots even if the tests pass')
                        configure addHiddenParameter('browser', '', 'NULL')
                        configure addHiddenParameter('DefaultPool', '', defaultMobilePool)
                        configure addHiddenParameter('platform', '', 'ANDROID')
                        break;
                    case ~/^.*ios.*$/:
                        //TODO:  Need to adjust this for virtual as well.
                        choiceParam('device', getiOSDeviceList(), "Select the Device a Test will run against.  ALL - Any available device, PHONE - Any available phone, TABLET - Any tablet")
                        stringParam('build', 'latest', "latest - use fresh build artifact from S3 or local storage;\n2.2.0.3741.45 - exact version you would like to use")
                        booleanParam('recoveryMode', false, 'Restart application between retries')
                        booleanParam('auto_screenshot', true, 'Generate screenshots automatically during the test')
                        booleanParam('keep_all_screenshots', true, 'Keep screenshots even if the tests pass')
                        configure addHiddenParameter('browser', '', 'NULL')
                        configure addHiddenParameter('DefaultPool', '', defaultMobilePool)
                        configure addHiddenParameter('platform', '', 'iOS')
                        break;
                    default:
                        booleanParam('auto_screenshot', false, 'Generate screenshots automatically during the test')
                        booleanParam('keep_all_screenshots', false, 'Keep screenshots even if the tests pass')
                        configure addHiddenParameter('browser', '', 'NULL')
                        configure addHiddenParameter('platform', '', '*')
                        break;
                }

                configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", "master")
                configure addHiddenParameter('project', '', project)
                configure addHiddenParameter('sub_project', '', sub_project)
                configure addHiddenParameter('zafira_project', '', zafira_project)
                configure addHiddenParameter('suite', '', suite)
                configure addHiddenParameter('ci_parent_url', '', '')
                configure addHiddenParameter('ci_parent_build', '', '')
                configure addExtensibleChoice('ci_run_id', '', 'import static java.util.UUID.randomUUID\nreturn [randomUUID()]')
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")

                def threadCount = '1'
                if (currentSuite.toXml().contains("jenkinsDefaultThreadCount")) {
                	threadCount = currentSuite.getParameter("jenkinsDefaultThreadCount")
                }
                stringParam('thread_count', threadCount, 'number of threads, number')


                stringParam('email_list', currentSuite.getParameter("jenkinsEmail").toString(), 'List of Users to be emailed after the test')
                if (currentSuite.toXml().contains("jenkinsFailedEmail")) {
                    configure addHiddenParameter('failure_email_list', '', currentSuite.getParameter("jenkinsFailedEmail").toString())
                } else {
                    configure addHiddenParameter('failure_email_list', '', '')
                }

                choiceParam('retry_count', [0, 1, 2, 3], 'Number of Times to Retry a Failed Test')
                booleanParam('develop', false, 'Check to execute test without registration to Zafira')
                booleanParam('rerun_failures', false, 'During \"Rebuild\" pick it to execute only failed cases')
                def customFields = getCustomFields(currentSuite)
                configure addHiddenParameter('overrideFields', '' , customFields)

		addCustomParams(currentSuite)
            }

            /** Git Stuff **/
            definition {
                cps {
                    script("@Library('QPS-Jenkins')\nimport com.qaprosoft.jenkins.repository.pipeline.RunJob;\nnew RunJob().runJob()")
                    sandbox()
                }
            }
        }
    }

    static Closure addExtensibleChoice(choiceName, globalName, desc, choice) {
        return { node ->
            node / 'properties' / 'hudson.model.ParametersDefinitionProperty' / 'parameterDefinitions' << 'jp.ikedam.jenkins.plugins.extensible__choice__parameter.ExtensibleChoiceParameterDefinition'(plugin: 'extensible-choice-parameter@1.3.3') {
                name choiceName
                description desc
                editable false
                choiceListProvider(class: 'jp.ikedam.jenkins.plugins.extensible_choice_parameter.GlobalTextareaChoiceListProvider') {
                    whenToAdd 'Triggered'
                    name globalName
                    defaultChoice choice
                }
            }
        }
    }

    static Closure addExtensibleChoice(choiceName, desc, code) {
        return { node ->
            node / 'properties' / 'hudson.model.ParametersDefinitionProperty' / 'parameterDefinitions' << 'jp.ikedam.jenkins.plugins.extensible__choice__parameter.ExtensibleChoiceParameterDefinition'(plugin: 'extensible-choice-parameter@1.3.3') {
                name choiceName
                description desc
                editable true
                choiceListProvider(class: 'jp.ikedam.jenkins.plugins.extensible_choice_parameter.SystemGroovyChoiceListProvider') {
                    groovyScript {
                        script code
                        sandbox true
                        usePrefinedVariables false
                    }
                }
            }
        }
    }

    static Closure addHiddenParameter(paramName, paramDesc, paramValue) {
        return { node ->
            node / 'properties' / 'hudson.model.ParametersDefinitionProperty' / 'parameterDefinitions' << 'com.wangyin.parameter.WHideParameterDefinition'(plugin: 'hidden-parameter@0.0.4') {
                name paramName
                description paramDesc
                defaultValue paramValue
            }
        }
    }

    static void createRegressionPipeline(pipelineJob, org.testng.xml.XmlSuite currentSuite, String project, String sub_project) {

        pipelineJob.with {

            description("project: ${project}; type: cron")
            logRotator {
                numToKeep 100
            }

            authenticationToken('ciStart')

            parameters {
                choiceParam('env', getEnvironments(currentSuite), 'Environment to test against.')
                configure addHiddenParameter('project', '', project)
                configure addHiddenParameter('sub_project', '', sub_project)
                configure addHiddenParameter('ci_parent_url', '', '')
                configure addHiddenParameter('ci_parent_build', '', '')

                configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", "master")

                stringParam('email_list', '', 'List of Users to be emailed after the test. If empty then populate from jenkinsEmail suite property')
            }
            definition {
                cps {
                    script("@Library('QPS-Jenkins')\nimport com.qaprosoft.jenkins.repository.pipeline.RunCron;\nnew RunCron().runCron()")
                    sandbox()
                }
            }
        }
    }

    static String addCustomParams(currentSuite) {
        def paramsMap = [:]
	paramsMap = currentSuite.getAllParameters()
	for (param in paramsMap) {
	    println("name: " + param.key + "; value: " + param.value)
	}
    }


    static List<String> getEnvironments(currentSuite) {
        def envList = getGenericSplit(currentSuite, "jenkinsEnvironments")

        if (envList.isEmpty()) {
            envList.add("DEMO")
            envList.add("STAG")
        }

        return envList
    }

    static String getCustomFields(currentSuite) {
        def overrideFields = getGenericSplit(currentSuite, "overrideFields")
        def prepCustomFields = ""

        if (!overrideFields.isEmpty()) {
            for (String customField : overrideFields) {
                prepCustomFields = prepCustomFields + " -D" + customField
            }
        }

        return prepCustomFields
    }

    static List<String> getGenericSplit(currentSuite, parameterName) {
        String genericField = currentSuite.getParameter(parameterName)
        def genericFields = []

        if (genericField != null) {
            if (!genericField.contains(", ")) {
                genericFields = genericField.split(",")
            } else {
                genericFields = genericField.split(", ")
            }
        }
        return genericFields
    }

    static List<String> getAndroidDeviceList(String suite) {
        def deviceList = ["DefaultPool", "ANY", "Google_Nexus_7", "Google_Pixel", "Google_Pixel_XL", "One_M8"]
        return deviceList
    }


    static List<String> getiOSDeviceList() {
        def deviceList = ["DefaultPool", "ANY", "iPhone_7_Plus", "iPhone_7", "iPhone_6S", "iPad_Air_2"]
        return deviceList
    }

}