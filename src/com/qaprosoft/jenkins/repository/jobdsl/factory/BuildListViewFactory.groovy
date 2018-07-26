package com.qaprosoft.jenkins.repository.jobdsl.factory

public class BuildListViewFactory extends ListViewFactory{

    def listView(folder, viewName, descFilter) {
        def view = factoryListView(folder, viewName)
        view.with {
            logRotator { numToKeep 100 }
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