package com.proveyu.booking.infrastructure.locking;

import com.proveyu.shared.error.SlotContendedException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class SlotLockService {

    private static final Logger log = LoggerFactory.getLogger(SlotLockService.class);

    private final RedissonClient redissonClient;

    public SlotLockService(@Autowired(required = false) RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public <T> T withSlotLock(UUID slotId, Supplier<T> action) {
        if (redissonClient == null) {
            log.debug("Redisson client unavailable, falling back to database pessimistic locking");
            return action.get();
        }

        RLock lock = redissonClient.getLock("slot_lock:" + slotId.toString());
        boolean acquired = false;
        try {
            acquired = lock.tryLock(500, 3000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Redis lock error, continuing with DB lock", e);
            return action.get();
        }

        if (!acquired) {
            throw new SlotContendedException();
        }

        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
