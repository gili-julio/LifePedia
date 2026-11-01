package com.lifepedia.api.financing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FinancingRateRepository extends JpaRepository<FinancingRate, UUID> {

    List<FinancingRate> findByModalityOrderByPositionAsc(Modality modality);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FinancingRate r where r.modality = :modality")
    int deleteByModality(Modality modality);
}
