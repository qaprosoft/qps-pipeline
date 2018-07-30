package com.qaprosoft.jenkins.repository.jobdsl.factory.view

import groovy.transform.InheritConstructors

@InheritConstructors
public class ListViewFactory extends ViewFactory {
	def folder
	def viewName
	def descFilter
	
	public ListViewFactory(folder, viewName, descFilter) {
		this.folder = folder
		this.viewName = viewName
		this.descFilter = descFilter
	}
	
	public init(args) {
		this.folder = args.get("folder")
		this.viewName = args.get("viewName")
		this.descFilter = args.get("descFilter")
	}
	
	def get() {
		return folder + "/" + viewName + "/" + descFilter
	}

	def create() {
		def view = factoryListView(folder, viewName)
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