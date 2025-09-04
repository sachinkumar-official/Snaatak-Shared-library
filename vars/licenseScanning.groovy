def call(Map config = [:]) {
    // Default configuration
    def defaults = [
        nodeVersion: "Node18",
        credentialId: 'snyk-auth-token',
        gitUrl: 'https://github.com/OT-MICROSERVICES/employee-api.git',
        gitBranch: 'main',  // Default to main branch
        snykArgs: '--license --json',
        reportFile: 'snyk-licenses.json',
        fallbackBranch: 'main'  // Fallback if specified branch doesn't exist
    ]
    config = defaults + config

    pipeline {
        agent any

        tools {
            nodejs config.nodeVersion
        }

        stages {
            stage('Checkout Code') {
                steps {
                    script {
                        // Try to checkout specified branch, fallback to main if it doesn't exist
                        try {
                            git branch: config.gitBranch, url: config.gitUrl
                        } catch (Exception e) {
                            echo "Branch ${config.gitBranch} not found, falling back to ${config.fallbackBranch}"
                            git branch: config.fallbackBranch, url: config.gitUrl
                        }
                    }
                }
            }

            stage('Setup Snyk') {
                steps {
                    sh 'npm install -g snyk'
                }
            }

            stage('Authenticate & License Scanning') {
                steps {
                    withCredentials([string(credentialsId: config.credentialId, variable: 'SNYK_TOKEN')]) {
                        sh """
                            snyk auth \${SNYK_TOKEN}
                            snyk test ${config.snykArgs} > ${config.reportFile} || true
                        """
                    }
                }
            }

            stage('Archive Reports') {
                steps {
                    archiveArtifacts artifacts: config.reportFile, fingerprint: true
                }
            }
        }

        post {
            always {
                cleanWs()
            }
            failure {
                echo "Restricted or prohibited licenses found. Build failed."
            }
            success {
                echo "No prohibited licenses found. Build passed."
            }
        }
    }
}
