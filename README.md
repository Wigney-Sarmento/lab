# Transaction Lab

Aplicação Spring Boot com painel web de simulação de transações para laboratório DevOps.

## Funcionalidades

- Painel em `/`: totais, últimas 25 transações e criação manual.
- Controles em tempo real: habilitar/desabilitar gerador, intervalo de 1 a 60 segundos e percentual de aprovação.
- API: `GET /api/transacoes`, `GET /api/transacoes/resumo`, `POST /api/transacoes`, `GET /api/gerador` e `PUT /api/gerador`.
- Health: `/actuator/health/liveness` e `/actuator/health/readiness`.
- Métricas Prometheus: `/actuator/prometheus`.
- Logs estruturados JSON para coleta pelo Fluent Bit e OpenSearch.
- OpenTelemetry Java Agent no Docker, exportando traces e metricas para o SigNoz via OTLP/HTTP.

**Não utiliza MariaDB nem outro banco.** Armazena até 1000 transações recentes na memória. Os totais e as configurações reiniciam ao recriar o processo/Pod. Use uma réplica por vez para manter o painel consistente.

## Docker Compose

```bash
docker compose up --build -d
```

Abra `http://localhost:8085`.

O endpoint padrao do collector OTLP/HTTP no laboratorio e `http://192.168.1.143:4318`.
No Compose, ajuste `OTEL_EXPORTER_OTLP_ENDPOINT` se o SigNoz mudar de endereco.
No Kubernetes, altere a variavel de mesmo nome no `k8s/app.yaml`.
Logs por OpenTelemetry ficam desabilitados para evitar duplicidade com Fluent Bit.
Para validar a conectividade do collector a partir do Minikube, confira a porta TCP 4318
e procure o servico `transaction-lab` em Services do SigNoz apos gerar trafego HTTP.

## Jenkins e Minikube

O Jenkinsfile mantém os estágios Checkout, Testes, Build Docker, Publicação no Docker Hub e Deploy no Kubernetes. O manifesto `k8s/app.yaml` não exige mais o Secret `transaction-db`.

Após o pipeline finalizar:

```bash
kubectl -n transaction-lab rollout status deployment/transaction-api
kubectl -n transaction-lab port-forward service/transaction-api 8085:8080
```

Abra `http://localhost:8085` na máquina que executou o port-forward.

**Atenção:** o controle do gerador não tem autenticação; exponha o serviço apenas em laboratório/rede confiável.
