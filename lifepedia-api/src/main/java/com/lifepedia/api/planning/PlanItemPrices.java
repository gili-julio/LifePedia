package com.lifepedia.api.planning;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Fachada do planejamento para o acompanhamento de preço: confere o item e atualiza o valor com o menor preço. */
@Component
public class PlanItemPrices {

    /** @param url link do item, para começar o acompanhamento */
    public record PlanItemRef(UUID id, String name, String url) {
    }

    private final PlanItemRepository items;

    public PlanItemPrices(PlanItemRepository items) {
        this.items = items;
    }

    /** Item do planejamento deste grupo. */
    @Transactional(readOnly = true)
    public Optional<PlanItemRef> find(UUID planItemId, UUID groupId) {
        return items.findByIdAndGroupId(planItemId, groupId).map(i -> new PlanItemRef(i.getId(), i.getName(), i.getUrl()));
    }

    /** Valor do item = menor preço (só compra ainda não feita). */
    @Transactional
    public void updateAmount(UUID planItemId, BigDecimal price) {
        items.findById(planItemId)
                .filter(i -> i.getKind() == PlanItem.Kind.PURCHASE && !i.isAcquired() && i.getAmount().compareTo(price) != 0)
                .ifPresent(i -> i.setAmount(price));
    }
}
