pipeline {
    agent any

    environment {
        DEPLOYMENT_NAME = "admin-panel"

        DOCKER_IMAGE = "k8s-${DEPLOYMENT_NAME}"
        K8S_NAMESPACE = "dev"
        KUBECONFIG_CREDENTIALS_ID = 'k8s-creds'

        REPOSITORY_URI = "10.0.0.70:8090"
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    checkout scm
                    // Unique, immutable image tag for this build: <commit>-b<build number>. Deploys use it, so the
                    // running version is always known and an older build can be redeployed by its tag.
                    env.IMAGE_TAG = "${sh(script: 'git rev-parse --short=8 HEAD', returnStdout: true).trim()}-b${env.BUILD_NUMBER}"
                    echo "Image tag: ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Build and Test') {
            steps {
                script {
                    sh "chmod +x ./mvnw"
                    sh "./mvnw clean package"
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    sh "docker build -t ${DOCKER_IMAGE} ."
                    sh "docker tag ${DOCKER_IMAGE}:latest ${REPOSITORY_URI}/${DOCKER_IMAGE}:${IMAGE_TAG}"
                    sh "docker tag ${DOCKER_IMAGE}:latest ${REPOSITORY_URI}/${DOCKER_IMAGE}:latest"
                }
            }
        }

        stage('Upload to Nexus') {
            steps {
                script {
                    // Own Docker config per build: builds share the agent, and one build's "docker logout" used to log
                    // out another build mid-push ("unauthorized").
                    withEnv(["DOCKER_CONFIG=${env.WORKSPACE}/.docker-nexus"]) {
                        withCredentials([usernamePassword(credentialsId: 'nexus-jenkins', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PWD')]) {
                            try {
                                sh 'echo "$NEXUS_PWD" | docker login -u "$NEXUS_USER" --password-stdin "$REPOSITORY_URI"'
                                sh "docker push ${REPOSITORY_URI}/${DOCKER_IMAGE}:${IMAGE_TAG}"
                                // :latest is a convenience copy; deploys use the unique tag. Overwriting it needs the Nexus
                                // 'edit' privilege, so a refusal here must not fail the build.
                                sh "docker push ${REPOSITORY_URI}/${DOCKER_IMAGE}:latest || echo 'WARNING: could not update :latest (Nexus edit privilege); deploy uses the unique tag'"
                                sh "docker rmi ${DOCKER_IMAGE}:latest ${REPOSITORY_URI}/${DOCKER_IMAGE}:${IMAGE_TAG} ${REPOSITORY_URI}/${DOCKER_IMAGE}:latest || true"
                            } finally {
                                sh 'docker logout "$REPOSITORY_URI"'
                            }
                        }
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                script {
                    // Point the manifest at this build's image (workspace copy only; the repo keeps :latest).
                    sh "sed -i 's#${REPOSITORY_URI}/${DOCKER_IMAGE}:latest#${REPOSITORY_URI}/${DOCKER_IMAGE}:${IMAGE_TAG}#g' deployment.yaml"
                    sh 'kubectl apply -f deployment.yaml -n dev || true'
                    // The new image tag in the manifest triggers the rollout; wait for it so a failed deploy fails the build.
                    sh "kubectl rollout status deployment/${env.DEPLOYMENT_NAME} -n ${env.K8S_NAMESPACE} --timeout=5m"
                    echo "Deployed ${REPOSITORY_URI}/${DOCKER_IMAGE}:${IMAGE_TAG}. Roll back: kubectl rollout undo deployment/${env.DEPLOYMENT_NAME} -n ${env.K8S_NAMESPACE}"
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo 'Build and deployment successful!'
        }
        failure {
            echo 'Build or deployment failed.'
        }
    }
}