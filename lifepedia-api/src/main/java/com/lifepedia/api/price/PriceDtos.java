package com.lifepedia.api.price;

import com.lifepedia.api.price.PricePoint.Source;
import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PriceDtos {

    private PriceDtos() {
    }

    /**
     * Novo item acompanhado.
     *
     * @param groupId    do grupo (membros veem e editam); nulo = lista de desejos pessoal
     * @param planItemId item do planejamento do mesmo grupo
     * @param links      links de lojas (até {@link PriceWatchService#MAX_LINKS})
     * @param price      preço visto agora (ex.: pelo botão do navegador)
     */
    public record CreateRequest(
            @NotBlank(message = "Dê um nome para o item.") @Size(max = 160, message = "Nome muito longo.") String name,
            UUID groupId,
            UUID planItemId,
            @Valid Settings settings,
            @Size(max = 5, message = "No máximo 5 links.") List<@NotBlank @Size(max = 2048, message = "Link muito longo.") String> links,
            @Valid ManualPriceRequest price) {
    }

    /**
     * @param targetPrice  avisa quando o menor preço chegar nele
     * @param dropPercent  avisa quando cair pelo menos isso desde a última consulta (padrão 10)
     * @param notifyGroup  avisa todos os membros (só itens de grupo)
     * @param updatePlan   atualiza o valor do item do planejamento com o menor preço
     */
    public record Settings(
            @DecimalMin(value = "0.01", message = "Preço-alvo inválido.") @Digits(integer = 12, fraction = 2) BigDecimal targetPrice,
            @Min(value = 1, message = "Queda mínima de 1%.") @Max(value = 90, message = "Queda de no máximo 90%.") Integer dropPercent,
            Boolean notifyGroup,
            Boolean updatePlan,
            Boolean active) {

        static final Settings DEFAULT = new Settings(null, null, null, null, null);
    }

    public record UpdateRequest(
            @NotBlank(message = "Dê um nome para o item.") @Size(max = 160, message = "Nome muito longo.") String name,
            @NotNull @Valid Settings settings) {
    }

    public record LinkRequest(@NotBlank(message = "Cole o link.") @Size(max = 2048, message = "Link muito longo.") String url) {
    }

    /** @param day padrão: hoje */
    public record ManualPriceRequest(
            @NotBlank(message = "Informe a loja.") @Size(max = 80, message = "Nome da loja muito longo.") String store,
            @NotNull(message = "Informe o preço.") @DecimalMin(value = "0.01", message = "Preço inválido.")
            @Digits(integer = 12, fraction = 2, message = "Preço inválido.") BigDecimal price,
            LocalDate day,
            @Size(max = 2048, message = "Link muito longo.") String url) {
    }

    /**
     * Oferta de uma loja na última consulta.
     *
     * @param totalPrice parcelado, quando diferente
     * @param day        dia do preço (anotado à mão pode ser de dias antes)
     */
    public record Offer(String store, BigDecimal price, BigDecimal totalPrice, String url, Source source, LocalDate day) {
    }

    /** Menor preço do dia e a loja. */
    public record HistoryPoint(LocalDate day, BigDecimal price, String store) {
    }

    /**
     * Nos últimos 90 dias (menor preço de cada dia).
     *
     * @param change7  variação do menor preço atual em relação a 7 dias atrás, em %
     * @param change30 em relação a 30 dias atrás, em %
     */
    public record Stats(BigDecimal min, BigDecimal avg, BigDecimal max, int days, BigDecimal change7, BigDecimal change30) {
    }

    public record LinkView(UUID id, String url, String store, BigDecimal lastPrice, OffsetDateTime lastCheckedAt,
                           String lastError) {
    }

    public record ManualView(UUID id, LocalDate day, String store, BigDecimal price, String url) {
    }

    /**
     * @param canDelete criador ou admin do grupo
     * @param atTarget  menor preço já no alvo
     */
    public record WatchSummary(UUID id, String name, UUID groupId, String groupName, UUID planItemId, String imageUrl,
                               BigDecimal bestPrice, String bestStore, String bestUrl, Integer storeCount,
                               BigDecimal targetPrice, int dropPercent, boolean notifyGroup, boolean updatePlan,
                               boolean active, BigDecimal change7, BigDecimal change30, boolean atTarget,
                               OffsetDateTime lastCheckedAt, String lastError, UserSummary owner, boolean mine,
                               boolean canDelete, OffsetDateTime createdAt) {
    }

    public record WatchDetail(WatchSummary watch, String buscapeUrl, List<Offer> offers, List<HistoryPoint> history,
                              Stats stats, List<LinkView> links, List<ManualView> manual) {
    }

    /** Para o arquivo da conta. */
    public record WatchData(String name, String group, BigDecimal targetPrice, int dropPercent, boolean active,
                            BigDecimal bestPrice, String bestStore, List<String> links, List<ManualData> manual,
                            OffsetDateTime createdAt) {
    }

    public record ManualData(LocalDate day, String store, BigDecimal price) {
    }
}
