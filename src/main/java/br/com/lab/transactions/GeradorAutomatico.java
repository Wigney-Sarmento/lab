package br.com.lab.transactions;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "lab.generator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GeradorAutomatico {
    private static final Logger LOG = LoggerFactory.getLogger(GeradorAutomatico.class);
    private static final String[] ESTABELECIMENTOS = {"PAYSTORE", "QR_GATEWAY", "ECOMMERCE", "LOJA_FISICA"};
    private final TransacaoService service;

    public GeradorAutomatico(TransacaoService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${lab.generator.interval-ms:5000}")
    public void gerar() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BigDecimal valor = BigDecimal.valueOf(random.nextLong(100, 50_001), 2);
        String estabelecimento = ESTABELECIMENTOS[random.nextInt(ESTABELECIMENTOS.length)];
        try {
            service.criar(new NovaTransacaoRequest(valor, estabelecimento, null), "GERADOR");
        } catch (Exception ex) {
            LOG.error("falha_ao_gerar_transacao", ex);
        }
    }
}
