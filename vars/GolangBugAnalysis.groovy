def call(Map config) {
    node {
        def DEFAULT_SONAR_URL = 'http://43.205.114.58:9000'
        def DEFAULT_EMAIL_TO = 'chaudhary2000sachin@gmail.com'
        def DEFAULT_SONAR_SERVER = 'sonarqube'
        
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
            targetDir: config.targetDir ?: 'employee-api',
            gitCredentialsId: config.gitCredentialsId ?: '',
            sonarServerName: config.sonarServerName ?: DEFAULT_SONAR_SERVER
        ]

        def currentStage = ''

        try {
            // Stage 1: Clean Workspace
            currentStage = 'Clean Workspace'
            stage(currentStage) { 
                echo "Cleaning workspace..."
                cleanWs() 
            }

            // Stage 2: Checkout Code
            currentStage = 'Checkout Code'
            stage(currentStage) {
                echo "Checking out code from ${params.repoUrl}..."
                dir(params.targetDir) {
                    if (params.gitCredentialsId) {
                        git branch: params.repoBranch, 
                             url: params.repoUrl, 
                             credentialsId: params.gitCredentialsId
                    } else {
                        git branch: params.repoBranch, url: params.repoUrl
                    }
                }
            }

            // Stage 3: SonarQube Analysis
            currentStage = 'SonarQube Analysis'
            stage(currentStage) {
                echo "Running SonarQube analysis with server: ${params.sonarServerName}"
                
                // Check if credentials exist before proceeding
                def credentialsExist = checkCredentialsExist(params.credentialsId)
                if (!credentialsExist) {
                    error "Credentials '${params.credentialsId}' not found. Please configure them in Jenkins."
                }
                
                dir(params.targetDir) {
                    withSonarQubeEnv(params.sonarServerName) {
                        withCredentials([string(credentialsId: params.credentialsId, variable: 'SONAR_TOKEN')]) {
                            def scannerHome = tool params.scannerTool
                            sh """
                                ${scannerHome}/bin/sonar-scanner \
                                  -Dsonar.projectKey=${params.projectKey} \
                                  -Dsonar.projectName="${params.projectName}" \
                                  -Dsonar.sources=. \
                                  -Dsonar.sourceEncoding=UTF-8 \
                                  -Dsonar.host.url=${params.sonarUrl} \
                                  -Dsonar.login=\${SONAR_TOKEN}
                            """
                        }
                    }
                }
            }

            // Stage 4: Success Notification
            currentStage = 'Send Success Email'
            stage(currentStage) {
                echo "Sending success email..."
                def reportUrl = "${params.sonarUrl}/dashboard?id=${params.projectKey}"
                def trigger = getTrigger()
                def body = """
SonarQube Analysis Completed 

Job: ${env.JOB_NAME}
Build #: ${env.BUILD_NUMBER}
Triggered by: ${trigger}
Job URL: ${env.BUILD_URL}

Report: ${reportUrl}
"""
                mail(to: params.emailTo, 
                     subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}", 
                     body: body)
            }

        } catch (Exception err) {
            echo "ERROR: Build failed at stage '${currentStage}': ${err.message}"
            def trigger = getTrigger()
            def body = """
SonarQube Analysis FAILED

Job: ${env.JOB_NAME}
Build #: ${env.BUILD_NUMBER}
Triggered by: ${trigger}
Failed at: ${currentStage}
Error: ${err.message}
Logs: ${env.BUILD_URL}
"""
            mail(to: params.emailTo, 
                 subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}", 
                 body: body)
            error "Build failed at stage: ${currentStage}"
        }
    }
}

// Helper method to check if credentials exist
def checkCredentialsExist(String credentialsId) {
    try {
        // Try to access the credentials to see if they exist
        withCredentials([string(credentialsId: credentialsId, variable: 'TEST_TOKEN')]) {
            return true
        }
    } catch (Exception e) {
        return false
    }
}

// Helper method to get trigger information
def getTrigger() {
    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')?.getAt(0)
    return cause ? cause.userName : 'Auto-triggered'
}
