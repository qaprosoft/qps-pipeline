package com.qaprosoft.jenkins.pipeline

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.XmlSuite
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PullRequestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook.PushJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.TestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.CronJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.folder.FolderFactory

import groovy.json.JsonOutput


class Scanner extends Executor {
	
	protected Map dslObjects = [:]
	
	protected def pipelineLibrary = "QPS-Pipeline"
	protected def runnerClass = "com.qaprosoft.jenkins.pipeline.Runner"
	
	protected def factoryTarget = "qps-pipeline/src/com/qaprosoft/jenkins/jobdsl/Factory.groovy"
	protected def additionalClasspath = "qps-pipeline/src"
	
	def onlyUpdated = false
	
    public Scanner(context) {
        super(context)
        this.context = context
        scmClient = new GitHub(context)
		
		if (Configuration.get("onlyUpdated") != null) {
			onlyUpdated = Configuration.get("onlyUpdated").toBoolean()
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
	
	protected void prepare() {
        scmClient.clone(!onlyUpdated)
		String QPS_PIPELINE_GIT_URL = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_URL)
		String QPS_PIPELINE_GIT_BRANCH = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_BRANCH)
		scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")
	}

	protected void create() {

		context.stage("Scan Repository") {
			def BUILD_NUMBER = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
			def project = Configuration.get("project")
			def jobFolder = Configuration.get("project")

			def branch = Configuration.get("branch")
			context.currentBuild.displayName = "#${BUILD_NUMBER}|${project}|${branch}"

			def workspace = getWorkspace()
			context.println("WORKSPACE: ${workspace}")



			// TODO: move folder and main trigger job creation onto the createRepository method
			def folder = new FolderFactory(jobFolder, "")
			registerObject("project_folder", new FolderFactory(jobFolder, ""))

			// Support DEV related CI workflow
            def gitUrl = Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_HTML_URL)}/${Configuration.get("project")}")

			registerObject("hooks_view", new ListViewFactory(jobFolder, 'SYSTEM', '.*system.*'))

            def pullRequestJobDescription = "To finish GitHub WebHook setup, please, follow the steps below:\n- Go to your GitHub repository\n- Click \"Settings\" tab\n- Click \"Webhooks\" menu option\n" +
                    "- Click \"Add webhook\" button\n- Type http://your-jenkins-domain.com/ghprbhook/ into \"Payload URL\" field\n" +
                    "- Select x-www-form-urlencoded in \"Content Type\" field\n- Tick \"Let me select individual events\" and tick \"Pull request\" and \"Issue comments\" options only\n- Click \"Add webhook\" button"

			registerObject("pull_request_job", new PullRequestJobFactory(jobFolder, getOnPullRequestScript(), "onPullRequest-" + project, pullRequestJobDescription, project, gitUrl))

            def pushJobDescription = "To finish GitHub WebHook setup, please, follow the steps below:\n- Go to your GitHub repository\n- Click \"Settings\" tab\n- Click \"Webhooks\" menu option\n" +
                    "- Click \"Add webhook\" button\n- Type http://your-jenkins-domain.com/github-webhook/ into \"Payload URL\" field\n" +
                    "- Select application/json in \"Content Type\" field\n- Tick \"Just the push event.\" option\n- Click \"Add webhook\" button"

            registerObject("push_job", new PushJobFactory(jobFolder, getOnPushScript(), "onPush-" + project, pushJobDescription, project))

			// put into the factories.json all declared jobdsl factories to verify and create/recreate/remove etc
			context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)

			context.println("factoryTarget: " + factoryTarget)

			context.jobDsl additionalClasspath: additionalClasspath,
			removedConfigFilesAction: 'IGNORE',
			removedJobAction: 'IGNORE',
			removedViewAction: 'IGNORE',
			targets: factoryTarget,
			ignoreExisting: false
			
		}
	}
			
	protected void scan() {

		context.stage("Scan Repository") {
			def BUILD_NUMBER = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
			def project = Configuration.get("project")
			def jobFolder = Configuration.get("project")

			def branch = Configuration.get("branch")
			context.currentBuild.displayName = "#${BUILD_NUMBER}|${project}|${branch}"

			def workspace = getWorkspace()
			context.println("WORKSPACE: ${workspace}")

			def removedConfigFilesAction = Configuration.get("removedConfigFilesAction")
			def removedJobAction = Configuration.get("removedJobAction")
			def removedViewAction = Configuration.get("removedViewAction")

			// Support DEV related CI workflow
			//TODO: analyze if we need 3 system object declarations

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

				context.println("factoryTarget: " + factoryTarget)
				//TODO: test carefully auto-removal for jobs/views and configs
				context.jobDsl additionalClasspath: additionalClasspath,
				removedConfigFilesAction: removedConfigFilesAction,
				removedJobAction: removedJobAction,
				removedViewAction: removedViewAction,
				targets: factoryTarget,
				ignoreExisting: false
			}
		}
	}
	
	//TODO: find valid way to override and remember custom scanner/runner etc class
	public void setPipelineLibrary(pipelineLibrary, runnerClass) {
		this.pipelineLibrary = pipelineLibrary
		this.runnerClass = runnerClass
	}

	protected String getOnPullRequestScript() {
		context.println("@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).onPullRequest()")
		return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).onPullRequest()"
	}
	
	protected String getOnPushScript() {
		return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).onPush()"
	}
	
	protected String getPipelineScript() {
		return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).runJob()"
	}
	
	protected String getCronPipelineScript() {
		return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).runCron()"
	}
	
	protected void registerObject(name, object) {
		if (dslObjects.containsKey(name)) {
			context.println("WARNING! key '" + name + "' already defined and will be replaced!")
			context.println("old item: " + dslObjects.get(name).dump())
			context.println("new item: " + object.dump())
		}
		dslObjects.put(name, object)
	}

	protected void setDslTargets(targets) {
		this.factoryTarget = targets
	}

	protected void setDslClasspath(additionalClasspath) {
		this.additionalClasspath = additionalClasspath
	}
}