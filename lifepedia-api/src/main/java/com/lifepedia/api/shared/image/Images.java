package com.lifepedia.api.shared.image;

import com.lifepedia.api.shared.BusinessException;
import org.springframework.http.HttpStatus;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

/** Validação e re-encode de imagens: confere dimensões antes de decodificar e gera sempre um JPEG novo, sem metadados. */
public final class Images {

    /** Limite de imagens enviadas pelo usuário. */
    public static final int MAX_UPLOAD_BYTES = 1024 * 1024;
    private static final int MIN_SIDE = 64;
    private static final int MAX_INPUT_SIDE = 4096;

    static {
        // Registra leitores extras do classpath (WebP), também dentro do jar do Spring Boot.
        ImageIO.scanForPlugins();
    }

    private Images() {
    }

    /** Imagem enviada pelo usuário: só JPEG/PNG até 1 MB. Devolve JPEG com até {@code maxSide}px. */
    public static byte[] fromUpload(byte[] bytes, int maxSide) {
        if (bytes.length == 0 || bytes.length > MAX_UPLOAD_BYTES) {
            throw invalid("A imagem deve ter até 1 MB.");
        }
        if (!isJpeg(bytes) && !isPng(bytes)) {
            throw invalid("Envie uma imagem JPEG ou PNG.");
        }
        return encodeJpeg(resize(decode(bytes), maxSide));
    }

    /** Imagem baixada da internet (JPEG, PNG ou WebP; tamanho já limitado no download). */
    public static byte[] fromDownload(byte[] bytes, int maxSide) {
        if (!isJpeg(bytes) && !isPng(bytes) && !isWebp(bytes)) {
            throw invalid("Formato de imagem não suportado.");
        }
        return encodeJpeg(resize(decode(bytes), maxSide));
    }

    private static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Imagem inválida.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < MIN_SIDE || height < MIN_SIDE || width > MAX_INPUT_SIDE || height > MAX_INPUT_SIDE) {
                    throw invalid("A imagem deve ter entre " + MIN_SIDE + " e " + MAX_INPUT_SIDE + " px de lado.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException business) throw business;
            throw invalid("Imagem inválida.");
        }
    }

    /** Reduz para caber em {@code maxSide} e achata transparência em branco. */
    private static BufferedImage resize(BufferedImage source, int maxSide) {
        double scale = Math.min(1, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean isWebp(byte[] b) {
        return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
