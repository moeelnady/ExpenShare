pipeline {
    agent any
    tools {
        jdk 'java21'  // Java 21 for Micronaut
        gradle 'gradle8' // Gradle 8.x
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
    }
}