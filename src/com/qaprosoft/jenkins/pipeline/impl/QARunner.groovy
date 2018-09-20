package com.qaprosoft.jenkins.pipeline.impl

import com.qaprosoft.jenkins.pipeline.Executor
import com.qaprosoft.zafira.ZafiraClient
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

//[VD] do not remove this important import!
import com.qaprosoft.jenkins.pipeline.Configuration

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
    protected static final String zafiraReport = "ZafiraReport"
	protected def onlyUpdated = false
    protected def uuid
    protected def zc
	protected def JobType jobType = JobType.JOB
	
	public enum JobType {
		JOB("JOB"),
		CRON("CRON")
		
		private final String type

		JobType(String type) {
			this.type = type
		}

		@NonCPS
		public String getType() {
			return type
		}
	}
	
	public QARunner(context) {
		super(context)
		scmClient = new GitHub(context)
        zc = new ZafiraClient(context)
		if (Configuration.get("onlyUpdated") != null) {
			onlyUpdated = Configuration.get("onlyUpdated").toBoolean()
		}
	}
	
	public QARunner(context, jobType) {
		this (context)
		this.jobType = jobType
	}
	
	//Methods
	public void build() {
		context.node("master") {
			context.println("QARunner->build")
			if (jobType.equals(JobType.JOB)) {
				runJob()
			}
			if (jobType.equals(JobType.CRON)) {
				runCron()
			}
			//TODO: identify if it is job or cron and execute appropriate protected method
		}
	}


	//Events
	public void onPush() {
		context.node("master") {
			context.timestamps {
				context.println("QARunner->onPush")
				prepare()
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
                                Executor.printStackTrace(context, e)
							} catch (Exception e) {
								context.println("ERROR! Unable to parse suite: " + suite.path)
                                Executor.printStackTrace(context, e)
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

	public static Object parseJSON(String path) {
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
		return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
	}
	
	protected String getCronPipelineScript() {
		return "@Library(\'${pipelineLibrary}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
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

	protected void runJob() {
		context.println("QARunner->runJob")
        //use this method to override any beforeRunJob logic
        beforeRunJob()

        uuid = Executor.getUUID()
        context.println "UUID: " + uuid
        String nodeName = "master"

        //TODO: remove master node assignment
        context.node(nodeName) {
            zc.queueZafiraTestRun(uuid)
            nodeName = chooseNode()
        }

        context.node(nodeName) {
            context.wrap([$class: 'BuildUser']) {
                try {
                    context.timestamps {

                        prepareBuild(context.currentBuild)
                        scmClient.clone()

                        downloadResources()

                        def timeoutValue = Configuration.get(Configuration.Parameter.JOB_MAX_RUN_TIME)
                        context.timeout(time: timeoutValue.toInteger(), unit: 'MINUTES') {
                            this.build()
                        }

                        //TODO: think about seperate stage for uploading jacoco reports
                        this.publishJacocoReport()
                    }

                } catch (Exception ex) {
                    Executor.printStackTrace(context, ex)
                    String failureReason = getFailure(context.currentBuild)
                    context.echo "failureReason: ${failureReason}"
                    //explicitly execute abort to resolve anomalies with in_progress tests...
                    zc.abortZafiraTestRun(uuid, failureReason)
                    throw ex
                } finally {
                    this.exportZafiraReport()
                    this.sendTestRunResultsEmail()
                    this.reportingResults()
                    //TODO: send notification via email, slack, hipchat and whatever... based on subscription rules
                    this.clean()
                }
            }
        }
	}

    protected void beforeRunJob() {
        // do nothing
    }

    protected String chooseNode() {

        Configuration.set("node", "master") //master is default node to execute job

        //TODO: handle browserstack etc integration here?
        switch(Configuration.get("platform").toLowerCase()) {
            case "api":
                context.println("Suite Type: API")
                Configuration.set("node", "api")
                Configuration.set("browser", "NULL")
                break;
            case "android":
                context.println("Suite Type: ANDROID")
                Configuration.set("node", "android")
                break;
            case "ios":
                //TODO: Need to improve this to be able to handle where emulator vs. physical tests should be run.
                context.println("Suite Type: iOS")
                Configuration.set("node", "ios")
                break;
            default:
                if ("NULL".equals(Configuration.get("browser"))) {
                    context.println("Suite Type: Default")
                    Configuration.set("node", "master")
                } else {
                    context.println("Suite Type: Web")
                    Configuration.set("node", "web")
                }
        }

        def nodeLabel = Configuration.get("node_label")
        context.println("nodeLabel: " + nodeLabel)
        if (!Executor.isParamEmpty(nodeLabel)) {
            context.println("overriding default node to: " + nodeLabel)
            Configuration.set("node", nodeLabel)
        }

        context.println "node: " + Configuration.get("node")
        return Configuration.get("node")
    }

    //TODO: moved almost everything into argument to be able to move this methoud outside of the current class later if necessary
    protected void prepareBuild(currentBuild) {

        Configuration.set("BUILD_USER_ID", getBuildUser())

        String BUILD_NUMBER = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
        String CARINA_CORE_VERSION = Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)
        String suite = Configuration.get("suite")
        String branch = Configuration.get("branch")
        String env = Configuration.get("env")
        //TODO: rename to devicePool
        String devicePool = Configuration.get("devicePool")
        String browser = Configuration.get("browser")

        //TODO: improve carina to detect browser_version on the fly
        String browser_version = Configuration.get("browser_version")

        context.stage('Preparation') {
            currentBuild.displayName = "#${BUILD_NUMBER}|${suite}|${env}|${branch}"
            if (!Executor.isParamEmpty("${CARINA_CORE_VERSION}")) {
                currentBuild.displayName += "|" + "${CARINA_CORE_VERSION}"
            }
            if (!Executor.isParamEmpty(devicePool)) {
                currentBuild.displayName += "|${devicePool}"
            }
            if (!Executor.isParamEmpty(Configuration.get("browser"))) {
                currentBuild.displayName += "|${browser}"
            }
            if (!Executor.isParamEmpty(Configuration.get("browser_version"))) {
                currentBuild.displayName += "|${browser_version}"
            }
            currentBuild.description = "${suite}"

            if (Executor.isMobile()) {
                //this is mobile test
                prepareForMobile()
            }
        }
    }

    protected String getBuildUser() {
        try {
            return context.currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
        } catch (Exception e) {
            return ""
        }
    }

    protected void prepareForMobile() {
        context.println("Runner->prepareForMobile")
        def devicePool = Configuration.get("devicePool")
        def defaultPool = Configuration.get("DefaultPool")
        def platform = Configuration.get("platform")

        if (platform.equalsIgnoreCase("android")) {
            prepareForAndroid()
        } else if (platform.equalsIgnoreCase("ios")) {
            prepareForiOS()
        } else {
            context.echo "Unable to identify mobile platform: ${platform}"
        }

        //general mobile capabilities
        //TODO: find valid way for naming this global "MOBILE" quota
        Configuration.set("capabilities.deviceName", "QPS-HUB")
        if ("DefaultPool".equalsIgnoreCase(devicePool)) {
            //reuse list of devices from hidden parameter DefaultPool
            Configuration.set("capabilities.devicePool", defaultPool)
        } else {
            Configuration.set("capabilities.devicePool", devicePool)
        }
        // ATTENTION! Obligatory remove device from the params otherwise
        // hudson.remoting.Channel$CallSiteStackTrace: Remote call to JNLP4-connect connection from qpsinfra_jenkins-slave_1.qpsinfra_default/172.19.0.9:39487
        // Caused: java.io.IOException: remote file operation failed: /opt/jenkins/workspace/Automation/<JOB_NAME> at hudson.remoting.Channel@2834589:JNLP4-connect connection from
        Configuration.remove("device")
        //TODO: move it to the global jenkins variable
        Configuration.set("capabilities.newCommandTimeout", "180")
        Configuration.set("java.awt.headless", "true")

    }

    protected void prepareForAndroid() {
        context.println("Runner->prepareForAndroid")
        Configuration.set("mobile_app_clear_cache", "true")
        Configuration.set("capabilities.platformName", "ANDROID")
        Configuration.set("capabilities.autoGrantPermissions", "true")
        Configuration.set("capabilities.noSign", "true")
        Configuration.set("capabilities.STF_ENABLED", "true")
        Configuration.set("capabilities.appWaitDuration", "270000")
        Configuration.set("capabilities.androidInstallTimeout", "270000")
    }

    protected void prepareForiOS() {
        context.println("Runner->prepareForiOS")
        Configuration.set("capabilities.platform", "IOS")
        Configuration.set("capabilities.platformName", "IOS")
        Configuration.set("capabilities.deviceName", "*")
        Configuration.set("capabilities.appPackage", "")
        Configuration.set("capabilities.appActivity", "")
        Configuration.set("capabilities.autoAcceptAlerts", "true")
        Configuration.set("capabilities.STF_ENABLED", "false")
    }

    protected void downloadResources() {
        //DO NOTHING as of now

/*		def CARINA_CORE_VERSION = Configuration.get(Configuration.Parameter.CARINA_CORE_VERSION)
		context.stage("Download Resources") {
		def pomFile = Executor.getSubProjectFolder() + "/pom.xml"
		context.echo "pomFile: " + pomFile
			if (context.isUnix()) {
				context.sh "'mvn' -B -U -f ${pomFile} clean process-resources process-test-resources -Dcarina-core_version=$CARINA_CORE_VERSION"
			} else {
				//TODO: verify that forward slash is ok for windows nodes.
				context.bat(/"mvn" -B -U -f ${pomFile} clean process-resources process-test-resources -Dcarina-core_version=$CARINA_CORE_VERSION/)
			}
		}
*/	}

    //TODO: move into valid jacoco related package
    protected void publishJacocoReport() {
        def JACOCO_ENABLE = Configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean()
        if (!JACOCO_ENABLE) {
            context.println("do not publish any content to AWS S3 if integration is disabled")
            return
        }

        def JACOCO_BUCKET = Configuration.get(Configuration.Parameter.JACOCO_BUCKET)
        def JOB_NAME = Configuration.get(Configuration.Parameter.JOB_NAME)
        def BUILD_NUMBER = Configuration.get(Configuration.Parameter.BUILD_NUMBER)

        def files = context.findFiles(glob: '**/jacoco.exec')
        if(files.length == 1) {
            context.archiveArtifacts artifacts: '**/jacoco.exec', fingerprint: true, allowEmptyArchive: true
            // https://github.com/jenkinsci/pipeline-aws-plugin#s3upload
            //TODO: move region 'us-west-1' into the global var 'JACOCO_REGION'
            context.withAWS(region: 'us-west-1',credentials:'aws-jacoco-token') {
                context.s3Upload(bucket:"$JACOCO_BUCKET", path:"$JOB_NAME/$BUILD_NUMBER/jacoco-it.exec", includePathPattern:'**/jacoco.exec')
            }
        }
    }

     protected String getFailure(currentBuild) {
        //TODO: move string constants into object/enum if possible
        currentBuild.result = 'FAILURE'
        def failureReason = "undefined failure"

        String JOB_URL = Configuration.get(Configuration.Parameter.JOB_URL)
        String BUILD_NUMBER = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
        String JOB_NAME = Configuration.get(Configuration.Parameter.JOB_NAME)

        def bodyHeader = "<p>Unable to execute tests due to the unrecognized failure: ${JOB_URL}${BUILD_NUMBER}</p>"
        def subject = "UNRECOGNIZED FAILURE: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"

        if (currentBuild.rawBuild.log.contains("COMPILATION ERROR : ")) {
            failureReason = "COMPILATION ERROR"
            bodyHeader = "<p>Unable to execute tests due to the compilation failure. ${JOB_URL}${BUILD_NUMBER}</p>"
            subject = "COMPILATION FAILURE: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
        } else if (currentBuild.rawBuild.log.contains("BUILD FAILURE")) {
            failureReason = "BUILD FAILURE"
            bodyHeader = "<p>Unable to execute tests due to the build failure. ${JOB_URL}${BUILD_NUMBER}</p>"
            subject = "BUILD FAILURE: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
        } else  if (currentBuild.rawBuild.log.contains("Aborted by ")) {
            currentBuild.result = 'ABORTED'
            failureReason = "Aborted by " + getAbortCause(currentBuild)
            bodyHeader = "<p>Unable to continue tests due to the abort by " + getAbortCause(currentBuild) + " ${JOB_URL}${BUILD_NUMBER}</p>"
            subject = "ABORTED: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
        } else  if (currentBuild.rawBuild.log.contains("Cancelling nested steps due to timeout")) {
            currentBuild.result = 'ABORTED'
            failureReason = "Aborted by timeout"
            bodyHeader = "<p>Unable to continue tests due to the abort by timeout ${JOB_URL}${BUILD_NUMBER}</p>"
            subject = "TIMED OUT: ${JOB_NAME} - Build # ${BUILD_NUMBER}!"
        }


        def body = bodyHeader + """<br>Rebuild: ${JOB_URL}${BUILD_NUMBER}/rebuild/parameterized<br>
		${zafiraReport}: ${JOB_URL}${BUILD_NUMBER}/${zafiraReport}<br>
				Console: ${JOB_URL}${BUILD_NUMBER}/console"""

        //        def to = Configuration.get("email_list") + "," + Configuration.get(Configuration.Parameter.ADMIN_EMAILS)
        def to = Configuration.get(Configuration.Parameter.ADMIN_EMAILS)
        //TODO: enable emailing but seems like it should be moved to the notification code
        context.emailext Executor.getEmailParams(body, subject, to)
        return failureReason
    }

    protected void exportZafiraReport() {
        //replace existing local emailable-report.html by Zafira content
        def zafiraReport = zc.exportZafiraReport(uuid)
        //context.println(zafiraReport)
        if (!zafiraReport.isEmpty()) {
            context.writeFile file: getWorkspace() + "/zafira/report.html", text: zafiraReport
        }

        //TODO: think about method renaming because in additions it also could redefine job status in Jenkins.
        // or move below code into another method

        // set job status based on zafira report
        if (!zafiraReport.contains("PASSED:") && !zafiraReport.contains("PASSED (known issues):") && !zafiraReport.contains("SKIP_ALL:")) {
            //context.echo "Unable to Find (Passed) or (Passed Known Issues) within the eTAF Report."
            context.currentBuild.result = 'FAILURE'
        } else if (zafiraReport.contains("SKIP_ALL:")) {
            context.currentBuild.result = 'UNSTABLE'
        }
    }

    protected void sendTestRunResultsEmail() {
        String emailList = Configuration.get("email_list")
        emailList = overrideRecipients(emailList)
        String failureEmailList = Configuration.get("failure_email_list")

        if (emailList != null && !emailList.isEmpty()) {
            zc.sendTestRunResultsEmail(uuid, emailList, "all")
        }
        if (Executor.isFailure(context.currentBuild.rawBuild) && failureEmailList != null && !failureEmailList.isEmpty()) {
            zc.sendTestRunResultsEmail(uuid, failureEmailList, "failures")
        }
    }

    protected def overrideRecipients(emailList) {
        return emailList
    }

    protected void reportingResults() {
        context.stage('Results') {
            publishReports('**/zafira/report.html', "${zafiraReport}")
            publishReports('**/artifacts/**', 'eTAF_Artifacts')
            publishReports('**/target/surefire-reports/index.html', 'Full TestNG HTML Report')
            publishReports('**/target/surefire-reports/emailable-report.html', 'TestNG Summary HTML Report')
        }
    }

    protected void publishReports(String pattern, String reportName) {
        def reports = context.findFiles(glob: pattern)
        for (int i = 0; i < reports.length; i++) {
            def parentFile = new File(reports[i].path).getParentFile()
            if (parentFile == null) {
                context.println "ERROR! Parent report is null! for " + reports[i].path
                continue
            }
            def reportDir = parentFile.getPath()
            context.println "Report File Found, Publishing " + reports[i].path
            if (i > 0){
                def reportIndex = "_" + i
                reportName = reportName + reportIndex
            }
            context.publishHTML Executor.getReportParameters(reportDir, reports[i].name, reportName )
        }
    }

    protected void runCron() {
		context.println("QARunner->runCron")
	}

}
