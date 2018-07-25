package com.qaprosoft.jenkins.repository.jobdsl.factory

public class JobFactory {

    def _dslFactory

    JobFactory(dslFactory){
        _dslFactory = dslFactory
    }

    def myJob(_name, _description) {
        return _dslFactory.freeStyleJob(_name){
            description "DSL MANAGED: - $_description"
            logRotator { numToKeep 100 }
        }
    }

}