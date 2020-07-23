package com.qaprosoft.jenkins.jobdsl.factory.pipeline

class JobParameter {
    def paramType = ""
    def paramDescription = ""
    def paramValue = null

    public def setParamContent(paramType, paramDescription, paramValue) {
        this.paramType = paramType
        this.paramDescription = paramDescription
        this.paramValue = paramValue
    }
}
