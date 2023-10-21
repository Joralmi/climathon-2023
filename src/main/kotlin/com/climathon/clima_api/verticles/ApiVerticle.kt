package com.climathon.clima_api.verticles

import com.climathon.clima_api.microservices.AiTools
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.openapi.RouterBuilder
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory

class ApiVerticle : CoroutineVerticle() {
  private val AiTools = AiTools()
  private val logger = LoggerFactory.getLogger(ApiVerticle::class.java)

  override suspend fun start() {
    // Build Vert.x Web router
    // path to yaml file in resources
    val url = javaClass.getResource("/api.yaml")
    if (url === null) {
      throw Exception("api.yaml not found")
    }

    val routerBuilder = RouterBuilder.create(vertx, url.path).onSuccess {
      logger.debug("Api router created")
    }.onFailure {
      logger.debug("Api router not created")
      logger.error(it.message)
    }.await()

    routerBuilder.options.setMountNotImplementedHandler(true)

    /**
     * Registration Routes
     */
//    routerBuilder.operation("registerThing").handler { ctx ->
//      launch(vertx.dispatcher()) { registerThingApi(ctx, vertx) }
//    }
//    routerBuilder.operation("getThing").handler { ctx ->
//      launch(vertx.dispatcher()) { getThingsApi(ctx, vertx) }
//    }
//    routerBuilder.operation("updateThing").handler { ctx ->
//      launch(vertx.dispatcher()) { putThingApi(ctx, vertx) }
//    }
//    routerBuilder.operation("deleteThing").handler { ctx ->
//      launch(vertx.dispatcher()) { deleteThingApi(ctx, vertx) }
//    }

    /**
     * Validation Routes
     */
    val router = routerBuilder.createRouter()

    /**
     * Static files
     */
    router.get("/doc/openapi.yaml").handler {
      it.response().putHeader("Content-Type", "application/yaml")
      // send file
      // set name of file
      it.response().putHeader("Content-Disposition", "inline; filename=\"api.yaml\"")
      it.response().sendFile("api.yaml")
      it.response().end()
    }
    router.get("/doc").handler(StaticHandler.create("doc"))
    router.get("/doc/swagger/*").handler(StaticHandler.create(("swagger-ui")))
    router.get("/doc/redoc/*").handler(StaticHandler.create("redoc"))

    /**
     * Start the server
     */
    vertx.createHttpServer().requestHandler(router).listen(config.getInteger("port", 8080)).await()
  }

}


