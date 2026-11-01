package br.com.lab.transactions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transacoes", indexes = {
        @Index(name = "idx_transacoes_criada_em", columnList = "criada_em"),
        @Index(name = "idx_transacoes_status", columnList = "status")
})
public class Transacao {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 80)
    private String estabelecimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StatusTransacao status;

    @Column(name = "codigo_resposta", nullable = false, length = 4)
    private String codigoResposta;

    @Column(nullable = false, length = 16)
    private String origem;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected Transacao() { }

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
