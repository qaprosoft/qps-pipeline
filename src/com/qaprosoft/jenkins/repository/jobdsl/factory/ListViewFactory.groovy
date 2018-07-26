package com.qaprosoft.jenkins.repository.jobdsl.factory

public class ListViewFactory {

    def _dslFactory

    ListViewFactory(dslFactory){
        _dslFactory = dslFactory
    }

    def listView(folder, view, descFilter) {
        return _dslFactory.listView("${folder}/${view}") {
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
    }
}