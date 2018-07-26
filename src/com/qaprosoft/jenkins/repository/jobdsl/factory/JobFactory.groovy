package com.qaprosoft.jenkins.repository.jobdsl.factory

public class JobFactory {

    def dslFactory

    JobFactory(dslFactory){
        this.dslFactory = dslFactory
    }

    def freeStyleJob(_name, _description) {
        return dslFactory.freeStyleJob(_name){
            description "DSL MANAGED: - $_description"
            logRotator { numToKeep 100 }
        }
    }

}