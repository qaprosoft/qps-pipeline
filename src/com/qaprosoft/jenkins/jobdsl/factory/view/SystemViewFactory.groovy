package com.qaprosoft.jenkins.jobdsl.factory.view

import com.qaprosoft.jenkins.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class SystemViewFactory extends DslFactory {

	def folder
	def name

	def create() {
		//TODO: reuse getFullName
		def view = _dslFactory.listView("${folder}/${name}")
		view.with {
			columns {
				status()
				weather()
				name()
				lastSuccess()
				lastFailure()
				lastDuration()
				buildButton()
			}

            jobFilters {
                regex {
                    matchType(MatchType.INCLUDE_MATCHED)
                    matchValue(RegexMatchValue.NAME)
                    regex(".*onPush.*|.*onPullRequest.*")
                }
            }
		}
		return view
	}

}