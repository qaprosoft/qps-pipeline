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
	
	// dynamically load properties from map to member
	public load(args) {
		println("loads...")
		args.each{
			println(it.name)
			println(it.value)
		}
		//this."$objectName" = <value>"
	}

}