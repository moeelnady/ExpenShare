pipeline {
    agent none

    environment {
        DOCKER_IMAGE = "moeelnady/expenshare:latest"
        DOCKERHUB_CRED = credentials('dockerhub-cred')
        // SonarCloud token stored in Jenkins credentials (secret text)
        SONAR_TOKEN_CRED = credentials('75f1b68b-52e8-4a74-a959-80826d0b4d70')
    }

    stages {

        /* ----------------------------
           BUILD STAGE
        ------------------------------ */
        stage('Build') {
            agent {
                docker {
                    image 'gradle:8.7-jdk21-alpine'
                    args '-v $HOME/.gradle:/home/gradle/.gradle --user root'
                }
            }
            steps {
                sh './gradlew clean build -x test'
            }
        }

        /* ----------------------------
           UNIT TESTS + JACOCO
        ------------------------------ */
        stage('Unit Tests & Jacoco') {
            agent {
                docker {
                    image 'gradle:8.7-jdk21-alpine'
                    args '-v $HOME/.gradle:/home/gradle/.gradle --user root'
                }
            }
            steps {
                sh './gradlew test jacocoTestReport'
            }
            post {
                always {
                    junit 'build/test-results/test/**/*.xml'
                    archiveArtifacts artifacts: 'build/reports/jacoco/test/html/**', allowEmptyArchive: true
                }
            }
        }

        /* ----------------------------
           SONARCLOUD ANALYSIS
        ------------------------------ */
        stage('SonarCloud Analysis') {
            agent any
            steps {
                script {
                    // IMPORTANT: tool() must be inside a node context
                    SONAR_SCANNER = tool 'SonarScanner'
                }

                withSonarQubeEnv('SonarCloud') {
                    sh """
                        SONAR_TOKEN=${SONAR_TOKEN_CRED_PSW} \
                        ${SONAR_SCANNER}/bin/sonar-scanner \
                            -Dsonar.projectKey=moeelnady_ExpenShare \
                            -Dsonar.organization=moeelnady \
                            -Dsonar.sources=src/main/java \
                            -Dsonar.tests=src/test/java \
                            -Dsonar.java.binaries=build/classes/java/main \
                            -Dsonar.java.test.binaries=build/classes/java/test \
                            -Dsonar.junit.reportPaths=build/test-results/test \
                            -Dsonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/test/jacocoTestReport.xml \
                            -Dsonar.sourceEncoding=UTF-8
                    """
                }
            }
        }

        /* ----------------------------
           QUALITY GATE
        ------------------------------ */
        stage('Quality Gate') {
            agent any
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        /* ----------------------------
           DOCKER BUILD
        ------------------------------ */
        stage('Docker Build') {
            agent any
            steps {
                sh "docker build -t ${DOCKER_IMAGE} ."
            }
        }

        /* ----------------------------
           DOCKER PUSH
        ------------------------------ */
        stage('Docker Push') {
            agent any
            steps {
                sh """
                    echo "${DOCKERHUB_CRED_PSW}" | docker login -u "${DOCKERHUB_CRED_USR}" --password-stdin
                    docker push ${DOCKER_IMAGE}
                """
            }
        }
    }
}
