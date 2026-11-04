
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    environment {
        IMAGE_NAME = 'transaction-lab'
        K8S_NAMESPACE = 'transaction-lab'
        K8S_DEPLOYMENT = 'transaction-api'
    }

    stages {

        stage('Checkout') {
            steps {
                echo 'Baixando codigo do GitHub...'
                checkout scm
            }
        }

        stage('Testes') {
            steps {
                echo 'Executando testes Java com Maven...'

                sh '''
                    set -eu

                    docker run --rm \
                        -v "$PWD:/app" \
                        -w /app \
                        maven:3.9-eclipse-temurin-21 \
                        mvn -B -ntp clean verify
                '''
            }
        }

        stage('Build Docker') {
            steps {
                echo 'Construindo imagem Docker com OpenTelemetry...'

                sh '''
                    set -eu

                    docker build --pull \
                        -t "$IMAGE_NAME:ci" .
                '''
            }
        }

        stage('Publicar no Docker Hub') {
            steps {
                echo 'Publicando imagem no Docker Hub...'

                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKERHUB_USER',
                        passwordVariable: 'DOCKERHUB_TOKEN'
                    )
                ]) {
                    script {
                        env.DEPLOY_IMAGE = "docker.io/${env.DOCKERHUB_USER}/${env.IMAGE_NAME}:${env.BUILD_NUMBER}"
                    }

                    sh '''
                        set -eu

                        printf '%s' "$DOCKERHUB_TOKEN" | \
                            docker login \
                            --username "$DOCKERHUB_USER" \
                            --password-stdin

                        docker tag \
                            "$IMAGE_NAME:ci" \
                            "$DEPLOY_IMAGE"

                        docker push "$DEPLOY_IMAGE"

                        docker logout
                    '''
                }
            }
        }

        stage('Deploy no Kubernetes') {
            steps {
                echo 'Iniciando deploy no Minikube...'

                script {
                    def manifesto = readFile('k8s/app.yaml')

                    if (!manifesto.contains('__IMAGE__')) {
                        error('Marcador __IMAGE__ nao encontrado em k8s/app.yaml')
                    }

                    if (!env.DEPLOY_IMAGE?.trim()) {
                        error('DEPLOY_IMAGE nao foi definida')
                    }

                    def manifestoFinal = manifesto.replace(
                        '__IMAGE__',
                        env.DEPLOY_IMAGE
                    )

                    writeFile(
                        file: 'rendered-app.yaml',
                        text: manifestoFinal
                    )
                }

                sh '''
                    set -eu

                    echo "Verificando cluster Kubernetes..."
                    kubectl cluster-info

                    echo "Contexto Kubernetes:"
                    kubectl config current-context

                    echo "Verificando namespace..."
                    kubectl get namespace "$K8S_NAMESPACE"

                    echo "Aplicando manifesto..."
                    kubectl -n "$K8S_NAMESPACE" \
                        apply -f rendered-app.yaml

                    echo "Aguardando rollout..."
                    kubectl -n "$K8S_NAMESPACE" \
                        rollout status \
                        "deployment/$K8S_DEPLOYMENT" \
                        --timeout=180s

                    echo "Verificando Deployment..."
                    kubectl -n "$K8S_NAMESPACE" \
                        get deployment "$K8S_DEPLOYMENT"

                    echo "Verificando Pods..."
                    kubectl -n "$K8S_NAMESPACE" \
                        get pods -o wide

                    echo "Verificando Services..."
                    kubectl -n "$K8S_NAMESPACE" \
                        get svc

                    echo "Imagem implantada:"
                    kubectl -n "$K8S_NAMESPACE" \
                        get deployment "$K8S_DEPLOYMENT" \
                        -o jsonpath='{.spec.template.spec.containers[0].image}'

                    echo
                '''
            }
        }
    }

    post {
        success {
            echo '=========================================='
            echo 'PIPELINE EXECUTADA COM SUCESSO!'
            echo 'Testes aprovados.'
            echo 'Imagem publicada no Docker Hub.'
            echo 'Deploy realizado no Kubernetes.'
            echo '=========================================='
        }

        failure {
            echo '=========================================='
            echo 'FALHA NA PIPELINE!'
            echo 'Consulte o Console Output.'
            echo '=========================================='
        }

        always {
            echo "Build finalizada: ${env.BUILD_NUMBER}"
        }
    }
}

