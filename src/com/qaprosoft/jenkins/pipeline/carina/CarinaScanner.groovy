package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.IScanner

class CarinaScanner implements IScanner {

	protected def context
	
    public CarinaScanner(context) {
		this.context = context
    }

	public void scanRepository() {
//		context.println("CarinaScanner->scanRepository")
	}
}