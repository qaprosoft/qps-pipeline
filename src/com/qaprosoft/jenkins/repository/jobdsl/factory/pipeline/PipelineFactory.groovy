package com.qaprosoft.jenkins.repository.jobdsl.factory.pipeline

import com.qaprosoft.jenkins.repository.jobdsl.factory.job.JobFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class PipelineFactory extends JobFactory {
	def suiteOwner = ""
	
	public PipelineFactory(folder, name, description) {
		super(folder, name, description)
	}
	
	public PipelineFactory(folder, name, description, logRotator) {
		super(folder, name, description, logRotator)
	}
	
	public PipelineFactory(folder, name, description, logRotator, suiteOwner) {
		super(folder, name, description, logRotator)
		this.suiteOwner = suiteOwner
	}
	
	def create() {
		def pipelineJob = _dslFactory.pipelineJob(getFullName()){
			description "${description}"
			logRotator { numToKeep logRotator }
			
			authenticationToken('ciStart')
			
			try {
				properties {
					durabilityHint { hint("PERFORMANCE_OPTIMIZED") }
				}
			} catch (Exception e) {
				context.println("Unable to specify performance_optimized mode!")
			}

			if (!suiteOwner.isEmpty()) {
				properties {
					ownership { primaryOwnerId(suiteOwner) }
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