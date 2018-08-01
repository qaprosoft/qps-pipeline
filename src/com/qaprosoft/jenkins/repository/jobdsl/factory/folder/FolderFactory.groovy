package com.qaprosoft.jenkins.repository.jobdsl.factory.folder

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class FolderFactory extends DslFactory {

    def folder

    public FolderFactory(folder) {
        this.folder = folder
    }

    def create() {
        def folder = _dslFactory.folder("${folder}")
        return folder
    }
}