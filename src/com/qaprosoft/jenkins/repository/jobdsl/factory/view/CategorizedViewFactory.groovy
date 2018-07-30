package com.qaprosoft.jenkins.repository.jobdsl.factory.view

import groovy.transform.InheritConstructors

@InheritConstructors
public class CategorizedViewFactory extends ListViewFactory {
	def criteria
	
	public CategorizedViewFactory(folder, name, descFilter, criteria) {
		super(folder, name, descFilter)
		this.criteria = criteria
	}
	
	
    def create() {
        def view = _dslFactory.categorizedJobsView("${folder}/${name}") 
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
						regex("${descFilter}")
					}
				}
			}

            categorizationCriteria {
                regexGroupingRule(criteria)
            }
        }
        return view
    }
}