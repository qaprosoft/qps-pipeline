package com.qaprosoft.jenkins.jobdsl.factory.job.hook

import com.qaprosoft.jenkins.jobdsl.factory.job.JobFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class FreestyleJobFactory extends JobFactory {
    def suiteOwner = ""


    public FreestyleJobFactory(folder, name, description, logRotator) {
        super(folder, name, description, logRotator)
    }

    public FreestyleJobFactory(folder, name, description, logRotator, suiteOwner) {
        super(folder, name, description, logRotator)
        this.suiteOwner = suiteOwner
    }
}