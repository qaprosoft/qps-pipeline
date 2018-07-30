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
			_dslFactory.println("it.key: " + it.key)
			_dslFactory.println("it.value: " + it.value)
			if (it.value != null) {
				this."${it.key}" = it.value
			}
		}
		
	}

}