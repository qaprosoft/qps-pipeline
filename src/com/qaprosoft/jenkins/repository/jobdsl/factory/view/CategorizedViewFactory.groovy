package com.qaprosoft.jenkins.repository.jobdsl.factory.view

import com.qaprosoft.jenkins.repository.jobdsl.factory.DslFactory

import groovy.transform.InheritConstructors

@InheritConstructors
public class CategorizedViewFactory extends DslFactory {
	
	def folder
	def viewName
	def criteria
	
	public CategorizedViewFactory(folder, viewName, criteria) {
		this.folder = folder
		this.viewName = viewName
		this.criteria = criteria
		
		this.clazz = this.getClass().getCanonicalName()
	}
	
	public load(args) {
		super.load(args)
		this.folder = args.get("folder")
		this.viewName = args.get("viewName")
		this.criteria = args.get("criteria")
	}
	
    def create() {
        def view = _dslFactory.categorizedJobsView("${folder}/${viewName}") 
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