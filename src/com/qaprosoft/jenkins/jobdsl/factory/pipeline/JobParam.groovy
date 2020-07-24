package com.qaprosoft.jenkins.jobdsl.factory.pipeline

class JobParam {
    def type
    def desc
    def value
    def globalName

    public JobParam(paramType, paramDesc, paramValue) {
        this.type = paramType
        this.desc = paramDesc
        this.value = paramValue
    }

    public JobParam(paramType, paramDesc, paramValue, globalName) {
        this.type = paramType
        this.desc = paramDesc
        this.value = paramValue
        this.globalName = globalName
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

    public def getGlobalName() {
        return this.globalName
    }
}
