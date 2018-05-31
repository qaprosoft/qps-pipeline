package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.v2.Creator

def creator = new Creator()
creator.createJob()

/*class Creator3 {
	//pipeline context to provide access to existing pipeline methods like echo, sh etc...
	protected def context
	
	public Creator3(context) {
		this.context = context
	}
	
	public void test() {
		 //do nothing
		context.println("qwe")
	}
}
*/