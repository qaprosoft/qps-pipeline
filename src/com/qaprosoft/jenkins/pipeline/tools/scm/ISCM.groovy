package com.qaprosoft.jenkins.pipeline.tools.scm

interface ISCM {

	def clone()

	def clonePR()

	def clone(isShallow)

	def clone(gitUrl, branch, subFolder)

	def mergePR()

	def clonePush()

}
