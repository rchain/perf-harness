import sbt._

Global / conflictManager := ConflictManager.strict

name := "rchain-perf-harness"

scalaVersion := "2.12.8"

lazy val projectSettings = Seq(
  organization := "coop.rchain",
  scalaVersion := "2.12.8",
  version := "0.1.0-SNAPSHOT",
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "jitpack" at "https://jitpack.io"
  ),
  scalafmtOnCompile := true,
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-Xfatal-warnings",
  )
)

val config              = "com.typesafe"                % "config"                    % "1.3.1"
val scalapbRuntimegGrpc = "com.thesamet.scalapb"       %% "scalapb-runtime-grpc"      % scalapb.compiler.Version.scalapbVersion
val gatling             = "io.gatling.highcharts"       % "gatling-charts-highcharts" % "2.3.1" exclude("org.asynchttpclient", "async-http-client-netty-utils") excludeAll(ExclusionRule(organization = "io.netty"), ExclusionRule(organization = "org.asynchttpclient"))
val gatlingTF           = "io.gatling"                  % "gatling-test-framework"    % "2.3.1" % "test" exclude("org.asynchttpclient", "async-http-client-netty-utils") excludeAll(ExclusionRule(organization = "io.netty"), ExclusionRule(organization = "org.asynchttpclient"))
val grpcNetty           = "io.grpc"                     % "grpc-netty"                % scalapb.compiler.Version.grpcJavaVersion

lazy val commonSettings = projectSettings

lazy val repoHashRef = sys.env.get("RCHAIN_REPO_HASH").filter(_ != "").getOrElse("dev")

lazy val crypto = sbt.ProjectRef(uri(s"git://github.com/rchain/rchain.git#$repoHashRef"), "crypto")
lazy val models = sbt.ProjectRef(uri(s"git://github.com/rchain/rchain.git#$repoHashRef"), "models")

lazy val runner = (project in file("runner"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      scalapbRuntimegGrpc,
      grpcNetty,
      gatling,
      gatlingTF,
      config,
    ),
    dependencyOverrides ++= Seq(
	"org.bouncycastle" % "bcprov-jdk15on" % "1.61",
	"org.bouncycastle" % "bcpkix-jdk15on" % "1.61",
	"com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
	"org.hdrhistogram" %  "HdrHistogram" % "2.1.11",
    "com.fasterxml.jackson.core"%"jackson-databind" %"2.9.8",
    ),
  )
  .settings(
    assemblyJarName in assembly := "runner.jar",
    mainClass in assembly := Some("coop.rchain.perf.ContinuousRunner"),
    assemblyMergeStrategy in assembly := {
      case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
      case x if x.endsWith("gatling-version.properties") => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
  ).enablePlugins(GatlingPlugin)
  .dependsOn(models, crypto)
