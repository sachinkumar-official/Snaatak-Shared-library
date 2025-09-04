def call(Map cfg = [:]) {
  // ---- Defaults (override via call args) ----
  def d = [
    gitBranch      : 'main',
    gitRepo        : 'https://github.com/OT-MICROSERVICES/salary-api.git',
    emailTo        : 'chaudhary2000sachin@gmail.com',
    slackChannel   : '#jenkins-pipeline-notification',
    mvnCmd         : 'mvn -B -ntp clean compile',   // -B = batch; -ntp = no transfer progress
    timeoutMinutes : 30,
    archivePattern : '',                             // e.g. 'target/**/*.jar' (empty = skip)
    mavenTool      : null,                           // e.g. 'Maven3' (Global Tool Config name)
    jdkTool        : null                            // e.g. 'jdk17' (Global Tool Config name)
  ] + (cfg ?: [:])

  pipeline {
    agent any

    options {
      timestamps()
      ansiColor('xterm')
      disableConcurrentBuilds()
      buildDiscarder(logRotator(numToKeepStr: '20'))
      timeout(time: d.timeoutMinutes as int, unit: 'MINUTES')
      skipDefaultCheckout(true)
    }

    parameters {
      string(name: 'GIT_BRANCH',    defaultValue: d.gitBranch,    description: 'Git branch to build')
      string(name: 'GIT_REPO',      defaultValue: d.gitRepo,      description: 'Git repository URL')
      string(name: 'EMAIL_TO',      defaultValue: d.emailTo,      description: 'Email address for notifications (empty to skip)')
      string(name: 'SLACK_CHANNEL', defaultValue: d.slackChannel, description: 'Slack channel (empty to skip)')
    }

    environment {
      GIT_BRANCH    = "${params.GIT_BRANCH}"
      GIT_REPO      = "${params.GIT_REPO}"
      EMAIL_TO      = "${params.EMAIL_TO}"
      SLACK_CHANNEL = "${params.SLACK_CHANNEL}"
    }

    stages {
      stage('Checkout') {
        steps {
          retry(3) {
            git branch: "${env.GIT_BRANCH}", url: "${env.GIT_REPO}"
          }
        }
      }

      stage('Setup Tools (optional)') {
        steps {
          script {
            // Only runs if configured in cfg
            if (d.mavenTool) {
              def mvnHome = tool name: d.mavenTool, type: 'maven'
              env.PATH = "${mvnHome}/bin:${env.PATH}"
              echo "Maven tool configured: ${d.mavenTool}"
            }
            if (d.jdkTool) {
              def jdkHome = tool name: d.jdkTool, type: 'jdk'
              env.JAVA_HOME = jdkHome
              env.PATH = "${jdkHome}/bin:${env.PATH}"
              echo "JDK tool configured: ${d.jdkTool}"
            }
            if (!d.mavenTool && !d.jdkTool) {
              echo 'No tools configured via cfg.mavenTool/jdkTool; assuming they are on PATH.'
            }
          }
        }
      }

      stage('Build') {
        steps {
          sh d.mvnCmd
        }
      }
    }

    post {
      always {
        script {
          if (d.archivePattern) {
            try {
              archiveArtifacts artifacts: d.archivePattern, fingerprint: true
            } catch (err) {
              echo "Archiving failed (non-fatal): ${err}"
            }
          }
        }
      }

      success {
        script {
          if (env.EMAIL_TO?.trim()) {
            try {
              emailext(
                to: env.EMAIL_TO,
                subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: "The build succeeded! Logs: ${env.BUILD_URL}"
              )
            } catch (err) {
              echo "Email notification failed (non-fatal): ${err}"
            }
          }
          if (env.SLACK_CHANNEL?.trim()) {
            try {
              slackSend(
                channel: env.SLACK_CHANNEL,
                message: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BUILD_URL})"
              )
            } catch (err) {
              echo "Slack notification failed (non-fatal): ${err}"
            }
          }
        }
      }

      failure {
        script {
          if (env.EMAIL_TO?.trim()) {
            try {
              emailext(
                to: env.EMAIL_TO,
                subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: "The build failed! Logs: ${env.BUILD_URL}"
              )
            } catch (err) {
              echo "Email notification failed (non-fatal): ${err}"
            }
          }
          if (env.SLACK_CHANNEL?.trim()) {
            try {
              slackSend(
                channel: env.SLACK_CHANNEL,
                message: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BUILD_URL})"
              )
            } catch (err) {
              echo "Slack notification failed (non-fatal): ${err}"
            }
          }
        }
      }
    }
  }
}
