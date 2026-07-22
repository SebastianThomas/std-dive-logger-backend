package ch.sthomas.stddivelogger.model.dive.gear;

public enum BaseConfiguration {
    SINGLE_TANK,
    SINGLE_TANK_AVELO,
    SIDEMOUNT,
    BACKMOUNT_DOUBLES,
    BACKMOUNT_CCR,
    SIDEMOUNT_CCR,
    CHESTMOUNT_CCR,
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
