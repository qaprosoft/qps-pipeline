package com.qaprosoft.jenkins.pipeline.scanner

public interface IScanner {

	public void createRepository()

	public void updateRepository()
	
	public void setPipelineLibrary(pipelineLibrary, runnerClass)
}

