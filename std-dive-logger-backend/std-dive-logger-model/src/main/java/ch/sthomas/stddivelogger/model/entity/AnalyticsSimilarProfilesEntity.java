package ch.sthomas.stddivelogger.model.entity;

public class AnalyticsSimilarProfilesEntity {
    // TODO
    // CREATE TABLE t_analytics_similar_profiles_result
    //         (
    //                 pk_result_id     INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    //                 similarity_score DOUBLE PRECISION NOT NULL
    //         );

    // CREATE TABLE t_dive_analytics_similar_profiles
    //         (
    //                 fk_profile_id        INTEGER NOT NULL REFERENCES t_dive_profiles
    // (pk_dive_profile_id),
    //                 fk_analytics_result  INTEGER NOT NULL REFERENCES
    // t_analytics_similar_profiles_result (pk_result_id),
    //                 fk_measurement_start INTEGER NOT NULL REFERENCES t_dive_measurements
    // (pk_dive_measurement_id),
    //                 fk_measurement_end   INTEGER NOT NULL REFERENCES t_dive_measurements
    // (pk_dive_measurement_id),
    //                 PRIMARY KEY (fk_profile_id, fk_analytics_result),
    //                 CHECK (fk_measurement_start != fk_measurement_end)
    // );

}
