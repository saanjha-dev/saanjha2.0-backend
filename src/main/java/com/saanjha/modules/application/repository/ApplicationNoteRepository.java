package com.saanjha.modules.application.repository;

import com.saanjha.modules.application.entity.ApplicationNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationNoteRepository extends JpaRepository<ApplicationNote, UUID> {

    List<ApplicationNote> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
