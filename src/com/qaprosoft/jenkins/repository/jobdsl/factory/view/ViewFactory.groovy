package com.qaprosoft.jenkins.repository.jobdsl.factory.view

public class ViewFactory {

    def _dslFactory

    ViewFactory(dslFactory){
        _dslFactory = dslFactory
    }

	def setDsl(dsl) {
		this._dslFactory = dsl
	}

    def factoryListView(folder, name) {
        return _dslFactory.listView("${folder}/${name}")
    }

    def factoryCategorizedView(folder, name) {
        return _dslFactory.categorizedJobsView("${folder}/${name}")
    }

}