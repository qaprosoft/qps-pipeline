package com.qaprosoft.jenkins.repository.jobdsl.factory.job

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class JobFactory extends DslFactory {
	def folder
	def name
	def description
	def logRotator = 100
	
	public JobFactory(folder, name, description) {
		this.folder = folder
		this.name = name
		this.description = description
		
		this.clazz = this.getClass().getCanonicalName()
	}
	
	public JobFactory(folder, name, description, logRotator) {
		this.folder = folder
		this.name = name
		this.description = description
		this.logRotator = logRotator
		
		this.clazz = this.getClass().getCanonicalName()
	}
	
	def create() {
		def job = _dslFactory.freeStyleJob("${folder}/${name}"){
			description "${description}"
			logRotator { numToKeep logRotator }
		}
		return job
	}
}