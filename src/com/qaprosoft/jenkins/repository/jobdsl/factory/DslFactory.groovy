package com.qaprosoft.jenkins.repository.jobdsl.factory

public class DslFactory {

    def _dslFactory
	def clazz

	DslFactory(dslFactory, clazz) {
		this._dslFactory = dslFactory
		this.clazz = clazz
	}
	
    DslFactory(dslFactory) {
        _dslFactory = dslFactory
    }
	
	DslFactory() {
		_dslFactory = null
	}

}