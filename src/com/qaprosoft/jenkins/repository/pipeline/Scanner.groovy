package com.qaprosoft.jenkins.repository.pipeline

@Grab('org.testng:testng:6.3.1')
import org.testng.xml.Parser;
import org.testng.xml.XmlSuite;
import groovy.json.JsonSlurper;

import static java.util.UUID.randomUUID


def scanRepository() {
    node("master") {
        timestamps {
            this.clone()
            this.scan()
//            this.clean()
        }
    }
}


def clone() {
    stage('Checkout GitHub Repository') {
        git branch: '${branch}', url: '${GITHUB_SSH_URL}/${project}', changelog: false, poll: false, shallow: true
    }
}

def scan() {
    stage("Scan Repository") {

        currentBuild.displayName = "#${BUILD_NUMBER}|${project}|${branch}"

	println "WORKSPACE: ${WORKSPACE}"
	def workspace = "${WORKSPACE}"

        def jobFolder = "${folder}"


	def jenkinsFile = ".jenkinsfile.json"
	if (!fileExists("${WORKSPACE}/${jenkinsFile}")) {
		println "Skip repository scan as no .jenkinsfile.json discovered! Project: ${project}"
	        currentBuild.result = 'UNSTABLE'
		return
	}

	Object subProjects = parseJSON(jenkinsFile).sub_projects
	subProjects.each {
		println "sub_project: " + it
		println "multi_maven: " + it.multi_maven
		println "pr_checker: " + it.pr_checker
		println "deployable: " + it.deployable
		println "tests_module: " + it.tests_module
		println "suites_folder: " + it.suites_folder

		def sub_project = it.name
		def multi_maven = it.multi_maven
		def prChecker = it.pr_checker
		def pr_merger = it.deployable
		def testModule = it.tests_module
		def testngFolder = it.suites_folder

		def zafira_project = 'unknown'
		def zafiraFilter = "src/main/resources/zafira.properties"
		if (multi_maven) {
			zafiraFilter = sub_project + "/**/${testModule}/src/main/resources/zafira.properties"
		}
		def zafiraProperties = findFiles(glob: zafiraFilter)
		for (File file : zafiraProperties) {
			def props = readProperties file: file.path
			if (props['zafira_project'] != null) {
				zafira_project = props['zafira_project']
			}
		}
		println "zafira_project: ${zafira_project}"

		if (prChecker) {
			// PR_Checker is supported only for the repo with single sub_project!
			println "Launching Create-PR-Checker job for " + project
		        build job: 'Management_Jobs/Create-PR-Checker', \
		                parameters: [string(name: 'project', value: project), \
				string(name: 'sub_project', value: sub_project)], \
		                wait: false
		}

		//TODO: [OPTIONAL] try to read existing views and compare with suggested settings. Maybe we can skip execution better
		List<String> views = []


		//TODO: #2 declare global list for created regression cron jobs
		//	   provide extra flag includeIntoCron for CreateJob 
		List<String> crons = []
	        build job: "Management_Jobs/CreateView",
	            propagate: false,
	                parameters: [
       		                string(name: 'folder', value: jobFolder),
        	                string(name: 'view', value: 'CRON'),
                	        string(name: 'descFilter', value: 'cron'),
	                ]


		// find all tetsng suite xml files and launch job creator dsl job

		def suiteFilter =  "src/test/resources/${testngFolder}/**/*.xml"
		if (multi_maven) {
			suiteFilter = sub_project + "/**/${testModule}/src/test/resources/${testngFolder}/**/*.xml"
		}
	        println "suiteFilter: " + suiteFilter
		def suites = findFiles(glob: suiteFilter)

		for (File suite : suites) {
        		println suite.path
			def suiteOwner = "anonymous"

			XmlSuite currentSuite = parseSuite(suite.path)
        	    	if (currentSuite.toXml().contains("jenkinsJobCreation") && currentSuite.getParameter("jenkinsJobCreation").contains("true")) {
				def suiteName = suite.path
		                suiteName = suiteName.substring(suiteName.lastIndexOf(testngFolder) + testngFolder.length(), suiteName.indexOf(".xml"))

				println "suite name: " + suiteName
				println "suite path: " + suite.path
				
				if (currentSuite.toXml().contains("suiteOwner")) {
					suiteOwner = currentSuite.getParameter("suiteOwner")
				}
				if (currentSuite.toXml().contains("zafira_project")) {
					zafira_project = currentSuite.getParameter("zafira_project")
				}
				if (!views.contains(project.toUpperCase())) {
					views << project.toUpperCase()
				        build job: "Management_Jobs/CreateView",
				            propagate: false,
				                parameters: [
		        		                string(name: 'folder', value: jobFolder),
			        	                string(name: 'view', value: project.toUpperCase()),
			                	        string(name: 'descFilter', value: project),
				                ]
				}

				if (!views.contains(zafira_project)) {
					views << zafira_project

				        build job: "Management_Jobs/CreateView",
				            propagate: false,
				                parameters: [
		        		                string(name: 'folder', value: jobFolder),
			        	                string(name: 'view', value: zafira_project),
			                	        string(name: 'descFilter', value: zafira_project),
				                ]
				}
				
				if (!views.contains(suiteOwner)) {
					views << suiteOwner

				        build job: "Management_Jobs/CreateView",
				            propagate: false,
				                parameters: [
		        		                string(name: 'folder', value: jobFolder),
		                		        string(name: 'view', value: suiteOwner),
		                        		string(name: 'descFilter', value: suiteOwner),
				                ]
				}

				def createCron = false
				if (currentSuite.toXml().contains("jenkinsRegressionPipeline")) {
					def cronName = currentSuite.getParameter("jenkinsRegressionPipeline")
					// we need only single regression cron declaration
					createCron = !crons.contains(cronName)
					crons << cronName
				}

			        build job: "Management_Jobs/CreateJob",
			            propagate: false,
			                parameters: [
		        	                string(name: 'jobFolder', value: jobFolder),
			                        string(name: 'project', value: project),
			                        string(name: 'sub_project', value: sub_project),
		                	        string(name: 'suite', value: suiteName),
		                        	string(name: 'suiteOwner', value: suiteOwner),
		                        	string(name: 'zafira_project', value: zafira_project),
			                        string(name: 'suiteXML', value: parseSuiteToText(suite.path)),
			                        booleanParam(name: 'createCron', value: createCron),
			                ]
			}
		}
	}
    }
}

def clean() {
    stage('Wipe out Workspace') {
        deleteDir()
    }
}

@NonCPS
XmlSuite parseSuite(String path) {
	def xmlFile = new Parser(new File(workspace + "/" + path).absolutePath)
	xmlFile.setLoadClasses(false)
	
	List<XmlSuite> suiteXml = xmlFile.parseToList()
	XmlSuite currentSuite = suiteXml.get(0)
	return currentSuite
}

@NonCPS
String parseSuiteToText(String path) {
	def content = new File(workspace + "/" + path).getText()
	return content
}

@NonCPS
Object parseJSON(String path) {
	def inputFile = new File("${WORKSPACE}/${path}")
	def content = new groovy.json.JsonSlurperClassic().parseFile(inputFile, 'UTF-8')
	return content
}