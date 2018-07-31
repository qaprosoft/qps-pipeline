package com.qaprosoft.jenkins.repository.pipeline.v2

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.XmlSuite
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.repository.pipeline.v2.Executor
import com.qaprosoft.jenkins.repository.pipeline.v2.Configurator
import com.qaprosoft.jenkins.repository.jobdsl.v2.Creator
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory

import com.qaprosoft.jenkins.repository.jobdsl.factory.job.JobFactory
import com.qaprosoft.jenkins.repository.jobdsl.factory.job.BuildJobFactory

import com.qaprosoft.jenkins.repository.jobdsl.factory.pipeline.PipelineFactory
import com.qaprosoft.jenkins.repository.jobdsl.factory.pipeline.TestNGPipelineFactory


import groovy.json.JsonOutput


class Scanner extends Executor {
	//TODO: specify default factory classes
	//protected String viewFactory = "CreateViewFactory"
	
	protected Map dslFactories = [:]

    public Scanner(context) {
		super(context)
		this.context = context
		scmClient = new GitHub(context)
 	}

    public void scanRepository() {
		context.node('master') {
			context.timestamps {
				scmClient.clone()

				String QPS_PIPELINE_GIT_URL = Configurator.get(Configurator.Parameter.QPS_PIPELINE_GIT_URL)
				String QPS_PIPELINE_GIT_BRANCH = Configurator.get(Configurator.Parameter.QPS_PIPELINE_GIT_BRANCH)

				scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")

				this.scan()
				this.clean()
			}
		}
	}

	protected void scan() {
        context.println('DUMP')
        context.println(context.binding.dump())

		context.stage("Scan Repository") {
			def BUILD_NUMBER = Configurator.get(Configurator.Parameter.BUILD_NUMBER)
			def project = Configurator.get("project")
			def branch = Configurator.get("branch")
			context.currentBuild.displayName = "#${BUILD_NUMBER}|${project}|${branch}"

			
			def workspace = getWorkspace()
			context.println("WORKSPACE: ${workspace}")

			def jobFolder = Configurator.get("folder")

            if (!isItemAvailable(jobFolder)){
                context.build job: "Management_Jobs/CreateFolder",
                        propagate: false,
                        parameters: [context.string(name: 'folder', value: jobFolder)]
            }

            def jenkinsFile = ".jenkinsfile.json"
			if (!context.fileExists("${workspace}/${jenkinsFile}")) {
				context.println("Skip repository scan as no .jenkinsfile.json discovered! Project: ${project}")
				context.currentBuild.result = 'UNSTABLE'
				return
			}

			Object subProjects = this.parseJSON("${workspace}/${jenkinsFile}").sub_projects
		
			subProjects.each {
				context.println("sub_project: " + it)

				def sub_project = it.name

				def subProjectFilter = it.name
				if (sub_project.equals(".")) {
					subProjectFilter = "**"
				}
				
				def prChecker = it.pr_checker
				def prMerger = it.deployable
				def zafiraFilter = it.zafira_filter
				def suiteFilter = it.suite_filter

				def zafira_project = 'unknown'
				def zafiraProperties = context.findFiles(glob: subProjectFilter + "/" + zafiraFilter)
				for (File file : zafiraProperties) {
					def props = context.readProperties file: file.path
					if (props['zafira_project'] != null) {
						zafira_project = props['zafira_project']
					}
				}
				context.println("zafira_project: ${zafira_project}")

				//TODO: restore PR checker functionality after configuring by default sonarqube scanner in jenkins-master:
				// https://github.com/qaprosoft/jenkins-master/issues/11
				
/*				if (prChecker) {
					//TODO: implement PR_Checker creation
				}
*/				
				
				if (prMerger) {
					//TODO: implement auto-deploy artifact job
				}


				if (suiteFilter.endsWith("/")) {
					//remove last character if it is slash
					suiteFilter = suiteFilter[0..-2]
				}
				def testngFolder = suiteFilter.substring(suiteFilter.lastIndexOf("/"), suiteFilter.length()) + "/"
				context.println("testngFolder: " + testngFolder)

				
				// VIEWS
				dslFactories.put("cron", new ListViewFactory(jobFolder, 'CRON', '.*cron.*'))
				dslFactories.put(project, new ListViewFactory(jobFolder, project.toUpperCase(), ".*${project}.*"))

				// find all tetsng suite xml files and launch dsl creator scripts (views, folders, jobs etc)
				def suites = context.findFiles(glob: subProjectFilter + "/" + suiteFilter + "/**")
				for (File suite : suites) {
					if (!suite.path.endsWith(".xml")) {
						continue;
					}
					context.println("suite: " + suite.path)
					def suiteOwner = "anonymous"

					def suiteName = suite.path
					suiteName = suiteName.substring(suiteName.lastIndexOf(testngFolder) + testngFolder.length(), suiteName.indexOf(".xml"))

					//TODO: remove/comment below factories
					// --- JUST IN DEMO PURPOSED
					dslFactories.put("categorizedView", new CategorizedViewFactory(jobFolder, 'Categorized', '.*', 'API|Web|Android|iOS'))
					dslFactories.put("job1", new JobFactory(jobFolder, "job1", "desc1", 10))
					dslFactories.put("job2", new JobFactory(jobFolder, "job2", "desc2"))
					dslFactories.put("job3", new BuildJobFactory(jobFolder, "job3", "desc3"))
					dslFactories.put("pipeline1", new PipelineFactory(jobFolder, "pipeline1", "project: ${project}; zafira_project: ${zafira_project}; owner: ${suiteOwner}"))
					// --- JUST IN DEMO PURPOSED
					
					try {
						XmlSuite currentSuite = parseSuite(workspace + "/" + suite.path)
						if (currentSuite.toXml().contains("jenkinsJobCreation") && currentSuite.getParameter("jenkinsJobCreation").contains("true")) {
							context.println("suite name: " + suiteName)
							context.println("suite path: " + suite.path)

							if (currentSuite.toXml().contains("suiteOwner")) {
								suiteOwner = currentSuite.getParameter("suiteOwner")
							}
							if (currentSuite.toXml().contains("zafira_project")) {
								zafira_project = currentSuite.getParameter("zafira_project")
							}
							
							// put standard views factory into the map
							dslFactories.put(zafira_project, new ListViewFactory(jobFolder, zafira_project, ".*${zafira_project}.*"))
							dslFactories.put(suiteOwner, new ListViewFactory(jobFolder, suiteOwner, ".*${suiteOwner}"))
		
							//pipeline job
							//TODO: review each argument to TestNGPipelineFactory and think about removal, rename class(?!)
							//TODO: verify suiteName duplication here and generate email failure to the owner and admin_emails
							dslFactories.put(suiteName, new TestNGPipelineFactory(jobFolder, project, sub_project, zafira_project, getWorkspace() + "/" + suite.path, suiteName))
							
							//cron job
							//TODO: 
							// 1. restore boolean creat/recreate cron logic
							// 2. create new CronPipelineFactory extending PipelineFactory
							// 3. move implementatin from Job.createRegressionPipeline to CronPipelineFactory.create()
							// 4. uncomment below code and adjust according to above points
/*							boolean createCron = true
							if (createCron && !currentSuite.getParameter("jenkinsRegressionPipeline").toString().contains("null")) {
								def cronJobNames = currentSuite.getParameter("jenkinsRegressionPipeline").toString()
								for (def cronJobName : cronJobNames.split(",")) {
									cronJobName = cronJobName.trim()
									job.createRegressionPipeline(context.pipelineJob(jobFolder + "/" + cronJobName), currentSuite, project, sub_project)
								}
							}*/
						}
						
					} catch (FileNotFoundException e) {
						context.echo("ERROR! Unable to find suite: " + suite.path)
					} catch (Exception e) {
						context.echo("ERROR! Unable to parse suite: " + suite.path, e)
					}
					
				}
				
				// put into the factories.json all declared jobdsl factories to verify and create/recreate/remove etc
				context.writeFile file: "factories.json", text: JsonOutput.toJson(dslFactories)
				
				//TODO: test carefully auto-removal for jobs/views and configs
				context.jobDsl additionalClasspath: 'qps-pipeline/src', \
					removedConfigFilesAction: 'DELETE', removedJobAction: 'DELETE', removedViewAction: 'DELETE', \
					targets: 'qps-pipeline/src/com/qaprosoft/jenkins/repository/jobdsl/Creator.groovy'
			}
		}
	}

}