package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.IScanner
import com.qaprosoft.jenkins.pipeline.impl.Scanner

import groovy.transform.InheritConstructors

@InheritConstructors
class CarinaScanner implements IScanner {

	protected def context
	
    public CarinaScanner(context) {
		this.context = context
    }

	public void scanRepository() {
//		context.println("CarinaScanner->scanRepository")
	}
}