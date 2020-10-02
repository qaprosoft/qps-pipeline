package com.qaprosoft.jenkins.pipeline.runner.docker

import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.Utils

import static com.qaprosoft.jenkins.pipeline.Executor.*

class Runner extends AbstractRunner {

	protected def registry
	protected def registryCreds
	protected def releaseName
	protected def dockerFile
	protected def dockerFilePath
	
	public Runner(context) {
		super(context)
		registry = "${Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)}/${Configuration.get("repo")}"
		registryCreds = "${Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)}-docker"
		releaseName = "1.${Configuration.get(Configuration.Parameter.BUILD_NUMBER)}-SNAPSHOT"
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
					def buildTool = Configuration.get("build_tool")
					releaseName = Configuration.get('release_version')
					dockerFile = Configuration.get("dockerfile")

					setDisplayNameTemplate("#${releaseName}|${Configuration.get('branch')}")
					currentBuild.displayName = getDisplayName()
					getScm().clone()

					context.stage("$buildTool build") {
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