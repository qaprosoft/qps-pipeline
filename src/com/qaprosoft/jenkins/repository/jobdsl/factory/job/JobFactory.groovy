package com.qaprosoft.jenkins.repository.jobdsl.factory.job

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class JobFactory extends DslFactory {
	
	def name
	def description
	def logRotator
	
	public JobFactory(name, description, logRotator) {
		this.name = name
		this.description = description
		this.logRotator = logRotator
		
		this.clazz = this.getClass().getCanonicalName()
	}
	
	def create() {
		def job = _dslFactory.freeStyleJob("${name}"){
			description "${description}"
			logRotator { numToKeep logRotator }
		}
		return job
	}
}