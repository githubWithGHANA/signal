package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.OrderDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class OrderDetailsRepository {



        private static final Logger logger = LoggerFactory.getLogger(OrderDetailsRepository.class);

        @Autowired
        @Qualifier("redisTemplateForOrderDetails")
        private final RedisTemplate<String, OrderDetails> redisTemplate;

        public OrderDetailsRepository(RedisTemplate<String, OrderDetails> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }


        /**
         * Save OrderDetails to Redis with a dynamic key.
         *
         * @param key          The key to store the order under.
         * @param orderDetails The OrderDetails object to be saved.
         */
        public void saveOrderDetailsToRedis(String key, OrderDetails orderDetails) {
            try {
                redisTemplate.opsForValue().set(key, orderDetails);
                logger.info("Order with key '{}' saved successfully.", key);
            } catch (Exception e) {
                logger.error("Error saving order with key '{}'", key, e);
            }
        }

        /**
         * Get OrderDetails from Redis using a dynamic key.
         *
         * @param key The key under which the order is stored.
         * @return The OrderDetails object.
         */
        public OrderDetails getOrderDetailsFromRedis(String key) {
            try {
                OrderDetails orderDetails = redisTemplate.opsForValue().get(key);
                if (orderDetails == null) {
                    logger.warn("No order found for key '{}'.", key);
                }
                return orderDetails;
            } catch (Exception e) {
                logger.error("Error retrieving order with key '{}'", key, e);
                return null;
            }
        }

        /**
         * Save OrderDetails with a given key.
         *
         * @param key          The key under which to save the order.
         * @param orderDetails The OrderDetails object to be saved.
         */
        public void save(String key, OrderDetails orderDetails) {
            try {
                redisTemplate.opsForValue().set(key, orderDetails);
                logger.info("Order with key '{}' saved successfully.", key);
            } catch (Exception e) {
                logger.error("Error saving order with key '{}'", key, e);
            }
        }

        /**
         * Retrieve OrderDetails from Redis by key.
         *
         * @param key The key to retrieve the order.
         * @return The OrderDetails object, or null if not found.
         */
        public OrderDetails find(String key) {
            try {
                OrderDetails orderDetails = redisTemplate.opsForValue().get(key);
                if (orderDetails != null) {
                    logger.info("Order found with key '{}'.", key);
                } else {
                    logger.warn("No order found for key '{}'.", key);
                }
                return orderDetails;
            } catch (Exception e) {
                logger.error("Error finding order with key '{}'", key, e);
                return null;
            }
        }


        /**
         * Delete an order from Redis by key.
         *
         * @param key The key of the order to be deleted.
         */
        public void clearOrder(String key) {
            try {
                redisTemplate.delete(key);
                logger.info("Order with key '{}' cleared successfully.", key);
            } catch (Exception e) {
                logger.error("Error clearing order with key '{}'", key, e);
            }
        }

        public Map<String, OrderDetails> findKeys(String pattern) {
            Map<String, OrderDetails> result = new HashMap<>();

            // Execute SCAN with pattern 'od_*'
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
                Cursor<byte[]> cursor = connection.scan(options);

                while (cursor.hasNext()) {
                    String key = new String(cursor.next());  // Convert byte[] to String
                    OrderDetails value = redisTemplate.opsForValue().get(key); // Get the value for the key
                    result.put(key, value);  // Add the key and its value to the result map
                }

                return null;
            });

            return result;
        }



}
