package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.IScanner
import groovy.transform.InheritConstructors

@InheritConstructors
class CarinaScanner implements IScanner {

	def context
	
    public CarinaScanner(context) {
		this.context = context
    }

	public void scanRepository() {
		context.println("CarinaScanner->scanRepository")
	}
}