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
		
		context.println("suite path: " + context.readFileFromWorkspace("suite.path.txt"))
		context.println("vars: " + binding.variables.dump())
		
		def xmlFile = new Parser(new File(context.readFileFromWorkspace("suite.path.txt")).absolutePath)
		
		xmlFile.setLoadClasses(false)
		List<XmlSuite> suiteXml = xmlFile.parseToList()
		XmlSuite currentSuite = suiteXml.get(0)
		def jobName = currentSuite.getParameter("jenkinsJobName").toString()
		context.println("jobName: " + jobName)

		def project = binding.variables.project
		context.println("project: " + project)
		
		def sub_project = context.readFileFromWorkspace("sub_project.txt")
		context.println("sub_project: " + sub_project)
		
		def zafira_project = context.readFileFromWorkspace("zafira_project.txt")
		context.println("zafira_project: " + zafira_project)
		
		def suiteOwner = "anonymous"
		if (currentSuite.toXml().contains("suiteOwner")) {
			suiteOwner = currentSuite.getParameter("suiteOwner")
		}
		context.println("suiteOwner: " + suiteOwner)
		
		if (currentSuite.toXml().contains("zafira_project")) {
			zafira_project = currentSuite.getParameter("zafira_project")
			context.println("updated zafira_project: " + zafira_project)
		}
		
		//TODO: improve cron recreation here
		//boolean createCron = binding.variables.createCron.toBoolean()
		boolean createCron = true

		Job job = new Job(context)
		job.createPipeline(context.pipelineJob(jobFolder + "/" + jobName), currentSuite, project, sub_project, suite, suiteOwner, zafira_project)
		
		if (createCron && !currentSuite.getParameter("jenkinsRegressionPipeline").toString().contains("null")) {
			def cronJobNames = currentSuite.getParameter("jenkinsRegressionPipeline").toString()
			for (def cronJobName : cronJobNames.split(",")) {
				cronJobName = cronJobName.trim()
				job.createRegressionPipeline(context.pipelineJob(jobFolder + "/" + cronJobName), currentSuite, project, sub_project)
			}
		}
	}
}
