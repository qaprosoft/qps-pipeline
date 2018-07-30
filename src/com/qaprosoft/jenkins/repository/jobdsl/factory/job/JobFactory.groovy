package com.qaprosoft.jenkins.repository.jobdsl.factory.job

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class JobFactory extends DslFactory {

    def freeStyleJob(_name, _description) {
        return dslFactory.freeStyleJob(_name){
            description "DSL MANAGED: - $_description"
            logRotator { numToKeep 100 }
        }
    }
}