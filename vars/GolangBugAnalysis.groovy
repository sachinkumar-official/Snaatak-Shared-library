def call(Map config) {
    node {
        def DEFAULT_SONAR_URL = 'http://13.53.121.108:9000'
        def DEFAULT_EMAIL_TO = 'chaudhary2000sachin@gmail.com'
        
        // Merge config with defaults
        def params = [
            sonarUrl: config.sonarUrl ?: DEFAULT_SONAR_URL,
            emailTo: config.emailTo ?: DEFAULT_EMAIL_TO,
            projectKey: config.projectKey ?: 'Bug_Analysis_employee_api',
            projectName: config.projectName ?: 'Golang CI Bug Analysis',
            credentialsId: config.credentialsId ?: 'sonar-token',
            scannerTool: config.scannerTool ?: 'sonar-scanner',
            repoUrl: config.repoUrl ?: 'https://github.com/OT-MICROSERVICES/employee-api.git',
            repoBranch: config.repoBranch ?: 'main',
            targetDir: config.targetDir ?: 'employee-api'
        ]

        def currentStage = ''

        try {
            // Stage 1: Clean Workspace
            currentStage = 'Clean Workspace'
            stage(currentStage) { cleanWs() }

            // Stage 2: Checkout Code
            currentStage = 'Checkout Code'
            stage(currentStage) {
                gitCheckout(params.targetDir, params.repoUrl, params.repoBranch)
            }

            // Stage 3: SonarQube Analysis
            currentStage = 'SonarQube Analysis'
            stage(currentStage) {
                sonarScan(
                    params.sonarUrl,
                    params.credentialsId,
                    params.scannerTool,
                    params.projectKey,
                    params.projectName
                )
            }

            // Stage 4: Success Notification
            currentStage = 'Send Success Email'
            stage(currentStage) {
                sendSuccessEmail(
                    params.emailTo,
                    params.sonarUrl,
                    params.projectKey,
                    env.JOB_NAME,
                    env.BUILD_NUMBER,
                    env.BUILD_URL
                )
            }

        } catch (Exception err) {
            sendFailureEmail(
                params.emailTo,
                env.JOB_NAME,
                env.BUILD_NUMBER,
                currentStage,
                env.BUILD_URL
            )
            error "Build failed at stage: ${currentStage}"
        }
    }
}

// Helper Methods
def gitCheckout(String dir, String url, String branch) {
    dir(dir) {
        git branch: branch, url: url
    }
}

def sonarScan(String url, String credsId, String tool, String key, String name) {
    withSonarQubeEnv('sonarqube') {
        withCredentials([string(credentialsId: credsId, variable: 'SONAR_TOKEN')]) {
            def scannerHome = tool tool
            sh """
                ${scannerHome}/bin/sonar-scanner \
                  -Dsonar.projectKey=${key} \
                  -Dsonar.projectName="${name}" \
                  -Dsonar.sources=. \
                  -Dsonar.sourceEncoding=UTF-8 \
                  -Dsonar.host.url=${url} \
                  -Dsonar.login=${SONAR_TOKEN}
            """
        }
    }
}

def sendSuccessEmail(String emailTo, String sonarUrl, String projectKey, 
                    String jobName, String buildNumber, String buildUrl) {
    def reportUrl = "${sonarUrl}/dashboard?id=${projectKey}"
    def trigger = getTrigger()
    def body = """
SonarQube Analysis Completed 

Job: ${jobName}
Build #: ${buildNumber}
Triggered by: ${trigger}
Job URL: ${buildUrl}

Report: ${reportUrl}
"""
    mail(to: emailTo, subject: "SUCCESS: ${jobName} #${buildNumber}", body: body)
}

def sendFailureEmail(String emailTo, String jobName, String buildNumber, 
                    String failedStage, String buildUrl) {
    def trigger = getTrigger()
    def body = """
SonarQube Analysis FAILED

Job: ${jobName}
Build #: ${buildNumber}
Triggered by: ${trigger}
Failed at: ${failedStage}
Logs: ${buildUrl}
"""
    mail(to: emailTo, subject: "FAILURE: ${jobName} #${buildNumber}", body: body)
}

def getTrigger() {
    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')?.getAt(0)
    return cause ? cause.userName : 'Auto-triggered'
}
