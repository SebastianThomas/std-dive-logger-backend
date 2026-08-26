package ch.sthomas.stddivelogger.model.dive.gear;

/**
 * How the diver's own cylinders (whether OC-only or CCR bailout) are rigged - independent of
 * whether/how many CCR units are in play, which is tracked separately via {@link
 * DiveConfiguration#ccrUnit} / {@link DiveConfiguration#secondaryCcrUnit} and each unit's own
 * {@link CcrMountPosition}. Cylinder count/size is tracked in even finer detail via {@link
 * DiveConfiguration#cylinders} and isn't duplicated here.
 */
public enum BaseConfiguration {
    BACKMOUNT,
    SIDEMOUNT
}
