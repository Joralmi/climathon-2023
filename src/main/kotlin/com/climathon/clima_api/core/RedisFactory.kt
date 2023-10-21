package eu.bavenir.databroker.core

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.ReplyException
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.redis.client.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.pow
import kotlin.reflect.typeOf

class RedisFactory(vertx: Vertx, db: String) {
  private val vertx = vertx
  private val MAX_RECONNECT_RETRIES = 16
  private val CONNECTING = AtomicBoolean()
  private val options = RedisOptions()
  private var redis: Redis? = null
  private var client: RedisConnection? = null
  private lateinit var api: RedisAPI

  private val logger = LoggerFactory.getLogger(this::class.java)

  init {
    createRedisClient(db)
      .onSuccess { _: RedisConnection ->
        api = RedisAPI.api(client)
        logger.info("Connection to Redis db $db successful")
      }.onFailure {
        vertx.eventBus().publish("killapp", "Redis error: " + it.message)
      }
  }

  // PUBLIC API Methods

  /**
   * Implementation of the necessary REDIS commands from:
   * https://redis.io/commands
   */

  suspend fun ping(message: Message<String>) {
    val res = api.hello(listOf())
      .onFailure() { err -> handleRedisFailureStr(message, err) }
      .await()
    if (res != null) {
      message.reply(res.toString())
    } else {
      message.fail(503, "Redis Service Unavailable")
    }
  }

  suspend fun save(message: Message<String>) {
    val res = api.bgsave(listOf())
      .onFailure() { err -> handleRedisFailureStr(message, err) }
      .await()
    if (res != null) {
      logger.info(res.toString())
      message.reply(res.toString())
    }
  }

  suspend fun get(message: Message<String>) {
    val key = message.body()
    val res = api.jsonget(key)
      .onFailure { err -> handleRedisFailureStr(message, err) }
      .await()
    if (res != null) {
      message.reply(res.toString())
    } else {
      message.reply(null)
//        message.fail(404,"Nothing found")
    }
  }

  suspend fun set(message: Message<Array<String>>) {
    val array = message.body()
    if (array.size != 2) {
      message.fail(400, "You need to pass an array with 2 elements (key/value)")
    }
    // First element in list is the key, second the value
    val res = api.set(array.toList())
      .onFailure() { err -> handleRedisFailureArrStr(message, err) }
      .await()
    if (res != null) {
      message.reply(res.toString())
    } else {
      message.fail(400, "Input error storing in Redis")
    }
  }

  suspend fun del(message: Message<String>) {
    val key = message.body()
    val res = api.del(listOf(key))
      .onFailure() { err -> handleRedisFailureStr(message, err) }
      .await()
    if (res != null) {
      message.reply(res.toString())
    } else {
      message.fail(404, "Nothing found")
    }
  }

  suspend fun exists(message: Message<String>) {
    val key = message.body()
    val res = api.exists(listOf(key))
      .onFailure() { err -> handleRedisFailureStr(message, err) }
      .await()
    message.reply(res.toBoolean())
  }

  suspend fun sMembers(message: Message<String>) {
    val key = message.body()
    val res = api.smembers(key)
      .onFailure() { err -> handleRedisFailureStr(message, err) }
      .await()
    if (res != null) {
      // res to Array of strings
      val a = res.map { it.toString() }.toTypedArray()
      message.reply(a)
    } else {
      message.reply(null)
//        message.fail(404,"Nothing found")
    }
  }

  suspend fun sAdd(message: Message<Array<String>>) {
    val array = message.body()
    // First element in list is the set key
    // Rest of the elements are members of the set
    val res = api.sadd(array.toList())
      .onFailure() { err -> handleRedisFailureArrStr(message, err) }
      .await()
    if (res != null) {
      message.reply(res.toString())
    } else {
      message.fail(400, "Input error storing in Redis")
    }
  }

  suspend fun sRem(message: Message<Array<String>>) {
    val array = message.body()
    // First element in list is the set key
    // Rest of the elements are members of the set
    val res = api.srem(array.toList())
      .onFailure() { err -> handleRedisFailureArrStr(message, err) }
      .await()
    if (res != null) {
      message.reply(res.toString())
    } else {
      message.fail(400, "Input error removing from Redis")
    }
  }

  suspend fun sExists(message: Message<Array<String>>) {
    val array = message.body()
    // First element in list is the set key
    val res = api.sismember(array[0], array[1])
      .onFailure() { err -> handleRedisFailureArrStr(message, err) }
      .await()
    message.reply(res.toBoolean())
  }

  suspend fun hSet(message: Message<Array<String>>) {
    val array = message.body()
    // First element in list is the hash key
    // Then add pairs key:value
    val res = api.hset(array.toList())
      .onFailure() { err -> handleRedisFailureArrStr(message, err) }
      .await()
    if (res != null) {
      message.reply(res.toString())
    } else {
      message.fail(400, "Input error storing in Redis")
    }
  }

  suspend fun hGet(message: Message<Array<String>>) {
    val array = message.body()
    // First element in list is the hash key
    // Second element is the desired key to retrieve
    val res = api.hget(array[0], array[1])
      .onFailure() { err -> handleRedisFailureArrStr(message, err) }
      .await()
    if (res != null) {
      message.reply(res.toString())
    } else {
      message.fail(404, "Not found in Redis")
    }
  }

  suspend fun hGetAll(message: Message<String>) {
    val key = message.body()
    // Retrieve all elements from the hash
    val res = api.hgetall(key)
      .onFailure() { err -> handleRedisFailureStr(message, err) }
      .await()
    if (res != null) {
      message.reply(res.toString())
    } else {
      message.fail(404, "Not found in Redis")
    }
  }

  suspend fun hDel(message: Message<Array<String>>) {
    val array = message.body()
    // Deletes one or more fields from a hash
    // First element is the hash key
    // From the second to N element are those to be deleted
    val res = api.hdel(array.toList())
      .onFailure() { err -> handleRedisFailureArrStr(message, err) }
      .await()
    if (res != null) {
      message.reply(res.toString())
    } else {
      message.fail(404, "Error deleting in Redis")
    }
  }

  // PRIVATE Methods
  private fun handleRedisFailureStr(message: Message<String>, err: Throwable) {
    logger.error(err.toString())
    message.fail(503, "Redis Service unavailable")
  }

  private fun handleRedisFailureArrStr(message: Message<Array<String>>, err: Throwable) {
    logger.error(err.toString())
    message.fail(503, "Redis Service unavailable")
  }

  /**
   * Will create a redis client and setup a reconnect handler when there is
   * an exception in the connection.
   */
  private fun createRedisClient(connString: String): Future<RedisConnection> {
    val promise: Promise<RedisConnection> = Promise.promise()

    // make sure to invalidate old connection if present
    if (redis != null) {
      redis!!.close()
    }
    if (CONNECTING.compareAndSet(false, true)) {
      options.setConnectionString(connString)
      redis = Redis.createClient(vertx, options)
      redis!!
        .connect()
        .onSuccess { conn: RedisConnection ->
          client = conn

          // make sure the client is reconnected on error
          // eg, the underlying TCP connection is closed but the client side doesn't know it yet
          //     the client tries to use the staled connection to talk to server. An exceptions will be raised
          conn.exceptionHandler { _: Throwable? -> attemptReconnect(0, connString) }

          // make sure the client is reconnected on connection close
          // eg, the underlying TCP connection is closed with normal 4-Way-Handshake
          //     this handler will be notified instantly
          // Causes issues for gracefully closing VERTX app
//          conn.endHandler { _: Void? -> attemptReconnect(0) }

          // allow further processing
          promise.complete(conn)
          CONNECTING.set(false)
        }.onFailure { t: Throwable? ->
          promise.fail(t)
          CONNECTING.set(false)
        }
    } else {
      promise.complete()
    }
    return promise.future()
  }

  /**
   * Attempt to reconnect up to MAX_RECONNECT_RETRIES
   */
  private fun attemptReconnect(retry: Int, connString: String) {
    if (retry > MAX_RECONNECT_RETRIES) {
      // we should stop now, as there's nothing we can do.
      CONNECTING.set(false)
    } else {
      // retry with backoff up to 10240 ms
      val backoff = (2.0.pow(min(retry, 10).toDouble()) * 10).toLong()
      vertx.setTimer(backoff) { _: Long? ->
        createRedisClient(connString)
          .onFailure { _: Throwable -> attemptReconnect(retry + 1, connString) }
      }
    }
  }

}
