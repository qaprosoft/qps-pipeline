package com.qaprosoft.jenkins.pipeline.runner.docker

import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner
import com.qaprosoft.jenkins.pipeline.Configuration

import com.qaprosoft.jenkins.Utils

class Runner extends AbstractRunner {

	private def registry
	private def registryCreds
	private def releaseName
	private def dockerFile
	private def dockerFilePath
	
	public Runner(context) {
		super(context)
		registry = "${Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)}/${Configuration.get("repo")}"
		registryCreds = "sandino1995-docker"
		releaseName = "${}"
		context.currentBuild.setDisplayName(releaseName)
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
				getScm.clonePR()
				
				try {
					def image = dockerDeploy.build(releaseName, registry)
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
				releaseName = Configuration.get('RELEASE_VERSION')
				dockerFile = Configuration.get("DOCKEFILE")
				def gitUrl = Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_HTML_URL)}/${Configuration.get('repo')}")
				getScm.clone(gitUrl, Configuration.get("BRANCH"), Configuration.get('repo'))
				context.dockerDeploy(releaseName, registry, registryCreds, dockerFile)
				clean()
			}
		}
	}
}