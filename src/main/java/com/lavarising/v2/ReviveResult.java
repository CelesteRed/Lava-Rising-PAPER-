package com.lavarising.v2;

import org.bukkit.entity.Player;

public record ReviveResult(ReviveStatus status, Player anchor) {
}
