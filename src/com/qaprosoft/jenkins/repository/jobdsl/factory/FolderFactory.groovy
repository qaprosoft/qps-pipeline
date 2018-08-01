package com.qaprosoft.jenkins.repository.jobdsl.factory

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class FolderFactory extends DslFactory {

    def folder

    public FolderFactory(folder) {
        this.folder = folder
    }

    def create() {
        return _dslFactory.folder("${folder}")
    }
}