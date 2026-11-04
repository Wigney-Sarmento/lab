package br.com.lab.transactions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gerador")
public class GeradorAutomatico {
    private static final Logger LOG = LoggerFactory.getLogger(GeradorAutomatico.class);
    private static final String[] ESTABELECIMENTOS = {"PAYSTORE", "QR_GATEWAY", "ECOMMERCE", "LOJA_FISICA"};
    private final TransacaoService service;
    private volatile boolean habilitado;
    private volatile int intervaloMs;
    private volatile int percentualAprovacao = 75;
    private volatile long ultimaGeracao;

    public GeradorAutomatico(TransacaoService service,
            @Value("${lab.generator.enabled:true}") boolean habilitado,
            @Value("${lab.generator.interval-ms:5000}") int intervaloMs) {
        this.service = service;
        this.habilitado = habilitado;
        this.intervaloMs = Math.max(1000, intervaloMs);
    }

    @Scheduled(fixedDelay = 500)
    public synchronized void gerar() {
        long agora = System.currentTimeMillis();
        if (!habilitado || agora - ultimaGeracao < intervaloMs) return;
        ultimaGeracao = agora;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BigDecimal valor = BigDecimal.valueOf(random.nextLong(100, 50_001), 2);
        String estabelecimento = ESTABELECIMENTOS[random.nextInt(ESTABELECIMENTOS.length)];
        int sorteio = random.nextInt(100);
        StatusTransacao status = sorteio < percentualAprovacao ? StatusTransacao.APROVADA
                : sorteio < percentualAprovacao + (100 - percentualAprovacao) * 4 / 5
                ? StatusTransacao.NEGADA : StatusTransacao.ERRO;
        try {
            service.criar(new NovaTransacaoRequest(valor, estabelecimento, status), "GERADOR");
        } catch (Exception ex) {
            LOG.error("falha_ao_gerar_transacao", ex);
        }
    }

    @GetMapping
    public Configuracao configuracao() {
        return new Configuracao(habilitado, intervaloMs, percentualAprovacao);
    }

    @PutMapping
    public synchronized Configuracao configurar(@Valid @RequestBody Configuracao nova) {
        habilitado = nova.habilitado();
        intervaloMs = nova.intervaloMs();
        percentualAprovacao = nova.percentualAprovacao();
        return configuracao();
    }

    public record Configuracao(boolean habilitado, @Min(1000) @Max(60000) int intervaloMs,
                               @Min(0) @Max(100) int percentualAprovacao) { }
}
