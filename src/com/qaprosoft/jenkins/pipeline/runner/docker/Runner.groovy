package com.qaprosoft.jenkins.pipeline.runner.docker

import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.Utils

import static com.qaprosoft.jenkins.pipeline.Executor.*

class Runner extends AbstractRunner {
	
	protected def pipelineLibrary
	protected def runnerClass
	protected def registry
	protected def registryCreds
	protected def releaseName
	protected def dockerFile
  	protected def buildTool
	
	public Runner(context) {
		super(context)
		pipelineLibrary = "QPS-Pipeline"
		runnerClass = "com.qaprosoft.jenkins.pipeline.runner.docker.Runner"
		registry = "${Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)}/${Configuration.get("repo")}"
		registryCreds = "${Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)}-docker"
		releaseName = "1.${Configuration.get("BUILD_NUMBER")}-SNAPSHOT"
		buildTool = Configuration.get("build_tool")
		dockerFile = Configuration.get("DOCKERFILE")
		context.currentBuild.setDisplayName(releaseName)
	}

	@Override
	public void onPush() {
		context.node('docker') {
			context.timestamps {
				logger.info('DockerRunner->onPush')
				try {
					getScm().clonePush()
					context.dockerDeploy(releaseName, registry, registryCreds)
				} catch (Exception e) {
					logger.error("Something went wrong while pushing the docker image. \n" + Utils.printStackTrace(e))
					context.currentBuild.result = BuildResult.FAILURE
				} finally {
					clean()
				}
			}
		}
	}

	@Override
	public void onPullRequest() {
		context.node('docker') {
			context.timestamps {
				logger.info('DockerRunner->onPullRequest')
				try {
					context.currentBuild.setDisplayName(releaseName)
					getScm().clonePR()
					def image = context.dockerDeploy.build(releaseName, registry)
					context.dockerDeploy.clean(image)
				} catch (Exception e) {
					logger.error("Something went wrong while building the docker image. \n" + Utils.printStackTrace(e))
					context.currentBuild.result = BuildResult.FAILURE
				} finally {
					clean()
				}
			}
		}
	}

	@Override
	public void build() {
		context.node('docker') {
			context.timestamps {
				logger.info('DockerRunner->build')
				try {
					setDisplayNameTemplate("#${releaseName}|${Configuration.get('branch')}")
					currentBuild.displayName = getDisplayName()
					getScm().clone()

          context.stage("${this.buildTool} build") {
						switch (buildTool.toLowerCase()) {
							case 'maven':
								context.mavenBuild(Configuration.get('maven_goals'))
								break
							case 'gradle':
								context.gradleBuild('./gradlew ' + Configuration.get('gradle_tasks'))
								break
						}
					}

				} catch (Exception e) {
					logger.error("Something went wrond while building the project. \n" + Utils.printStackTrace(e))
					context.currentBuild.result = BuildResult.FAILURE
				}

				try {
					context.currentBuild.setDisplayName(releaseName)
					context.dockerDeploy(releaseName, registry, registryCreds, dockerFile)
				} catch(Exception e) {
					logger.error("Something went wrond while pushin the image. \n" + Utils.printStackTrace(e))
					context.currentBuild.result = BuildResult.FAILURE
				} finally {
					clean()
				}
			}
		}
	}

}