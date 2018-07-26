package com.qaprosoft.jenkins.repository.jobdsl.factory

import groovy.transform.InheritConstructors

@InheritConstructors
public class BuildListViewFactory extends ListViewFactory{

    def listView(folder, viewName, descFilter) {
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