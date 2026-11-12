package com.lifepedia.api.delivery;

import com.lifepedia.api.delivery.TrackSnapshot.TrackEvent;
import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    /** @param groupId compartilhar com um grupo da pessoa (membros veem e recebem os avisos) */
    public record DeliveryRequest(
            @NotBlank(message = "Informe o código de rastreio.") @Size(max = 40, message = "Código muito longo.") String trackingCode,
            @NotBlank(message = "Dê um nome para o item.") @Size(max = 80, message = "Nome muito longo.") String name,
            UUID addressId,
            UUID groupId) {
    }

    public record DeliveryUpdate(
            @NotBlank(message = "Dê um nome para o item.") @Size(max = 80, message = "Nome muito longo.") String name,
            UUID addressId,
            UUID groupId) {
    }

    public record ArchiveRequest(@NotNull Boolean archived) {
    }

    public record AddressRequest(
            @NotBlank(message = "Dê um apelido (ex.: Casa).") @Size(max = 40, message = "Apelido muito longo.") String label,
            @Size(max = 120, message = "Nome muito longo.") String recipient,
            @NotBlank(message = "Informe o CEP.") @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido.") String cep,
            @NotBlank(message = "Informe a rua.") @Size(max = 160, message = "Rua muito longa.") String street,
            @Size(max = 20, message = "Número muito longo.") String number,
            @Size(max = 80, message = "Complemento muito longo.") String complement,
            @Size(max = 80, message = "Bairro muito longo.") String district,
            @NotBlank(message = "Informe a cidade.") @Size(max = 80, message = "Cidade muito longa.") String city,
            @NotBlank(message = "Informe o estado.") @Pattern(regexp = "[A-Za-z]{2}", message = "Estado inválido (UF).") String state,
            Boolean defaultAddress) {
    }

    public record AddressResponse(UUID id, String label, String recipient, String cep, String street, String number,
                                  String complement, String district, String city, String state, boolean defaultAddress) {

        static AddressResponse of(DeliveryAddress a) {
            return new AddressResponse(a.getId(), a.getLabel(), a.getRecipient(), a.getCep(), a.getStreet(), a.getNumber(),
                    a.getComplement(), a.getDistrict(), a.getCity(), a.getState(), a.isDefaultAddress());
        }
    }

    /** Para quem não é dono: só apelido e cidade. */
    public record AddressShort(String label, String city, String state) {
    }

    public record CepResponse(String cep, String street, String district, String city, String state) {
    }

    public record GroupRef(UUID id, String name) {
    }

    public record Carrier(String name, String url) {
    }

    /**
     * Entrega na lista e no detalhe ({@code events} só no detalhe).
     *
     * @param stage         0 postado, 1 em trânsito, 2 saiu para entrega/retirada, 3 entregue; -1 sem informação
     * @param daysInTransit dias desde o 1º evento até hoje ou a entrega
     * @param stalledDays   dias sem novidade (nulo se entregue ou sem eventos)
     * @param arrivesToday  saiu para entrega ou a previsão termina hoje
     * @param address       do dono: completo; de quem vê pelo grupo: {@code addressShort}
     * @param tracking      rastreio automático ligado no servidor
     */
    public record DeliveryResponse(UUID id, String name, String trackingCode, DeliveryStatus status, int stage, boolean problem,
                                   String subStatus, boolean taxPending, Carrier carrier, OffsetDateTime latestAt,
                                   String latestText, String latestLocation, String origin, LocalDate estimatedFrom,
                                   LocalDate estimatedTo, OffsetDateTime deliveredAt, Integer daysInTransit, Integer stalledDays,
                                   boolean arrivesToday, String imageUrl, AddressResponse address, AddressShort addressShort,
                                   GroupRef group, UserSummary owner, boolean mine, OffsetDateTime archivedAt,
                                   OffsetDateTime lastSyncAt, String syncError, boolean tracking, OffsetDateTime createdAt,
                                   List<TrackEvent> events) {
    }

    /** Início: entregas ativas, as que chegam hoje e as com problema. */
    public record Summary(long active, List<DeliveryResponse> today, long problems, long taxes) {
    }

    /** Para o arquivo da conta. */
    public record DeliveryData(String name, String trackingCode, DeliveryStatus status, String carrier, String address,
                               OffsetDateTime deliveredAt, OffsetDateTime archivedAt, OffsetDateTime createdAt) {
    }

    public record AddressData(String label, String recipient, String cep, String street, String number, String complement,
                              String district, String city, String state, boolean defaultAddress) {
    }

    public record DeliveriesExport(List<AddressData> addresses, List<DeliveryData> deliveries) {
    }
}
