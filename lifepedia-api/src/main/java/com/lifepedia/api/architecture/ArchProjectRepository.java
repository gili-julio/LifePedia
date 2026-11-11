package com.lifepedia.api.architecture;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ArchProjectRepository extends JpaRepository<ArchProject, UUID> {

    List<ArchProject> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<ArchProject> findByIdInAndUserIdNotOrderByUpdatedAtDesc(Collection<UUID> ids, UUID userId);

    long countByUserId(UUID userId);

    /** Espaço usado pelos projetos do dono: maquetes (inclusive as que ainda estão chegando) e anexos. */
    @Query(value = """
            select coalesce((select sum(v.size_bytes) from arch_versions v
                               join arch_projects p on p.id = v.project_id where p.user_id = :userId), 0)
                 + coalesce((select sum(a.size_bytes) from arch_attachments a
                               join arch_projects p on p.id = a.project_id where p.user_id = :userId), 0)
            """, nativeQuery = true)
    long usedBytes(UUID userId);

    @Query("select p.coverKey from ArchProject p where p.userId = :userId and p.coverKey is not null")
    List<String> coverKeysOfUser(UUID userId);
}
