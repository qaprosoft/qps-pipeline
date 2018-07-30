package com.qaprosoft.jenkins.repository.jobdsl.factory.view

public class ViewFactory {

    def _dslFactory

    ViewFactory(dslFactory) {
        _dslFactory = dslFactory
    }
	
	ViewFactory() {
		_dslFactory = null
	}

	def setDsl(def dsl) {
		this._dslFactory = dsl
	}
	
	def getDsl() {
		return _dslFactory
	}

    def factoryListView(folder, name) {
        return _dslFactory.listView("${folder}/${name}")
    }

    def factoryCategorizedView(folder, name) {
        return _dslFactory.categorizedJobsView("${folder}/${name}")
    }

}