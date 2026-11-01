package com.lifepedia.api.finance.bank;

import java.util.Arrays;
import java.util.Optional;

/** Catálogo de bancos (espelhado em {@code lifepedia-web/src/lib/banks.ts}, com os logos); fora dele, {@link #OTHER}. */
public enum Bank {
    BB("Banco do Brasil"),
    CAIXA("Caixa"),
    ITAU("Itaú"),
    BRADESCO("Bradesco"),
    SANTANDER("Santander"),
    NUBANK("Nubank"),
    INTER("Inter"),
    C6("C6 Bank"),
    BTG("BTG Pactual"),
    XP("XP"),
    SAFRA("Safra"),
    ORIGINAL("Original"),
    PAN("Banco Pan"),
    BV("BV"),
    NEON("Neon"),
    PICPAY("PicPay"),
    MERCADO_PAGO("Mercado Pago"),
    PAGBANK("PagBank"),
    STONE("Stone"),
    BANRISUL("Banrisul"),
    BRB("BRB"),
    BNB("Banco do Nordeste"),
    BMG("BMG"),
    MERCANTIL("Mercantil"),
    SICOOB("Sicoob"),
    SICREDI("Sicredi"),
    UNICRED("Unicred"),
    CRESOL("Cresol"),
    IFOOD("iFood Pago"),
    MAGALU("MagaluPay"),
    OTHER("Outro banco");

    private final String label;

    Bank(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<Bank> of(String code) {
        return Arrays.stream(values()).filter(b -> b.name().equals(code)).findFirst();
    }
}
