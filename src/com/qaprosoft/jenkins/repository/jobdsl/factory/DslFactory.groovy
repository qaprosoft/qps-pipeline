package com.qaprosoft.jenkins.repository.jobdsl.factory

public class DslFactory {
	def folder
	def name
	def description
	
    def _dslFactory
	def clazz

	// ATTENTION! this is very important constructor. Please do not override on children level constructor with single argument
    DslFactory(dslFactory) {
        this._dslFactory = dslFactory
		this.clazz = this.getClass().getCanonicalName()
    }
	
	DslFactory() {
		this._dslFactory = null
		this.clazz = this.getClass().getCanonicalName()
	}


	DslFactory(folder, name, description) {
		this.folder = folder
		this.name = name
		this.description = description
		
		this.clazz = this.getClass().getCanonicalName()
	}
	
	public String getFullName() {
		if (folder != null && !folder.isEmpty()) {
			_dslFactory.println("full name: ${folder}/${name}")
			return "${folder}/${name}"
		} else {
			return name
		}
	}
	
	// dynamically load properties from map to members
	public load(args) {
		//_dslFactory.println("loads: " + args.dump())
		args.each{
			//_dslFactory.println("it.key: " + it.key)
			//_dslFactory.println("it.value: " + it.value)
			if (it.value != null) {
				this."${it.key}" = it.value
			}
		}
		
	}
	
	
	public setClass(_clazz) {
		clazz = _clazz
	}

}