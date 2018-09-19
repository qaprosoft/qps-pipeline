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

	public void createRepository() {
		context.node('master') {
			context.timestamps {
				this.prepare()
				this.create()
				this.clean()
			}
		}
	}
	
    public void updateRepository() {
		context.node('master') {
			context.timestamps {
                this.prepare()
                if (!isUpdated("**.xml,**/zafira.properties") && onlyUpdated) {
					context.println("do not continue scanner as none of suite was updated ( *.xml )")
					return
                }
                this.scan()
                this.clean()
            }
        }
	}

}