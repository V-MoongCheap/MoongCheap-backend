package com.moongcheap_backend.product.domain.productCatalog;

import com.moongcheap_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product_catalog",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_catalog_name", columnNames = "name"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCatalog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "spec_summary", length = 500)
    private String specSummary;

    @Column(name = "list_price")
    private Integer listPrice;

    @Column(name = "thumbnail_url", length = 255)
    private String thumbnailUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductCatalogStatus status;

    @Builder
    private ProductCatalog(String name, String specSummary, Integer listPrice,
                           String thumbnailUrl, String description) {
        this.name = name;
        this.specSummary = specSummary;
        this.listPrice = listPrice;
        this.thumbnailUrl = thumbnailUrl;
        this.description = description;
        this.status = ProductCatalogStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ProductCatalogStatus.INACTIVE;
    }

    public void activate() {
        this.status = ProductCatalogStatus.ACTIVE;
    }
}
