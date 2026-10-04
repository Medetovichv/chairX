package kg.chairx.product.application;

import kg.chairx.product.api.ProductResponse;
import kg.chairx.product.api.ProductVariantResponse;
import kg.chairx.product.domain.Product;
import kg.chairx.product.domain.ProductVariant;

final class ProductMapper {
    private ProductMapper() { }

    static ProductResponse response(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getCategory(), product.isActive(), product.getCreatedAt(), product.getUpdatedAt());
    }

    static ProductVariantResponse response(ProductVariant variant) {
        return new ProductVariantResponse(variant.getId(), variant.getProductId(), variant.getName(),
                variant.getSku(), variant.getColor(), variant.getRecommendedSalePrice(),
                variant.isActive(), variant.getCreatedAt(), variant.getUpdatedAt());
    }
}
