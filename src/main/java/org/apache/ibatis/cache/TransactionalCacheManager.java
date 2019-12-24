/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;

/**
 * @author Clinton Begin
 */

/**
 * TransactionalCacheManager 对象，支持事务的缓存管理器。
 * 因为二级缓存是支持跨 Session 进行共享，此处需要考虑事务，
 * 那么，必然需要做到事务提交时，才将当前事务中查询时产生的缓存，同步到二级缓存中。
 * 这个功能，就通过 TransactionalCacheManager 来实现。
  */
public class TransactionalCacheManager {

  // Cache 和 TransactionalCache 的映射
  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  // 清空缓存回滚所有 TransactionalCache
  public void clear(Cache cache) {
    getTransactionalCache(cache).clear();
  }

  // 获得缓存中，指定 Cache + K 的值
  public Object getObject(Cache cache, CacheKey key) {
    return getTransactionalCache(cache).getObject(key);
  }

  // 添加 Cache + KV ，到缓存中
  public void putObject(Cache cache, CacheKey key, Object value) {
    getTransactionalCache(cache).putObject(key, value);
  }

  // 提交所有 TransactionalCache
  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }

  // 回滚所有 TransactionalCache
  public void rollback() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  private TransactionalCache getTransactionalCache(Cache cache) {
    return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
  }

}
