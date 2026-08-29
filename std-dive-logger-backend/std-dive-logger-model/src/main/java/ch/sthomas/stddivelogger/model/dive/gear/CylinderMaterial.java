package ch.sthomas.stddivelogger.model.dive.gear;

/**
 * What a tracked cylinder is made of. Descriptive only for now - no consumption or buoyancy maths
 * hangs off it yet (a hook for a future weighting feature). Set from the standard-cylinder picker
 * on the frontend, or inferred from litre volume for legacy/imported rows - see {@link
 * StandardCylinder#inferMaterial}.
 */
public enum CylinderMaterial {
    ALU,
    STEEL
}
