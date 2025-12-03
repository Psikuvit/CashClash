package me.psikuvit.cashClash.player;

import me.psikuvit.cashClash.shop.ShopItem;

public record PurchaseRecord(ShopItem item, int quantity, long price) {
}

