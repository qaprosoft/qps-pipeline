package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.scanner.IScanner
import groovy.transform.InheritConstructors

@InheritConstructors
class CarinaScanner implements IScanner {

    public CarinaScanner(context) {
        super(context)
		
		pipelineLibrary = "QPS-Pipeline"
		runnerClass = "com.qaprosoft.jenkins.pipeline.carina.CarinaRunner"
    }


	public void scanRepository() {
		context.println("CarinaScanner->scanRepository")
	}
}