package com.qaprosoft.jenkins.repository.jobdsl.factory

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class FolderFactory extends DslFactory {

    public FolderFactory(folder) {
        this.folder = folder
        this.name = ''
    }

    def create() {
        return _dslFactory.folder("${folder}")
    }
}