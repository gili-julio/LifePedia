package com.lifepedia.api.shared.image;

import com.lifepedia.api.shared.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/** Pasta de imagens JPEG em disco, com nome aleatório (nunca vindo do cliente). */
public class ImageStore {

    private static final Pattern KEY = Pattern.compile("^[0-9a-f]{32}$");
    private static final Logger log = LoggerFactory.getLogger(ImageStore.class);

    private final Path dir;

    public ImageStore(Path dir) {
        this.dir = dir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar " + this.dir, e);
        }
    }

    /** Grava um JPEG já validado ({@link Images}); devolve a chave. Se a transação falhar, o arquivo é apagado. */
    public String saveJpeg(byte[] jpeg) {
        String key = UUID.randomUUID().toString().replace("-", "");
        try {
            Files.write(path(key), jpeg);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) delete(key);
                }
            });
        }
        return key;
    }

    public byte[] read(String key) {
        try {
            return Files.readAllBytes(path(key));
        } catch (IOException e) {
            throw new NotFoundException("Imagem não encontrada.");
        }
    }

    public ImageFile file(String key) {
        return new ImageFile(key, read(key));
    }

    /** Apaga depois do commit (se a transação falhar, o arquivo continua valendo). */
    public void deleteAfterCommit(String key) {
        if (key == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete(key);
            }
        });
    }

    private void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException e) {
            log.warn("Não foi possível apagar a imagem {}", key, e);
        }
    }

    private Path path(String key) {
        if (!KEY.matcher(key).matches()) throw new NotFoundException("Imagem não encontrada.");
        return dir.resolve(key + ".jpg");
    }
}
