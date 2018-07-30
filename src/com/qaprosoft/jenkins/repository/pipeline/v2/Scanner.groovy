package com.qaprosoft.jenkins.repository.pipeline.v2

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.XmlSuite
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.repository.pipeline.v2.Executor
import com.qaprosoft.jenkins.repository.pipeline.v2.Configurator
import com.qaprosoft.jenkins.repository.jobdsl.v2.Creator
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ViewType
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ListViewFactory2
import groovy.json.JsonOutput


class Scanner extends Executor {
	//TODO: specify default factory classes
	//protected String viewFactory = "CreateViewFactory"
	
	protected Map viewFactories = [:]

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
				context.writeFile file: "sub_project.txt", text: sub_project

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
				context.writeFile file: "zafira_project.txt", text: zafira_project

				//TODO: restore PR checker functionality after configuring by default sonarqube scanner in jenkins-master:
				// https://github.com/qaprosoft/jenkins-master/issues/11
				
/*				if (prChecker) {
					// PR_Checker is supported only for the repo with single sub_project!
					context.println("Launching Create-PR-Checker job for " + project)
					context.build job: 'Management_Jobs/Create-PR-Checker', \
							parameters: [context.string(name: 'project', value: project), context.string(name: 'sub_project', value: sub_project)], \
							wait: false
				}
*/				
				
				if (prMerger) {
					//TODO: implement auto-deploy artifact job
				}

				//TODO: [OPTIONAL] try to read existing views and compare with suggested settings. Maybe we can skip execution better
				List<String> views = []


				//TODO: #2 declare global list for created regression cron jobs
				//	   provide extra flag includeIntoCron for CreateJob
				List<String> crons = []


				if (suiteFilter.endsWith("/")) {
					//remove last character if it is slash
					suiteFilter = suiteFilter[0..-2]
				}
				def testngFolder = suiteFilter.substring(suiteFilter.lastIndexOf("/"), suiteFilter.length()) + "/"
				context.println("testngFolder: " + testngFolder)


				// find all tetsng suite xml files and launch job creator dsl job
				def suites = context.findFiles(glob: subProjectFilter + "/" + suiteFilter + "/**")
				for (File suite : suites) {
					if (!suite.path.endsWith(".xml")) {
						continue;
					}
					context.println("suite: " + suite.path)
					def suiteOwner = "anonymous"

					viewFactories.put("cronListfactory2", new ListViewFactory2(jobFolder, 'CRON', 'cron'))
					
					
					Map<String, ViewType> listViewFactories = [:]

					ViewType cronView = createListView(jobFolder, 'CRON', 'cron')
					listViewFactories.put(cronView.viewName, cronView)

					ViewType projectView = createListView(jobFolder, project.toUpperCase(), project)
					listViewFactories.put(projectView.viewName, projectView)

					ViewType zafiraProjectView = createListView(jobFolder, zafira_project, zafira_project)
					listViewFactories.put(zafiraProjectView.viewName, zafiraProjectView)

					ViewType suiteOwnerView = createListView(jobFolder, suiteOwner, suiteOwner)
					listViewFactories.put(suiteOwnerView.viewName, suiteOwnerView)

					context.writeFile file: "factory_data.txt", text: JsonOutput.toJson(listViewFactories)

					context.writeFile file: "suite_path.txt", text: getWorkspace() + "/" + suite.path

					def suiteName = suite.path
					suiteName = suiteName.substring(suiteName.lastIndexOf(testngFolder) + testngFolder.length(), suiteName.indexOf(".xml"))
					context.writeFile file: "suite_name.txt", text: suiteName

					context.jobDsl additionalClasspath: 'qps-pipeline/src', \
						targets: 'qps-pipeline/src/com/qaprosoft/jenkins/repository/jobdsl/v2/CreateJob.groovy'


					//TODO: transfer descFilter and maybe jobFolder, owner and zafira project 
                    context.jobDsl additionalClasspath: 'qps-pipeline/src', \
						targets: 'qps-pipeline/src/com/qaprosoft/jenkins/repository/jobdsl/v2/CreateView.groovy'


                    continue

					try {
						XmlSuite currentSuite = parseSuite(workspace + "/" + suite.path)

						if (currentSuite.toXml().contains("jenkinsJobCreation") && currentSuite.getParameter("jenkinsJobCreation").contains("true")) {
							//def suiteName = suite.path
							//suiteName = suiteName.substring(suiteName.lastIndexOf(testngFolder) + testngFolder.length(), suiteName.indexOf(".xml"))

							context.println("suite name: " + suiteName)
							context.println("suite path: " + suite.path)

							if (currentSuite.toXml().contains("suiteOwner")) {
								suiteOwner = currentSuite.getParameter("suiteOwner")
							}
							if (currentSuite.toXml().contains("zafira_project")) {
								zafira_project = currentSuite.getParameter("zafira_project")
							}

							if (!views.contains(project.toUpperCase())) {
								views << project.toUpperCase()
								context.build job: "Management_Jobs/CreateView",
									propagate: false,
									parameters: [context.string(name: 'folder', value: jobFolder), context.string(name: 'view', value: project.toUpperCase()), context.string(name: 'descFilter', value: project),]
							}

							//TODO: review later if we need views by zafira poject name and owner
							if (!views.contains(zafira_project)) {
								views << zafira_project

								context.build job: "Management_Jobs/CreateView",
									propagate: false,
									parameters: [context.string(name: 'folder', value: jobFolder), context.string(name: 'view', value: zafira_project), context.string(name: 'descFilter', value: zafira_project),]
							}

							if (!views.contains(suiteOwner)) {
								views << suiteOwner

								context.build job: "Management_Jobs/CreateView",
									propagate: false,
									parameters: [context.string(name: 'folder', value: jobFolder), context.string(name: 'view', value: suiteOwner), context.string(name: 'descFilter', value: suiteOwner),]
							}

	                        def createCron = false
	                        if (currentSuite.toXml().contains("jenkinsRegressionPipeline")) {
	                            def cronName = currentSuite.getParameter("jenkinsRegressionPipeline")

	                            if (!isItemAvailable(jobFolder + "/" + cronName)) {
	                                createCron = true
	                            }
	                            // we need only single regression cron declaration
	                            //createCron = !crons.contains(cronName)
	                            crons << cronName
	                        }

/*	                        context.build job: "Management_Jobs/CreateJob",
	                                propagate: false,
	                                parameters: [
	                                        context.string(name: 'jobFolder', value: jobFolder),
	                                        context.string(name: 'project', value: project),
	                                        context.string(name: 'sub_project', value: sub_project),
	                                        context.string(name: 'suite', value: suiteName),
	                                        context.string(name: 'suiteOwner', value: suiteOwner),
	                                        context.string(name: 'zafira_project', value: zafira_project),
	                                        context.string(name: 'suiteXML', value: parseSuiteToText(workspace + "/" + suite.path)),
	                                        context.booleanParam(name: 'createCron', value: createCron),
	                                ], wait: false*/

//							context.jobDsl additionalClasspath: 'qps-pipeline/src', scriptText: '''package com.qaprosoft.jenkins.repository.jobdsl.v2
//
//import com.qaprosoft.jenkins.repository.jobdsl.v2.Creator
//
//def creator = new Creator(this)
//creator.createJob()'''
						}
					} catch (FileNotFoundException e) {
						context.echo("ERROR! Unable to find suite: " + suite.path)
					} catch (Exception e) {
						context.echo("ERROR! Unable to parse suite: " + suite.path, e)
					}
				}
			}
		}
	}

	protected ViewType createListView(jobFolder, viewName, descFilter){
		ViewType newView = new ViewType(ListViewFactory.class.getCanonicalName(), jobFolder)
		newView.viewName = viewName
		newView.descFilter = descFilter
		return newView
	}
	
}