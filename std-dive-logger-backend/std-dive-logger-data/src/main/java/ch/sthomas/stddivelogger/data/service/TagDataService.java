package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.TagDefinitionRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.entity.TagDefinitionEntity;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;

import jakarta.validation.constraints.NotBlank;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class TagDataService {

    private final TagDefinitionRepository tagDefinitionRepository;
    private final UserRepository userRepository;

    public TagDataService(final TagDefinitionRepository tagDefinitionRepository,
                          final UserRepository userRepository) {
        this.tagDefinitionRepository = tagDefinitionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TagDefinition> findAllVisibleToUser(final long userId) {
        return tagDefinitionRepository.findAllVisibleToUser(userId).stream()
                .map(TagDefinitionEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TagDefinition> findById(final long id) {
        return tagDefinitionRepository.findById(id).map(TagDefinitionEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public List<TagDefinitionEntity> findAutoDetectEntitiesForUser(final long userId) {
        return tagDefinitionRepository.findAutoDetectTagsForUser(userId);
    }

    @Transactional(readOnly = true)
    public List<TagDefinitionEntity> findEntitiesByIdsVisibleToUser(final List<Long> ids,
                                                                     final long userId) {
        final var entities = tagDefinitionRepository.findAllVisibleToUser(userId);
        return entities.stream().filter(e -> ids.contains(e.getId())).toList();
    }

    @Transactional(readOnly = true)
    public List<TagDefinition> findByPartialName(final String query, final long userId) {
        return tagDefinitionRepository.findByPartialNameVisibleToUser(query, userId).stream()
                .map(TagDefinitionEntity::toRecord)
                .toList();
    }

    @Transactional
    public TagDefinition createTag(final long userId, @NotBlank final String name) {
        if (tagDefinitionRepository.existsByNameIgnoreCaseAndUserIsNull(name)
                || tagDefinitionRepository.existsByNameIgnoreCaseAndUser_Id(name, userId)) {
            throw new IllegalArgumentException("A tag with the name '" + name + "' already exists.");
        }
        final var user = userRepository.findById(userId).orElseThrow(
                () -> new NoSuchElementException("User not found: " + userId));
        final var entity = new TagDefinitionEntity(name, user, null);
        return tagDefinitionRepository.save(entity).toRecord();
    }
}
