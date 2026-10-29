package ch.sthomas.stddivegastracker.model.entity;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.model.fill.FillContent;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

/**
 * One source feeding a fill, with the stored results of DOMAIN §5.4; replaced as a whole on every
 * fill edit. A null source vessel is compressor AIR.
 */
@Entity
@Table(name = "t_fill_stage")
@SuppressWarnings("NullAway.Init")
public class FillStageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_fill_stage_id", nullable = false)
    private Long id;

    /** Position in the fill, 0-based. */
    @Column(name = "stage_index", nullable = false)
    private int stageIndex;

    @Column(name = "fk_source_vessel_id")
    private @Nullable Long sourceVesselId;

    /** Source mix snapshot (DOMAIN §5.1); AIR stages store {@code GasMix.AIR}. */
    @Column(name = "source_o2", nullable = false)
    private double sourceO2;

    @Column(name = "source_he", nullable = false)
    private double sourceHe;

    @Column(name = "pressure_after_bar", nullable = false)
    private double pressureAfterBar;

    @Column(name = "added_litres", nullable = false)
    private double addedLitres;

    @Column(name = "added_o2_litres", nullable = false)
    private double addedO2Litres;

    @Column(name = "added_he_litres", nullable = false)
    private double addedHeLitres;

    @Column(name = "line_loss_litres", nullable = false)
    private double lineLossLitres;

    @Column(name = "draw_litres", nullable = false)
    private double drawLitres;

    /** The source bank's gauge after this stage, as entered; also stored as a bank reading. */
    @Column(name = "source_pressure_after_bar")
    private @Nullable Double sourcePressureAfterBar;

    public FillStageEntity() {}

    public FillStageEntity(
            final int stageIndex,
            final @Nullable Long sourceVesselId,
            final GasMix sourceMix,
            final double pressureAfterBar,
            final double addedLitres,
            final double addedO2Litres,
            final double addedHeLitres,
            final double lineLossLitres,
            final double drawLitres,
            final @Nullable Double sourcePressureAfterBar) {
        this.stageIndex = stageIndex;
        this.sourceVesselId = sourceVesselId;
        this.sourceO2 = sourceMix.o2();
        this.sourceHe = sourceMix.he();
        this.pressureAfterBar = pressureAfterBar;
        this.addedLitres = addedLitres;
        this.addedO2Litres = addedO2Litres;
        this.addedHeLitres = addedHeLitres;
        this.lineLossLitres = lineLossLitres;
        this.drawLitres = drawLitres;
        this.sourcePressureAfterBar = sourcePressureAfterBar;
    }

    public Long getId() {
        return id;
    }

    public int getStageIndex() {
        return stageIndex;
    }

    public @Nullable Long getSourceVesselId() {
        return sourceVesselId;
    }

    public GasMix getSourceMix() {
        return new GasMix(sourceO2, sourceHe);
    }

    public double getPressureAfterBar() {
        return pressureAfterBar;
    }

    public double getAddedLitres() {
        return addedLitres;
    }

    public double getAddedO2Litres() {
        return addedO2Litres;
    }

    public double getAddedHeLitres() {
        return addedHeLitres;
    }

    public double getLineLossLitres() {
        return lineLossLitres;
    }

    public double getDrawLitres() {
        return drawLitres;
    }

    public boolean isAir() {
        return sourceVesselId == null;
    }

    public FillContent.Stage toRecord() {
        return new FillContent.Stage(
                id,
                stageIndex,
                sourceVesselId,
                getSourceMix(),
                pressureAfterBar,
                addedLitres,
                addedO2Litres,
                addedHeLitres,
                lineLossLitres,
                drawLitres,
                sourcePressureAfterBar);
    }
}
