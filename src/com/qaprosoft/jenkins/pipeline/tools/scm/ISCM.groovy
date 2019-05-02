package com.qaprosoft.jenkins.pipeline.tools.scm

public interface ISCM {

    public def clone()

    public def clonePR()

    public def clone(isShallow)

    public def clone(gitUrl, branch, subFolder)

    public def mergePR()

    public def clonePush()

    public def setUrl(url)
}