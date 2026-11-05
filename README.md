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
