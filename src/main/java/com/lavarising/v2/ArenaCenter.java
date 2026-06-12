package com.lavarising.v2;

public record ArenaCenter(int x, int z) {
    public double distanceSquared(ArenaCenter other) {
        long dx = (long) x - other.x;
        long dz = (long) z - other.z;
        return (double) dx * dx + (double) dz * dz;
    }
}
