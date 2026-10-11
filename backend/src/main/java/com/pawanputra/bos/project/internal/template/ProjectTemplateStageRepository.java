package com.pawanputra.bos.project.internal.template;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProjectTemplateStageRepository extends JpaRepository<ProjectTemplateStage, UUID> {

    List<ProjectTemplateStage> findAllByTemplateIdOrderByPositionAsc(UUID templateId);

    List<ProjectTemplateStage> findAllByTemplateIdInOrderByPositionAsc(Collection<UUID> templateIds);

    /** Removes a template's stages at once, so the new set can reuse their keys. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProjectTemplateStage s where s.templateId = :templateId")
    void deleteAllOfTemplate(UUID templateId);
}
