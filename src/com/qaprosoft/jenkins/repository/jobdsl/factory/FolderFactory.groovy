package com.qaprosoft.jenkins.repository.jobdsl.factory

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class FolderFactory extends DslFactory {

    def folder
    def name

    public FolderFactory(folder, name, description) {
        this.folder = folder
        this.name = name
        this.description = description
    }

    def create() {
        return _dslFactory.folder("${folder}")
    }
}