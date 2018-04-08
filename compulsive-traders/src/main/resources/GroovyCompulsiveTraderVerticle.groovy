/*
This is a Groovy verticle implemented as a _script_. To the content of this script is the `start` method of the
 verticle.
 */

import io.vertx.core.CompositeFuture as CompositeFuture
import io.vertx.servicediscovery.types.EventBusService as EventBusService
import io.vertx.core.eventbus.MessageConsumer as MessageConsumer
import io.vertx.workshop.portfolio.PortfolioService as PortfolioService
import io.vertx.workshop.trader.impl.TraderUtils as TraderUtils
import io.vertx.core.Future as Future
import io.vertx.servicediscovery.types.MessageSource as MessageSource
import io.vertx.servicediscovery.ServiceDiscovery as ServiceDiscovery


java.lang.Object company  = TraderUtils.pickACompany()
java.lang.Object numberOfShares  = TraderUtils.pickANumber()
this.println('Groovy compulsive trader configured for company ' +  company  + ' and shares: ' +  numberOfShares )
java.lang.Object discovery  = ServiceDiscovery.create(vertx)
Future<MessageConsumer> marketFuture  = Future.future()
Future<PortfolioService> portfolioFuture  = Future.future()
MessageSource.getConsumer(discovery, ['name': 'market-data'], marketFuture.completer())
EventBusService.getProxy(discovery, 'io.vertx.workshop.portfolio.PortfolioService', portfolioFuture.completer())
CompositeFuture.all(marketFuture, portfolioFuture).setHandler({ java.lang.Object ar ->
  if (ar.failed()) {
    System .err.println('One of the required service cannot be retrieved: ' + ar.cause())


  } else {
    PortfolioService portfolio  = portfolioFuture.result()
    MessageConsumer<Map> marketConsumer  = marketFuture.result()
    marketConsumer.handler({ java.lang.Object message ->
      Map quote  = message.body()
      TraderUtils.dumbTradingLogic(company, numberOfShares, portfolio, quote)

    })


  }


})

 //-------- BEGIN GroovyCompulsiveTraderVerticle --------


