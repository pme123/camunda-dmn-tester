package pme123.camunda.dmn.tester.server

import ammonite.ops.pwd
import boopickle.Default._
import boopickle.UnpickleImpl
import cats.effect._
import org.http4s.EntityDecoder._
import org.http4s.EntityEncoder._
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import org.http4s.syntax.kleisli._
import org.http4s.{Request, StaticFile, _}
import org.slf4j.{Logger, LoggerFactory}
import pme123.camunda.dmn.tester.shared.DmnApi

import java.io.File
import java.nio.ByteBuffer
import scala.concurrent.ExecutionContext.global

object HttpServer extends IOApp {

  val logger: Logger = LoggerFactory.getLogger(getClass)
  val testReportsPath = pwd / "target" / "test-reports"

  override def run(args: List[String]): IO[ExitCode] =
    IO(println("Server starting at Port 8883")) *>
      app.use(_ => IO.never).as(ExitCode.Success)

  private def static(file: String, blocker: Blocker, request: Request[IO]) = {
    logger.info(s"Static File: $file")
    StaticFile
      .fromResource("/assets/" + file, blocker, Some(request))
      .orElse(StaticFile
        .fromFile(new File(testReportsPath.toIO.getAbsolutePath + file)
          , blocker, Some(request)))
      .getOrElseF(NotFound())
  }

  private def testReports(blocker: Blocker, request: Request[IO]) = {
    logger.info(s"TestReports path: $testReportsPath")
    StaticFile
      .fromFile(( testReportsPath / "index.html").toIO
        , blocker, Some(request))
      .getOrElseF(NotFound())
  }

  private def routes(blocker: Blocker) = HttpRoutes
    .of[IO] {
      case req if req.method == Method.OPTIONS =>
        IO(Response(Ok, headers = Headers.of(Header("Allow", "OPTIONS, POST"))))
      case req if req.uri.path.startsWith("/api") =>
        logger.info(s"API call!")
        println(s"API call println!")
        autowireApi(req)
      case request if request.uri.path.startsWith("/testReports") =>
        testReports(blocker, request)
      case request@GET -> Root =>
        static("index.html", blocker, request)
      case request@GET -> path =>
        static(path.toString, blocker, request)
    }
    .orNotFound

  private val app: Resource[IO, Server[IO]] =
    for {
      blocker <- Blocker[IO]
      server <- BlazeServerBuilder[IO](global)
        .bindHttp(8883, "0.0.0.0")
        .withHttpApp(CORS(routes(blocker)))
        .resource
    } yield server

  private def autowireApi(request: Request[IO]) = {
    for {
      path <- IO(request.uri.path.split("/").filter(_.nonEmpty).tail)
      _ <- IO(logger.info(s"Request path 2: ${path.toSeq}"))
      response <- request.decode[Array[Byte]] { array =>
        Ok(for {
          result <- IO.fromFuture(IO(inputToOutput(path, array)))
        } yield {
          result.array()
        })
      }
    } yield response

  }

  implicit val ec: scala.concurrent.ExecutionContext = global

  private def inputToOutput(path: Seq[String], body: Array[Byte]) = {
    // call Autowire route
    val args =
      if (body.nonEmpty)
      // Unpickle was not correct in Intellij > UnpickleImpl
        UnpickleImpl[Map[String, ByteBuffer]].fromBytes(ByteBuffer.wrap(body))
      else Map.empty[String, ByteBuffer]

    ApiRouter
      .route[DmnApi](new DmnService())(
        autowire.Core.Request(path, args)
      )
      .map(buffer => {
        val data = Array.ofDim[Byte](buffer.remaining())
        buffer.get(data)
      })
  }

}
