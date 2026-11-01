pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build + testes') {
            steps {
                // Docker multi-stage executa "mvn clean verify" antes de criar a imagem final.
                sh 'docker build --pull -t transaction-lab:ci .'
            }
        }

        stage('Publicar no Docker Hub') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials',
                                                  usernameVariable: 'DOCKERHUB_USER',
                                                  passwordVariable: 'DOCKERHUB_TOKEN')]) {
                    script {
                        env.DEPLOY_IMAGE = "docker.io/${env.DOCKERHUB_USER}/transaction-lab:${env.BUILD_NUMBER}"
                    }
                    sh '''
                        set -eu
                        printf '%s' "$DOCKERHUB_TOKEN" | docker login --username "$DOCKERHUB_USER" --password-stdin
                        docker tag transaction-lab:ci "$DEPLOY_IMAGE"
                        docker push "$DEPLOY_IMAGE"
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy no Kubernetes') {
            steps {
                script {
                    def modelo = readFile('k8s/app.yaml')
                    if (!modelo.contains('__IMAGE__')) {
                        error('Marcador __IMAGE__ ausente no manifesto Kubernetes')
                    }
                    writeFile file: 'rendered-app.yaml', text: modelo.replace('__IMAGE__', env.DEPLOY_IMAGE)
                }
                withCredentials([file(credentialsId: 'kubeconfig-minikube', variable: 'KUBECONFIG')]) {
                    sh '''
                        set -eu
                        kubectl -n transaction-lab apply -f rendered-app.yaml
                        kubectl -n transaction-lab rollout status deployment/transaction-api --timeout=180s
                        kubectl -n transaction-lab get pods
                    '''
                }
            }
        }
    }
}
