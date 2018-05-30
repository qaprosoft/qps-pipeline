package com.qaprosoft.jenkins.repository.jobdsl.v2

import groovy.json.JsonSlurper;

class Job {

    static void createPipeline(pipelineJob, org.testng.xml.XmlSuite currentSuite, String project, String sub_project, String suite, String suiteOwner, String zafira_project) {

        pipelineJob.with {
            description("project: ${project}; zafira_project: ${zafira_project}; owner: ${suiteOwner}")
            logRotator {
                numToKeep 100
            }

            authenticationToken('ciStart')

	    def scheduling = currentSuite.getParameter("scheduling")
            if (scheduling != null) {
                triggers {
                    cron(scheduling)
                }
            }

	    println "test message for sync1"
            /** Properties & Parameters Area **/
            parameters {
                choiceParam('env', getEnvironments(currentSuite), 'Environment to test against.')
                booleanParam('fork', false, "Reuse forked repository for ${project} project.")
                booleanParam('debug', false, 'Check to start tests in remote debug mode.')

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
                        stringParam('build', '.*', ".* - use fresh build artifact from S3 or local storage;\n2.2.0.3741.45 - exact version you would like to use")
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
                        stringParam('build', '.*', ".* - use fresh build artifact from S3 or local storage;\n2.2.0.3741.45 - exact version you would like to use")
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

                def paramsMap = [:]
                paramsMap = currentSuite.getAllParameters()
                println "paramsMap: " + paramsMap
                for (param in paramsMap) {
                    // read each param and parse for generating custom project fields
                    //	<parameter name="stringParam::name::desc" value="value" />
                    //	<parameter name="stringParam::name" value="value" />
                    println("param: " + param)
                    def delimitor = "::"
                    if (param.key.contains(delimitor)) {
                    def (type, name, desc) = param.key.split(delimitor)
                        switch(type.toLowerCase()) {
                            case "hiddenparam":
                                configure addHiddenParameter(name, desc, param.value)
                                break;
                            case "stringparam":
                                stringParam(name, param.value, desc)
                                break;
                            case "choiceparam":
                                choiceParam(name, Arrays.asList(param.value.split(',')), desc)
                                break;
                            case "booleanparam":
                                booleanParam(name, param.value.toBoolean(), desc)
                                break;
                            default:
                                break;
                        }
                    }
                }
            }

            /** Git Stuff **/
            definition {
                cps {
                    script("@Library('QPS-Pipeline')\nimport com.qaprosoft.jenkins.repository.pipeline.v2.Runner;\nnew Runner(this).runJob()")
                    sandbox()
                }
            }

            properties {
                ownership {
                    primaryOwnerId(suiteOwner)
                }
            }
        }
    }

    static Closure addExtensibleChoice(choiceName, globalName, desc, choice) {
        return { node ->
            node / 'properties' / 'hudson.model.ParametersDefinitionProperty' / 'parameterDefinitions' << 'jp.ikedam.jenkins.plugins.extensible__choice__parameter.ExtensibleChoiceParameterDefinition'(plugin: 'extensible-choice-parameter@1.3.3') {
                name choiceName
                description desc
                editable true
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

	    def scheduling = currentSuite.getParameter("scheduling")
            if (scheduling != null) {
                triggers {
                    cron(scheduling)
                }
            }

            parameters {
                choiceParam('env', getEnvironments(currentSuite), 'Environment to test against.')
                configure addHiddenParameter('project', '', project)
                configure addHiddenParameter('sub_project', '', sub_project)
                configure addHiddenParameter('ci_parent_url', '', '')
                configure addHiddenParameter('ci_parent_build', '', '')

                configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", "master")

                stringParam('email_list', '', 'List of Users to be emailed after the test. If empty then populate from jenkinsEmail suite property')
                stringParam('thread_count', '1', 'number of threads, number')
                choiceParam('retry_count', [0, 1, 2, 3], 'Number of Times to Retry a Failed Test')
            }
            definition {
                cps {
                    script("@Library('QPS-Pipeline')\nimport com.qaprosoft.jenkins.repository.pipeline.v2.Runner;\nnew Runner(this).runCron()")
                    sandbox()
                }
            }
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
        def deviceList = ["DefaultPool", "ANY", "Google_Nexus_7", "Google_Pixel", "Google_Pixel_XL", "One_M8", "Samsung_Grand_Prime", "Samsung_Galaxy_S7", "Samsung_Galaxy_S6", "Samsung_Galaxy_Note_4", "Motorola_Nexus_6", "Samsung_Galaxy_J3", "Samsung_Galaxy_J5", "Samsung_Galaxy_J7", "Xiaomi_Redmi_Note4", "LG_K7", "Nexus_7", "Samsung_Galaxy_S5", "Moto_G4_Plus", "Samsung_Galaxy_S7_Ed", "LG_Nexus_5", "Xiaomi_MI_MAX", "Lenovo_Tab4", "Samsung_Galaxy_Tab_A8", "Honor_6X", "OnePlus_3", "Nokia_3", "Xiaomi_Redmi_4X", "Sony_Xperia_XA1", "Xiaomi_Redmi_4A", "Samsung_Galaxy_A5", "Samsung_Galaxy_Tab_A10", "LG_Nexus_4"]
	println(deviceList)
	println "test message for sync2"
	def json = new JsonSlurper().parse("https://smule.qaprosoft.com/grid/admin/ProxyInfo".toURL())
	println json
	json.each {
		println "platform: " + it.configuration.capabilities.platform + "; device: " + it.configuration.capabilities.browserName
	}
        return deviceList
    }


    static List<String> getiOSDeviceList() {
        def deviceList = ["DefaultPool", "ANY", "iPhone_7_Plus", "iPhone_7", "iPhone_6S", "iPad_Air_2", "iPhone_7Plus", "iPhone_8", "iPhone_8Plus", "iPhone_5s", "iPad_Air2", "iPhone_7_Black", "iPhone_8_Black"]
        return deviceList
    }

}
