package com.lifepedia.api.finance.importing;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.ai.GeminiClient.Attachment;
import com.lifepedia.api.shared.image.Images;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Documentos enviados para leitura: o tipo vem do conteúdo, nunca do nome ou do que o navegador informou. */
final class ImportDocuments {

    static final int MAX_FILES = 10;
    static final long MAX_TOTAL_BYTES = 15 * 1024 * 1024;
    static final int MAX_PAGES = 50;

    private static final int PDF_HEADER_WINDOW = 1024;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private ImportDocuments() {
    }

    /** Recusa antes de ler qualquer arquivo para a memória. */
    static void checkLimits(List<MultipartFile> files) {
        if (files.isEmpty()) throw invalid("Envie ao menos um documento.");
        if (files.size() > MAX_FILES) throw invalid("Envie no máximo " + MAX_FILES + " documentos por vez.");
        long total = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > MAX_TOTAL_BYTES) {
            throw invalid("Os documentos juntos passam de " + MAX_TOTAL_BYTES / (1024 * 1024) + " MB.");
        }
    }

    static List<Attachment> read(List<MultipartFile> files) {
        List<Attachment> attachments = new ArrayList<>();
        for (MultipartFile file : files) attachments.add(attachment(file));
        return attachments;
    }

    private static Attachment attachment(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw invalid("Não consegui ler " + name(file) + ".");
        }
        if (bytes.length == 0) throw invalid(name(file) + " está vazio.");
        if (isPdf(bytes)) {
            checkPdf(bytes, file);
            return new Attachment("application/pdf", bytes);
        }
        String image = Images.mimeOf(bytes);
        if (image != null) return new Attachment(image, bytes);
        return new Attachment("text/plain", utf8(bytes, file));
    }

    private static boolean isPdf(byte[] bytes) {
        String start = new String(bytes, 0, Math.min(bytes.length, PDF_HEADER_WINDOW), StandardCharsets.ISO_8859_1);
        return start.contains("%PDF-");
    }

    private static void checkPdf(byte[] bytes, MultipartFile file) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            int pages = pdf.getNumberOfPages();
            if (pages > MAX_PAGES) throw invalid(name(file) + " tem " + pages + " páginas; o limite é " + MAX_PAGES + " por arquivo.");
        } catch (InvalidPasswordException e) {
            throw invalid(name(file) + " tem senha. Salve uma cópia sem senha e envie de novo.");
        } catch (IOException e) {
            throw invalid("Não consegui abrir " + name(file) + ". Confira se o PDF está inteiro.");
        }
    }

    /** Extrato em CSV ou OFX de banco costuma vir em Windows-1252. */
    private static byte[] utf8(byte[] bytes, MultipartFile file) {
        for (byte b : bytes) {
            if (b == 0) throw invalid(name(file) + " não é PDF, imagem nem texto.");
        }
        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            return bytes;
        } catch (CharacterCodingException e) {
            return new String(bytes, WINDOWS_1252).getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String name(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "O arquivo" : name;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
