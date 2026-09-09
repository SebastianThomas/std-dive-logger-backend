package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.model.dive.DerivedSiteLocation;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Point-in-polygon lookups backed exclusively by the named maps JDBC connection. */
@Service
@ConditionalOnProperty(name = "maps.enabled", havingValue = "true")
public class MapsBoundaryDataService {

    private static final String READ_LOCATION_SQL =
            """
            SELECT fk_dive_site_id, country_name, country_code, region_name, region_code,
                   status, resolved_at, boundary_import_version
            FROM maps.site_location
            WHERE fk_dive_site_id = :siteId
            """;

    private static final String RESOLVE_SITE_SQL =
            """
            INSERT INTO maps.site_location (
                fk_dive_site_id, fk_country_boundary_id, country_name, country_code,
                fk_region_boundary_id, region_name, region_code, status, resolved_at,
                boundary_import_version
            )
            SELECT site.pk_dive_site_id,
                   country.pk_admin_boundary_id,
                   country.name,
                   country.iso3166_1,
                   region.pk_admin_boundary_id,
                   region.name,
                   COALESCE(region.iso3166_2, region.iso3166_1),
                   CASE WHEN country.pk_admin_boundary_id IS NULL THEN 'NO_COUNTRY' ELSE 'RESOLVED' END,
                   now(),
                   version.pk_import_run_id
            FROM public.t_dive_site site
            LEFT JOIN LATERAL (
                SELECT boundary.*
                FROM maps.admin_boundary boundary
                WHERE boundary.admin_level = 2
                  AND ST_Covers(boundary.geometry, site.location)
                ORDER BY ST_Area(boundary.geometry::geography), boundary.osm_relation_id
                LIMIT 1
            ) country ON true
            LEFT JOIN LATERAL (
                SELECT boundary.*
                FROM maps.admin_boundary boundary
                WHERE boundary.admin_level = 4
                  AND ST_Covers(boundary.geometry, site.location)
                ORDER BY ST_Area(boundary.geometry::geography), boundary.osm_relation_id
                LIMIT 1
            ) region ON true
            LEFT JOIN LATERAL (
                SELECT max(pk_import_run_id) AS pk_import_run_id
                FROM maps.import_run
                WHERE status = 'SUCCEEDED'
            ) version ON true
            WHERE site.pk_dive_site_id = :siteId
            ON CONFLICT (fk_dive_site_id) DO UPDATE SET
                fk_country_boundary_id = EXCLUDED.fk_country_boundary_id,
                country_name = EXCLUDED.country_name,
                country_code = EXCLUDED.country_code,
                fk_region_boundary_id = EXCLUDED.fk_region_boundary_id,
                region_name = EXCLUDED.region_name,
                region_code = EXCLUDED.region_code,
                status = EXCLUDED.status,
                resolved_at = EXCLUDED.resolved_at,
                boundary_import_version = EXCLUDED.boundary_import_version
            """;

    private static final String STALE_SITE_IDS_SQL =
            """
            SELECT site.pk_dive_site_id
            FROM public.t_dive_site site
            LEFT JOIN maps.site_location location
              ON location.fk_dive_site_id = site.pk_dive_site_id
            WHERE location.fk_dive_site_id IS NULL
               OR location.status = 'STALE'
               OR location.boundary_import_version IS DISTINCT FROM :importVersion
            ORDER BY site.pk_dive_site_id
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate mapsJdbc;

    public MapsBoundaryDataService(
            @Qualifier("mapsNamedParameterJdbcTemplate")
                    final NamedParameterJdbcTemplate mapsJdbc) {
        this.mapsJdbc = mapsJdbc;
    }

    @Transactional(transactionManager = "mapsTransactionManager", readOnly = true)
    public Optional<DerivedSiteLocation> findForSite(final long siteId) {
        return mapsJdbc
                .query(
                        READ_LOCATION_SQL,
                        new MapSqlParameterSource("siteId", siteId),
                        MapsBoundaryDataService::mapLocation)
                .stream()
                .findFirst();
    }

    @Transactional(transactionManager = "mapsTransactionManager")
    public Optional<DerivedSiteLocation> resolveSite(final long siteId) {
        final var params = new MapSqlParameterSource("siteId", siteId);
        if (mapsJdbc.update(RESOLVE_SITE_SQL, params) == 0) return Optional.empty();
        return findForSite(siteId);
    }

    @Transactional(transactionManager = "mapsTransactionManager")
    public int resolveStaleSites(final long importVersion, final int limit) {
        final var params =
                new MapSqlParameterSource("importVersion", importVersion).addValue("limit", limit);
        final List<Long> ids = mapsJdbc.queryForList(STALE_SITE_IDS_SQL, params, Long.class);
        ids.forEach(this::resolveSite);
        return ids.size();
    }

    private static DerivedSiteLocation mapLocation(final ResultSet rs, final int ignored)
            throws SQLException {
        final long version = rs.getLong("boundary_import_version");
        return new DerivedSiteLocation(
                rs.getLong("fk_dive_site_id"),
                rs.getString("country_name"),
                rs.getString("country_code"),
                rs.getString("region_name"),
                rs.getString("region_code"),
                rs.getString("status"),
                rs.getTimestamp("resolved_at").toInstant(),
                rs.wasNull() ? null : version);
    }
}
