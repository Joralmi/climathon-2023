package com.climathon.clima_api.verticles

import com.climathon.clima_api.microservices.AiTools
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.openapi.RouterBuilder
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class ApiVerticle : CoroutineVerticle() {
  private lateinit var aiTools: AiTools
  private val logger = LoggerFactory.getLogger(ApiVerticle::class.java)

  override suspend fun start() {
    // Build Vert.x Web router
    // path to yaml file in resources
    // init ai tools
    aiTools = AiTools(vertx)
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
    routerBuilder.operation("registerInitiative").handler { ctx ->
      launch(vertx.dispatcher()) { postInitiative(ctx, vertx, logger, aiTools) }
    }
    routerBuilder.operation("getInitiative").handler { ctx ->
      launch(vertx.dispatcher()) { getInitiatives(ctx, vertx, logger) }
    }
    routerBuilder.operation("deleteInitiative").handler { ctx ->
      launch(vertx.dispatcher()) { deleteInitiative(ctx, vertx, logger) }
    }
    routerBuilder.operation("voteForInitiative").handler { ctx ->
      launch(vertx.dispatcher()) { voteForInitiative(ctx, vertx, logger) }
    }
    routerBuilder.operation("approveInitiative").handler { ctx ->
      launch(vertx.dispatcher()) { approveInitiative(ctx, vertx, logger) }
    }
    routerBuilder.operation("rejectInitiative").handler { ctx ->
      launch(vertx.dispatcher()) { rejectInitiative(ctx, vertx, logger) }
    }

    /**
     * Validation Routes
     */
    val router = routerBuilder.createRouter()

    router.route("/api/prefill").handler { ctx ->
      launch(vertx.dispatcher()) { prefillDb(ctx, vertx, logger) }
    }

    router.route("/api/*").failureHandler {
      logger.debug("Failure: " + it.failure().message)
      it.response().setStatusCode(500).end()
    }

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

    // UI
    router.get("/postui").handler(StaticHandler.create("postui"))
    router.get("/").handler(StaticHandler.create("ui"))
    router.get("/*").handler(StaticHandler.create("ui"))
    /**
     * Start the server
     */
    vertx.createHttpServer().requestHandler(router).listen(config.getInteger("port", 8081)).await()
  }

}


