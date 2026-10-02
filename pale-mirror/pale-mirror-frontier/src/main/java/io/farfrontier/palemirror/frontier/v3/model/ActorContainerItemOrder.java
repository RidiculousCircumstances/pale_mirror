package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Process-owned instruction for one actor/container item interaction at a declared station.
 * The owning job retains phase and persistence; this immutable instruction cannot choose a
 * different source, actor, recipe or destination when the world changes. An arrival only makes
 * the interaction eligible: canonical custody still requires its own confirmed transition.
 */
public record ActorContainerItemOrder(SubjectId ownerId, SubjectId actorId, Direction direction,
                                      Portion portion, ContainerEndpoint containerEndpoint,
                                      SurfaceAnchor station, ActorItemSlot actorSlot, long goalOrdinal, long goalRevision) {
    public enum Direction { TAKE, PLACE }
    public enum Hand { MAIN, OFF }
    public enum StationPort { INPUT, OUTPUT }

    public ActorContainerItemOrder(SubjectId ownerId, SubjectId actorId, Direction direction,
                                   Portion portion, ContainerEndpoint endpoint, SurfaceAnchor station,
                                   Hand hand, long ordinal, long revision) {
        this(ownerId, actorId, direction, portion, endpoint, station, new ActorItemSlot.Hand(hand), ordinal, revision);
    }

    /** An exact stack keeps its slot; a fungible lot keeps only its account's container. */
    public sealed interface ContainerEndpoint permits ContainerEndpoint.ExactSlot, ContainerEndpoint.FungibleContainer,
            ContainerEndpoint.ExactStationSlot, ContainerEndpoint.FungibleStation {
        SubjectId containerId();

        record ExactSlot(InventoryCustody.ContainerSlot slot) implements ContainerEndpoint {
            public ExactSlot { Objects.requireNonNull(slot, "exact item slot"); }
            @Override public SubjectId containerId() { return slot.containerId(); }
        }

        record FungibleContainer(SubjectId containerId) implements ContainerEndpoint {
            public FungibleContainer { Objects.requireNonNull(containerId, "fungible item container"); }
        }

        record ExactStationSlot(ProductionStationSpec spec, StationPort port,
                                InventoryCustody.ContainerSlot slot) implements ContainerEndpoint {
            public ExactStationSlot {
                Objects.requireNonNull(spec, "exact station declaration");
                Objects.requireNonNull(port, "exact station port");
                Objects.requireNonNull(slot, "exact station slot");
                if (!spec.containerId().equals(slot.containerId())
                        || slot.slot() != (port == StationPort.INPUT ? spec.inputSlot() : spec.outputSlot()))
                    throw new IllegalArgumentException("exact station endpoint disagrees with its declared port");
            }
            @Override public SubjectId containerId() { return spec.containerId(); }
        }

        record FungibleStation(ProductionStationSpec spec, StationPort port) implements ContainerEndpoint {
            public FungibleStation {
                Objects.requireNonNull(spec, "fungible station declaration");
                Objects.requireNonNull(port, "fungible station port");
            }
            @Override public SubjectId containerId() { return spec.containerId(); }
        }
    }

    public sealed interface Portion permits Portion.Exact, Portion.Fungible {
        String itemKind();
        int quantity();

        record Exact(ExactItemStack item) implements Portion {
            public Exact { Objects.requireNonNull(item, "exact item portion"); }
            @Override public String itemKind() { return item.itemKind(); }
            @Override public int quantity() { return item.count(); }
        }

        record Fungible(SubjectId sourceAccountId, ResourceCustody sourceCustody,
                        SubjectId destinationAccountId, ResourceCustody destinationCustody,
                        Optional<SubjectId> claimId, String itemKind, Map<SubjectId, Integer> lotQuantities) implements Portion {
            public Fungible {
                Objects.requireNonNull(sourceAccountId, "source resource account");
                Objects.requireNonNull(sourceCustody, "source resource custody");
                Objects.requireNonNull(destinationAccountId, "destination resource account");
                Objects.requireNonNull(destinationCustody, "destination resource custody");
                claimId = Objects.requireNonNull(claimId, "optional resource claim");
                Objects.requireNonNull(itemKind, "resource item kind");
                lotQuantities = Map.copyOf(Objects.requireNonNull(lotQuantities, "resource lot portions"));
                if (sourceAccountId.equals(destinationAccountId)
                        || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")
                        || lotQuantities.isEmpty() || lotQuantities.size() > 64
                        || lotQuantities.values().stream().anyMatch(value -> value == null || value < 1 || value > 64)
                        || lotQuantities.values().stream().mapToInt(Integer::intValue).sum() > 64) {
                    throw new IllegalArgumentException("actor item order needs one bounded declared resource portion");
                }
            }

            @Override public int quantity() { return lotQuantities.values().stream().mapToInt(Integer::intValue).sum(); }
        }
    }

    public ActorContainerItemOrder {
        Objects.requireNonNull(ownerId, "item order owner");
        Objects.requireNonNull(actorId, "item order actor");
        Objects.requireNonNull(direction, "item order direction");
        Objects.requireNonNull(portion, "item order portion");
        Objects.requireNonNull(containerEndpoint, "item order container endpoint");
        Objects.requireNonNull(station, "item order station");
        Objects.requireNonNull(actorSlot, "item order actor slot");
        // Identity syntax is not a type registry. The owning process validates that this
        // subject is its actual actor against canonical state before issuing the order.
        if (goalOrdinal < 0 || goalRevision < 1)
            throw new IllegalArgumentException("item order needs a versioned semantic station");
        ProductionStationSpec stationSpec = switch (containerEndpoint) {
            case ContainerEndpoint.ExactStationSlot value -> value.spec();
            case ContainerEndpoint.FungibleStation value -> value.spec();
            default -> null;
        };
        if (stationSpec != null && (!stationSpec.workerStation().equals(station)
                || direction == Direction.PLACE && stationPort(containerEndpoint) != StationPort.INPUT
                || direction == Direction.TAKE && stationPort(containerEndpoint) != StationPort.OUTPUT))
            throw new IllegalArgumentException("actor order uses the wrong declared station surface or port");
        if (portion instanceof Portion.Exact exact) {
            if (!(actorSlot instanceof ActorItemSlot.Hand))
                throw new IllegalArgumentException("exact equipment requires a declared hand");
            if (!(containerEndpoint instanceof ContainerEndpoint.ExactSlot)
                    && !(containerEndpoint instanceof ContainerEndpoint.ExactStationSlot))
                throw new IllegalArgumentException("exact item order requires its exact source or destination slot");
            InventoryCustody.ContainerSlot declaredSlot = switch (containerEndpoint) {
                case ContainerEndpoint.ExactSlot value -> value.slot();
                case ContainerEndpoint.ExactStationSlot value -> value.slot();
                default -> throw new IllegalArgumentException("exact item order has no declared slot");
            };
            InventoryCustody custody = exact.item().custody();
            if (direction == Direction.TAKE && !declaredSlot.equals(custody)
                    || direction == Direction.PLACE && !new InventoryCustody.Actor(actorId).equals(custody)) {
                throw new IllegalArgumentException("exact item order source disagrees with canonical custody");
            }
        } else if (portion instanceof Portion.Fungible fungible) {
            if (!(containerEndpoint instanceof ContainerEndpoint.FungibleContainer)
                    && !(containerEndpoint instanceof ContainerEndpoint.FungibleStation))
                throw new IllegalArgumentException("fungible item order requires a container account, not a permanent slot");
            ResourceCustody.Container container = new ResourceCustody.Container(containerEndpoint.containerId());
            ResourceCustody.Actor actor = new ResourceCustody.Actor(actorId);
            if (direction == Direction.TAKE && (!container.equals(fungible.sourceCustody())
                    || !actor.equals(fungible.destinationCustody()))
                    || direction == Direction.PLACE && (!actor.equals(fungible.sourceCustody())
                    || !container.equals(fungible.destinationCustody()))) {
                throw new IllegalArgumentException("fungible item order disagrees with its explicit source and destination custody");
            }
        }
    }

    public InventoryCustody.ContainerSlot exactSlot() {
        return switch (containerEndpoint) {
            case ContainerEndpoint.ExactSlot exact -> exact.slot();
            case ContainerEndpoint.ExactStationSlot exact -> exact.slot();
            default -> throw new IllegalArgumentException("fungible resource order has no permanent container slot");
        };
    }

    /** Validates the producer-declared machine against current canonical inventory before effect admission. */
    public void requireCurrentStation(ExactInventory inventory) {
        Objects.requireNonNull(inventory, "current station inventory");
        ProductionStationSpec declared = switch (containerEndpoint) {
            case ContainerEndpoint.ExactStationSlot value -> value.spec();
            case ContainerEndpoint.FungibleStation value -> value.spec();
            default -> throw new IllegalArgumentException("item order does not target a production station");
        };
        ContainerRecord current = inventory.containers().get(declared.containerId());
        if (current == null || !current.productionStation().equals(Optional.of(declared)))
            throw new IllegalArgumentException("item order has no current exact production-station declaration");
    }

    private static StationPort stationPort(ContainerEndpoint endpoint) {
        return switch (endpoint) {
            case ContainerEndpoint.ExactStationSlot value -> value.port();
            case ContainerEndpoint.FungibleStation value -> value.port();
            default -> throw new IllegalArgumentException("item endpoint has no production-station port");
        };
    }

    public MovementOrder movementOrder() {
        return new MovementOrder(ownerId, actorId, goalOrdinal, goalRevision, List.of(station),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
}
