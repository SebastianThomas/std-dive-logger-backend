package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.TagDataService;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.validation.constraints.NotBlank;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagService {

    private final TagDataService tagDataService;

    public TagService(final TagDataService tagDataService) {
        this.tagDataService = tagDataService;
    }

    public List<TagDefinition> getTagsForUser(final User user) {
        return tagDataService.findAllVisibleToUser(user.id());
    }

    public TagDefinition createTag(final User user, @NotBlank final String name) {
        return tagDataService.createTag(user.id(), name);
    }

    public List<TagDefinition> getTagsByPartialName(final User user, final String query) {
        return tagDataService.findByPartialName(query, user.id());
    }
}
