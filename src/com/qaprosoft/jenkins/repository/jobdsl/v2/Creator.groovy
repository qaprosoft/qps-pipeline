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
		context.dump(this)

/*		def workspace = binding.variables.WORKSPACE
		context.println("workspace: ${workspace}")


		def jobFolder = binding.variables.jobFolder
		def project = binding.variables.project
		def sub_project = binding.variables.sub_project
		def suite = binding.variables.suite
		def suiteOwner = binding.variables.suiteOwner
		def zafira_project = binding.variables.zafira_project
		boolean createCron = binding.variables.createCron.toBoolean()
		def suiteTmpPath = "${workspace}/suite.xml"
		File file = new File(suiteTmpPath)
		file.write(binding.variables.suiteXML)
		def xmlFile = new Parser(new File(suiteTmpPath).absolutePath)
		xmlFile.setLoadClasses(false)
		List<XmlSuite> suiteXml = xmlFile.parseToList()
		XmlSuite currentSuite = suiteXml.get(0)
		def jobName = currentSuite.getParameter("jenkinsJobName").toString()
		
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
