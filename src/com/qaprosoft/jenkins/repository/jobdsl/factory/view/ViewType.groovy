package com.qaprosoft.jenkins.repository.jobdsl.factory.view

import groovy.transform.Canonical

//@Canonical
public class ViewType {

    ViewType(){

    }

    ViewType(factory, jobFolder) {
        this.factory = factory
        this.folder = jobFolder
    }

    String factory
    String folder
    String viewName
    String descFilter
    String criteria
}