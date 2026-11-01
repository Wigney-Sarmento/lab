package br.com.lab.transactions;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransacaoService {
    private static final Logger LOG = LoggerFactory.getLogger(TransacaoService.class);

    private final TransacaoRepository repository;
    private final MeterRegistry meterRegistry;

    public TransacaoService(TransacaoRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    public Transacao criar(NovaTransacaoRequest request, String origem) {
        long inicio = System.nanoTime();
        StatusTransacao status = request.status() == null ? sortearStatus() : request.status();
        String codigo = switch (status) {
            case APROVADA -> "00";
            case NEGADA -> "05";
            case ERRO -> "96";
        };

        Transacao transacao = new Transacao(UUID.randomUUID().toString(), request.valor(),
                request.estabelecimento(), status, codigo, origem, Instant.now());

        // O resultado ERRO e simulado e tambem e armazenado para analise no banco.
        Transacao salva = repository.save(transacao);
        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;

        Counter.builder("lab.transacoes")
                .description("Transacoes gravadas no banco, por status")
                .tag("status", status.name())
                .register(meterRegistry)
                .increment();

        LoggingEventBuilder evento = switch (status) {
            case APROVADA -> LOG.atInfo();
            case NEGADA -> LOG.atWarn();
            case ERRO -> LOG.atError();
        };
        evento.addKeyValue("tipo_evento", "TRANSACAO")
                .addKeyValue("transacao_id", salva.getId())
                .addKeyValue("status", status.name())
                .addKeyValue("valor", salva.getValor())
                .addKeyValue("estabelecimento", salva.getEstabelecimento())
                .addKeyValue("origem", origem)
                .addKeyValue("codigo_resposta", codigo)
                .addKeyValue("duracao_ms", duracaoMs)
                .log("transacao_processada");
        return salva;
    }

    @Transactional(readOnly = true)
    public List<Transacao> listar(int limite) {
        return repository.findAll(PageRequest.of(0, Math.max(1, Math.min(limite, 100)),
                Sort.by(Sort.Direction.DESC, "criadaEm"))).getContent();
    }

    @Transactional(readOnly = true)
    public Transacao buscar(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacao nao encontrada"));
    }

    @Transactional(readOnly = true)
    public ResumoTransacoes resumo() {
        return new ResumoTransacoes(repository.count(), repository.countByStatus(StatusTransacao.APROVADA),
                repository.countByStatus(StatusTransacao.NEGADA), repository.countByStatus(StatusTransacao.ERRO));
    }

    private StatusTransacao sortearStatus() {
        int sorteio = ThreadLocalRandom.current().nextInt(100);
        if (sorteio < 75) return StatusTransacao.APROVADA;
        if (sorteio < 95) return StatusTransacao.NEGADA;
        return StatusTransacao.ERRO;
    }

    public record ResumoTransacoes(long total, long aprovadas, long negadas, long erros) { }
}
