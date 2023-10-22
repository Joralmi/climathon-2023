package com.climathon.clima_api.microservices

import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory

class AiTools(vertx: Vertx) {
  private val logger = LoggerFactory.getLogger(AiTools::class.java)
  val webHost = "192.168.88.200"
  val webPort = 4000
  val options = WebClientOptions().setDefaultPort(webPort)
    .setDefaultHost(webHost)
  val client = WebClient.create(vertx, options)


  public suspend fun isOffensive(text: String): Boolean {
    try {
      val result =
        client.post("/api/offensive").sendJsonObject(JsonObject().put("text", text)).await().body().toJsonObject()
      logger.debug("Offensive: " + result.getBoolean("message").toString())
      return result.getBoolean("message")
    } catch (e: Exception) {
      logger.error("Could not check if text is offensive: " + e.message)
      return false
    }
  }

  public suspend fun getSustainability(text: String): Float {
    try {
      val result =
        client.post("/api/sustainability").sendJsonObject(JsonObject().put("text", text)).await().body().toJsonObject()
      return result.getFloat("message")
    } catch (e: Exception) {
      logger.error("Could not check if text is sustainable: " + e.message)
      return 0.0f
    }
  }

  public suspend fun getSimilarities(newText: String): JsonArray {
    // TODO
    try {
      logger.debug("Sending text: " + newText)
      val result =
        client.post("/api/similarity").sendJsonObject(JsonObject().put("text", newText))
          .await().body().toJsonObject()
      logger.debug(result.toString())
      val similarities = result.getJsonArray("message")
      return similarities
    } catch (e: Exception) {
      return JsonArray()
    }
  }
}
