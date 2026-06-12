package com.lavarising.v2;

public record ArenaBounds(int minX, int minZ, int maxXExclusive, int maxZExclusive) {
    public boolean contains(int x, int z) {
        return x >= minX && x < maxXExclusive && z >= minZ && z < maxZExclusive;
    }
}
