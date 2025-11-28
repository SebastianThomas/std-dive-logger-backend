package ch.sthomas.stddivelogger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.entity.UserEntity;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class DiveServiceTest {
    @Test
    void testPagedResponse() {
        final var users =
                List.of(
                        new UserEntity(1, "email@email.ch", "abc123", "name"),
                        new UserEntity(2, "email2@email.ch", "abc", "other"));
        final var pageable = Pageable.ofSize(5);
        final var response =
                PagedResponse.of(new PageImpl<>(users, pageable, 2), UserEntity::toRecord);
        assertEquals(users.stream().map(UserEntity::toRecord).toList(), response.result());
        assertEquals(pageable.getPageSize(), response.pageSize());
    }
}
