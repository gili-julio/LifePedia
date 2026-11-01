package com.lifepedia.api.financing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IndexValueRepository extends JpaRepository<IndexValue, UUID> {

    List<IndexValue> findByIndexOrderByMonthAsc(CorrectionIndex index);
}
