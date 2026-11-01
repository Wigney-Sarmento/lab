package br.com.lab.transactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransacaoServiceTest {
    @Mock
    private TransacaoRepository repository;
    private TransacaoService service;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void iniciar() {
        registry = new SimpleMeterRegistry();
        service = new TransacaoService(repository, registry);
    }

    @Test
    void devePersistirAprovadaComCodigo00() {
        when(repository.save(any(Transacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Transacao criada = service.criar(new NovaTransacaoRequest(new BigDecimal("49.90"), "PAYSTORE",
                StatusTransacao.APROVADA), "API");

        assertNotNull(criada.getId());
        assertEquals(StatusTransacao.APROVADA, criada.getStatus());
        assertEquals("00", criada.getCodigoResposta());
        assertEquals(1.0, registry.get("lab.transacoes").tag("status", "APROVADA").counter().count());
        verify(repository).save(any(Transacao.class));
    }

    @Test
    void deveGravarErroSimuladoNoBanco() {
        when(repository.save(any(Transacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Transacao criada = service.criar(new NovaTransacaoRequest(new BigDecimal("10.00"), "QR_GATEWAY",
                StatusTransacao.ERRO), "API");

        assertEquals(StatusTransacao.ERRO, criada.getStatus());
        assertEquals("96", criada.getCodigoResposta());
        assertEquals(1.0, registry.get("lab.transacoes").tag("status", "ERRO").counter().count());
    }
}
