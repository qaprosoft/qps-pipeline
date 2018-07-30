package com.qaprosoft.jenkins.repository.jobdsl.factory.view

import groovy.transform.InheritConstructors

@InheritConstructors
public class CategorizedViewFactory extends ViewFactory {
	
	def folder
	def viewName
	def criteria
	
	public CategorizedViewFactory(folder, viewName, criteria) {
		this.folder = folder
		this.viewName = viewName
		this.criteria = criteria
	}
	
	public init(args) {
		this.folder = args.get("folder")
		this.viewName = args.get("viewName")
		this.criteria = args.get("criteria")
	}
	
	
    def create() {
        def view = factoryCategorizedView(folder, viewName)
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

            categorizationCriteria {
                regexGroupingRule(criteria)
            }
        }
        return view
    }
}