package com.qaprosoft.jenkins.pipeline.runner.docker

import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner
import com.qaprosoft.jenkins.pipeline.Configuration

import com.qaprosoft.jenkins.Utils

class Runner extends AbstractRunner {

	private def registry
	private def registryCreds
	private def releaseName
	
	public Runner(context) {
		super(context)
		registry = "${Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)}/${Configuration.get("repo")}"
		registryCreds = ""
		releaseName = "1.${Configuration.get(Configuration.Parameter.BUILD_NUMBER)}-SNAPSHOT"
		context.currentBuild.setDisplayName(releaseName)
	}

	@Override
	public void onPush() {
		context.node('docker') {
			logger.info('DockerRunner->onPush')
			context.timestamps {
				getScm().clonePush()
				context.dockerBuild(releaseName, registry, registryCreds, "Dockerfile-jdk11")
				clean()
			}
		}
	}

	@Override
	public void onPullRequest() {

	}

	@Override
	public void build() {

	}
}