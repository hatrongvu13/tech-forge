package com.htv.identity.application;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Gộp các lời gọi đồng thời cùng một khóa thành một lần tính: nhiều service cùng hỏi quyền của một
 * người dùng khi cache trống thì chỉ truy vấn DB một lần. Lời gọi sau khi lần tính xong sẽ tính lại (không cache).
 */
public class SingleFlight<K, V> {

    private final ConcurrentMap<K, CompletableFuture<V>> inflight = new ConcurrentHashMap<>();

    public V run(K key, Supplier<V> work) {
        CompletableFuture<V> mine = new CompletableFuture<>();
        CompletableFuture<V> existing = inflight.putIfAbsent(key, mine);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException re) {
                    throw re;
                }
                throw e;
            }
        }
        try {
            V value = work.get();
            mine.complete(value);
            return value;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inflight.remove(key, mine);
        }
    }
}
