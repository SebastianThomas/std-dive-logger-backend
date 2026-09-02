package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import java.util.List;

@Entity
@Table(name = "t_dive_computer")
@SuppressWarnings("NullAway.Init")
public class DiveComputerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_computer_id", nullable = false)
    private Long id;

    @Column(name = "serial_number")
    private @Nullable String serialNumber;

    @Column(name = "custom_identifier", nullable = false)
    private String customIdentifier;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "fk_manufacturer_id")
    private DiveComputerManufacturerEntity manufacturer;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "fk_user_id")
    private UserEntity user;

    // Set when this computer/handset is permanently paired with a specific CCR unit - see
    // DiveComputer.ccrUnitId and DiveService#inferConfigurationFromComputer.
    @ManyToOne
    @JoinColumn(name = "fk_ccr_unit_id")
    private @Nullable CcrUnitEntity ccrUnit;

    @OneToMany(mappedBy = "computer")
    private List<DiveProfileEntity> profiles;

    public DiveComputerEntity() {}

    public DiveComputerEntity(
            @Nullable final String serialNumber,
            @NotNull final String customIdentifier,
            final DiveComputerManufacturerEntity manufacturer,
            final UserEntity user) {
        this.serialNumber = serialNumber;
        this.customIdentifier = customIdentifier;
        this.manufacturer = manufacturer;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public DiveComputerManufacturerEntity getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(final DiveComputerManufacturerEntity manufacturer) {
        this.manufacturer = manufacturer;
    }

    public DiveComputer toRecord() {
        return new DiveComputer(
                id,
                manufacturer.toRecord(),
                serialNumber,
                customIdentifier,
                ccrUnit != null ? ccrUnit.getId() : null);
    }

    public void setIdentifier(final @NotBlank String customIdentifier) {
        this.customIdentifier = customIdentifier;
    }

    public @Nullable CcrUnitEntity getCcrUnit() {
        return ccrUnit;
    }

    public void setCcrUnit(final @Nullable CcrUnitEntity ccrUnit) {
        this.ccrUnit = ccrUnit;
    }
}
