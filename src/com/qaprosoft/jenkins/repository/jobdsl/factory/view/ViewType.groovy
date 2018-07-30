package com.qaprosoft.jenkins.repository.jobdsl.factory.view

import groovy.transform.Canonical

@Canonical
public class ViewType {

    String factory
    String folder
    String viewName
    String jobNames
    String descFilter
    String criteria
}