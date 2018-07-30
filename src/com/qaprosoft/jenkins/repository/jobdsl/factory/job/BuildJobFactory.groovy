package com.qaprosoft.jenkins.repository.jobdsl.factory.job

import groovy.transform.InheritConstructors

@InheritConstructors
public class BuildJobFactory extends JobFactory {

	def job(_name, _description) {
		def job = freeStyleJob(_name, _description)
		job.with {
			logRotator { numToKeep 100 }
			parameters {
				booleanParam('parameterIsHere', true, 'First factory parameter')
			}
		}
		return job
	}
}