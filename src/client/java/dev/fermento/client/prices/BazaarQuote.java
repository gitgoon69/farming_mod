package dev.fermento.client.prices;

public record BazaarQuote(String productId, double buyPrice, double sellPrice, long fetchedAt) {
	/**
	 * Cofl: buyPrice = instant buy / sell offer, sellPrice = instant sell.
	 */
	public double selectedPrice(boolean sellOffer) {
		return sellOffer ? buyPrice : sellPrice;
	}

	public boolean valid() {
		return buyPrice > 0 || sellPrice > 0;
	}
}
