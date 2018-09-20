package com.qaprosoft.jenkins.pipeline

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.Parser;
import org.testng.xml.XmlSuite;
import com.cloudbees.groovy.cps.NonCPS

import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths

import com.qaprosoft.jenkins.pipeline.AbstractRunner
import com.qaprosoft.jenkins.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.TestJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.CronJobFactory
import com.qaprosoft.jenkins.jobdsl.factory.folder.FolderFactory
import com.qaprosoft.scm.github.GitHub;


import hudson.plugins.sonar.SonarGlobalConfiguration

public class QARunner extends AbstractRunner {
	protected Map dslObjects = [:]
	
	protected def pipelineLibrary = "QPS-Pipeline"
	protected def runnerClass = "com.qaprosoft.jenkins.pipeline.impl.QARunner"

	
	public QARunner(context) {
		super(context)
		scmClient = new GitHub(context)
	}

	//Events
	public void onPush() {
		context.node("master") {
			context.timestamps {
				context.println("QARunner->onPush")
				prepare()
				boolean onlyUpdated = Configuration.get("onlyUpdated").toBoolean()
				if (!isUpdated("**.xml,**/zafira.properties") && onlyUpdated) {
					context.println("do not continue scanner as none of suite was updated ( *.xml )")
					return
				}
				//TODO: implement repository scan and QA jobs recreation
				scan()
				clean()
			}
		}
	}

	public void onPullRequest() {
		context.node("master") {
			context.println("QARunner->onPullRequest")
			scmClient.clonePR()

			context.stage('Maven Compile') {
				def goals = "clean compile test-compile \
						 -f pom.xml -Dmaven.test.failure.ignore=true \
						-Dcom.qaprosoft.carina-core.version=${Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)}"

				executeMavenGoals(goals)
			}
			context.stage('Sonar Scanner') { 
				performSonarQubeScan() 
			}

			//TODO: investigate whether we need this piece of code
			//            if (Configuration.get("ghprbPullTitle").contains("automerge")) {
			//                scmClient.mergePR()
			//            }
		}
	}
	
	protected void prepare() {
		boolean onlyUpdated = Configuration.get("onlyUpdated").toBoolean()
		scmClient.clone(!onlyUpdated)
		String QPS_PIPELINE_GIT_URL = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_URL)
		String QPS_PIPELINE_GIT_BRANCH = Configuration.get(Configuration.Parameter.QPS_PIPELINE_GIT_BRANCH)
		scmClient.clone(QPS_PIPELINE_GIT_URL, QPS_PIPELINE_GIT_BRANCH, "qps-pipeline")
	}

	
	protected def void executeMavenGoals(goals){
		if (context.isUnix()) {
			context.sh "'mvn' -B ${goals}"
		} else {
			context.bat "mvn -B ${goals}"
		}
	}
	
	protected void performSonarQubeScan(){
		def sonarQubeEnv = ''
		Jenkins.getInstance().getDescriptorByType(SonarGlobalConfiguration.class).getInstallations().each { installation ->
			sonarQubeEnv = installation.getName()
		}
		if(sonarQubeEnv.isEmpty()){
			context.println "There is no SonarQube server configured. Please, configure Jenkins for performing SonarQube scan."
			return
		}
		//TODO: find a way to get somehow 2 below hardcoded string values
		context.stage('SonarQube analysis') {
			context.withSonarQubeEnv(sonarQubeEnv) {
				context.sh "mvn clean package sonar:sonar -DskipTests \
				 -Dsonar.github.endpoint=${Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_API_URL)}")} \
				 -Dsonar.analysis.mode=preview  \
				 -Dsonar.github.pullRequest=${Configuration.get("ghprbPullId")} \
				 -Dsonar.github.repository=${Configuration.get("ghprbGhRepository")} \
				 -Dsonar.projectKey=${Configuration.get("project")} \
				 -Dsonar.projectName=${Configuration.get("project")} \
				 -Dsonar.projectVersion=1.${Configuration.get(Configuration.Parameter.BUILD_NUMBER)} \
				 -Dsonar.github.oauth=${Configuration.get(Configuration.Parameter.GITHUB_OAUTH_TOKEN)} \
				 -Dsonar.sources=. \
				 -Dsonar.tests=. \
				 -Dsonar.inclusions=**/src/main/java/** \
				 -Dsonar.test.inclusions=**/src/test/java/** \
				 -Dsonar.java.source=1.8"
			}
		}
	}
	/** **/
	
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
						//TODO: figure our howto work with Jenkinsfile
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
		
						context.println("factoryTarget: " + FACTORY_TARGET)
						//TODO: test carefully auto-removal for jobs/views and configs
						context.jobDsl additionalClasspath: EXTRA_CLASSPATH,
						removedConfigFilesAction: removedConfigFilesAction,
						removedJobAction: removedJobAction,
						removedViewAction: removedViewAction,
						targets: FACTORY_TARGET,
						ignoreExisting: false
					}
				}
			}
		
	
	protected clean() {
		context.stage('Wipe out Workspace') {
			context.deleteDir()
		}
	}


    /** Detects if any changes are present in files matching patterns  */
    @NonCPS
    protected boolean isUpdated(String patterns) {
        def isUpdated = false
        def changeLogSets = context.currentBuild.rawBuild.changeSets
        changeLogSets.each { changeLogSet ->
            /* Extracts GitChangeLogs from changeLogSet */
            for (entry in changeLogSet.getItems()) {
                /* Extracts paths to changed files */
                for (path in entry.getPaths()) {
                    context.println("UPDATED: " + path.getPath())
                    Path pathObject = Paths.get(path.getPath())
                    /* Checks whether any changed file matches one of patterns */
                    for (pattern in patterns.split(",")){
                        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern)
                        /* As only match is found stop search*/
                        if (matcher.matches(pathObject)){
                            isUpdated = true
                            return
                        }
                    }
                }
            }
        }
        return isUpdated
    }

	protected String getWorkspace() {
		return context.pwd()
	}

	protected Object parseJSON(String path) {
		def inputFile = new File(path)
		def content = new JsonSlurperClassic().parseFile(inputFile, 'UTF-8')
		return content
	}

	protected XmlSuite parseSuite(String path) {
		def xmlFile = new Parser(path)
		xmlFile.setLoadClasses(false)

		List<XmlSuite> suiteXml = xmlFile.parseToList()
		XmlSuite currentSuite = suiteXml.get(0)
		return currentSuite
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
