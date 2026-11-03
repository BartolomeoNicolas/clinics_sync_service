package com.bartolomeo.clinics.sync.sync.infrastructure.persistence.adapter;

import com.bartolomeo.clinics.sync.sync.domain.model.SyncError;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncErrorPort;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.SyncErrorEntity;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository.SyncErrorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link SyncErrorPort} sobre PostgreSQL.
 *
 * <p><strong>{@code REQUIRES_NEW} es lo que hace que el registro sobreviva.</strong> Se llama
 * desde dentro de la transacción de la sincronización que falló, y si corriera en esa misma
 * transacción el error se perdería junto con todo lo demás: un fallo que no deja rastro es el
 * peor de los posibles, porque no hay nada que consultar ni por dónde empezar. Con
 * {@code REQUIRES_NEW} se suspende la transacción llamadora, se confirma la propia y el
 * registro queda aunque la original se revierta entera después.
 *
 * <p><strong>La columna manda.</strong> {@code message} es {@code NOT NULL} y tiene 500
 * caracteres: un mensaje nulo o demasiado largo no es motivo para perder el registro, así que
 * se le pone un sustituto y se recorta. {@code kind} también se acota por su columna. El
 * recorte es una decisión de persistencia y vive acá, no en el dominio, que no debería saber
 * qué largo tiene una columna.
 *
 * <p>Si el registro no se puede escribir —la misma base que suele estar detrás del fallo—,
 * esta excepción sale igual: quien llama la atrapa para no tapar el error original.
 */
@Component
@RequiredArgsConstructor
public class SyncErrorAdapter implements SyncErrorPort {

    /** Largos de las columnas del changeset {@code 002-03-sync-error}. */
    private static final int KIND_MAX = 64;
    private static final int MESSAGE_MAX = 500;

    private final SyncErrorRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SyncError error) {

        SyncErrorEntity entity = new SyncErrorEntity();
        entity.setOccurredAt(error.occurredAt());
        entity.setKind(acotar(error.kind(), KIND_MAX, "sin-tipo"));
        entity.setAttemptedVersion(error.attemptedVersion());
        entity.setMessage(acotar(error.message(), MESSAGE_MAX, "sin mensaje"));
        entity.setDetail(error.detail());

        repository.save(entity);
    }

    /** Recorta al largo de la columna y pone un sustituto si el valor vino vacío. */
    private static String acotar(String valor, int maximo, String sustituto) {
        if (valor == null || valor.isBlank()) {
            return sustituto;
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
