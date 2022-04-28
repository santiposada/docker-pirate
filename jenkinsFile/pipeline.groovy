pipeline {
    agent any

    tools {
        nodejs 'nodejs'
    }

    environment {
        // General Variables for Pipeline
        PROJECT_ROOT = 'express-mysql/app'
        EMAIL_ADDRESS = 'santiago.posada@pragma.com.co'
        REGISTRY = 'santiagoposada/node-practica'
        DOCKERHUB_SANTIAGO_POSADA = credentials('DOCKERHUB_SANTIAGO_POSADA')
    }

    stages {
        stage('Hello') {
            steps {
                // First stage is a sample hello-world step to verify correct Jenkins Pipeline
                echo 'Hello World, I am Happy'
                echo 'This is my amazing Pipeline'
            }
        }
        stage('Checkout') {
            steps {
                // Get Github repo using Github credentials (previously added to Jenkins credentials)
                checkout([$class: 'GitSCM', branches: [[name: '*/main']], extensions: [], userRemoteConfigs: [[credentialsId: 'github', url: 'https://github.com/santiposada/docker-pirate.git']]])
            }
        }
        stage('Install dependencies') {
            steps {
                sh 'npm --version'
                sh "cd ${PROJECT_ROOT}; npm install"
            }
        }
        stage('Unit tests') {
            steps {
                // Run unit tests
                sh "cd ${PROJECT_ROOT}; npm run test"
            }
        }
        stage('Generate coverage report') {
            steps {
                // Run code-coverage reports
                sh "cd ${PROJECT_ROOT}; npm run coverage"
            }
        }
        stage('scan') {
            environment {
                // Previously defined in the Jenkins "Global Tool Configuration"
                scannerHome = tool 'sonar-scanner'
            }
            steps {
                // "sonarqube" is the server configured in "Configure System"
                withSonarQubeEnv('sonarqube') {
                    // Execute the SonarQube scanner with desired flags
                    sh "${scannerHome}/bin/sonar-scanner \
                          -Dsonar.projectKey=test-nodejs:Test \
                          -Dsonar.projectName=test-nodejs \
                          -Dsonar.projectVersion=0.0.${BUILD_NUMBER} \
                          -Dsonar.host.url=http://mysonarqube:9000 \
                          -Dsonar.sources=./${PROJECT_ROOT}/app.js,./${PROJECT_ROOT}/config/db.config.js,./${PROJECT_ROOT}/routes/developers.js \
                          -Dsonar.login=admin \
                          -Dsonar.password=admin \
                          -Dsonar.tests=./${PROJECT_ROOT}/test \
                          -Dsonar.javascript.lcov.reportPaths=./${PROJECT_ROOT}/coverage/lcov.info"
                }
                timeout(time: 3, unit: 'MINUTES') {
                    // In case of SonarQube failure or direct timeout exceed, stop Pipeline
                    waitForQualityGate abortPipeline: qualityGateValidation(waitForQualityGate())
                }
            }
        }
        stage('Build docker-image') {
            steps {
                sh "cd ./${PROJECT_ROOT};docker build -t ${REGISTRY}:${BUILD_NUMBER} . "
            }
        }
        stage('Deploy docker-image') {
            steps {
                // If the Dockerhub authentication stopped, do it again
                sh 'docker login --username $DOCKERHUB_SANTIAGO_POSADA_USR --password $DOCKERHUB_SANTIAGO_POSADA_PSW'
                sh "docker push ${REGISTRY}:${BUILD_NUMBER}"
            }
        }
    }

    post {

        always {



            //emailext body: "WARNING SANTI: Code coverage is lower than 80% in Pipeline ${BUILD_NUMBER}", subject: 'Error Sonar Scan,   Quality Gate', to: "${EMAIL_ADDRESS}"

            emailext body: "${currentBuild.currentResult}: Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}\n More info at: ${env.BUILD_URL}",
                    recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']],
                    subject: "Jenkins Build ${currentBuild.currentResult}: Job ${env.JOB_NAME}"

        }




    }
}

// Function to validate that the message returned from SonarQube is ok
def qualityGateValidation(qg) {
    if (qg.status != 'OK') {
        emailext body: "WARNING SANTI: Code coverage is lower than 80% in Pipeline ${BUILD_NUMBER}", subject: 'Error Sonar Scan,   Quality Gate', to: "${EMAIL_ADDRESS}"
        return true
    }else{
        emailext body: "CONGRATS SANTI: Code coverage is higher than 80%  in Pipeline ${BUILD_NUMBER} - SUCCESS", subject: 'Info - Correct Pipeline', to: "${EMAIL_ADDRESS}"
        return false
    }

}
