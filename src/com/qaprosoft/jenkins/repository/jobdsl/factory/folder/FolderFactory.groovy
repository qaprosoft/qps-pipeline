package com.qaprosoft.jenkins.repository.jobdsl.factory.folder

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class FolderFactory extends DslFactory {

    public FolderFactory(folder, name) {
        this.folder = folder
        this.name = name
    }

    def create() {
        return _dslFactory.folder("${folder}")
    }
}