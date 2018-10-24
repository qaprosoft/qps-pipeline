package com.qaprosoft.jenkins.jobdsl.factory

import com.qaprosoft.Logger
import com.qaprosoft.Utils

public class DslFactory {
	def folder
	def name
	def description

    def _dslFactory
	def clazz
	def logLevel
    def Logger logger

	// ATTENTION! this is very important constructor. Please do not override on children level constructor with single argument
    DslFactory(dslFactory) {
        this._dslFactory = dslFactory
		this.clazz = this.getClass().getCanonicalName()
        this.logLevel = _dslFactory.binding.variables.PIPELINE_LOG_LEVEL
        this.logger = new Logger(logLevel, _dslFactory)
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
            logger.debug("FactoryFullName: ${folder}/${name}")
			return "${folder}/${name}"
		} else {
			return name
		}
	}
	
	// dynamically load properties from map to members
	public load(args) {
		logger.debug("FactoryProperties: ${args.dump()}")
		args.each {
			if (it.value != null) {
				this."${it.key}" = it.value
			}
		}
    }

	public setClass(_clazz) {
		clazz = _clazz
	}

}