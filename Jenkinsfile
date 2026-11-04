
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
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
                echo 'Executando testes da aplicacao Java...'

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
                        -t transaction-lab:ci .
                '''
            }
        }

        stage('Publicar no Docker Hub') {
            when {
                branch 'main'
            }

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
                        env.DEPLOY_IMAGE = "docker.io/${env.DOCKERHUB_USER}/transaction-lab:${env.BUILD_NUMBER}"
                    }

                    sh '''
                        set -eu

                        printf '%s' "$DOCKERHUB_TOKEN" | \
                            docker login \
                            --username "$DOCKERHUB_USER" \
                            --password-stdin

                        docker tag transaction-lab:ci "$DEPLOY_IMAGE"

                        docker push "$DEPLOY_IMAGE"

                        docker logout
                    '''
                }
            }
        }

        stage('Deploy no Kubernetes') {
            when {
                branch 'main'
            }

            steps {
                echo 'Iniciando deploy no Minikube...'

                script {
                    def modelo = readFile('k8s/app.yaml')

                    if (!modelo.contains('__IMAGE__')) {
                        error('Marcador __IMAGE__ nao encontrado no manifesto Kubernetes')
                    }

                    writeFile(
                        file: 'rendered-app.yaml',
                        text: modelo.replace(
                            '__IMAGE__',
                            env.DEPLOY_IMAGE
                        )
                    )
                }

                sh '''
                    set -eu

                    echo "Verificando conexao com Kubernetes..."
                    kubectl cluster-info

                    echo "Contexto atual:"
                    kubectl config current-context

                    echo "Aplicando manifesto Kubernetes..."
                    kubectl -n transaction-lab apply \
                        -f rendered-app.yaml

                    echo "Aguardando rollout..."
                    kubectl -n transaction-lab rollout status \
                        deployment/transaction-api \
                        --timeout=180s

                    echo "Verificando Pods..."
                    kubectl -n transaction-lab get pods -o wide

                    echo "Verificando Services..."
                    kubectl -n transaction-lab get svc

                    echo "Imagem implantada:"
                    kubectl -n transaction-lab get deployment \
                        transaction-api \
                        -o jsonpath='{.spec.template.spec.containers[0].image}'

                    echo
                '''
            }
        }
    }

    post {
        success {
            echo '========================================'
            echo 'PIPELINE EXECUTADA COM SUCESSO!'
            echo 'Imagem publicada no Docker Hub.'
            echo 'Aplicacao atualizada no Kubernetes.'
            echo 'OpenTelemetry configurado para SigNoz.'
            echo '========================================'
        }

        failure {
            echo '========================================'
            echo 'FALHA NA PIPELINE!'
            echo 'Verifique o Console Output.'
            echo '========================================'
        }

        always {
            echo "Build finalizada: ${env.BUILD_NUMBER}"
        }
    }
}

