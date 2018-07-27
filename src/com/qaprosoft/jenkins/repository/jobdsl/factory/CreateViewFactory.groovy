package com.qaprosoft.jenkins.repository.jobdsl.factory

import groovy.transform.InheritConstructors

@InheritConstructors
public class CreateViewFactory extends ViewFactory{

    def listView(folder, viewName, descFilter, jobNames) {
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
            jobs {
                names(jobNames)
            }
        }
        return view
    }

    def categorizedView(folder, viewName, criteria, jobNames) {
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

            jobs {
                names(jobNames)
            }
        }
        return view
    }
}