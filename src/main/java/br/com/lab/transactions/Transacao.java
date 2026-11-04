package br.com.lab.transactions;

import java.math.BigDecimal;
import java.time.Instant;

public class Transacao {
    private String id;
    private BigDecimal valor;
    private String estabelecimento;
    private StatusTransacao status;
    private String codigoResposta;
    private String origem;
    private Instant criadaEm;

    public Transacao(String id, BigDecimal valor, String estabelecimento, StatusTransacao status,
                     String codigoResposta, String origem, Instant criadaEm) {
        this.id = id;
        this.valor = valor;
        this.estabelecimento = estabelecimento;
        this.status = status;
        this.codigoResposta = codigoResposta;
        this.origem = origem;
        this.criadaEm = criadaEm;
    }

    public String getId() { return id; }
    public BigDecimal getValor() { return valor; }
    public String getEstabelecimento() { return estabelecimento; }
    public StatusTransacao getStatus() { return status; }
    public String getCodigoResposta() { return codigoResposta; }
    public String getOrigem() { return origem; }
    public Instant getCriadaEm() { return criadaEm; }
}
