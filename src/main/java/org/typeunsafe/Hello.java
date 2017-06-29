package org.typeunsafe;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.core.json.JsonObject;

import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.StaticHandler;
import io.vertx.rxjava.ext.web.handler.BodyHandler;

import io.vertx.rxjava.servicediscovery.types.HttpEndpoint;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;

import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.Record;

import java.util.Optional;
//import java.util.function.*;
//import java.util.List;

public class Hello extends AbstractVerticle {
  
  private ServiceDiscovery discovery;
  private Record record;

  private void setDiscovery() {
    ServiceDiscoveryOptions serviceDiscoveryOptions = new ServiceDiscoveryOptions();

    // Redis settings
    Integer redisPort = Integer.parseInt(Optional.ofNullable(System.getenv("REDIS_PORT")).orElse("6379"));
    String redisHost = Optional.ofNullable(System.getenv("REDIS_HOST")).orElse("127.0.0.1");
    String redisAuth = Optional.ofNullable(System.getenv("REDIS_PASSWORD")).orElse(null);
    String redisRecordsKey = Optional.ofNullable(System.getenv("REDIS_RECORDS_KEY")).orElse("vert.x.ms");    // the redis hash
    
    discovery = ServiceDiscovery.create(
      vertx,
      serviceDiscoveryOptions.setBackendConfiguration(
        new JsonObject()
          .put("host", redisHost)
          .put("port", redisPort)
          .put("auth", redisAuth)
          .put("key", redisRecordsKey)
      ));
  }

  private void setRecord() {

    // Settings to record the service
    String serviceName = Optional.ofNullable(System.getenv("SERVICE_NAME")).orElse("botsgarden");
    String serviceHost = Optional.ofNullable(System.getenv("SERVICE_HOST")).orElse("localhost"); // domain name
    // this is the visible port from outside
    // for example you run your service with 8080 on a platform (Clever Cloud, Docker, ...)
    // and the visible port is 80
    Integer servicePort = Integer.parseInt(Optional.ofNullable(System.getenv("SERVICE_PORT")).orElse("80")); // set to 80 on Clever Cloud
    String serviceRoot = Optional.ofNullable(System.getenv("SERVICE_ROOT")).orElse("/api");

    // create the microservice record
    record = HttpEndpoint.createRecord(
      serviceName,
      serviceHost,
      servicePort,
      serviceRoot
    );

    record.setMetadata(new JsonObject()
      .put("kind", "botsgarden")
      .put("message", "Hello 🌍")
      .put("uri", "/ping")
    );

  }

  public void stop(Future<Void> stopFuture) {
    System.out.println("Unregistration process is started ("+record.getRegistration()+")...");

    discovery
      .rxUnpublish(record.getRegistration())
      .subscribe(
        successfulResult -> {
          System.out.println("👋 bye bye " + record.getRegistration());
          stopFuture.complete();
        },
        failure -> {
          failure.getCause().printStackTrace();
          System.out.println("😡 Unable to unpublish the microservice: " + failure.getMessage());
        }
      );
  }

  private Router defineRoutes(Router router) {
    
    router.route().handler(BodyHandler.create());

    router.post("/api/ping").handler(context -> {
      String name = Optional.ofNullable(context.getBodyAsJson().getString("name")).orElse("John Doe");
      System.out.println("🤖 called by " + name);

      context.response()
        .putHeader("content-type", "application/json;charset=UTF-8")
        .end(
          new JsonObject().put("message", "👋 hey "+ name + " 😃").toString()
        );
    });

    router.get("/api/ping").handler(context -> {
      context.response()
        .putHeader("content-type", "application/json;charset=UTF-8")
        .end(
          new JsonObject().put("message", "🏓 pong!").toString()
        );
    });

    // serve static assets, see /resources/webroot directory
    router.route("/*").handler(StaticHandler.create());

    return router;
  }

  public void start() {
    
    setDiscovery();
    setRecord();

    System.out.println(record.getName() + "is starting... ");

    /* === Define routes and start the server ==

    */
    Router router = Router.router(vertx);
    defineRoutes(router);
    Integer httpPort = Integer.parseInt(Optional.ofNullable(System.getenv("PORT")).orElse("8080"));
    HttpServer server = vertx.createHttpServer();

    server
      .requestHandler(router::accept)
      .rxListen(httpPort)
      .subscribe(
        successfulHttpServer -> {
          System.out.println("🌍 Listening on " + successfulHttpServer.actualPort());
          /* === publication ===
              publish the microservice to the discovery backend
          */
          discovery
            .rxPublish(record)
            .subscribe(
              succesfulRecord -> {
                System.out.println("😃 Microservice is published! " + succesfulRecord.getRegistration());
              },
              failure -> {
                System.out.println("😡 Not able to publish the microservice: " + failure.getMessage());
              }
            );
          /* === search for the other services ===
              - all: (JsonObject) null
              - by name: r -> r.getName().equals("some-name")
              - by "json": new JsonObject().put("name", "some-service")
              - by filter: r -> "some-value".equals(r.getMetadata().getString("some-label")
          */
          discovery
            .rxGetRecords((JsonObject) null)
            .subscribe(
              successfulRecords -> {
                String message = successfulRecords.size() > 0 
                  ? "😋 " + successfulRecords.size() + " record(s) found."
                  : "😢 no record found" ;
                System.out.println(message);

                successfulRecords.forEach(item -> System.out.println(item.toJson()));

                // TODO: create client(s) reference(s)
              },
              failure -> {
                System.out.println("😡 Unable to discover the services: " + failure.getMessage());
              }
            );
        },
        failure -> {
          System.out.println("😡 Houston, we have a problem: " + failure.getMessage());
        }
      );
  }

}
