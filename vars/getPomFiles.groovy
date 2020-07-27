import com.qaprosoft.jenkins.Logger
import groovy.transform.Field

@Field final Logger logger = new Logger(this)

def call() {
    def pomFiles = []

    def files = findFiles(glob: "**/pom.xml")
    if (files.length > 0) {
        logger.info("Number of pom.xml files to analyze: " + files.length)

        int curLevel = 5 //do not analyze projects where highest pom.xml level is lower or equal 5
        for (pomFile in files) {
            def path = pomFile.path
            int level = path.count("/")
            logger.debug("file: " + path + "; level: " + level + "; curLevel: " + curLevel)
            if (level < curLevel) {
                curLevel = level
                pomFiles.clear()
                pomFiles.add(pomFile.path)
            } else if (level == curLevel) {
                pomFiles.add(pomFile.path)
            }
        }
        logger.info("PROJECT POMS: " + pomFiles)
    }
    return pomFiles
}
