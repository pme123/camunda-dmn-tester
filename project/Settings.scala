import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.docker.DockerPlugin
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import org.scalablytyped.converter.plugin.ScalablyTypedPluginBase.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys.{logLevel, _}
import sbt.{Level, _}
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

object Settings {

  val projectName = "camunda-dmn-tester"
  lazy val projectVersion = scala.io.Source.fromFile("version").mkString.trim

  lazy val projectSettings: Project => Project =
    _.settings(
      organization := "io.github.pme123",
      version := projectVersion,
      scalaVersion := "2.13.4"
    )

  lazy val ReleaseCmd = Command.command("release") { state =>
    "clean" ::
      "build" ::
      "server/compile" ::
      "server/stage" ::
      state
  }

  lazy val publicationSettings: Project => Project = _.settings(
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    publishTo := {
      val nexus = "https://s01.oss.sonatype.org/"
      if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    homepage := Some(url("https://github.com/pme123/camunda-dmn-tester")),
    startYear := Some(2020),
    logLevel := Level.Debug,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/pme123/camunda-dmn-tester"),
        "scm:git:github.com:/pme123/camunda-dmn-tester"
      )
    ),
    developers := List(
      Developer(
        id    = "pme123",
        name  = "Pascal Mengelt",
        email = "pascal.mengelt@gmail.com",
        url   = url("https://github.com/pme123")
      )
    )
  )

  lazy val preventPublication: Project => Project =
    _.settings(
      publish := {},
      publishTo := Some(
        Resolver
          .file("Unused transient repository", target.value / "fakepublish")
      ),
      publishArtifact := false,
      publishLocal := {},
      packagedArtifacts := Map.empty
    ) // doesn't work - https://github.com/sbt/sbt-pgp/issues/42

  object shared {
    lazy val deps: Project => Project =
      _.settings(
        libraryDependencies ++= Seq(
          "com.lihaoyi" %%% "autowire" % "0.3.2",
          "io.suzaku" %%% "boopickle" % "1.3.2",
          "com.lihaoyi" %%% "upickle" % "1.4.0",
          "com.lihaoyi" %%% "ujson" % "1.4.0"
        )
      ) //.withoutSuffixFor(JVMPlatform)
  }

  object server {
    lazy val settings: Project => Project = _.settings(
      name := s"$projectName-server",
      Compile / unmanagedResourceDirectories += baseDirectory.value / "../client/target/build",
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      resolvers += Resolver.mavenLocal, // only needed for dmn-engine SNAPSHOT
      //    crossScalaVersions := Deps.supportedScalaVersions
      Test / unmanagedSourceDirectories += baseDirectory.value / "target" / "generated-src"
    )

    lazy val serverDeps: Project => Project =
      _.settings(
        libraryDependencies ++= Seq(
          Deps.http4sDsl,
          Deps.http4sServer,
          Deps.logback
        )
      )

    lazy val deps: Project => Project = _.settings(
      libraryDependencies ++= Seq(
        Deps.ammonite,
        Deps.dmnScala,
        Deps.zio,
        Deps.zioCats,
        Deps.zioConfigHocon,
        Deps.zioConfigMagnolia,
        Deps.zioTest % Test,
        Deps.zioTestSbt % Test,
        Deps.scalaTest % Test
      )
    )

    lazy val docker: Project => Project =
      _.settings(
        dockerBaseImage := "openjdk:11", //eed3si9n/sbt:jdk11-alpine",
        dockerExposedPorts ++= Seq(8883),
        packageName in Docker := projectName,
        dockerUsername := Some("pame"),
        dockerUpdateLatest := true
      ).enablePlugins(DockerPlugin)
  }

  object client {

    lazy val slinkyBasics: Project => Project =
      _.settings(
        scalacOptions += "-Ymacro-annotations",
        requireJsDomEnv in Test := true,
        addCommandAlias(
          "dev",
          "fastOptJS::startWebpackDevServer;~fastOptJS"
        ),
        addCommandAlias("build", "fullOptJS::webpack"),
        libraryDependencies ++= Seq(
          "me.shadaj" %%% "slinky-web" % "0.6.7",
          "me.shadaj" %%% "slinky-hot" % "0.6.7"
        ),
        libraryDependencies ++= Seq(
          "org.scalatest" %%% "scalatest" % "3.1.1" % Test
        ),
        Compile / npmDependencies ++= Seq(
          "react" -> "16.13.1",
          "react-dom" -> "16.13.1",
          "react-proxy" -> "1.1.8"
        ),
        Compile / npmDevDependencies ++= Seq(
          "file-loader" -> "6.0.0",
          "style-loader" -> "1.2.1",
          "css-loader" -> "3.5.3",
          "html-webpack-plugin" -> "4.3.0",
          "copy-webpack-plugin" -> "5.1.1",
          "webpack-merge" -> "4.2.2"
        )
      )

    lazy val antdSettings: Project => Project =
      _.settings(
        stFlavour := Flavour.Slinky,
        useYarn := true,
        stIgnore := List("react-proxy"),
        Compile / npmDependencies ++= Seq(
          "antd" -> "4.5.1", //"4.7.0",
          "@types/react" -> "16.9.42",
          "@types/react-dom" -> "16.9.8"
        )
      )

    lazy val webpackSettings: Project => Project =
      _.settings(
        webpackDevServerPort := 8024,
        version in webpack := "4.43.0",
        version in startWebpackDevServer := "3.11.0",
        webpackResources := baseDirectory.value / "webpack" * "*",
        webpackConfigFile in fastOptJS := Some(
          baseDirectory.value / "webpack" / "webpack-fastopt.config.js"
        ),
        webpackConfigFile in fullOptJS := Some(
          baseDirectory.value / "webpack" / "webpack-opt.config.js"
        ),
        webpackConfigFile in Test := Some(
          baseDirectory.value / "webpack" / "webpack-core.config.js"
        ),
        webpackDevServerExtraArgs in fastOptJS := Seq(
          "--inline",
          "--hot",
          "--disableHostCheck"
        ),
        webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly()
      )

  }

}
