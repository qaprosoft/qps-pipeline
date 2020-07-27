package com.qaprosoft.jenkins.pipeline.runner.docker

import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.Utils

import static com.qaprosoft.jenkins.pipeline.Executor.*

class Runner extends AbstractRunner {

	private def registry
	private def registryCreds
	private def releaseName
	private def dockerFile
	private def dockerFilePath
	
	public Runner(context) {
		super(context)
		registry = "${Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)}/${Configuration.get("repo")}"
		registryCreds = "${Configuration.get(Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION))}-docker"
		releaseName = "${}"
		//context.currentBuild.setDisplayName(releaseName)
	}

	@Override
	public void onPush() {
		context.node('docker') {
			context.timestamps {
				logger.info('DockerRunner->onPush')
				getScm().clonePush()
				context.dockerDeploy(releaseName, registry, registryCreds)
				clean()
			}
		}
	}

	@Override
	public void onPullRequest() {
		context.node('docker') {
			context.timestamps {
				logger.info('DockerRunner->onPullRequest')
				getScm().clonePR()

				try {
					def image = context.dockerDeploy.build(releaseName, registry)
					context.dockerDeploy.clean(image)
				} catch (Exception e) {
					logger.error("Something went wrong while building the docker image. \n" + e.getMessage())
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
				releaseName = Configuration.get('release_version')
				dockerFile = Configuration.get("dockerfile")
				getScm().clone()
				context.dockerDeploy(releaseName, registry, registryCreds, dockerFile)
				clean()
			}
		}
	}
}