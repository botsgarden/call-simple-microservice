package org.typeunsafe;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.core.json.JsonObject;

import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.BodyHandler;

import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.rxjava.servicediscovery.ServiceReference;
import io.vertx.rxjava.ext.web.client.WebClient;


import java.util.Optional;

public class Hello extends AbstractVerticle {
  
  private ServiceDiscovery discovery;

  private void setDiscovery() {
    ServiceDiscoveryOptions serviceDiscoveryOptions = new ServiceDiscoveryOptions();

    // how to access to the backend
    Integer httpBackendPort = Integer.parseInt(Optional.ofNullable(System.getenv("HTTPBACKEND_PORT")).orElse("8080"));
    String httpBackendHost = Optional.ofNullable(System.getenv("HTTPBACKEND_HOST")).orElse("127.0.0.1");
    
    // Mount the service discovery backend (my http backend)
    discovery = ServiceDiscovery.create(
      vertx,
      serviceDiscoveryOptions.setBackendConfiguration(
        new JsonObject()
          .put("host", httpBackendHost)
          .put("port", httpBackendPort)
          .put("registerUri", "/register")
          .put("removeUri", "/remove")
          .put("updateUri", "/update")
          .put("recordsUri", "/records")
      ));
  }

  public void start() {
    
    setDiscovery();

    Router router = Router.router(vertx);

    Integer httpPort = Integer.parseInt(Optional.ofNullable(System.getenv("PORT")).orElse("9095"));
    HttpServer server = vertx.createHttpServer();
    router.route().handler(BodyHandler.create());

    server
      .requestHandler(router::accept)
      .rxListen(httpPort)
      .subscribe(
        successfulHttpServer -> {
          System.out.println("🌍 Listening on " + successfulHttpServer.actualPort());

          discovery
            .rxGetRecord(r -> r.getName().equals("hey"))
            .subscribe(
              successfulRecord -> {
                ServiceReference reference = discovery.getReference(successfulRecord);
                WebClient client = reference.getAs(WebClient.class);
                
                router.get("/call/ping").handler(context -> {
                  client.get("/api/ping").send(resp -> {

                    context.response()
                      .putHeader("content-type", "application/json;charset=UTF-8")
                      .end(
                        resp.result().body()
                      );

                  });
                });

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
