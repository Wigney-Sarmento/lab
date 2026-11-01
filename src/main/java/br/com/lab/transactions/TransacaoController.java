package br.com.lab.transactions;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transacoes")
public class TransacaoController {
    private final TransacaoService service;

    public TransacaoController(TransacaoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transacao criar(@Valid @RequestBody NovaTransacaoRequest request) {
        return service.criar(request, "API");
    }

    @GetMapping
    public List<Transacao> listar(@RequestParam(defaultValue = "20") int limite) {
        return service.listar(limite);
    }

    @GetMapping("/resumo")
    public TransacaoService.ResumoTransacoes resumo() {
        return service.resumo();
    }

    @GetMapping("/{id}")
    public Transacao buscar(@PathVariable String id) {
        return service.buscar(id);
    }
}
