package br.com.lab.transactions;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransacaoRepository extends JpaRepository<Transacao, String> {
    long countByStatus(StatusTransacao status);
}
