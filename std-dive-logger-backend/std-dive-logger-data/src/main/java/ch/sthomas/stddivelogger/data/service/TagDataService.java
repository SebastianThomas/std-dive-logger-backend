package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.DiveTagRepository;
import ch.sthomas.stddivelogger.data.repository.TagDefinitionRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.entity.TagDefinitionEntity;

import jakarta.validation.constraints.NotBlank;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TagDataService {

    private final TagDefinitionRepository tagDefinitionRepository;
    private final DiveTagRepository diveTagRepository;
    private final UserRepository userRepository;

    public TagDataService(
            final TagDefinitionRepository tagDefinitionRepository,
            final DiveTagRepository diveTagRepository,
            final UserRepository userRepository) {
        this.tagDefinitionRepository = tagDefinitionRepository;
        this.diveTagRepository = diveTagRepository;
        this.userRepository = userRepository;
    }

    /** Builds a tagId → diveCount map for the given user. */
    private Map<Long, Long> buildCountMap(final long userId) {
        return diveTagRepository.countTagUsageForUser(userId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    @Transactional(readOnly = true)
    public List<TagDefinition> findAllVisibleToUser(final long userId) {
        final var countMap = buildCountMap(userId);
        return tagDefinitionRepository.findAllVisibleToUser(userId).stream()
                .map(e -> e.toRecord(countMap.getOrDefault(e.getId(), 0L)))
                .sorted(
                        Comparator.comparingLong(TagDefinition::diveCount)
                                .reversed()
                                .thenComparing(TagDefinition::name))
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
    public List<TagDefinitionEntity> findEntitiesByIdsVisibleToUser(
            final List<Long> ids, final long userId) {
        final var entities = tagDefinitionRepository.findAllVisibleToUser(userId);
        return entities.stream().filter(e -> ids.contains(e.getId())).toList();
    }

    @Transactional(readOnly = true)
    public List<TagDefinition> findByPartialName(final String query, final long userId) {
        final var countMap = buildCountMap(userId);
        return tagDefinitionRepository.findByPartialNameVisibleToUser(query, userId).stream()
                .map(e -> e.toRecord(countMap.getOrDefault(e.getId(), 0L)))
                .sorted(
                        Comparator.comparingLong(TagDefinition::diveCount)
                                .reversed()
                                .thenComparing(TagDefinition::name))
                .toList();
    }

    @Transactional
    public void deleteTag(final long userId, final long tagId) {
        final var entity =
                tagDefinitionRepository
                        .findByIdAndUser_Id(tagId, userId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Tag "
                                                        + tagId
                                                        + " not found or not owned by user "
                                                        + userId));
        tagDefinitionRepository.delete(entity);
    }

    /** Used by the autocomplete service which has no authenticated user context. */
    @Transactional(readOnly = true)
    public List<TagDefinition> findSystemTagsByPartialName(final String query) {
        return tagDefinitionRepository.findByPartialNameSystemOnly(query).stream()
                .map(TagDefinitionEntity::toRecord)
                .toList();
    }

    @Transactional
    public TagDefinition createTag(final long userId, @NotBlank final String name) {
        if (tagDefinitionRepository.existsByNameIgnoreCaseAndUserIsNull(name)
                || tagDefinitionRepository.existsByNameIgnoreCaseAndUser_Id(name, userId)) {
            throw new IllegalArgumentException(
                    "A tag with the name '" + name + "' already exists.");
        }
        final var user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        final var entity = new TagDefinitionEntity(name, user, null);
        return tagDefinitionRepository.save(entity).toRecord();
    }
}
