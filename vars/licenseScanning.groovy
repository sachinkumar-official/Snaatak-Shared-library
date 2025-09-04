// vars/licenseScanning.groovy
def call(Map config = [:]) {
    // default configuration
    config = [
        nodeTool                 : 'Node18',                                 // Jenkins NodeJS tool name
        gitUrl                   : 'https://github.com/OT-MICROSERVICES/employee-api.git',
        branch                   : 'main',
        snykCredentialId         : 'snyk-auth-token',                        // Jenkins credential (Secret Text)
        reportFile               : 'snyk-licenses.json',
        snykInstallCommand       : 'npm install -g snyk',
        snykArgs                 : '--license --json',                       // arguments passed to `snyk test`
        archiveArtifacts         : true,
        allowEmptyArchive        : true,
        failOnProhibited         : false,                                    // toggle to parse report and fail build
        prohibitedLicensePatterns: [],                                       // list of substrings to treat as prohibited, e.g. ['gpl', 'agpl']
        extraShellCommands       : ''                                        // optional extra shell commands to run before snyk test
    ] + config

    pipeline {
        agent any

        tools {
            nodejs config.nodeTool
        }

        stages {
            stage('Checkout Code') {
                steps {
                    git branch: config.branch, url: config.gitUrl
                }
            }

            stage('Setup Snyk CLI') {
                steps {
                    // keep this simple — if your agents already include snyk, you can set snykInstallCommand to ''
                    script {
                        if (config.snykInstallCommand?.trim()) {
                            sh script: config.snykInstallCommand, returnStatus: false
                        } else {
                            echo "Skipping Snyk install (snykInstallCommand is empty)."
                        }
                    }
                }
            }

            stage('Authenticate & License Scanning') {
                steps {
                    withCredentials([string(credentialsId: config.snykCredentialId, variable: 'SNYK_TOKEN')]) {
                        sh """
                            set -e
                            snyk auth \$SNYK_TOKEN
                            ${config.extraShellCommands ?: ''}
                            snyk test ${config.snykArgs} > ${config.reportFile} || true
                        """
                    }
                }
            }

            stage('Evaluate License Report') {
                when {
                    expression { return config.failOnProhibited == true }
                }
                steps {
                    script {
                        if (!fileExists(config.reportFile)) {
                            error "Snyk report not found: ${config.reportFile}"
                        }
                        def raw = readFile(config.reportFile)
                        def json
                        try {
                            json = new groovy.json.JsonSlurper().parseText(raw)
                        } catch (err) {
                            echo "Warning: could not parse Snyk JSON report - ${err}"
                            json = null
                        }

                        // collect license strings from the JSON (best-effort generic walk)
                        def foundLicenses = [] as Set
                        def walk
                        walk = { node ->
                            if (node == null) return
                            if (node instanceof Map) {
                                node.each { k, v ->
                                    if (k.toString().toLowerCase().contains('license') && v instanceof String) {
                                        foundLicenses << v
                                    } else {
                                        walk(v)
                                    }
                                }
                            } else if (node instanceof List) {
                                node.each { item -> walk(item) }
                            }
                        }
                        walk(json)

                        echo "Detected license strings (sample): ${foundLicenses.take(10)}"

                        // match against prohibited patterns
                        def matched = []
                        config.prohibitedLicensePatterns.each { pattern ->
                            foundLicenses.each { lic ->
                                if (lic?.toLowerCase()?.contains(pattern?.toLowerCase())) {
                                    matched << lic
                                }
                            }
                        }

                        if (matched) {
                            echo "Prohibited license(s) matched: ${matched.unique()}"
                            error "Failing build because prohibited license(s) were found."
                        } else {
                            echo "No prohibited licenses matched configured patterns."
                        }
                    }
                }
            }

            stage('Archive Reports') {
                steps {
                    script {
                        if (config.archiveArtifacts) {
                            archiveArtifacts artifacts: config.reportFile, fingerprint: true, allowEmptyArchive: config.allowEmptyArchive
                        } else {
                            echo "Skipping artifact archive (archiveArtifacts=false)."
                        }
                    }
                }
            }
        } // stages

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
    } // pipeline
}
