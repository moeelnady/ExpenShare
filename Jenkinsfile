pipeline {

    environment {
        DOCKER_IMAGE = "moeelnady/expenshare:latest"
        DOCKERHUB_CRED = credentials('dockerhub-cred')
    }

    stages {

        /* ----------------------------
           BUILD STAGE (GRADLE INSIDE DOCKER)
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
           TEST STAGE (ALSO INSIDE GRADLE DOCKER)
        ------------------------------ */
        stage('Unit Tests') {
            agent {
                docker {
                    image 'gradle:8.7-jdk21-alpine'
                    args '-v $HOME/.gradle:/home/gradle/.gradle --user root'
                }
            }
            steps {
                sh './gradlew test'
            }
            post {
                always { junit 'build/test-results/test/**/*.xml' }
            }
        }

        /* ----------------------------
           DOCKER BUILD (ON JENKINS HOST)
        ------------------------------ */
        stage('Docker Build') {
            agent any  // Jenkins host
            steps {
                sh "docker build -t ${DOCKER_IMAGE} ."
            }
        }

        /* ----------------------------
           DOCKER PUSH (ON JENKINS HOST)
        ------------------------------ */
        stage('Docker Push') {
            agent any
            steps {
                sh """
                    echo "Logging into Docker Hub..."
                    echo "${DOCKERHUB_CRED_PSW}" | docker login -u "${DOCKERHUB_CRED_USR}" --password-stdin

                    echo "Pushing image ${DOCKER_IMAGE}"
                    docker push ${DOCKER_IMAGE}
                """
            }
        }
    }
}