package com.qaprosoft.jenkins.pipeline

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.XmlSuite
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.TriggerJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.TestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.CronJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.folder.FolderFactory

import groovy.json.JsonOutput


class Scanner extends Executor {
	
	protected Map dslObjects = [:]
	
	protected def triggerScript = "@Library('QPS-Pipeline')\nimport com.qaprosoft.jenkins.pipeline.Repository;\nnew Repository(this).trigger()"
	
	protected def pipelineScript = "@Library('QPS-Pipeline')\nimport com.qaprosoft.jenkins.pipeline.Runner;\nnew Runner(this).runJob()"
	protected def cronPipelineScript = "@Library('QPS-Pipeline')\nimport com.qaprosoft.jenkins.pipeline.Runner;\nnew Runner(this).runCron()"
	
	protected def creatorTarget = "qps-pipeline/src/com/qaprosoft/jenkins/jobdsl/Creator.groovy"
	protected def additionalClasspath = "qps-pipeline/src"
	
	def onlyUpdated = false
	
    public Scanner(context) {
        super(context)
        this.context = context
        scmClient = new GitHub(context)
		
		if (Configurator.get("onlyUpdated") != null) {
			onlyUpdated = Configurator.get("onlyUpdated").toBoolean()
		}
    }

	
    public void updateRepository() {
		context.node('master') {
			context.timestamps {
                this.prepare()
                if (!isUpdated("**.xml") && onlyUpdated) {
					context.println("do not continue scanner as none of suite was updated ( *.xml )")
					return
                }
                this.scan()
                this.clean()
            }
        }
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

	protected void prepare() {
        scmClient.clone(!onlyUpdated)
		String QPS_PIPELINE_GIT_URL = Configurator.get(Configurator.Parameter.QPS_PIPELINE_GIT_URL)
		String QPS_PIPELINE_GIT_BRANCH = Configurator.get(Configurator.Parameter.QPS_PIPELINE_GIT_BRANCH)
		scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")
	}

	protected void create() {

		context.stage("Scan Repository") {
			def BUILD_NUMBER = Configurator.get(Configurator.Parameter.BUILD_NUMBER)
			def project = Configurator.get("project")
			def jobFolder = Configurator.get("project")

			def branch = Configurator.get("branch")
			context.currentBuild.displayName = "#${BUILD_NUMBER}|${project}|${branch}"

			def workspace = getWorkspace()
			context.println("WORKSPACE: ${workspace}")



			// TODO: move folder and main trigger job creation onto the createRepository method
			registerObject(jobFolder, new FolderFactory(jobFolder, ""))

			// Support DEV related CI workflow
			registerObject("trigger", new ListViewFactory(jobFolder, 'TRIGGER', '.*trigger.*'))
			registerObject(project, new TriggerJobFactory(jobFolder, getTriggerScript(), "_trigger-" + project, "trigger project: ${project};", project))


			// put into the factories.json all declared jobdsl factories to verify and create/recreate/remove etc
			context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)

			context.println("creatorTarget: " + creatorTarget)

			context.jobDsl additionalClasspath: additionalClasspath,
			removedConfigFilesAction: 'DELETE',
			removedJobAction: 'DELETE',
			removedViewAction: 'DELETE',
			targets: creatorTarget,
			ignoreExisting: false

		}
	}
			
	protected void scan() {

		context.stage("Scan Repository") {
			def BUILD_NUMBER = Configurator.get(Configurator.Parameter.BUILD_NUMBER)
			def project = Configurator.get("project")
			def jobFolder = Configurator.get("project")

			def branch = Configurator.get("branch")
			context.currentBuild.displayName = "#${BUILD_NUMBER}|${project}|${branch}"

			def workspace = getWorkspace()
			context.println("WORKSPACE: ${workspace}")

			def removedConfigFilesAction = Configurator.get("removedConfigFilesAction")
			def removedJobAction = Configurator.get("removedJobAction")
			def removedViewAction = Configurator.get("removedViewAction")


			// TODO: move folder and main trigger job creation onto the createRepository method
			registerObject(jobFolder, new FolderFactory(jobFolder, ""))

			// Support DEV related CI workflow
			registerObject("trigger", new ListViewFactory(jobFolder, 'TRIGGER', '.*trigger.*'))
			registerObject(project, new TriggerJobFactory(jobFolder, getTriggerScript(), "_trigger-" + project, "trigger project: ${project};", project))

			def jenkinsFileOrigin = "Jenkinsfile"
			if (context.fileExists("${workspace}/${jenkinsFileOrigin}")) {
				// this is the repo with already available pipeline script in Jenkinsfile
				// just create a job
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

				if (suiteFilter.endsWith("/")) {
					//remove last character if it is slash
					suiteFilter = suiteFilter[0..-2]
				}
				def testngFolder = suiteFilter.substring(suiteFilter.lastIndexOf("/"), suiteFilter.length()) + "/"
				context.println("testngFolder: " + testngFolder)

				// VIEWS
				registerObject("cron", new ListViewFactory(jobFolder, 'CRON', '.*cron.*'))
				//registerObject(project, new ListViewFactory(jobFolder, project.toUpperCase(), ".*${project}.*"))

				//TODO: create default personalized view here

				// find all tetsng suite xml files and launch dsl creator scripts (views, folders, jobs etc)
				def suites = context.findFiles(glob: subProjectFilter + "/" + suiteFilter + "/**")
				for (File suite : suites) {
					if (!suite.path.endsWith(".xml")) {
						continue
					}
					context.println("suite: " + suite.path)
					def suiteOwner = "anonymous"

					def suiteName = suite.path
					suiteName = suiteName.substring(suiteName.lastIndexOf(testngFolder) + testngFolder.length(), suiteName.indexOf(".xml"))

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
							registerObject(zafira_project, new ListViewFactory(jobFolder, zafira_project.toUpperCase(), ".*${zafira_project}.*"))
							registerObject(suiteOwner, new ListViewFactory(jobFolder, suiteOwner, ".*${suiteOwner}"))

							//pipeline job
							//TODO: review each argument to TestJobFactory and think about removal
							//TODO: verify suiteName duplication here and generate email failure to the owner and admin_emails
							def jobDesc = "project: ${project}; zafira_project: ${zafira_project}; owner: ${suiteOwner}"
							registerObject(suiteName, new TestJobFactory(jobFolder, getPipelineScript(), project, sub_project, zafira_project, getWorkspace() + "/" + suite.path, suiteName, jobDesc))

							//cron job
							if (!currentSuite.getParameter("jenkinsRegressionPipeline").toString().contains("null")
							&& !currentSuite.getParameter("jenkinsRegressionPipeline").toString().isEmpty()) {
								def cronJobNames = currentSuite.getParameter("jenkinsRegressionPipeline").toString()
								for (def cronJobName : cronJobNames.split(",")) {
									cronJobName = cronJobName.trim()
									def cronDesc = "project: ${project}; type: cron"
									registerObject(cronJobName, new CronJobFactory(jobFolder, getCronPipelineScript(), cronJobName, project, sub_project, getWorkspace() + "/" + suite.path, cronDesc))
								}
							}
						}

					} catch (FileNotFoundException e) {
						context.println("ERROR! Unable to find suite: " + suite.path)
					} catch (Exception e) {
						context.println("ERROR! Unable to parse suite: " + suite.path)
						printStackTrace(e)
					}

				}

				// put into the factories.json all declared jobdsl factories to verify and create/recreate/remove etc
				context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)

				context.println("creatorTarget: " + creatorTarget)
				//TODO: test carefully auto-removal for jobs/views and configs
				context.jobDsl additionalClasspath: additionalClasspath,
				removedConfigFilesAction: removedConfigFilesAction,
				removedJobAction: removedJobAction,
				removedViewAction: removedViewAction,
				targets: creatorTarget,
				ignoreExisting: false
			}
		}
	}

	protected void setTriggerScript(script) {
		this.triggerScript = script
	}
	
	protected String getTriggerScript() {
		return triggerScript
	}
	
	protected void setPipelineScript(script) {
		this.pipelineScript = script
	}
	
	protected String getPipelineScript() {
		return pipelineScript
	}
	
	protected void setCronPipelineScript(script) {
		this.cronPipelineScript = script
	}
	
	protected String getCronPipelineScript() {
		return cronPipelineScript
	}
	
	protected void registerObject(name, object) {
		dslObjects.put(name, object)
	}

	protected void setDslTargets(targets) {
		this.creatorTarget = targets
	}

	protected void setDslClasspath(additionalClasspath) {
		this.additionalClasspath = additionalClasspath
	}
}