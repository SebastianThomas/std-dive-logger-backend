local boundaries = osm2pgsql.define_area_table('admin_boundary', {
    { column = 'admin_level', type = 'int' },
    { column = 'name', type = 'text' },
    { column = 'name_en', type = 'text' },
    { column = 'iso3166_1', type = 'text' },
    { column = 'iso3166_2', type = 'text' },
    { column = 'geometry', type = 'multipolygon', projection = 4326, not_null = true },
}, {
    schema = 'maps_osm_stage',
    ids = { type = 'relation', id_column = 'osm_relation_id' },
})

function osm2pgsql.process_relation(object)
    local level = tonumber(object.tags.admin_level)
    if object.tags.boundary ~= 'administrative' or (level ~= 2 and level ~= 4) then
        return
    end
    if object.tags.name == nil then
        return
    end

    boundaries:insert({
        admin_level = level,
        name = object.tags.name,
        name_en = object.tags['name:en'],
        iso3166_1 = object.tags['ISO3166-1'],
        iso3166_2 = object.tags['ISO3166-2'],
        geometry = object:as_multipolygon(),
    })
end
