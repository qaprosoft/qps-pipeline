package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import com.qaprosoft.jenkins.jobdsl.factory.job.JobFactory
import groovy.transform.InheritConstructors
import static com.qaprosoft.jenkins.Utils.*
import org.apache.tools.ant.types.resources.selectors.None

@InheritConstructors
public class PipelineFactory extends JobFactory {
    def pipelineScript = ""
    def suiteOwner = ""

    Map parametersMap = new LinkedHashMap()
    parametersMap.put("custom_capabilities", "NULL")
    parametersMap.put("auto_screenshot", "false")
    parametersMap.put("enableVideo", "true")
    parametersMap.put("capabilities", "platformName=*")
    parametersMap.put("job_type", "api")
    parametersMap.put("capabilities.provider", "")
    parametersMap.put("node_label", "")
    parametersMap.put("branch", "")
    parametersMap.put("repo", "")
    parametersMap.put("GITHUB_HOST", "")
    parametersMap.put("GITHUB_ORGANIZATION", "")
    parametersMap.put("sub_project", "")
    parametersMap.put("zafira_project", "")
    parametersMap.put("suite", "")
    parametersMap.put("ci_parent_url", "")
    parametersMap.put("ci_parent_build", "")
    parametersMap.put("slack_channels", "")
    parametersMap.put("ci_run_id", "")
    parametersMap.put("BuildPriority", "3")
    parametersMap.put("queue_registration", "true")
    parametersMap.put("thread_count", "")
    parametersMap.put("data_provider_thread_count", "")
    parametersMap.put("email_list", "")
    parametersMap.put("failure_email_list", "")
    parametersMap.put("retry_count", "")
    parametersMap.put("rerun_failures", "false")
    parametersMap.put("overrideFields", "")
    parametersMap.put("zafiraFields", "")

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