package com.lifepedia.api.architecture;

import com.lifepedia.api.config.StorageProperties;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/** Arquivos do módulo em {@code STORAGE_DIR/architecture}, sempre com nome aleatório (nunca vindo do cliente). */
@Component
public class ArchitectureFiles {

    /** Tipo do arquivo = extensão no disco. */
    public enum Type {
        MODEL(".glb"), IMAGE(".jpg"), PDF(".pdf");

        private final String extension;

        Type(String extension) {
            this.extension = extension;
        }
    }

    /** Arquivo recebido: chave e tamanho real. */
    public record Received(String key, long size) {
    }

    private static final Pattern KEY = Pattern.compile("^[0-9a-f]{32}$");
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_HEADER = 20;
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final Logger log = LoggerFactory.getLogger(ArchitectureFiles.class);

    private final Path dir;

    public ArchitectureFiles(StorageProperties properties) {
        this.dir = properties.dir().resolve("architecture").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar " + dir, e);
        }
    }

    /**
     * Copia a maquete enviada para um arquivo novo, sem passar de {@code maxBytes}, e confere se é um GLB 2.0 inteiro
     * (cabeçalho, tamanho declarado e bloco JSON). Fora disso apaga o arquivo e responde 400.
     */
    public Received receiveModel(InputStream body, long maxBytes) {
        String key = newKey();
        Path path = path(key, Type.MODEL);
        byte[] header = new byte[GLB_HEADER];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n = body.read(buffer); n >= 0; n = body.read(buffer)) {
                if (total < GLB_HEADER) System.arraycopy(buffer, 0, header, (int) total, (int) Math.min(n, GLB_HEADER - total));
                total += n;
                if (total > maxBytes) throw invalid("A maquete passou do tamanho informado ou do limite.");
                out.write(buffer, 0, n);
            }
            if (total < GLB_HEADER || !isGlb(header, total)) throw invalid("O arquivo enviado não é uma maquete GLB válida.");
            return new Received(key, total);
        } catch (IOException e) {
            delete(key, Type.MODEL);
            throw invalid("Não foi possível receber a maquete. Tente de novo.");
        } catch (RuntimeException e) {
            delete(key, Type.MODEL);
            throw e;
        }
    }

    /** Grava bytes já validados; se a transação falhar, o arquivo é apagado. */
    public String save(byte[] bytes, Type type) {
        String key = newKey();
        try {
            Files.write(path(key, type), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) delete(key, type);
                }
            });
        }
        return key;
    }

    public Resource resource(String key, Type type) {
        Path path = path(key, type);
        if (!Files.isRegularFile(path)) throw new NotFoundException("Arquivo não encontrado.");
        return new FileSystemResource(path);
    }

    /** Apaga depois do commit (se a transação falhar, o arquivo continua valendo). */
    public void deleteAfterCommit(String key, Type type) {
        if (key == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(key, type);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete(key, type);
            }
        });
    }

    public void delete(String key, Type type) {
        try {
            Files.deleteIfExists(path(key, type));
        } catch (IOException e) {
            log.warn("Não foi possível apagar o arquivo {}", key, e);
        }
    }

    private static boolean isGlb(byte[] header, long total) {
        ByteBuffer bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        return bytes.getInt(0) == GLB_MAGIC && bytes.getInt(4) == 2
                && Integer.toUnsignedLong(bytes.getInt(8)) == total && bytes.getInt(16) == JSON_CHUNK;
    }

    private static String newKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    private Path path(String key, Type type) {
        if (key == null || !KEY.matcher(key).matches()) throw new NotFoundException("Arquivo não encontrado.");
        return dir.resolve(key + type.extension);
    }
}
