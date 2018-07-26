package com.qaprosoft.jenkins.repository.jobdsl.factory

public class JobFactory {

    def _dslFactory

    JobFactory(dslFactory){
        _dslFactory = dslFactory
    }

    def freeStyleJob(_name, _description) {
        return _dslFactory.freeStyleJob(_name){
            description "DSL MANAGED JOB: - $_description"
            logRotator { numToKeep 100 }
        }
    }

    def pipelineJob(_name, _description) {
        return _dslFactory.pipelineJob(_name){
            description "DSL MANAGED PIPELINE: - $_description"
            logRotator { numToKeep 100 }
        }
    }
}