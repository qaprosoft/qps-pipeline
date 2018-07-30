package com.qaprosoft.jenkins.repository.jobdsl.factory.view

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class ListViewFactory extends DslFactory {
	def folder
	def viewName
	def descFilter
	
	public ListViewFactory(folder, viewName, descFilter) {
		this.folder = folder
		this.viewName = viewName
		this.descFilter = descFilter
		
		this.clazz = this.getClass().getCanonicalName()
	}
	
/*	public load(args) {
		this.folder = args.get("folder")
		this.viewName = args.get("viewName")
		this.descFilter = args.get("descFilter")
	}*/
	
	def create() {
		def view = _dslFactory.listView("${folder}/${viewName}")
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

			if (!"${descFilter}".isEmpty()) {
				jobFilters {
					regex {
						matchType(MatchType.INCLUDE_MATCHED)
						matchValue(RegexMatchValue.DESCRIPTION)
						regex(".*${descFilter}.*")
					}
				}
			}
		}
		return view
	}

}