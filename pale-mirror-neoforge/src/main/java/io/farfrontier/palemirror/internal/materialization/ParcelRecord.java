package io.farfrontier.palemirror.internal.materialization;

import io.farfrontier.palemirror.api.ParcelKind;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Persisted spatial ownership contract. Influence bounds deliberately grant no mutation authority. */
public final class ParcelRecord {
    private final String id;
    private final String regionId;
    private final String dimensionId;
    private final BlockPos min;
    private final BlockPos max;
    private final String bindingId;
    private ParcelKind kind;
    private UUID leaseOwner;
    private long revision;
    private String commissioningPermit;

    public ParcelRecord(String id, String regionId, String dimensionId, BlockPos min, BlockPos max,
                        String bindingId, ParcelKind kind, UUID leaseOwner, long revision,
                        String commissioningPermit) {
        this.id = required(id, "id");
        this.regionId = required(regionId, "regionId");
        this.dimensionId = required(dimensionId, "dimensionId");
        this.min = Objects.requireNonNull(min, "min").immutable();
        this.max = Objects.requireNonNull(max, "max").immutable();
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            throw new IllegalArgumentException("Parcel bounds must be ordered");
        }
        this.bindingId = required(bindingId, "bindingId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.leaseOwner = leaseOwner;
        this.revision = revision;
        this.commissioningPermit = commissioningPermit == null ? "" : commissioningPermit;
        if (kind == ParcelKind.PLAYER_LEASE && leaseOwner == null) {
            throw new IllegalArgumentException("Player lease requires an owner");
        }
        if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
    }

    public String id() { return id; }
    public String regionId() { return regionId; }
    public String dimensionId() { return dimensionId; }
    public BlockPos min() { return min; }
    public BlockPos max() { return max; }
    public String bindingId() { return bindingId; }
    public ParcelKind kind() { return kind; }
    public UUID leaseOwner() { return leaseOwner; }
    public long revision() { return revision; }
    public String commissioningPermit() { return commissioningPermit; }
    public boolean contains(BlockPos position) {
        return position.getX() >= min.getX() && position.getX() <= max.getX()
                && position.getY() >= min.getY() && position.getY() <= max.getY()
                && position.getZ() >= min.getZ() && position.getZ() <= max.getZ();
    }
    public void leaseTo(UUID playerId) {
        if (kind != ParcelKind.RESERVED) throw new IllegalStateException("Only a reserved plot can become a player lease");
        kind = ParcelKind.PLAYER_LEASE;
        leaseOwner = Objects.requireNonNull(playerId, "playerId");
        commissioningPermit = "";
        revision++;
    }
    public void commissionCommunity(String permit) {
        if (kind != ParcelKind.PLAYER_LEASE && kind != ParcelKind.RESERVED) {
            throw new IllegalStateException("Only a reserved or player-leased parcel can be commissioned");
        }
        commissioningPermit = required(permit, "permit");
        kind = ParcelKind.COMMUNITY;
        leaseOwner = null;
        revision++;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
