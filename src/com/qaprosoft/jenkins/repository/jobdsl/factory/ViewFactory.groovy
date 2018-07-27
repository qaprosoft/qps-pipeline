package com.qaprosoft.jenkins.repository.jobdsl.factory

public class ViewFactory {

    def _dslFactory

    ViewFactory(dslFactory){
        _dslFactory = dslFactory
    }

    def factoryListView(_folder, _name) {
        return _dslFactory.listView(${_folder} + '/' + ${_name})
    }

    def factoryCategorizedView(_folder, _name) {
        return _dslFactory.categorizedJobsView("$_folder/$_name".toString())
    }
}