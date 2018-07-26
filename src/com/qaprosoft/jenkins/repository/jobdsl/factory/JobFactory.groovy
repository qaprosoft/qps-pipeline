package com.qaprosoft.jenkins.repository.jobdsl.factory

public class JobFactory {

    def dslFactory

    JobFactory(dslFactory){
        this.dslFactory = dslFactory
    }

    def freeStyleJob(name, description) {
        return dslFactory.freeStyleJob(name){
            description "DSL MANAGED: - $description"
            logRotator { numToKeep 100 }
        }
    }

}