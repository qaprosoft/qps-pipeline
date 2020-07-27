package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import com.qaprosoft.jenkins.jobdsl.factory.job.JobFactory
import groovy.transform.InheritConstructors
import static com.qaprosoft.jenkins.Utils.*
import org.apache.tools.ant.types.resources.selectors.None

@InheritConstructors
public class PipelineFactory extends JobFactory {
    def pipelineScript = ""
    def suiteOwner = ""

    SortedMap parametersMap = new HashMap()


    def addParam(parameterName, value) {
        parametersMap.put(parameterName, value)
    }

    def setDefaultParams() {
        addParam('auto_screenshot', new JobParam('booleanparam', 'Generate screenshots automatically during the test', null))
        addParam('enableVideo', new JobParam('booleanparam', 'Enable video recording', null))
        addParam('capabilities', new JobParam('stringparam', 'Provide semicolon separated W3C driver capabilities', null))
        addParam('job_type', new JobParam('hiddenparam', "", null))
        addParam('capabilities.provider', new JobParam('hiddenparam', 'hub provider name', ""))
        addParam('node_label', new JobParam('hiddenparam', 'customized node label', ""))
        addParam('branch', new JobParam('stringparam', 'SCM repository branch to run against', "master"))
        addParam('repo', new JobParam('hiddenparam', '', ''))
        addParam('GITHUB_HOST', new JobParam('hiddenparam', '', ''))
        addParam('GITHUB_ORGANIZATION', new JobParam('hiddenparam', '', ''))
        addParam('sub_project', new JobParam('hiddenparam', '', null))
        addParam('zafira_project', new JobParam('hiddenparam', '', null))
        addParam('suite', new JobParam('hiddenparam', '', null))
        addParam('ci_parent_url', new JobParam('hiddenparam', '', ''))
        addParam('ci_parent_build', new JobParam('hiddenparam', '', ''))
        addParam('slack_channels', new JobParam('hiddenparam', '', null))
        addParam('queue_registration', new JobParam('hiddenparam', '', null))
        addParam('thread_count', new JobParam('stringparam', 'number of threads, number', null))
        addParam('data_provider_thread_count', new JobParam('stringparam', 'number of threads for data provider, number', null))
        addParam('email_list', new JobParam('stringparam', 'List of Users to be emailed after the test', null))
        addParam('failure_email_list', new JobParam('hiddenparam', '', null))
        addParam('retry_count', new JobParam('choiceparam', 'Number of Times to Retry a Failed Test', null))
        addParam('rerun_failures', new JobParam('booleanparam', 'During "Rebuild" pick it to execute only failed cases', null))
        addParam('overrideFields', new JobParam('hiddenparam', '', ""))
        addParam('zafiraFields', new JobParam('hiddenparam', '', ""))
        addParam('custom_capabilities', new JobParam('extensiblechoiceparam', "Set to NULL to run against Selenium Grid on Jenkin's Slave else, select an option for Browserstack", null, "gc_CUSTOM_CAPABILITIES"))
        addParam('ci_run_id', new JobParam('extensiblechoiceparam', '', ""))
        addParam('BuildPriority', new JobParam('extensiblechoiceparam', "Priority of execution. Lower number means higher priority", "3", "gc_BUILD_PRIORITY"))
    }

    public PipelineFactory(folder, name, description) {
        super(folder, name, description)
    }

    public PipelineFactory(folder, name, description, logRotator) {
        super(folder, name, description, logRotator)
    }

    public PipelineFactory(folder, name, description, logRotator, pipelineScript) {
        super(folder, name, description, logRotator)
        this.pipelineScript = pipelineScript
    }

    public PipelineFactory(folder, name, description, logRotator, pipelineScript, suiteOwner) {
        super(folder, name, description, logRotator)
        this.pipelineScript = pipelineScript
        this.suiteOwner = suiteOwner
    }

    def create() {
        def pipelineJob = _dslFactory.pipelineJob(getFullName()) {
            description "${description}"
            logRotator { numToKeep logRotator }

            authenticationToken('ciStart')

            properties {
                disableResume()
                durabilityHint { hint("PERFORMANCE_OPTIMIZED") }
                if (!suiteOwner.isEmpty()) {
                    ownership { primaryOwnerId(suiteOwner) }
                }
            }

            /** Git Stuff **/
            definition {
                cps {
                    script(pipelineScript)
                    sandbox()
                }
            }
        }
        return pipelineJob
    }

    protected String getEnvironments(currentSuite) {
        def enviroments = currentSuite.getParameter("jenkinsEnvironments")
        def parsedEnviroments = "DEMO\nSTAG\nPROD"

        if (!isParamEmpty(enviroments)) {
            parsedEnviroments = ""
            for (env in enviroments.split(",")) {
                parsedEnviroments += env.trim() + "\n"
            }
        }
        return parsedEnviroments
    }

    protected String getDefaultChoiceValue(currentSuite) {
        def enviroments = getEnvironments(currentSuite)
        return enviroments.substring(0, enviroments.indexOf('\n'))
    }

    protected List<String> getGenericSplit(currentSuite, parameterName) {
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

    protected Closure addHiddenParameter(paramName, paramDesc, paramValue) {
        return { node ->
            node / 'properties' / 'hudson.model.ParametersDefinitionProperty' / 'parameterDefinitions' << 'com.wangyin.parameter.WHideParameterDefinition'(plugin: 'hidden-parameter@0.0.4') {
                name paramName
                description paramDesc
                defaultValue paramValue
            }
        }
    }

    protected Closure addExtensibleChoice(choiceName, globalName, desc, choice) {
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

    protected Closure addExtensibleChoice(choiceName, desc, code) {
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

    protected def parseSheduling(scheduling) {
        if (scheduling.contains("::")) {
            def multilineArray = scheduling.split("::")
            def multilineValue = ""
            multilineArray.each { value ->
                multilineValue = multilineValue + value + "\n"
            }
            scheduling = multilineValue
        }
        return scheduling
    }


}