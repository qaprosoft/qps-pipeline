package com.qaprosoft.scm

public interface ISCM {

	public def clone()

	public def clonePR()

	public def clone(isShallow)

    public def clone(gitUrl, branch, subFolder)

	public void mergePR()
}