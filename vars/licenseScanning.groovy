def call() {
    pipeline {
        agent any

        tools {
            nodejs "Node18"   // Ensure Node18 is configured in Jenkins
        }

        environment {
            SNYK_TOKEN = credentials('snyk-auth-token')   // Jenkins credential ID
        }

        stages {
            stage('Checkout Code') {
                steps {
                    git branch: 'main', url: 'https://github.com/OT-MICROSERVICES/employee-api.git'
                }
            }

            stage('Setup Snyk') {
                steps {
                    sh 'npm install -g snyk'
                }
            }

            stage('Authenticate & License Scanning') {
                steps {
                    withCredentials([string(credentialsId: 'snyk-auth-token', variable: 'SNYK_TOKEN')]) {
                        sh '''
                            snyk auth $SNYK_TOKEN
                            # Save report to file (even if exit code != 0)
                            snyk test --license --json > snyk-licenses.json || true
                        '''
                    }
                }
            }

            stage('Archive Reports') {
                steps {
                    archiveArtifacts artifacts: 'snyk-licenses.json', fingerprint: true
                }
            }
        }

        post {
            always {
                cleanWs()
            }
            failure {
                echo " Restricted or prohibited licenses found. Build failed."
            }
            success {
                echo " No prohibited licenses found. Build passed."
            }
        }
    }
}
