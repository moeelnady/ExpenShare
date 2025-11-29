pipeline {
        agent {
            docker {
                image 'gradle:8.7-jdk21-alpine'
                args '-v $HOME/.gradle:/home/gradle/.gradle --user root'
                reuseNode true
            }
        }
        environment {
                DOCKER_IMAGE = "expenshare:latest"
        }
    stages {
        stage('Build') {
                    steps {
                        sh './gradlew clean build -x test'
                    }
                }

        stage('Unit Tests') {
                    steps {
                        sh './gradlew test'
                    }
                    post {
                        always {
                            junit 'build/test-results/test/**/*.xml'
                        }
                    }
        }
        stage('Docker Build') {
                    agent any   // Use Jenkins host, not the gradle docker container
                    steps {
                        sh """
                            docker build -t ${DOCKER_IMAGE} .
                        """
                    }
        }

    }
}