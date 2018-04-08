package io.vertx.workshop.trader.impl;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.types.MessageSource;
import io.vertx.workshop.common.MicroServiceVerticle;
import io.vertx.workshop.portfolio.PortfolioService;

/**
 * A compulsive trader...
 */
public class JavaCompulsiveTraderVerticle extends MicroServiceVerticle {

    @Override
    public void start(Future<Void> future) {
        super.start();

        //TODO
        //--future.fail("no implemented yet...");
        Future<MessageConsumer<JsonObject>> consumerFuture = Future.future();
        Future<PortfolioService> portfolioServicFuture = Future.future();
        //Firstly, I want to use this one to get market, however, we have to use micro-service feature of vertx
        //vertx.eventBus().consumer("market", consumerFuture);
        MessageSource.getConsumer(discovery, new JsonObject().put("name", "market-data"), consumerFuture);
        EventBusService.getProxy(discovery, PortfolioService.class, portfolioServicFuture);
        //Both of MessageSource and EventBusService are located at io.vertx.servicediscovery.types

        //Learned: if you want get some value from some where. Use future.
        CompositeFuture.all(portfolioServicFuture, consumerFuture).setHandler(ar -> {
                    if (ar.failed()) {
                        future.fail(ar.cause());
                    } else {
                        consumerFuture.result().handler(msg -> {
                            //There is just message not AsyncResult. Not need to check error?
                            JsonObject quote = msg.body();
                            TraderUtils.dumbTradingLogic(
                                    TraderUtils.pickACompany(),
                                    TraderUtils.pickANumber(),
                                    portfolioServicFuture.result(),
                                    quote
                            );

                        });

                    }
                }
        );

        //Firstly I want to fetch the porfolioService synchronous
        /*
        PortfolioService portfolioService = null;
        vertx.eventBus().consumer("market")
                .handler(msg -> {
                    JsonObject marketData = (JsonObject) msg.body();
                    TraderUtils.dumbTradingLogic(
                            TraderUtils.pickACompany(),
                            TraderUtils.pickANumber(),
                            portfolioService,
                            marketData


                    );

                });
                */

        // ----
    }


}
