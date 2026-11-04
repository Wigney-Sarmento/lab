package br.com.lab.transactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransacaoServiceTest {
    @Test
    void deveGuardarAprovadaEmMemoria() {
        var service = new TransacaoService(new SimpleMeterRegistry());
        var criada = service.criar(new NovaTransacaoRequest(new BigDecimal("49.90"), "PAYSTORE", StatusTransacao.APROVADA), "API");
        assertNotNull(criada.getId());
        assertEquals("00", criada.getCodigoResposta());
        assertEquals(criada.getId(), service.listar(10).get(0).getId());
        assertEquals(1, service.resumo().aprovadas());
    }

    @Test
    void deveRegistrarErroSimulado() {
        var service = new TransacaoService(new SimpleMeterRegistry());
        var criada = service.criar(new NovaTransacaoRequest(new BigDecimal("10.00"), "QR_GATEWAY", StatusTransacao.ERRO), "API");
        assertEquals("96", criada.getCodigoResposta());
        assertEquals(1, service.resumo().erros());
    }
}
