package com.qaprosoft.jenkins.repository.jobdsl.v2

def creator = new Creator3(this)
creator.test()

class Creator3 {
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
