package com.lifepedia.api.delivery;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Transportadoras que a pessoa pode escolher, com o código do 17TRACK
 * ({@code res.17track.net/asset/carrier/info/apicarrier.all.json}). Sem escolha, o 17TRACK detecta sozinho, exceto nos
 * códigos que têm dono certo ({@link #guess}).
 */
public enum DeliveryCarrier {
    CORREIOS(2151),
    SHOPEE(101332),
    LOGGI(100457),
    JT_EXPRESS(100797),
    JADLOG(101052),
    TOTAL_EXPRESS(101415),
    KANGU(100887),
    AMERICANAS(100727),
    ANJUN(191915),
    CAINIAO(190271),
    ALIEXPRESS(190625);

    /** Shopee Xpress no Brasil: BR + 12 a 14 dígitos + letra (a detecção do 17TRACK confunde com a Loggi). */
    private static final Pattern SHOPEE_CODE = Pattern.compile("BR\\d{12,14}[A-Z]");

    private final int key;

    DeliveryCarrier(int key) {
        this.key = key;
    }

    public int key() {
        return key;
    }

    public static Optional<DeliveryCarrier> of(Integer key) {
        if (key == null) return Optional.empty();
        return Arrays.stream(values()).filter(c -> c.key == key).findFirst();
    }

    /** Transportadora certa pelo formato do código, quando há; vazio = deixa o 17TRACK detectar. */
    public static Optional<DeliveryCarrier> guess(String code) {
        return SHOPEE_CODE.matcher(code).matches() ? Optional.of(SHOPEE) : Optional.empty();
    }

    /** Escolhida pela pessoa ou, sem escolha, a do formato do código. */
    public static Integer wanted(Integer choice, String code) {
        return choice != null ? choice : guess(code).map(DeliveryCarrier::key).orElse(null);
    }
}
