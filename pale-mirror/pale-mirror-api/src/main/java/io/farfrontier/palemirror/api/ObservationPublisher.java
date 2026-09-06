package io.farfrontier.palemirror.api;

/** Experimental SPI boundary. Adapters may publish facts but cannot mutate domain state directly. */
@FunctionalInterface
public interface ObservationPublisher<T> {
    void publish(T observation);
}
