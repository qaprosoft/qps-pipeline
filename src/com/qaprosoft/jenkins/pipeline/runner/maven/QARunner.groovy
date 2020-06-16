package com.qaprosoft.jenkins.pipeline.runner.maven

public class QARunner extends TestNG {

    public QARunner(context) {
        super(context)
        setBuildNameTemplate("${BUILD_NUMBER}${suite}${branch}${env}${browser}${browserVersion}${locale}${language}")
    }

    public QARunner(context, jobType) {
        super(context, jobType)
        setBuildNameTemplate("${BUILD_NUMBER}${suite}${branch}${env}${browser}${browserVersion}${locale}${language}")
    }
}
