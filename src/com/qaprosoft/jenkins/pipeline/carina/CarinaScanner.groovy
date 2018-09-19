package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.scanner.Scanner
import groovy.transform.InheritConstructors

@InheritConstructors
class CarinaScanner extends Scanner {

    public CarinaScanner(context) {
        super(context)
		
		pipelineLibrary = "QPS-Pipeline"
		runnerClass = "com.qaprosoft.jenkins.pipeline.carina.CarinaRunner"
    }


	@Override
	public void scanRepository() {
		context.println("CarinaScanner->scanRepository")
	}
}