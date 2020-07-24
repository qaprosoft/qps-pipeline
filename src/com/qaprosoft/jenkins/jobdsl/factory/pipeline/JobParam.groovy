package com.qaprosoft.jenkins.jobdsl.factory.pipeline

class JobParam {
    def type
    def desc
    def value

    public JobParam(paramType, paramDesc, paramValue) {
        this.type = paramType
        this.desc = paramDesc
        this.value = paramValue
    }

    public def getType() {
        return this.type
    }

    public def getDesc() {
        return this.desc
    }

    public def getValue() {
        return this.value
    }
}
