package br.com.avisabrasil.repositorio;

import br.com.avisabrasil.dominio.InscritoTelegram;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InscritoTelegramRepositorio extends JpaRepository<InscritoTelegram, Long> {

    Optional<InscritoTelegram> findByChatId(Long chatId);

    List<InscritoTelegram> findByAtivoTrue();
}
