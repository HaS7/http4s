package org.http4s
package grizzly

import play.api.libs.iteratee.{Concurrent, Enumerator, Done}
import org.glassfish.grizzly.http.server._
import org.glassfish.grizzly.threadpool.ThreadPoolConfig

import Bodies._


/**
 * @author ross
 */
object Example extends App {
  val http4sServlet = new Http4sGrizzly({
    case req if req.pathInfo == "/ping" =>
      Done(Responder(body = "pong"))

    case req if req.pathInfo == "/stream" =>
      Done(Responder(body = Concurrent.unicast({
        channel =>
          for (i <- 1 to 10) {
            channel.push("%d\n".format(i).getBytes)
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      })))

    case req if req.pathInfo == "/echo" =>
      println("Doing Echo")
      Done(Responder(body = req.body))

    case req =>
      println(s"Request path: ${req.pathInfo}")
      Done(Responder(body =
        s"${req.pathInfo}\n" +
        s"${req.uri}"
      ))
  })

  val httpServer = new HttpServer
  val networkListener = new NetworkListener("sample-listener", "brycepc.dyndns.org", 8080)

  // Configure NetworkListener thread pool to have just one thread,
  // so it would be easier to reproduce the problem
  val threadPoolConfig = ThreadPoolConfig
    .defaultConfig()
    .setCorePoolSize(1)
    .setMaxPoolSize(1);

  networkListener.getTransport().setWorkerThreadPoolConfig(threadPoolConfig);

  httpServer.addListener(networkListener);

  httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {

    override def service(request: Request, response: Response) {
      response.setContentType("text/plain")
      response.getWriter().write("Simple task is done!")
    }
  }, "/simple")

  httpServer.getServerConfiguration().addHttpHandler(http4sServlet, "/grizzly/*")

  try {
    httpServer.start()
    println("Press any key to stop the server...")
    readLine

    httpServer.stop()
  } catch  {
    case e: Throwable => println(e)
  }
}
