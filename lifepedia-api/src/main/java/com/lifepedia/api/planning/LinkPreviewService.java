package com.lifepedia.api.planning;

import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.planning.LinkMetadataParser.Metadata;
import com.lifepedia.api.planning.PlanningDtos.LinkPreview;
import com.lifepedia.api.planning.PlanningDtos.Reference;
import com.lifepedia.api.planning.PriceReferenceService.Match;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.image.Images;
import com.lifepedia.api.shared.net.SafeHttpClient;
import com.lifepedia.api.shared.net.SafeHttpClient.FetchException;
import com.lifepedia.api.shared.net.SafeHttpClient.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Lê nome, preço e foto de um link de produto; o que a loja não liberar vem de produto parecido no Buscapé.
 * Sem transação: a busca externa não segura conexão do banco.
 */
@Service
public class LinkPreviewService {

    private static final int MAX_PAGE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;
    private static final int PREVIEWS_PER_WINDOW = 20;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(LinkPreviewService.class);

    private final SafeHttpClient http;
    private final GroupAccess access;
    private final RateLimiter rateLimiter;
    private final PriceReferenceService references;

    public LinkPreviewService(SafeHttpClient http, GroupAccess access, RateLimiter rateLimiter,
                              PriceReferenceService references) {
        this.http = http;
        this.access = access;
        this.rateLimiter = rateLimiter;
        this.references = references;
    }

    public LinkPreview preview(UUID userId, UUID groupId, String rawUrl) {
        access.requireMember(groupId, userId);
        URI url = parse(rawUrl);
        acquire(userId);
        LinkPreview page = read(url);
        boolean missing = page.blocked() || page.price() == null || page.imageData() == null;
        if (!missing || page.title() == null) return page;
        Reference reference = reference(page.title(), page.imageData() == null);
        return reference == null ? page : new LinkPreview(page.url(), page.title(), page.titleFromUrl(), page.price(),
                page.currency(), page.site(), page.imageData(), page.blocked(), reference);
    }

    /** Preço e foto de produto parecido com o nome digitado (sem link); nulo se nada parecido. */
    public Reference referenceByName(UUID userId, UUID groupId, String name) {
        access.requireMember(groupId, userId);
        acquire(userId);
        return reference(name, true);
    }

    private void acquire(UUID userId) {
        if (!rateLimiter.tryAcquire("link-preview:" + userId, PREVIEWS_PER_WINDOW, WINDOW)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas buscas de link. Tente de novo em alguns minutos.");
        }
    }

    private Reference reference(String name, boolean withImage) {
        Match match = references.find(name).orElse(null);
        if (match == null) return null;
        String imageData = null;
        for (String candidate : withImage ? match.imageUrls() : List.<String>of()) {
            imageData = image(candidate);
            if (imageData != null) break;
        }
        return new Reference(PriceReferenceService.SITE, match.title(), match.url(), match.price(),
                match.stores() > 0 ? match.stores() : null, match.store(), imageData);
    }

    private LinkPreview read(URI url) {
        String site = LinkMetadataParser.siteName(url);
        String slugTitle = LinkMetadataParser.titleFromUrl(url);

        Metadata metadata;
        URI finalUrl;
        try {
            Response page = http.get(url, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1", MAX_PAGE_BYTES,
                    type -> type.contains("html"));
            finalUrl = page.url();
            Document doc = Jsoup.parse(new ByteArrayInputStream(page.body()),
                    page.charset() == null ? null : page.charset().name(), finalUrl.toString());
            metadata = LinkMetadataParser.parse(doc, finalUrl);
        } catch (FetchException | IOException e) {
            log.debug("Prévia de link falhou para {}: {}", url.getHost(), e.getMessage());
            return blocked(url, slugTitle, site);
        }
        if (!metadata.structured()) {
            // Link encurtado: o nome costuma estar no endereço final (ou no destino da página de verificação).
            String title = slugTitle != null ? slugTitle : LinkMetadataParser.titleFromRedirect(finalUrl);
            return blocked(url, title, site);
        }

        boolean brl = metadata.currency() == null || metadata.currency().equals("BRL");
        BigDecimal price = brl ? metadata.price() : null;
        String title = metadata.title() != null ? metadata.title() : slugTitle;
        return new LinkPreview(url.toString(), title, metadata.title() == null && slugTitle != null, price,
                metadata.currency(), metadata.site() != null ? metadata.site() : site, image(metadata.imageUrl()), false,
                null);
    }

    private static LinkPreview blocked(URI url, String title, String site) {
        return new LinkPreview(url.toString(), title, title != null, null, null, site, null, true, null);
    }

    /** Baixa e re-encoda a foto; falha vira "sem foto". */
    private String image(String imageUrl) {
        if (imageUrl == null) return null;
        try {
            Response image = http.get(SafeHttpClient.parseUserUrl(imageUrl), "image/jpeg,image/png,image/webp;q=0.9",
                    MAX_IMAGE_BYTES, type -> type.startsWith("image/"));
            byte[] jpeg = Images.fromDownload(image.body(), PlanImageStore.MAX_SIDE);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
        } catch (FetchException | BusinessException | IllegalArgumentException e) {
            log.debug("Imagem do link ignorada: {}", e.getMessage());
            return null;
        }
    }

    static URI parse(String rawUrl) {
        try {
            return SafeHttpClient.parseUserUrl(rawUrl);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Link inválido. Use um endereço http(s) completo.");
        }
    }
}
