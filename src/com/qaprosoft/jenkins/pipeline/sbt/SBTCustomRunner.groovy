package com.qaprosoft.jenkins.pipeline.sbt

import com.qaprosoft.Utils
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.AbstractRunner
import java.util.Date
import groovy.transform.InheritConstructors
import java.text.SimpleDateFormat


@InheritConstructors
class SBTCustomRunner extends SBTRunner {

    String randomCompareArchiveName = "loadTestingReports" + curDate + ".zip"

    @override
    protected void publishJenkinsReports() {
        context.stage('Results') {
            context.zip archive: true, dir: 'comparasionReports', glob: '', zipFile: randomCompareArchiveName

        }
    }

}