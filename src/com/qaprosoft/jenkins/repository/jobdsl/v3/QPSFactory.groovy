package com.qaprosoft.jenkins.repository.jobdsl.v3

import groovy.json.JsonSlurper

class QPSFactory {
	protected def context
	protected def binding

	public QPSFactory(context) {
		this.context = context
		this.binding = context.binding
	}
	
	public execute() {
		println(context.dump())
		
		def slurper = new JsonSlurper()
		String factoryDataMap = readFileFromWorkspace("factories.txt")
		def factories = new HashMap(slurper.parseText(factoryDataMap))
		
		factories.each{
			println(it.value)
			it.setDsl(context)
			it.create()
		}
	}
}
