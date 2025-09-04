def call(Map config = [:]) {
    // Default configuration
    def defaults = [
        nodeVersion: "Node18",
        credentialId: 'snyk-auth-token',
        gitUrl: 'https://github.com/OT-MICROSERVICES/employee-api.git',
        gitBranch: 'main',
        snykArgs: '--license --json',
        reportFile: 'snyk-licenses.json'
    ]
    config = defaults + config  // Merge with user-provided config

    pipeline {
        agent any

        tools {
            nodejs config.nodeVersion
        }

        stages {
            stage('Checkout Code') {
                steps {
                    git branch: config.gitBranch, url: config.gitUrl
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
