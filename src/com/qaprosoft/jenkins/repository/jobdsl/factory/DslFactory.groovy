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
	
	// dynamically load properties from map to members
	public load(args) {
		_dslFactory.println("loads: " + args.dump())
		args.each{
			this."${it.key}" = it.value
			_dslFactory.println(it.dump())
		}
		
	}

}