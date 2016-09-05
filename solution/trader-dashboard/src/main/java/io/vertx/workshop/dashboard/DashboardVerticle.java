package io.vertx.workshop.dashboard;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint;
import io.vertx.servicediscovery.types.HttpEndpoint;
import io.vertx.workshop.common.MicroServiceVerticle;

/**
 * The dashboard of the micro-trader application.
 */
public class DashboardVerticle extends MicroServiceVerticle {

  private CircuitBreaker circuit;
  private HttpClient client;

  @Override
  public void start(Future<Void> future) {
    super.start();
    Router router = Router.router(vertx);

    // Event bus bridge
    SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
    BridgeOptions options = new BridgeOptions();
    options
        .addOutboundPermitted(new PermittedOptions().setAddress("market"))
        .addOutboundPermitted(new PermittedOptions().setAddress("portfolio"))
        .addOutboundPermitted(new PermittedOptions().setAddress("service.portfolio"))
        .addInboundPermitted(new PermittedOptions().setAddress("service.portfolio"))
        .addOutboundPermitted(new PermittedOptions().setAddress("vertx.circuit-breaker"));

    sockJSHandler.bridge(options);
    router.route("/eventbus/*").handler(sockJSHandler);

    // Discovery endpoint
    ServiceDiscoveryRestEndpoint.create(router, discovery);

    // Last operations
    router.get("/operations").handler(this::callAuditServiceWithExceptionHandlerWithCircuitBreaker);

    // Static content
    router.route("/*").handler(StaticHandler.create());

    // Create a circuit breaker.
    circuit = CircuitBreaker.create("http-audit-service", vertx,
        new CircuitBreakerOptions()
            .setMaxFailures(2)
            .setFallbackOnFailure(true)
            .setResetTimeout(2000)
            .setTimeout(1000))
        .openHandler(v -> retrieveAuditService());

    vertx.createHttpServer()
        .requestHandler(router::accept)
        .listen(8080, ar -> {
          if (ar.failed()) {
            future.fail(ar.cause());
          } else {
            retrieveAuditService();
            future.complete();
          }
        });
  }

  @Override
  public void stop() throws Exception {
    if (client != null) {
      client.close();
    }
    circuit.close();
  }

  private Future<Void> retrieveAuditService() {
    Future<Void> future = Future.future();
    HttpEndpoint.getClient(discovery, new JsonObject().put("name", "audit"), client -> {
      this.client = client.result();
      if (client.succeeded()) {
        future.complete();
      } else {
        future.fail(client.cause());
      }
    });
    return future;
  }


  private void callAuditService(RoutingContext context) {
    if (client == null) {
      context.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(200)
          .end(new JsonObject().put("message", "No audit service").encode());
    } else {
      client.get("/", response -> {
        response
            .bodyHandler(buffer -> {
              context.response()
                  .putHeader("content-type", "application/json")
                  .setStatusCode(200)
                  .end(buffer);
            });
      })
          .end();
    }
  }

  private void callAuditServiceWithExceptionHandler(RoutingContext context) {
    if (client == null) {
      context.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(200)
          .end(new JsonObject().put("message", "No audit service").encode());
    } else {
      client.get("/", response -> {
        response
            .exceptionHandler(context::fail)
            .bodyHandler(buffer -> {
              context.response()
                  .putHeader("content-type", "application/json")
                  .setStatusCode(200)
                  .end(buffer);
            });
      })
          .exceptionHandler(context::fail)
          .setTimeout(5000)
          .end();
    }
  }

  private void callAuditServiceWithExceptionHandlerWithCircuitBreaker(RoutingContext context) {
    HttpServerResponse resp = context.response()
        .putHeader("content-type", "application/json")
        .setStatusCode(200);

    circuit.executeWithFallback(
        future ->
            client.get("/", response -> {
              response
                  .exceptionHandler(future::fail)
                  .bodyHandler(future::complete);
            })
                .exceptionHandler(future::fail)
                .setTimeout(5000)
                .end(),
        t -> Buffer.buffer("{\"message\":\"No audit service, or unable to call it\"}")
    )
        .setHandler(ar -> resp.end(ar.result()));
  }
}