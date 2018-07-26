package com.qaprosoft.jenkins.repository.jobdsl.factory

public class ListViewFactory {

    def _dslFactory

    ListViewFactory(dslFactory){
        _dslFactory = dslFactory
    }

    def factoryListView(folder, name) {
        return _dslFactory.listView("${folder}/${name}") {

        }
    }
}