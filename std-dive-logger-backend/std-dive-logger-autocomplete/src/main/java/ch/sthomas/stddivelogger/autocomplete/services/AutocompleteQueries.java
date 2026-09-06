package ch.sthomas.stddivelogger.autocomplete.services;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.dive.AutoDetectRule;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.dive.conditions.WaterType;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.Group;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class AutocompleteQueries {
    private static final int PAGE_SIZE = 10;
    private final NamedParameterJdbcTemplate jdbc;

    public AutocompleteQueries(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PagedResponse<FrontendUser> users(final String query, final int page) {
        return page(
                "t_users",
                "name",
                "pk_user_id, name, custom_icon_url, custom_background_url",
                query,
                page,
                (rs, row) ->
                        new FrontendUser(
                                rs.getLong("pk_user_id"),
                                rs.getString("name"),
                                rs.getString("custom_icon_url"),
                                rs.getString("custom_background_url")));
    }

    public List<Group> groups(final String query, final int page) {
        return page(
                        "t_groups",
                        "group_name",
                        "pk_group_id, group_name",
                        query,
                        page,
                        (rs, row) ->
                                new Group(rs.getLong("pk_group_id"), rs.getString("group_name")))
                .result();
    }

    public PagedResponse<DiveSite> sites(final String query, final int page) {
        return page(
                "t_dive_site",
                "name",
                "pk_dive_site_id, name, ST_Y(location::geometry) AS latitude, ST_X(location::geometry) AS longitude, water_type, zone_id",
                query,
                page,
                (rs, row) -> {
                    final var water = rs.getString("water_type");
                    return new DiveSite(
                            rs.getLong("pk_dive_site_id"),
                            rs.getString("name"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude"),
                            null,
                            null,
                            null,
                            null,
                            water == null ? null : WaterType.valueOf(water),
                            List.of(),
                            false,
                            rs.getString("zone_id"));
                });
    }

    public List<TagDefinition> tags(final String query, final @Nullable Long userId) {
        final var params =
                new MapSqlParameterSource("query", query)
                        .addValue("userId", userId, java.sql.Types.BIGINT);
        return jdbc.query(
                """
                SELECT t.pk_tag_id, t.name, t.auto_detect_rule, t.fk_user_id::bigint AS fk_user_id,
                       (SELECT count(*) FROM t_dive_tags dt JOIN t_dives d ON d.pk_dive_id=dt.fk_dive_id
                        WHERE dt.fk_tag_id=t.pk_tag_id AND NOT dt.dismissed AND d.fk_diver_id=:userId) AS dive_count
                FROM t_tag_definitions t
                WHERE (t.fk_user_id IS NULL OR t.fk_user_id=:userId)
                  AND lower(t.name) LIKE lower(concat('%', :query, '%'))
                ORDER BY dive_count DESC, t.name
                """,
                params,
                (rs, row) -> {
                    final var rule = rs.getString("auto_detect_rule");
                    return new TagDefinition(
                            rs.getLong("pk_tag_id"),
                            rs.getString("name"),
                            rule == null ? null : AutoDetectRule.valueOf(rule),
                            rs.getObject("fk_user_id", Long.class),
                            rs.getLong("dive_count"));
                });
    }

    // Identifiers come only from the fixed callers above; all user input is bound.
    private <T> PagedResponse<T> page(
            final String table,
            final String column,
            final String columns,
            final String query,
            final int page,
            final RowMapper<T> mapper) {
        final var params =
                new MapSqlParameterSource("query", query)
                        .addValue("limit", PAGE_SIZE)
                        .addValue("offset", (long) page * PAGE_SIZE);
        final var from =
                " FROM "
                        + table
                        + " WHERE starts_with("
                        + column
                        + ", :query) OR "
                        + column
                        + " % :query";
        final long count =
                Objects.requireNonNull(
                        jdbc.queryForObject("SELECT count(*)" + from, params, Long.class));
        final var result =
                jdbc.query(
                        "SELECT "
                                + columns
                                + from
                                + " ORDER BY starts_with("
                                + column
                                + ", :query) DESC, similarity("
                                + column
                                + ", :query) DESC, length("
                                + column
                                + ") LIMIT :limit OFFSET :offset",
                        params,
                        mapper);
        return new PagedResponse<>(
                PAGE_SIZE, (int) Math.ceil((double) count / PAGE_SIZE), count, result);
    }
}
