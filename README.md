# Transaction Lab — Java + MariaDB + Docker + Kubernetes + Jenkins

Laboratorio de CI/CD e observabilidade para apresentar em entrevista DevOps.

O projeto **simula** pagamentos (nao movimenta dinheiro real), grava os resultados
em MariaDB e gera **um log JSON por transacao** em stdout, pronto para coleta
por Fluent Bit e indexacao no OpenSearch.

## Arquitetura

```text
GitHub --> Jenkins --> Docker build + testes --> Docker Hub --> Kubernetes (Minikube)
                                                           |
                                              +------------+------------+
                                              |                         |
                                      API Java /actuator          MariaDB
                                      Logs JSON em stdout         PVC 2Gi
                                              |
                                          Fluent Bit (opcional)
                                              |
                                          OpenSearch
```

**Tecnologias:** Java 17, Spring Boot 3.5.16, Maven, Spring Data JPA,
MariaDB 11.4, Spring Actuator, Micrometer Prometheus, Docker Compose,
Kubernetes e Jenkins declarativo.

## 1. Iniciar localmente com Docker Compose

Pre-requisitos: Docker Engine + plugin `docker compose`.

```bash
cp .env.example .env
nano .env                       # Altere as duas senhas de exemplo.
docker compose up -d --build
docker compose ps
docker compose logs -f api
```

O build Docker executa **`mvn clean verify`**, inclusive os testes. A primeira
execucao baixa as dependencias e pode demorar. O Compose inicia o banco,
aguarda seu healthcheck e sobe a aplicacao. O MariaDB e acessivel apenas na
maquina local pela porta `3307`; a API usa `http://localhost:8080`.

O gerador automatico cria aproximadamente **uma transacao a cada 5 segundos**,
com distribuicao aproximada de **75% APROVADA, 20% NEGADA e 5% ERRO**.
Esses percentuais variam ao acaso. Em Kubernetes, cada replica gera seus
proprios eventos.

### Testar os endpoints

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/health/liveness
curl -s http://localhost:8080/actuator/health/readiness
curl -s http://localhost:8080/actuator/prometheus
curl -s http://localhost:8080/api/transacoes/resumo
curl -s 'http://localhost:8080/api/transacoes?limite=10'
```

Forcar uma **aprovada**:

```bash
curl -i -X POST http://localhost:8080/api/transacoes \
  -H 'Content-Type: application/json' \
  -d '{"valor":149.90,"estabelecimento":"PAYSTORE","status":"APROVADA"}'
```

Forcar uma **negada**:

```bash
curl -i -X POST http://localhost:8080/api/transacoes \
  -H 'Content-Type: application/json' \
  -d '{"valor":250.00,"estabelecimento":"QR_GATEWAY","status":"NEGADA"}'
```

Forcar um **erro simulado**:

```bash
curl -i -X POST http://localhost:8080/api/transacoes \
  -H 'Content-Type: application/json' \
  -d '{"valor":75.00,"estabelecimento":"ECOMMERCE","status":"ERRO"}'
```

Sem `status`, o resultado sera sorteado. O HTTP **201 Created** significa que
o resultado foi *persistido*: status `ERRO` representa erro transacional
simulado (codigo `96`), **nao** falha HTTP da API. Uma falha real de banco
retorna erro de servidor, e a readiness passa a indicar problema.

Codigos demonstrativos: `00` aprovada, `05` negada, `96` erro.

### Conferir no MariaDB

```bash
docker compose exec mariadb mariadb -u transaction_app -p transaction_lab
```

Digite a senha definida em `.env` e execute:

```sql
SELECT id, status, valor, estabelecimento, codigo_resposta, origem, criada_em
FROM transacoes ORDER BY criada_em DESC LIMIT 15;

SELECT status, COUNT(*) AS quantidade, SUM(valor) AS valor_total
FROM transacoes GROUP BY status;
```

Para parar (preservando dados):

```bash
docker compose down
```

**Nao use** `docker compose down -v` se quiser manter o historico: isso
remove o volume do banco.

## 2. Logs JSON e monitoramento

Todo log da aplicacao e JSON de uma linha. A saida abaixo e **ilustrativa**:

```json
{"@timestamp":"2026-11-01T20:00:00.000Z","@version":"1","message":"transacao_processada","logger_name":"br.com.lab.transactions.TransacaoService","thread_name":"scheduling-1","level":"WARN","tipo_evento":"TRANSACAO","transacao_id":"3f0a5c6d-2a52-410e-9449-f65d9b722998","status":"NEGADA","valor":250.00,"estabelecimento":"QR_GATEWAY","origem":"GERADOR","codigo_resposta":"05","duracao_ms":8}
```

Os campos de negocio (`tipo_evento`, `status`, `transacao_id`, etc.) sao enviados
pela API fluente de logging SLF4J e incorporados ao JSON pelo Spring Boot.
`valor` e `duracao_ms` sao campos numericos, uteis para agregacoes no OpenSearch.

```bash
docker compose logs --no-log-prefix api
kubectl -n transaction-lab logs -l app=transaction-api --tail=30 --prefix=true
```

Para o **OpenSearch**, instale Fluent Bit como DaemonSet, com entrada Tail
dos logs em `/var/log/containers/*.log`, filtro Kubernetes usando
`Merge_Log On`, e saida OpenSearch apontando para seu endpoint. Quando os
logs dos containers forem coletados, o JSON interno pode ser mesclado aos
campos do evento, permitindo filtro por `status` e `tipo_evento`.
Use a configuracao de TLS, usuario e senha adequada ao seu ambiente e nao
publique credenciais no GitHub. Exemplos de investigacao:

- `tipo_evento: TRANSACAO AND status: ERRO`
- `tipo_evento: TRANSACAO AND status: NEGADA`
- `estabelecimento: QR_GATEWAY`
- Grafico de volume por `status` ao longo do tempo (campo `@timestamp`).

O Actuator expoe:

| Endpoint | Objetivo |
| --- | --- |
| `/actuator/health/liveness` | Processo da API esta vivo |
| `/actuator/health/readiness` | API pronta **e** conexao com MariaDB saudavel |
| `/actuator/prometheus` | Metricas para Prometheus/Grafana |
| `/api/transacoes/resumo` | Totais historicos persistidos no MariaDB |

O contador Prometheus se chama `lab_transacoes_total{status="APROVADA"}`
(e tambem `NEGADA`, `ERRO`). Ele reflete eventos processados **desde o inicio
do processo**; o endpoint `/resumo` consulta o total historico no banco.

> Para o laboratorio, os endpoints Actuator ficam acessiveis na porta da API.
> Em producao, proteja os endpoints de administracao e limite o acesso de rede.

## 3. Subir MariaDB no Minikube

Pre-requisitos: Minikube iniciado, `kubectl` configurado e um provisionador
de volume padrao funcionando. Em uma maquina de 8 GB, experimente:

```bash
minikube start --driver=docker --cpus=2 --memory=4096
kubectl create namespace transaction-lab
```

Crie o Secret **sem colocar as senhas em arquivos versionados**:

```bash
read -rsp 'Senha do usuario de banco: ' LAB_DB_PASS; echo
read -rsp 'Senha root do banco: ' LAB_ROOT_PASS; echo
kubectl -n transaction-lab create secret generic transaction-db \
  --from-literal=password="$LAB_DB_PASS" \
  --from-literal=root-password="$LAB_ROOT_PASS"
unset LAB_DB_PASS LAB_ROOT_PASS
```

Inicialize o banco com armazenamento persistente:

```bash
kubectl apply -f k8s/mariadb.yaml
kubectl -n transaction-lab rollout status statefulset/mariadb --timeout=180s
kubectl -n transaction-lab get pods,pvc,svc
```

O arquivo `k8s/app.yaml` e aplicado **pelo Jenkins** apos substituir
`__IMAGE__` pela imagem publicada no Docker Hub. Se quiser fazer o
primeiro deploy manual, siga a etapa Jenkins ou substitua o marcador por
uma imagem existente e execute `kubectl apply -f` com o manifesto renderizado.

## 4. Configurar Jenkins para o deploy automatico

**O executor/agent Jenkins** precisa ter acesso ao daemon Docker (CLI e
permissao para executar `docker build/push`) e ao binario `kubectl`.
Se o Jenkins estiver em container, isso **nao** vem configurado por padrao;
prepare um agente de build apropriado. Evite expor o socket Docker em
ambientes compartilhados, pois ele concede privilegios elevados.

1. Crie a conta/repository **publico** `transaction-lab` no Docker Hub.
2. No Jenkins, configure **Manage Jenkins > Credentials**:
   - `dockerhub-credentials`: tipo *Username with password*, usuario do Docker Hub
     e **access token** como password.
   - `kubeconfig-minikube`: tipo *Secret file*, contendo kubeconfig valido
     para o agente Jenkins acessar o cluster.
3. Gere um kubeconfig completo **na maquina que ja acessa o Minikube**:

   ```bash
   kubectl config view --raw --flatten --minify > jenkins-kubeconfig.yaml
   ```

   Importe esse arquivo como credencial **Secret file** no Jenkins e exclua a
   copia local depois, se nao for necessaria. Ele contem dados sensiveis.
   O endereco de API dentro do kubeconfig deve ser alcancavel **pelo agente**
   Jenkins, especialmente quando Jenkins roda num container separado.

4. Crie um job **Pipeline** no Jenkins:
   - **Definition:** Pipeline script from SCM
   - **SCM:** Git
   - **Repository URL:** `https://github.com/Wigney-Sarmento/lab.git`
   - **Branch:** `*/main` (ou ajuste para sua branch real)
   - **Script Path:** `Jenkinsfile`
5. Clique em **Build Now**.

O `Jenkinsfile` executa:

1. Checkout do codigo.
2. Build da imagem Docker e **testes Maven** no estagio de build.
3. Login no Docker Hub com credencial, tag com numero de build e push.
4. Renderizacao do manifesto Kubernetes com a tag recem-publicada.
5. `kubectl apply`, acompanhamento de rollout e listagem de Pods.

O namespace `transaction-lab`, o Secret e o StatefulSet MariaDB **devem
existir antes** do primeiro deploy. O usuario do Docker Hub precisa ter
permissao de push no repositorio, e o cluster precisa ter acesso a imagem
publica do Docker Hub.

### Validar depois do deploy

```bash
kubectl -n transaction-lab get deploy,sts,pods,svc,pvc
kubectl -n transaction-lab logs -l app=transaction-api --tail=50
kubectl -n transaction-lab port-forward svc/transaction-api 8080:8080
```

Em **outro terminal**:

```bash
curl -s http://localhost:8080/actuator/health/readiness
curl -s http://localhost:8080/api/transacoes/resumo
curl -s http://localhost:8080/actuator/prometheus
```

Se a porta 8080 estiver ocupada pelo Compose, use `docker compose down` ou
troque a porta local do port-forward para `18080:8080`.

## 5. Publicar os arquivos no GitHub

Se o repositorio `https://github.com/Wigney-Sarmento/lab.git` ja existir:

```bash
git clone https://github.com/Wigney-Sarmento/lab.git
cd lab
# Extraia/copie o CONTEUDO da pasta transaction-lab para a raiz do repositorio lab.
git add .
git commit -m "feat: laboratorio transacional java com mariadb e ci-cd"
git push -u origin HEAD:main
```

O `.gitignore` impede o versionamento de `.env` (senhas locais). Revise o
resultado de `git status` antes do commit. Nunca envie senhas, tokens,
certificados ou kubeconfig para o GitHub.

## 6. Problemas comuns

| Sintoma | Checagem |
| --- | --- |
| API nao sobe / `Connection refused` | `docker compose ps`, logs MariaDB, variaveis `DB_*` |
| Pod API `CrashLoopBackOff` | `kubectl -n transaction-lab logs deploy/transaction-api --previous` |
| Pod `ImagePullBackOff` | Usuario/repo/tag Docker Hub e conectividade Minikube |
| MariaDB `Pending` | `kubectl -n transaction-lab get pvc` e `kubectl describe pvc` |
| Jenkins `docker: not found` | Instalar CLI no agente e configurar daemon/permissoes |
| Jenkins `kubectl` nao consegue conectar | Verificar endereco/certificados do kubeconfig do agente |
| Readiness DOWN | Verificar Secret e `kubectl logs statefulset/mariadb` |

**Limites intencionais do laboratorio:** MariaDB de uma replica, geracao
sintetica, sem autenticacao nos endpoints de negocio e `ddl-auto=update`.
Nao exponha a API em rede publica nem reutilize esta configuracao diretamente
em producao. Para producao: autenticacao, migracoes, HA/backup de banco,
TLS, NetworkPolicy, limites de taxa e pipelines de verificacao de seguranca.
