package com.climathon.clima_api.verticles

import com.climathon.clima_api.microservices.AiTools
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.await
import org.slf4j.Logger


suspend fun postIniciative(ctx: RoutingContext, vertx: Vertx, logger: Logger, aiTools: AiTools) {
  logger.debug("postIniciative called")
  val body = ctx.body().asJsonObject()
  logger.debug("Body: ${body.toString()}")
  // body is checked against schcema in OpenAPI
  // run AI to check if iniciative is not offensive
  val offensive = aiTools.isOffensive(body.getString("label") + " " + body.getString("content"))
  // run AI to check if iniciative is about sustainability
  val sustainability =
    if (offensive) 0f else aiTools.getSustainability(body.getString("label") + " " + body.getString("content"))
  // run AI to check if iniciative is similar to other iniciatives
  val similarity = if (offensive) 0f else aiTools.getSimilarity(
    body.getString("label") + " " + body.getString("content"),
    "other iniciatives"
  )
  val iniciative = JsonObject()
  iniciative.put("id", java.util.UUID.randomUUID().toString())
  iniciative.put("label", body.getString("label"))
  iniciative.put("offensive", offensive)
  iniciative.put("content", body.getString("content"))
  iniciative.put("sustainability", sustainability)
  //  iniciative.put("similarity", similarity)
  iniciative.put("votes", 0)
  iniciative.put("approved", false)
  iniciative.put("rejected", if (offensive) true else false)
  iniciative.put("rejectedReason", if (offensive) "Offensive content" else "")
  iniciative.put("created", System.currentTimeMillis())
  // store in redis as hash
  vertx.eventBus().request<String>(
    "db.set",
    arrayOf("iniciative" + iniciative.getString("id"), iniciative.toString())
  ).await()
  // add to iniciatives set
  vertx.eventBus().request<String>(
    "db.sadd",
    arrayOf("iniciatives", iniciative.getString("id"))
  ).await()
  ctx.response().statusCode = 200
  ctx.json(JsonObject().put("id", iniciative.getString("id")))
}

suspend fun getIniciatives(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  logger.debug("getIniciative called")
  // retrieve iniciatives ids from redis
  val iniciatives =
    vertx.eventBus().request<Array<String>>("db.smembers", "iniciatives").await().body()

  // retrieve iniciatives from redis
  val iniciativesJson = JsonArray()
  iniciatives.forEach {
    val iniciative = vertx.eventBus().request<String>("db.get", "iniciative" + it).await().body()
    iniciativesJson.add(JsonObject(iniciative))
  }
  ctx.response().statusCode = 200
  ctx.json(iniciativesJson)
}


suspend fun deleteIniciative(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  logger.debug("deleteIniciative called")
  // retrieve iniciatives ids from redis
  logger.debug(ctx.pathParam("id"))
  val iniciativeExists =
    vertx.eventBus().request<Boolean>("db.sismember", arrayOf("iniciatives", ctx.pathParam("id"))).await().body()
  logger.debug("Iniciative exists: ${iniciativeExists}")

  // remove from iniciatives set
  vertx.eventBus().request<String>("db.srem", arrayOf("iniciatives", ctx.pathParam("id"))).await()
  logger.debug("A")

  // remove from redis
  vertx.eventBus().request<String>(
    "db.del",
    "iniciative" + ctx.pathParam("id")
  ).await()
  logger.debug("B")

  ctx.response().statusCode = 200
  ctx.json(JsonObject().put("msg", "Removed"))
}

suspend fun voteForIniciative(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  logger.debug("Vote for iniciative called")
  val params = ctx.pathParams()
  val id = params.get("id")
  // retrieve iniciative id from redis
  val iniciativeString = vertx.eventBus().request<String>("db.get", "iniciative" + id).await().body()
  // if id is not found
  if (iniciativeString.isNullOrEmpty()) {
    ctx.response().statusCode = 404
    ctx.json(JsonObject().put("msg", "Iniciative not found"))
    return
  }
  val iniciative = JsonObject(iniciativeString)
  // TODO
  // add 1 vote
  iniciative.put("votes", iniciative.getInteger("votes") + 1)
  // back to redis
  vertx.eventBus().request<String>(
    "db.set",
    arrayOf("iniciative" + iniciative.getString("id"), iniciative.toString())
  ).await()
  ctx.response().statusCode = 200
  ctx.json(JsonObject().put("votes", iniciative.getInteger("votes")))
}

suspend fun approveIniciative(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  logger.debug("Approve iniciative called")
  val params = ctx.pathParams()
  val id = params.get("id")
  // retrieve iniciative id from redis
  val iniciativeString = vertx.eventBus().request<String>("db.get", "iniciative" + id).await().body()
  // if id is not found
  if (iniciativeString.isNullOrEmpty()) {
    ctx.response().statusCode = 404
    ctx.json(JsonObject().put("msg", "Iniciative not found"))
    return
  }
  val iniciative = JsonObject(iniciativeString)
  // approve
  iniciative.put("approved", true)
  // back to redis
  vertx.eventBus().request<String>(
    "db.set",
    arrayOf("iniciative" + iniciative.getString("id"), iniciative.toString())
  ).await()
  ctx.response().statusCode = 200
  ctx.json(JsonObject().put("msg", "Iniciative approved"))
}

suspend fun rejectIniciative(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
  logger.debug("Reject iniciative called")
  val params = ctx.pathParams()
  val id = params.get("id")
  // retrieve iniciative id from redis
  val iniciativeString = vertx.eventBus().request<String>("db.get", "iniciative" + id).await().body()
  // if id is not found
  if (iniciativeString.isNullOrEmpty()) {
    ctx.response().statusCode = 404
    ctx.json(JsonObject().put("msg", "Iniciative not found"))
    return
  }
  val iniciative = JsonObject(iniciativeString)
  // approve
  iniciative.put("approved", false)
  iniciative.put("rejected", true)
  iniciative.put("rejectedReason", "Rejected by city council")
  // back to redis
  vertx.eventBus().request<String>(
    "db.set",
    arrayOf("iniciative" + iniciative.getString("id"), iniciative.toString())
  ).await()
  ctx.response().statusCode = 200
  ctx.json(JsonObject().put("msg", "Iniciative rejected"))
}

//suspend fun getPropertyApi(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
//  try {
//    logger.debug("Incoming request")
//    // check if request is POST or GET
//    if (ctx.request().method().name() != "POST" && ctx.request().method().name() != "GET") {
//      throw CustomException("Only POST and GET are allowed", StdErrorMsg.METHOD_NOT_ALLOWED, 405)
//    }
//    if (ctx.pathParam("oid").isNullOrEmpty() || ctx.pathParam("pid").isNullOrEmpty()) {
//      ctx.response().statusCode = 400
//      ctx.response().end("oid and pid must be provided")
//    }
//    // check if combination of oid and pid exists
//    val item = itemsHelper.getOne(ctx.pathParam("oid"))
//    logger.debug("Item: ${item}")
//    // check if pid exists in td
//    if (item.getJsonObject("properties").getJsonObject(ctx.pathParam("pid")) == null) {
//      throw CustomException("PID not found", StdErrorMsg.OID_IID_NOT_FOUND, 404)
//    }
//    // retrieve url from redis
//    val urlObject = adaptersManager.buildUrl(
//      ctx.pathParam("oid"),
//      ctx.pathParam("pid"),
//      ctx.queryParams().map { it.key to it.value }.toMap(),
//      EnumInteractionType.valueOf(EnumInteractionType.property.toString())
//    )
////    val url = adaptersManager.getAdapterConnection(ctx.pathParam("oid"), ctx.pathParam("pid"))
//    // TODO FIX
//    logger.debug("Calling ${urlObject.url}")
//    val client: WebClient = WebClient.create(vertx)
//    // TODO: this part needs to be changed in future because it is not just proxying,
//    //  it is downloading and then resending
//    // TODO POST
//    // add headers and query params
//    val request = client.getAbs(urlObject.url)
//    ctx.request().headers().forEach { request.putHeader(it.key, it.value) }
//    ctx.queryParams().forEach { request.addQueryParam(it.key, it.value) }
//    val a = request.send()
//
//    a.onSuccess { response ->
//      logger.debug("Response received -> proxying")
//      ctx.response().statusCode = response.statusCode()
//      ctx.response().headers().setAll(response.headers())
//      ctx.response().end(response.body())
//    }
//    a.onFailure { failure ->
//      logger.error("Failure received -> proxying")
//      logger.error(failure.toString())
//      ctx.response().statusCode = 500
//      ctx.response().end(failure.message)
//    }
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//
///**
// * WOT Handlers
// */
//suspend fun registerThingApi(ctx: RoutingContext, vertx: Vertx) {
//  val td = ctx.body().asJsonObject()
//  try {
//    itemsHelper.registerOne(td)
//    successHndlr(ctx, HttpResponseStatus.CREATED.code())
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//suspend fun getThingsApi(ctx: RoutingContext, vertx: Vertx) {
//  // This handles GET ONE and GET MANY
//  // GET ONE or GET MANY is selected based on query params
//  try {
//    logger.debug("Get Things")
//    val oid = oidFromQueryParams(ctx.queryParams())
//    if (oid !== null) {
//      logger.debug("Get Things OID: ${oid} -> Get one")
//      // GET ONE
//      val item = itemsHelper.getOne(oid)
//      successHndlr(ctx, 200, item)
//    } else {
//      // GET MANY
//      logger.debug("Get Things OID: ${oid} -> Get many")
//      val allowedQueryParams = listOf("limit", "offset")
//      // convert query params to json
//      val queryParams = ctx.queryParams().fold(JsonObject()) { acc, entry ->
//        if (allowedQueryParams.contains(entry.key)) {
//          acc.put(entry.key, entry.value)
//        } else {
//          acc
//        }
//      }
//      val items = itemsHelper.getMany(queryParams)
//      logger.debug("items: ${items}")
//      successHndlr(ctx, 200, items)
//      return
//    }
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//suspend fun putThingApi(ctx: RoutingContext, vertx: Vertx) {
//  // extract query params (adapterId, oid)
//  val oid = oidFromQueryParams(ctx.queryParams())
//  if (oid == null) {
//    logger.debug("Item not found")
//    failureHndlr(ctx, Exception("Item not found"))
//    return
//  }
//  itemsHelper.updateOne(oid, ctx.body().asJsonObject())
//  successHndlr(ctx, HttpResponseStatus.OK.code())
//}
//
//suspend fun deleteThingApi(ctx: RoutingContext, vertx: Vertx) {
//  val oid = oidFromQueryParams(ctx.queryParams())
//  if (oid == null) {
//    logger.debug("Item not found")
//    failureHndlr(ctx, Exception("Item not found"))
//    return
//  }
//  itemsHelper.removeOne(oid)
//  successHndlr(ctx, HttpResponseStatus.NO_CONTENT.code())
//}
//
//
///**
// * Discovery Handlers
// */
//
//fun localSparqlRequestApi(ctx: RoutingContext, vertx: Vertx) {
//  vertx.eventBus().request<String>("wot.getSparql", ctx.request().body())
//    .onSuccess { msg -> successHndlr(ctx, 200, msg.body()) }
//    .onFailure { failure -> failureHndlr(ctx, failure) }
//}
//
//fun remoteSparqlRequestApi(ctx: RoutingContext, vertx: Vertx) {
//  ctx.response().statusCode = HttpResponseStatus.NOT_IMPLEMENTED.code()
//  ctx.response().putHeader("Content-Type", "application/json")
//  ctx.response().end(HttpResponseStatus.NOT_IMPLEMENTED.toString())
//}
//
///**
// * Consumption Handlers
// */
//
//
///**
// * Adapter management handlers
// */
//
//suspend fun getAdaptersApi(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
//  try {
//    // check if query params contains id
//    if (ctx.queryParams().contains("id")) {
//      logger.debug("getAdapter request")
//      val adp = adaptersManager.getAdapter(ctx.queryParams().get("id"))
//      successHndlr(ctx, 200, adp.toJson())
//      return
//    }
//    logger.debug("getAdapters request")
//    val adapters = adaptersManager.getAdapters()
//    // convert to JsonArray
//    val adaptersJson = JsonArray()
//    adapters.forEach { adaptersJson.add(it.toJson()) }
//    successHndlr(ctx, 200, adaptersJson)
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//suspend fun postAdapterApi(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
//  try {
//    logger.debug("postAdapter request")
//    logger.debug("body: ${ctx.body().asJsonObject().toString()}")
//    if (ctx.body() == null || ctx.body().asJsonObject().isEmpty) {
//      logger.error("Please provide valid body")
//      failureHndlr(ctx, CustomException("Please provide valid body", StdErrorMsg.WRONG_PARAMETERS, 400))
//      return
//    }
//    //TODO VALIDATE??
//    val adp = Adapter(ctx.body().asJsonObject())
//    adaptersManager.addAdapter(adp)
//    successHndlr(ctx, HttpResponseStatus.CREATED.code())
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//suspend fun deleteAdapterApi(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
//  try {
//    logger.debug("deleteAdapter request")
//    adaptersManager.removeAdapter(ctx.queryParams().get("id"))
//    successHndlr(ctx, HttpResponseStatus.OK.code())
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//suspend fun putAdapterApi(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
//  try {
//    logger.debug("putAdapter request")
//    if (ctx.body().asString().isNullOrEmpty()) {
//      logger.error("Please provide body")
//      failureHndlr(ctx, CustomException("Please provide body", StdErrorMsg.WRONG_PARAMETERS, 400))
//      return
//    }
//    //TODO VALIDATE??
//    val adp = Adapter(ctx.body().asJsonObject())
//    adaptersManager.updateAdapter(adp)
//    successHndlr(ctx, HttpResponseStatus.ACCEPTED.code())
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//suspend fun getAdapterConnectionsApi(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
//  try {
//    if (ctx.queryParams().contains("oid") && ctx.queryParams().contains("pid")) {
//      logger.debug("getAdapterConnection request")
//      val url = adaptersManager.getAdapterConnection(ctx.queryParams().get("oid"), ctx.queryParams().get("pid"))
//      successHndlr(ctx, 200, url)
//      return
//    }
//    logger.debug("getAdapterConnections request")
//
//    val adapterConnections = adaptersManager.getAdapterConnections()
//    // convert to JsonArray
//    val adapterConnectionsJson = JsonArray()
//    adapterConnections.forEach { adapterConnectionsJson.add(it.toJson()) }
//    successHndlr(ctx, 200, adapterConnectionsJson)
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//suspend fun postAdapterConnectionApi(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
//  try {
//    logger.debug("postAdapterConnection request")
//    if (ctx.body() == null || ctx.body().asJsonObject().isEmpty) {
//      logger.error("Please provide valid body")
//      failureHndlr(ctx, CustomException("Please provide valid body", StdErrorMsg.WRONG_PARAMETERS, 400))
//      return
//    }
//    //TODO VALIDATE??
//    val adpC = AdapterConnection(ctx.body().asJsonObject())
//    adaptersManager.addAdapterConnection(adpC)
//    successHndlr(ctx, HttpResponseStatus.CREATED.code())
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//suspend fun putAdapterConnectionApi(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
//  try {
//    logger.debug("putAdapterConnection request")
//    if (ctx.body().asString().isNullOrEmpty()) {
//      logger.error("Please provide body")
//      failureHndlr(ctx, CustomException("Please provide body", StdErrorMsg.WRONG_PARAMETERS, 400))
//      return
//    }
//    //TODO VALIDATE??
//    val adpC = AdapterConnection(ctx.body().asJsonObject())
//    adaptersManager.updateAdapterConnection(adpC)
//    successHndlr(ctx, HttpResponseStatus.ACCEPTED.code())
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//suspend fun deleteAdapterConnectionApi(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
//  try {
//    logger.debug("deleteAdapterConnection request")
//    val oid = oidFromQueryParams(ctx.queryParams())
//    if (oid == null) {
//      logger.error("Please provide oid")
//      failureHndlr(ctx, CustomException("Please provide oid", StdErrorMsg.WRONG_PARAMETERS, 400))
//      return
//    }
//    if (!ctx.queryParams().contains("pid")) {
//      logger.error("Please provide pid")
//      failureHndlr(ctx, CustomException("Please provide pid", StdErrorMsg.WRONG_PARAMETERS, 400))
//      return
//    }
//    adaptersManager.removeAdapterConnection(oid, ctx.queryParams().get("pid"))
//    successHndlr(ctx, HttpResponseStatus.NO_CONTENT.code())
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//// TEST
//suspend fun testUrlApi(ctx: RoutingContext, vertx: Vertx, logger: Logger) {
//  try {
//    logger.debug("testUrl request")
//    val a = adaptersManager.buildUrl(
//      ctx.pathParam("oid"),
//      ctx.pathParam("pid"),
//      ctx.queryParams().map { it.key to it.value }.toMap(),
//      EnumInteractionType.valueOf(EnumInteractionType.property.toString())
//    )
//    successHndlr(ctx, HttpResponseStatus.OK.code(), a)
//  } catch (e: Exception) {
//    logger.error(e.toString())
//    failureHndlr(ctx, e)
//    return
//  }
//}
//
//// discovery
//
//// Private functions
//
//
//private fun successHndlr(ctx: RoutingContext, status: Int) {
//  ctx.response().statusCode = status
//  ctx.response().putHeader("Content-Type", "application/json")
//  ctx.response().end()
//}
//
//private fun <T : Any> successHndlr(ctx: RoutingContext, status: Int, msg: T) {
//  ctx.response().statusCode = status
//  when (msg) {
//    is String -> {
//      ctx.response().putHeader("Content-Type", "text/plain")
//      ctx.response().end(msg)
//    }
//
//    is JsonObject -> {
//      ctx.response().putHeader("Content-Type", "application/json")
//      ctx.response().end(msg.encode())
//    }
//
//    is JsonArray -> {
//      ctx.response().putHeader("Content-Type", "application/json")
//      ctx.response().end(msg.encode())
//    }
//
//    is Int -> {
//      ctx.response().putHeader("Content-Type", "text/plain")
//      ctx.response().end(msg.toString())
//    }
//
//    else -> {
//      logger.debug("Unknown type: ${msg.javaClass}")
//      ctx.response().putHeader("Content-Type", "text/plain")
//      ctx.response().end(msg.toString())
//    }
//  }
//}
//
//
//
//private fun oidFromQueryParams(queryParams: MultiMap): String? {
//  if (queryParams.contains("oid")) {
//    return queryParams["oid"]
//  } else if (queryParams.contains("adapterId")) {
//    // TODO retrieve oid from adapterId
//
//    // propagate error
//    throw CustomException("Not implemented yet", StdErrorMsg.FUNCTION_NOT_IMPLEMENTED, 500)
//  } else {
//    return null
//  }
//}
