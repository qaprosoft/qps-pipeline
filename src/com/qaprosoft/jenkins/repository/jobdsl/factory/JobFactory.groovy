package com.qaprosoft.jenkins.repository.jobdsl.factory

public class JobFactory {

    def _dslFactory

    JobFactory(dslFactory){
        _dslFactory = dslFactory
    }

    def myJob(_name, _description) {
        return _dslFactory.freeStyleJob(_name){
            description "DSL MANAGED: - $_descripton"
            logRotator(-1, 10, -1, 10)
        }
    }

}