package com.qaprosoft.jenkins.repository.jobdsl.factory.folder

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class FolderFactory extends DslFactory {

    public FolderFactory(name) {
        super(name, name, '')
    }

    def create() {
        return _dslFactory.folder("${name}")
    }

    // dynamically load properties from map to members
    def load(args) {
        println("FolderFactory load: " + args.dump())
        super.load(args)
    }
}