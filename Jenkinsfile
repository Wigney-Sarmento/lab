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
                // Compila a aplicação Java e executa os testes via Docker.
                sh '''
                    set -eu
                    docker build --pull -t transaction-lab:ci .
                '''
            }
        }

        stage('Publicar no Docker Hub') {
            steps {
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

                        printf '%s' "$DOCKERHUB_TOKEN" | docker login \
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
            steps {
                script {
                    def modelo = readFile('k8s/app.yaml')

                    if (!modelo.contains('__IMAGE__')) {
                        error('Marcador __IMAGE__ ausente no manifesto Kubernetes')
                    }

                    writeFile(
                        file: 'rendered-app.yaml',
                        text: modelo.replace('__IMAGE__', env.DEPLOY_IMAGE)
                    )
                }

                sh '''
                    set -eu

                    echo "Verificando conexão com Kubernetes..."
                    kubectl cluster-info

                    echo "Aplicando manifesto..."
                    kubectl -n transaction-lab apply -f rendered-app.yaml

                    echo "Aguardando aplicação ficar disponível..."
                    kubectl -n transaction-lab rollout status \
                        deployment/transaction-api --timeout=180s

                    echo "Listando Pods..."
                    kubectl -n transaction-lab get pods -o wide

                    echo "Listando Services..."
                    kubectl -n transaction-lab get svc
                '''
            }
        }
    }

    post {
        success {
            echo 'Pipeline executada com sucesso!'
            echo 'Aplicação publicada no Kubernetes.'
        }

        failure {
            echo 'Pipeline falhou. Verifique o Console Output.'
        }

        always {
            echo "Build finalizada: ${env.BUILD_NUMBER}"
        }
    }
}
