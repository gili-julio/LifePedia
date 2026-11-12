package com.lifepedia.api.delivery;

/**
 * Situação da entrega (do 17TRACK). {@code stage}: 0 postado, 1 em trânsito, 2 saiu para entrega/retirada, 3 entregue;
 * -1 sem informação.
 */
public enum DeliveryStatus {
    PENDING(-1, false),
    POSTED(0, false),
    IN_TRANSIT(1, false),
    PICKUP(2, false),
    OUT_FOR_DELIVERY(2, false),
    DELIVERED(3, false),
    FAILED(2, true),
    EXCEPTION(1, true),
    EXPIRED(1, true);

    private final int stage;
    private final boolean problem;

    DeliveryStatus(int stage, boolean problem) {
        this.stage = stage;
        this.problem = problem;
    }

    public int stage() {
        return stage;
    }

    public boolean problem() {
        return problem;
    }

    /** Status principal do 17TRACK ({@code NotFound}, {@code InTransit}...). */
    public static DeliveryStatus of(String track17) {
        if (track17 == null) return PENDING;
        return switch (track17) {
            case "InfoReceived" -> POSTED;
            case "InTransit" -> IN_TRANSIT;
            case "AvailableForPickup" -> PICKUP;
            case "OutForDelivery" -> OUT_FOR_DELIVERY;
            case "Delivered" -> DELIVERED;
            case "DeliveryFailure" -> FAILED;
            case "Exception" -> EXCEPTION;
            case "Expired" -> EXPIRED;
            default -> PENDING;
        };
    }
}
