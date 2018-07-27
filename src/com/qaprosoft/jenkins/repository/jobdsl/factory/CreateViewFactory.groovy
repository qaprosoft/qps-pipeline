package com.qaprosoft.jenkins.repository.jobdsl.factory

import groovy.transform.InheritConstructors

@InheritConstructors
public class CreateViewFactory extends ViewFactory{

    def scannerListView(jobName, jobFolder, view, descFilter, propagate) {
        def _view = factoryListView(jobFolder, view)
        _view.with {
            jobs {
                name(jobName)
            }

            parameters {
                stringParameterValue {
                    name('folder')
                    value(jobFolder)
                }

                stringParameterValue {
                    name('view')
                    value(view)
                }

                stringParameterValue {
                    name('descFilter')
                    value(descFilter)
                }
            }
            propagate(propagate)
       }
        return _view
    }

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