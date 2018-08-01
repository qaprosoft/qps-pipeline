package com.qaprosoft.jenkins.repository.jobdsl.factory.folder

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class FolderFactory extends DslFactory {

    public FolderFactory(name) {
        this.folder = name
        this.name = name
    }

    def create() {
        return _dslFactory.folder("${folder}")
    }
}