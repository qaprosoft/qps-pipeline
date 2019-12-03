package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import com.qaprosoft.jenkins.jobdsl.factory.job.JobFactory
import groovy.transform.InheritConstructors
import org.apache.tools.ant.types.resources.selectors.None

@InheritConstructors
public class PipelineFactory extends JobFactory {
    def pipelineScript = ""
    def suiteOwner = ""

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
        def pipelineJob = _dslFactory.pipelineJob(getFullName()){
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

    protected List<String> getEnvironments(currentSuite) {
        def envList = getGenericSplit(currentSuite, "jenkinsEnvironments")

        if (envList.isEmpty()) {
            envList.add("DEMO")
            envList.add("STAG")
        }

        return envList
    }

    protected checkSuitOwner(suiteOwner) {
        def suiteOwnerList = []
        if (suiteOwner.contains(", ")) {
            suiteOwnerList = suiteOwner.split(", ")
        } else if (suiteOwner.contains(",")) {
            suiteOwnerList = suiteOwner.split(",")
        } else {
            suiteOwnerList = suiteOwner
        }
        return suiteOwnerList[0]
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

}