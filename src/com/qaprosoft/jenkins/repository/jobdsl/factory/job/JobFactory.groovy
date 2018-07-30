package com.qaprosoft.jenkins.repository.jobdsl.factory.job

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class JobFactory extends DslFactory {
	def logRotator = 100
	
	public JobFactory(folder, name, description) {
		super(folder, name, description)
	}
	
	public JobFactory(folder, name, description, logRotator) {
		super(folder, name, description)
		this.logRotator = logRotator
	}
	
	def create() {
		def job = _dslFactory.freeStyleJob("${folder}/${name}"){
			description "${description}"
			logRotator { numToKeep logRotator }
		}
		return job
	}
}