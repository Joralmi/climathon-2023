package com.climathon.clima_api

import com.climathon.clima_api.verticles.ApiVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

val logger = LoggerFactory.getLogger("Main")

class MainVerticle : CoroutineVerticle() {

  override suspend fun start() {

    //config
    val config = configInit(vertx)

    try {
      // create eventbus endpoint to kill the application
      vertx.eventBus().consumer<String>("killapp") { message ->
        logger.error("Killing application: " + message.body())
        vertx.close()
        exitProcess(0)
      }
      // Verticles
      vertx.deployVerticle(ApiVerticle()).await()
      logger.info("Application started")
    } catch (exception: Exception) {
      logger.error("Could not start application: " + exception.message)
      // exception.printStackTrace()
      // kill verticles
      vertx.close()
      // exit
      exitProcess(1)
    }
  }
}

private suspend fun configInit(vertx: Vertx?): JsonObject? {
  logger.debug("Loading config")
  try {
    val defaultConfig = ConfigStoreOptions()
      .setType("file")
      .setFormat("json")
      .setConfig(JsonObject().put("path", "./config.json"))
    val opts = ConfigRetrieverOptions()
      .addStore(defaultConfig)
    val retriever = ConfigRetriever.create(vertx, opts)
    return retriever.config.await()
  } catch (e: Exception) {
    logger.error("Could not load config")
    e.printStackTrace()
  }
  return null
}
