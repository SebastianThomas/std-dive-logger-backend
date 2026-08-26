package ch.sthomas.stddivelogger.model.dive.gear;

public enum BaseConfiguration {
    SINGLE_TANK,
    SINGLE_TANK_AVELO,
    SIDEMOUNT,
    BACKMOUNT_DOUBLES,
    BACKMOUNT_CCR,
    SIDEMOUNT_CCR,
    // A chestmount CCR unit's own bailout cylinder(s) are rigged separately - unlike back/side
    // mount CCR, where the same word already implies where bailout typically goes too, chestmount
    // genuinely leaves that ambiguous, so it's split into these two rather than one bare
    // CHESTMOUNT_CCR value (removed - see V0_4_5__gear_buddy_and_dive_summary_updates.sql).
    CHESTMOUNT_CCR_SIDEMOUNT_BAILOUT,
    CHESTMOUNT_CCR_BACKMOUNT_BAILOUT,
    DUAL_CCR_BACKMOUNT,
    DUAL_CCR_SIDEMOUNT,
    DUAL_CCR_BACKMOUNT_SIDEMOUNT,
    DUAL_CCR_BACKMOUNT_CHESTMOUNT,
    DUAL_CCR_SIDEMOUNT_CHESTMOUNT,
    OTHER;

    /** Whether this rig type is any kind of closed-circuit rebreather setup. */
    public boolean isCcr() {
        return name().contains("CCR");
    }
}
