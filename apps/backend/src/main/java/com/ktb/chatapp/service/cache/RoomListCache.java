package com.ktb.chatapp.service.cache;

import com.ktb.chatapp.dto.PageMetadata;
import com.ktb.chatapp.dto.rooms.RoomResponse;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RoomListCache {

    private final ConcurrentMap<RoomListCacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${room.list.cache.ttl-seconds:3}")
    private long ttlSeconds;

    private final Clock clock = Clock.systemUTC();

    public CachedRoomsPage get(RoomListCacheKey key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            cache.remove(key);
            return null;
        }
        return entry.page();
    }

    public void put(RoomListCacheKey key, List<RoomResponse> rooms, PageMetadata metadata) {
        cache.put(key, new CacheEntry(new CachedRoomsPage(rooms, metadata), clock.millis()));
    }

    public void invalidateAll() {
        cache.clear();
    }

    private boolean isExpired(CacheEntry entry) {
        long ttlMillis = Math.max(ttlSeconds, 1) * 1000;
        return clock.millis() - entry.cachedAt() > ttlMillis;
    }

    private record CacheEntry(CachedRoomsPage page, long cachedAt) {
    }

    public record CachedRoomsPage(List<RoomResponse> rooms, PageMetadata metadata) {
    }

    public record RoomListCacheKey(int page, int size, String sortField, String sortOrder, String search) {
        public RoomListCacheKey normalize() {
            return new RoomListCacheKey(
                Math.max(page, 0),
                Math.max(size, 1),
                normalize(sortField),
                normalize(sortOrder),
                normalize(search)
            );
        }

        private String normalize(String value) {
            return value != null ? value.trim().toLowerCase() : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RoomListCacheKey that = (RoomListCacheKey) o;
            return page == that.page &&
                size == that.size &&
                Objects.equals(sortField, that.sortField) &&
                Objects.equals(sortOrder, that.sortOrder) &&
                Objects.equals(search, that.search);
        }

        @Override
        public int hashCode() {
            return Objects.hash(page, size, sortField, sortOrder, search);
        }
    }
}
