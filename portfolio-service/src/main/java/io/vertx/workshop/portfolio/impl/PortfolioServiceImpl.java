package io.vertx.workshop.portfolio.impl;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.HttpEndpoint;
import io.vertx.workshop.portfolio.Portfolio;
import io.vertx.workshop.portfolio.PortfolioService;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vertx.ext.web.client.WebClient;

/**
 * The portfolio service implementation.
 */
public class PortfolioServiceImpl implements PortfolioService {

  private final Vertx vertx;
  private final Portfolio portfolio;
  private final ServiceDiscovery discovery;

  public PortfolioServiceImpl(Vertx vertx, ServiceDiscovery discovery, double initialCash) {
    this.vertx = vertx;
    this.portfolio = new Portfolio().setCash(initialCash);
    this.discovery = discovery;
  }

  @Override
  public void getPortfolio(Handler<AsyncResult<Portfolio>> resultHandler) {
    // TODO
    // ----
    resultHandler.handle(Future.succeededFuture(portfolio));
    // ----
  }

  private void sendActionOnTheEventBus(String action, int amount, JsonObject quote, int newAmount) {
    // TODO
    // ----
    JsonObject msg = new JsonObject();
    msg.put("action", action).put("quote", quote).put("date", System.currentTimeMillis());
    msg.put("amount", amount).put("owned", newAmount);
    EventBus eventBus = vertx.eventBus();
    eventBus.publish(EVENT_ADDRESS, msg);
    // ----
  }

  @Override
  public void evaluate(Handler<AsyncResult<Double>> resultHandler) {
    // TODO
    // ----
    HttpEndpoint.getClient(discovery, new JsonObject().put("name", "quotes"),
            webClientAsyncResult -> {
              if (webClientAsyncResult.failed()) {
                resultHandler.handle(Future.failedFuture(webClientAsyncResult.cause()));
              } else {
                computeEvaluation(webClientAsyncResult.result(), resultHandler);
              }
            }
            );

    // ---
  }

  private void computeEvaluation(HttpClient webClient, Handler<AsyncResult<Double>> resultHandler) {
    // We need to call the service for each company we own shares
    Map<String,Integer> shares = portfolio.getShares();
    shares.entrySet().forEach(x -> System.out.println("shares: " + x.getKey() + ", " + x.getValue()));

    List<Future> results = portfolio.getShares().entrySet().stream()
        .map(entry -> getValueForCompany(webClient, entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());

    // We need to return only when we have all results, for this we create a composite future. The set handler
    // is called when all the futures has been assigned.
    CompositeFuture.all(results).setHandler(
        ar -> {
          if (results.stream().anyMatch(Future::failed)) {
            results.stream().filter(Future::failed).forEach(x -> x.cause().printStackTrace());
          } else {
            results.forEach(x -> System.out.println(x.result()));
            double sum = results.stream().mapToDouble(fut -> (double) fut.result()).sum();
            resultHandler.handle(Future.succeededFuture(sum));
          }
        });
  }

  private Future<Double> getValueForCompany(HttpClient client, String company, int numberOfShares) {
    // Create the future object that will  get the value once the value have been retrieved
    Future<Double> future = Future.future();

    //TODO
    //----

    client.get("?name="+encode(company), resp -> {
      //Handle the exception when reading response.
      resp.exceptionHandler(future::fail);
      if (resp.statusCode() == 200) {
        System.out.println("200 OK: " + resp.statusCode());
        resp.bodyHandler(buff -> {
          JsonObject jsonObject = buff.toJsonObject();
          System.out.println(jsonObject);
          double bidVal = jsonObject.getDouble("bid");
          System.out.println("bid: " + bidVal);
          System.out.println(jsonObject);
          future.complete(numberOfShares * jsonObject.getDouble("bid"));
        }).exceptionHandler(future::fail);
      } else {
        System.out.println("Status Code: " + resp.statusCode());
        future.complete(0.0);
      }
      //Good question: should future.result() return null or correct double value?
      System.out.println("Jichao: Got bid for company: " + company + " - " + future.isComplete() +  " - " + future.result());
    }).exceptionHandler(future::fail).end(); //In call-back style, you have to register the exception handler.
    // ---

    return future;
  }


  @Override
  public void buy(int amount, JsonObject quote, Handler<AsyncResult<Portfolio>> resultHandler) {
    if (amount <= 0) {
      resultHandler.handle(Future.failedFuture("Cannot buy " + quote.getString("name") + " - the amount must be " +
          "greater than 0"));
      return;
    }

    if (quote.getInteger("shares") < amount) {
      resultHandler.handle(Future.failedFuture("Cannot buy " + amount + " - not enough " +
          "stocks on the market (" + quote.getInteger("shares") + ")"));
      return;
    }

    double price = amount * quote.getDouble("ask");
    String name = quote.getString("name");
    // 1) do we have enough money
    if (portfolio.getCash() >= price) {
      // Yes, buy it
      portfolio.setCash(portfolio.getCash() - price);
      int current = portfolio.getAmount(name);
      int newAmount = current + amount;
      portfolio.getShares().put(name, newAmount);
      sendActionOnTheEventBus("BUY", amount, quote, newAmount);
      resultHandler.handle(Future.succeededFuture(portfolio));
    } else {
      resultHandler.handle(Future.failedFuture("Cannot buy " + amount + " of " + name + " - " + "not enough money, " +
          "need " + price + ", has " + portfolio.getCash()));
    }
  }


  @Override
  public void sell(int amount, JsonObject quote, Handler<AsyncResult<Portfolio>> resultHandler) {
    if (amount <= 0) {
      resultHandler.handle(Future.failedFuture("Cannot sell " + quote.getString("name") + " - the amount must be " +
          "greater than 0"));
      return;
    }

    double price = amount * quote.getDouble("bid");
    String name = quote.getString("name");
    int current = portfolio.getAmount(name);
    // 1) do we have enough stocks
    if (current >= amount) {
      // Yes, sell it
      int newAmount = current - amount;
      if (newAmount == 0) {
        portfolio.getShares().remove(name);
      } else {
        portfolio.getShares().put(name, newAmount);
      }
      portfolio.setCash(portfolio.getCash() + price);
      sendActionOnTheEventBus("SELL", amount, quote, newAmount);
      resultHandler.handle(Future.succeededFuture(portfolio));
    } else {
      resultHandler.handle(Future.failedFuture("Cannot sell " + amount + " of " + name + " - " + "not enough stocks " +
          "in portfolio"));
    }

  }

  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Unsupported encoding");
    }
  }

  public static void main(final String[] args) {
  }

}
