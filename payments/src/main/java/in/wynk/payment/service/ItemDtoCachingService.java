package in.wynk.payment.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.ICacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.subscription.common.dto.ItemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.IN_MEMORY_CACHE_CRON;
import static in.wynk.common.constant.CacheBeanNameConstants.ITEM_DTO;
import static in.wynk.exception.WynkErrorType.UT025;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;

@Slf4j
@RequiredArgsConstructor
@Service(value = ITEM_DTO)
public class ItemDtoCachingService implements ICacheService<ItemDTO, String> {

    private final Map<String, ItemDTO> itemDTOMap = new ConcurrentHashMap<>();
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock writeLock = lock.writeLock();

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCacheItemDto")
    @Scheduled(fixedDelay = IN_MEMORY_CACHE_CRON, initialDelay = IN_MEMORY_CACHE_CRON)
    public void init() {
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        loadItems();
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void loadItems() {
        Collection<ItemDTO> itemList = subscriptionServiceManager.getItems();
        if (CollectionUtils.isNotEmpty(itemList) && writeLock.tryLock()) {
            try {
                Map<String, ItemDTO> temp = itemList.stream().collect(Collectors.toMap(ItemDTO::getId, Function.identity()));
                itemDTOMap.clear();
                itemDTOMap.putAll(temp);
            } catch (Throwable th) {
                log.error(APPLICATION_ERROR, "Exception occurred while refreshing items config cache. Exception: {}", th.getMessage(), th);
                throw th;
            } finally {
                writeLock.unlock();
            }
        }
    }

    @Override
    public ItemDTO get(String key) {
        return itemDTOMap.get(key);
    }

    @Override
    public ItemDTO save(ItemDTO item) {
        throw new WynkRuntimeException(UT025);
    }

    @Override
    public Collection<ItemDTO> getAll() {
        return itemDTOMap.values();
    }

    @Override
    public boolean containsKey(String key) {
        return itemDTOMap.containsKey(key);
    }

}