package com.qaprosoft.jenkins.repository.jobdsl.factory

import groovy.transform.*

@InheritConstructors
public class ListViewFactory extends JobFactory {

	def job(_name, _description) {
		def job = freeStyleJob(_name, _description)
		job.with {
            logRotator { numToKeep 100 }
            parameters {
                booleanParam('parameterIsHere', true, 'First factory parameter')
            }
            listView("Random view") {
                columns {
                    status()
//                    weather()
                    name()
                    lastSuccess()
                    lastFailure()
                    lastDuration()
                    buildButton()
                }

//                if (!"${descFilter}".isEmpty()) {
//                    jobFilters {
//                        regex {
//                            matchType(MatchType.INCLUDE_MATCHED)
//                            matchValue(RegexMatchValue.DESCRIPTION)
//                            regex(".*${descFilter}.*")
//                        }
//                    }
//                }
            }
		}
		return job
	}
}