package com.qaprosoft.jenkins.repository.jobdsl.factory

public class JobFactory {

    def dslFactory

    JobFactory(dslFactory){
        this.dslFactory = dslFactory
    }

    def freeStyleJob(name, description) {
        return dslFactory.freeStyleJob(name){
  //          description "DSL MANAGED JOB: - ${description}"
            logRotator { numToKeep 100 }
        }
    }

    def pipelineJob(name, description) {
        return dslFactory.pipelineJob(name){
//            description "DSL MANAGED PIPELINE: - ${description}"
            logRotator { numToKeep 100 }
        }
    }
}