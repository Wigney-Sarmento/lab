package br.com.lab.transactions;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransacaoService {
    private static final Logger LOG = LoggerFactory.getLogger(TransacaoService.class);
    private static final int MAX_HISTORICO = 1000;
    private final ArrayDeque<Transacao> historico = new ArrayDeque<>();
    private final MeterRegistry meterRegistry;
    private long total, aprovadas, negadas, erros;

    public TransacaoService(MeterRegistry meterRegistry) {
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
        Transacao salva = new Transacao(UUID.randomUUID().toString(), request.valor(),
                request.estabelecimento(), status, codigo, origem, Instant.now());
        synchronized (historico) {
            historico.addFirst(salva);
            if (historico.size() > MAX_HISTORICO) historico.removeLast();
            total++;
            switch (status) {
                case APROVADA -> aprovadas++;
                case NEGADA -> negadas++;
                case ERRO -> erros++;
            }
        }
        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
        Counter.builder("lab.transacoes").description("Transacoes simuladas, por status")
                .tag("status", status.name()).register(meterRegistry).increment();
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
                .addKeyValue("duracao_ms", duracaoMs).log("transacao_processada");
        return salva;
    }

    public List<Transacao> listar(int limite) {
        synchronized (historico) {
            return new ArrayList<>(historico).stream().limit(Math.max(1, Math.min(limite, 100))).toList();
        }
    }

    public Transacao buscar(String id) {
        synchronized (historico) {
            return historico.stream().filter(t -> t.getId().equals(id)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacao nao encontrada"));
        }
    }

    public ResumoTransacoes resumo() {
        synchronized (historico) {
            return new ResumoTransacoes(total, aprovadas, negadas, erros);
        }
    }

    private StatusTransacao sortearStatus() {
        int sorteio = ThreadLocalRandom.current().nextInt(100);
        if (sorteio < 75) return StatusTransacao.APROVADA;
        if (sorteio < 95) return StatusTransacao.NEGADA;
        return StatusTransacao.ERRO;
    }

    public record ResumoTransacoes(long total, long aprovadas, long negadas, long erros) { }
}
