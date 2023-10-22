package com.climathon.clima_api.verticles

import com.climathon.clima_api.microservices.AiTools
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.await
import org.slf4j.Logger


suspend fun postInitiative(ctx: RoutingContext, vertx: Vertx, logger: Logger, aiTools: AiTools) {
  try {

    logger.debug("postInitiative called")
    val body = ctx.body().asJsonObject()
    logger.debug("Body: ${body.toString()}")
    // body is checked against schcema in OpenAPI
    // run AI to check if initiative is not offensive
    val offensive = aiTools.isOffensive(body.getString("label") + " " + body.getString("content"))
    // run AI to check if initiative is about sustainability
    val sustainability =
      if (offensive) 0f else aiTools.getSustainability(body.getString("label") + " " + body.getString("content"))
    // run AI to check if initiative is similar to other initiatives
    val similarities = if (offensive) JsonArray() else aiTools.getSimilarities(body.getString("content"))
    val initiative = JsonObject()
    initiative.put("id", java.util.UUID.randomUUID().toString())
    initiative.put("label", body.getString("label"))
    initiative.put("label", body.getString("label"))
    initiative.put("offensive", offensive)
    initiative.put("content", body.getString("content"))
    initiative.put("department", body.getString("department"))
    initiative.put("sustainability", sustainability)
    initiative.put("yours", if (body.containsKey("yours")) body.getBoolean("yours") else false)
    initiative.put("similarities", similarities)
    initiative.put("votes", if(body.containsKey("votes")) body.getInteger("votes") else 0)
    initiative.put("approved", if(body.containsKey("approved")) body.getBoolean("approved") else false)
    initiative.put("rejected", if (offensive) true else false)
    initiative.put("rejectedReason", if (offensive) "Offensive content" else "")
    initiative.put("created", System.currentTimeMillis())
    // store in redis as hash
    vertx.eventBus().request<String>(
      "db.set",
      arrayOf("initiative:" + initiative.getString("id"), initiative.toString())
    ).await()
    // add to initiatives set
    vertx.eventBus().request<String>(
      "db.sadd",
      arrayOf("initiatives", initiative.getString("id"))
    ).await()
    ctx.response().statusCode = 200
    ctx.json(JsonObject().put("id", initiative.getString("id")))
  } catch (e: Exception) {
    logger.error(e.toString())
    ctx.response().statusCode = 500
    ctx.json(JsonObject().put("msg", e.toString()))
    return
  }

}

suspend fun getInitiatives(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  logger.debug("getInitiative called")
  // retrieve initiatives ids from redis
  val filtered = if (ctx.queryParams().contains("filtered")) ctx.queryParams().get("filtered").toBoolean() else false
  val initiatives =
    vertx.eventBus().request<Array<String>>("db.smembers", "initiatives").await().body()

  // retrieve initiatives from redis
  val initiativesJson = JsonArray()
  initiatives.forEach {
    val initiative = vertx.eventBus().request<String>("db.get", "initiative:" + it).await().body()
    // skip rejected initiatives if filtering is enabled
    if (filtered) {
      // skip rejected initiatives
      if (JsonObject(initiative).getBoolean("rejected")) {
        return@forEach
      }
      // add yours not approved initiatives
      if (JsonObject(initiative).getBoolean("yours") && !JsonObject(initiative).getBoolean("approved")) {
        initiativesJson.add(JsonObject(initiative))
        return@forEach
      }
      // skip not approved initiatives if filtering is enabled
      if (!JsonObject(initiative).getBoolean("approved")) {
        return@forEach
      }
      // add everything else
      initiativesJson.add(JsonObject(initiative))
    } else {
      // add everything
      initiativesJson.add(JsonObject(initiative))
    }
  }
  // order by votes
  val sorted = JsonArray()
  initiativesJson.stream().sorted { o1, o2 ->
    val o1Json = o1 as JsonObject
    val o2Json = o2 as JsonObject
    o2Json.getInteger("votes").compareTo(o1Json.getInteger("votes"))
  }.forEach { sorted.add(it) }
  ctx.response().statusCode = 200
  ctx.json(sorted)
}


suspend fun deleteInitiative(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  logger.debug("deleteInitiative called")
  // retrieve initiatives ids from redis
  logger.debug(ctx.pathParam("id"))
  val initiativeExists =
    vertx.eventBus().request<Boolean>("db.sismember", arrayOf("initiatives", ctx.pathParam("id"))).await().body()
  logger.debug("Initiative exists: ${initiativeExists}")

  // remove from initiatives set
  vertx.eventBus().request<String>("db.srem", arrayOf("initiatives", ctx.pathParam("id"))).await()
  logger.debug("A")

  // remove from redis
  vertx.eventBus().request<String>(
    "db.del",
    "initiative:" + ctx.pathParam("id")
  ).await()
  logger.debug("B")

  ctx.response().statusCode = 200
  ctx.json(JsonObject().put("msg", "Removed"))
}

suspend fun voteForInitiative(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  logger.debug("Vote for initiative called")
  val params = ctx.pathParams()
  val id = params.get("id")
  // retrieve initiative id from redis
  val initiativeString = vertx.eventBus().request<String>("db.get", "initiative:" + id).await().body()
  // if id is not found
  if (initiativeString.isNullOrEmpty()) {
    ctx.response().statusCode = 404
    ctx.json(JsonObject().put("msg", "Initiative not found"))
    return
  }
  val initiative = JsonObject(initiativeString)
  // add 1 vote
  initiative.put("votes", initiative.getInteger("votes") + 1)
  initiative.put("liked", true)
  // back to redis
  vertx.eventBus().request<String>(
    "db.set",
    arrayOf("initiative:" + initiative.getString("id"), initiative.toString())
  ).await()
  ctx.response().statusCode = 200
  ctx.json(JsonObject().put("votes", initiative.getInteger("votes")))
}

suspend fun approveInitiative(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  logger.debug("Approve initiative called")
  val params = ctx.pathParams()
  val id = params.get("id")
  // retrieve initiative id from redis
  val initiativeString = vertx.eventBus().request<String>("db.get", "initiative:" + id).await().body()
  // if id is not found
  if (initiativeString.isNullOrEmpty()) {
    ctx.response().statusCode = 404
    ctx.json(JsonObject().put("msg", "Initiative not found"))
    return
  }
  val initiative = JsonObject(initiativeString)
  // approve
  initiative.put("approved", true)
  // back to redis
  vertx.eventBus().request<String>(
    "db.set",
    arrayOf("initiative:" + initiative.getString("id"), initiative.toString())
  ).await()
  ctx.response().statusCode = 200
  ctx.json(JsonObject().put("msg", "Initiative approved"))
}

suspend fun rejectInitiative(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  logger.debug("Reject initiative called")
  val params = ctx.pathParams()
  val id = params.get("id")
  // retrieve initiative id from redis
  val initiativeString = vertx.eventBus().request<String>("db.get", "initiative:" + id).await().body()
  // if id is not found
  if (initiativeString.isNullOrEmpty()) {
    ctx.response().statusCode = 404
    ctx.json(JsonObject().put("msg", "Initiative not found"))
    return
  }
  val initiative = JsonObject(initiativeString)
  // approve
  initiative.put("approved", false)
  initiative.put("rejected", true)
  initiative.put("rejectedReason", "Rejected by city council")
  // back to redis
  vertx.eventBus().request<String>(
    "db.set",
    arrayOf("initiative:" + initiative.getString("id"), initiative.toString())
  ).await()
  ctx.response().statusCode = 200
  ctx.json(JsonObject().put("msg", "Initiative rejected"))
}

public suspend fun prefillDb(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  // load prefill json from resources
  val prefillString = vertx.fileSystem().readFileBlocking("prefill.json")
  logger.debug("Cleaning db")
  cleanDb(vertx)
  logger.debug("Db cleaned")
  logger.debug("Prefill db")
  // parse json
  val prefill = JsonArray(prefillString.toString())
  // call local api to add initiatives
  val options = WebClientOptions().setDefaultPort(8081)
    .setDefaultHost("localhost")
  val client = WebClient.create(vertx, options)
  prefill.forEach {
    val request = client.post("/api/user/initiatives").sendJsonObject(it as JsonObject).await()
    logger.debug("Initiative added: ${request.statusCode()}")
  }
  ctx.response().statusCode = 200
  ctx.json(JsonObject().put("msg", "Prefill done"))
}

suspend fun cleanDb(vertx: Vertx) {
  // remove everything from redis
  // get all initiatives ids
  val initiatives =
    vertx.eventBus().request<Array<String>>("db.smembers", "initiatives").await().body()
  vertx.eventBus().request<String>("db.del", "initiatives").await()
  // remove initiatives
  initiatives.forEach {
    vertx.eventBus().request<String>("db.del", "initiative:" + it).await()
  }
}
