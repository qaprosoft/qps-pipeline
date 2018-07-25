package com.qaprosoft.jenkins.repository.jobdsl.v2

@Grab('org.testng:testng:6.8.8')

import org.testng.xml.Parser;
import org.testng.xml.XmlSuite;
import com.qaprosoft.jenkins.repository.jobdsl.v2.Job;



class Creator {
	protected def context
	protected def binding

	public Creator(context) {
		this.context = context
		this.binding = context.binding
	}

	void createJob() {
		//TODO: remove hardcoded folder name 
		def jobFolder = "Automation"
		
		context.println("suite path: " + context.readFileFromWorkspace("curremt_suite.xml"))
		context.println("vars: " + binding.variables.dump())
		
		def xmlFile = new Parser(new File(context.readFileFromWorkspace("curremt_suite.xml")).absolutePath)
		
		xmlFile.setLoadClasses(false)
		List<XmlSuite> suiteXml = xmlFile.parseToList()
		XmlSuite currentSuite = suiteXml.get(0)
		def jobName = currentSuite.getParameter("jenkinsJobName").toString()
		context.println("jobName: " + jobName)

		def project = binding.variables.project
		context.println("project: " + project)
		def sub_project = binding.variables.sub_project
		context.println("sub_project: " + sub_project)
		
		def zafira_project = binding.variables.zafira_project
		context.println("zafira_project: " + zafira_project)
		

/*		

		def suite = binding.variables.suite
		def suiteOwner = binding.variables.suiteOwner
		boolean createCron = binding.variables.createCron.toBoolean()
		
		Job job = new Job(context)
		job.createPipeline(context.pipelineJob(jobFolder + "/" + jobName), currentSuite, project, sub_project, suite, suiteOwner, zafira_project)
		
		if (createCron && !currentSuite.getParameter("jenkinsRegressionPipeline").toString().contains("null")) {
			def cronJobNames = currentSuite.getParameter("jenkinsRegressionPipeline").toString()
			for (def cronJobName : cronJobNames.split(",")) {
				cronJobName = cronJobName.trim()
				job.createRegressionPipeline(context.pipelineJob(jobFolder + "/" + cronJobName), currentSuite, project, sub_project)
			}
		}
*/	}
}
