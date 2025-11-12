package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.GroupEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {}
