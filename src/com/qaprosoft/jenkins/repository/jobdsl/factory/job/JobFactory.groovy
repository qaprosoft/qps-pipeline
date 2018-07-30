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
	}

	public JobFactory(name, description) {
		this(name, descritption, 100)
	}
	
	public JobFactory(name) {
		this(name, "descritption", 100)
	}
	
	

	def create() {
		def job = _dslFactory.freeStyleJob("${name}"){
			description "${description}"
			logRotator { numToKeep "${logRotator}" }
		}
		return job
	}
}