package com.qaprosoft.jenkins.pipeline

import groovy.json.JsonSlurperClassic
@Grab('org.testng:testng:6.8.8')
import org.testng.xml.Parser;
import org.testng.xml.XmlSuite;
import com.cloudbees.groovy.cps.NonCPS

import com.qaprosoft.scm.ISCM

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths

import groovy.transform.InheritConstructors

@InheritConstructors
public class Runner {
//	protected def context
	
	public Runner(context) {
//		this.context = context
	}
	
	//Events
	public void onPush() {
//		context.println("Runner->onPush")
	}

	public void onPullRequest() {
//		context.println("Runner->onPullRequest")
    }
}
