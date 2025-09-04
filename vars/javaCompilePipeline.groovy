def call(Map cfg = [:]) {
  def d = [
    gitBranch      : 'main',
    gitRepo        : 'https://github.com/OT-MICROSERVICES/salary-api.git',
    emailTo        : 'chaudhary2000sachin@gmail.com',
    mvnCmd         : 'mvn -B -ntp clean compile',
    timeoutMinutes : 30
  ] + (cfg ?: [:])

  pipeline {
    agent any

    options {
      timestamps()
      disableConcurrentBuilds()
      buildDiscarder(logRotator(numToKeepStr: '20'))
      timeout(time: d.timeoutMinutes as int, unit: 'MINUTES')
      skipDefaultCheckout(true)
    }

    parameters {
      string(name: 'GIT_BRANCH', defaultValue: d.gitBranch, description: 'Git branch to build')
      string(name: 'GIT_REPO',   defaultValue: d.gitRepo,   description: 'Git repository URL')
      string(name: 'EMAIL_TO',   defaultValue: d.emailTo,   description: 'Email address for notifications')
    }

    environment {
      GIT_BRANCH = "${params.GIT_BRANCH}"
      GIT_REPO   = "${params.GIT_REPO}"
      EMAIL_TO   = "${params.EMAIL_TO}"
      MVN_CMD    = "${d.mvnCmd}"
    }

    stages {
      stage('Checkout') {
        steps {
          retry(3) {
            git branch: "${env.GIT_BRANCH}", url: "${env.GIT_REPO}"
          }
        }
      }

      stage('Build') {
        steps {
          ansiColor('xterm') {
            sh "${env.MVN_CMD}"
          }
        }
      }
    }

    post {
      success {
        script {
          if (env.EMAIL_TO?.trim()) {
            emailext(
              to: env.EMAIL_TO,
              subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
              body: "The build succeeded! Logs: ${env.BUILD_URL}"
            )
          }
        }
      }
      failure {
        script {
          if (env.EMAIL_TO?.trim()) {
            emailext(
              to: env.EMAIL_TO,
              subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
              body: "The build failed! Logs: ${env.BUILD_URL}"
            )
          }
        }
      }
    }
  }
}
