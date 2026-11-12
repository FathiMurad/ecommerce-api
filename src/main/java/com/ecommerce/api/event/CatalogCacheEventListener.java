package com.ecommerce.api.event;

import com.ecommerce.api.constant.CacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts product cache entries after a category update commits, decoupling CategoryService from Product cache details.
 */
@Slf4j
@Component
public class CatalogCacheEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true)
    public void onCategoryUpdated(CategoryUpdatedEvent event) {
        log.debug("Evicting product cache after category id={} updated", event.categoryId());
    }
}
