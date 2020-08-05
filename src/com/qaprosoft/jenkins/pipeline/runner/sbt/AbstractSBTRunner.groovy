package com.qaprosoft.jenkins.pipeline.runner.sbt

import com.qaprosoft.jenkins.pipeline.Configuration
import groovy.transform.InheritConstructors

import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner
import java.util.Date
import java.text.SimpleDateFormat


@InheritConstructors
abstract class AbstractSBTRunner extends AbstractRunner {

    def date = new Date()
    def sdf = new SimpleDateFormat("yyyyMMddHHmmss")
    String curDate = sdf.format(date)
    String randomCompareArchiveName = "loadTestingReports" + curDate + ".zip"
    String randomArchiveName = "loadTestingReports" + curDate + ".zip"

}