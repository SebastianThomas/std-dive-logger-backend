package ch.sthomas.stddivelogger.model.dive.gear;

/**
 * How a specific {@link CcrUnit} is worn - an intrinsic property of that physical unit, not of any
 * one dive. Unlike a diver's own {@link BaseConfiguration} (backmount/sidemount only), a rebreather
 * can also be chestmounted.
 */
public enum CcrMountPosition {
    BACKMOUNT,
    SIDEMOUNT,
    CHESTMOUNT
}
