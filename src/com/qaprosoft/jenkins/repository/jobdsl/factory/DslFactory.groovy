package com.qaprosoft.jenkins.repository.jobdsl.factory

public class DslFactory {

    def _dslFactory

    DslFactory(dslFactory) {
        _dslFactory = dslFactory
    }
	
	DslFactory() {
		_dslFactory = null
	}

}