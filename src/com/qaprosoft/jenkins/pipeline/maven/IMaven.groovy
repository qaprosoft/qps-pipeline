package com.qaprosoft.jenkins.pipeline.maven

trait IMaven {
	def context

	public IMaven(context) {
        	this.context = context
	}

	abstract void executeMaven()
}
