package com.qaprosoft.jenkins.repository.jobdsl.factory.folder

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class FolderFactory extends DslFactory {

    def logRotator = 100

    public FolderFactory(folder) {
        super(folder, '', '')
    }

    def create() {
        def folder = _dslFactory.folder(this.folder){
            logRotator { numToKeep logRotator }
        }
        return folder
    }
}