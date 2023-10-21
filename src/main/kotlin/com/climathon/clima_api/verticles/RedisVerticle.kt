package com.climathon.clima_api.verticles

import eu.bavenir.databroker.core.RedisFactory
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch

internal class RedisVerticle : CoroutineVerticle() {
  private lateinit var cache: RedisFactory
  private lateinit var db: RedisFactory

  override suspend fun start() {
    super.start()
    // Init
    db = RedisFactory(vertx, getConnectionString('0'))
    cache = RedisFactory(vertx, getConnectionString('1'))


    // Expose calls to event bus
    vertx.eventBus().consumer<String>("db.save") { message ->
      launch(vertx.dispatcher()) { cache.save(message) }
    }
    vertx.eventBus().consumer<String>("cache.ping") { message ->
      launch(vertx.dispatcher()) { cache.ping(message) }
    }
    vertx.eventBus().consumer<String>("db.ping") { message ->
      launch(vertx.dispatcher()) { db.ping(message) }
    }
    vertx.eventBus().consumer<String>("cache.get") { message ->
      launch(vertx.dispatcher()) { cache.get(message) }
    }
    vertx.eventBus().consumer<Array<String>>("cache.set") { message ->
      launch(vertx.dispatcher()) { cache.set(message) }
    }
    vertx.eventBus().consumer<String>("cache.exists") { message ->
      launch(vertx.dispatcher()) { cache.exists(message) }
    }
    vertx.eventBus().consumer<String>("db.exists") { message ->
      launch(vertx.dispatcher()) { db.exists(message) }
    }
    vertx.eventBus().consumer<Array<String>>("db.set") { message ->
      launch(vertx.dispatcher()) { db.set(message) }
    }
    vertx.eventBus().consumer<String>("db.get") { message ->
      launch(vertx.dispatcher()) { db.get(message) }
    }
    vertx.eventBus().consumer<String>("db.del") { message ->
      launch(vertx.dispatcher()) { db.del(message) }
    }
    vertx.eventBus().consumer<String>("db.smembers") { message ->
      launch(vertx.dispatcher()) { db.sMembers(message) }
    }
    vertx.eventBus().consumer<Array<String>>("db.sadd") { message ->
      launch(vertx.dispatcher()) { db.sAdd(message) }
    }
    vertx.eventBus().consumer<Array<String>>("db.srem") { message ->
      launch(vertx.dispatcher()) { db.sRem(message) }
    }
    vertx.eventBus().consumer<Array<String>>("db.sismember") { message ->
      launch(vertx.dispatcher()) { db.sExists(message) }
    }
    vertx.eventBus().consumer<Array<String>>("db.hset") { message ->
      launch(vertx.dispatcher()) { db.hSet(message) }
    }
    vertx.eventBus().consumer<Array<String>>("db.hget") { message ->
      launch(vertx.dispatcher()) { db.hGet(message) }
    }
    vertx.eventBus().consumer<String>("db.hgetall") { message ->
      launch(vertx.dispatcher()) { db.hGetAll(message) }
    }
    vertx.eventBus().consumer<Array<String>>("db.hdel") { message ->
      launch(vertx.dispatcher()) { db.hDel(message) }
    }
  }

  /**
   * Generates connection string for Redis
   * redis://[:password@]host[:port][/db-number]
   */
  private fun getConnectionString(db: Char): String {
    // Config
    val host = config.getString("server", "localhost")
    val port = config.getInteger("port", 6379)
    val pwd = config.getString("password")
//    logger.info(pwd)
    if (pwd !is String) {
      return "redis://$host:${port}/$db"
    } else {
      return "redis://:$pwd@$host:$port/$db"
    }
  }
}
