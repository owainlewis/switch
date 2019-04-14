package io.forward.gateway.core.backend

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Host, `Timeout-Access`}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.Materializer
import io.forward.gateway.modules.loadbalance.LoadBalancer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

final class LoadBalancedHttpBackend(balancer: LoadBalancer, timeout: FiniteDuration = 10.seconds)
                 (implicit
                  system: ActorSystem,
                  ex: ExecutionContext,
                  materializer: Materializer) extends Backend {

  /**
    * Prior to dispatching a request, strip headers and make initial modifications
    *
    * @param request An [[HttpRequest]] to address
    * @return A [[HttpRequest]] to dispatch
    */
  def address(request: HttpRequest): HttpRequest = {
    val initialRequest = request.copy().removeHeader(`Timeout-Access`.name)
    val proxyTarget = balancer.next()
    val headers = initialRequest.headers.filterNot(_.name() == `Host`.name) :+ Host(proxyTarget.authority.host)
    request.copy().withHeaders(headers).withUri(proxyTarget)
  }

  /**
    * TODO investigate connection reset error here with entity
    *
    * Apply all request filters, dispatch the request and run response filters when appropriate
    *
    * @param request A HTTP request to proxy
    * @param system
    * @param ex
    * @param materializer
    * @return
    */
  def apply(request: HttpRequest): Future[HttpResponse] =
    Http(system).singleRequest(address(request))
}

object LoadBalancedHttpBackend {
  def apply(balancer: LoadBalancer)
           (implicit system: ActorSystem, ex: ExecutionContext, materializer: Materializer) =
    new LoadBalancedHttpBackend(balancer)
}
