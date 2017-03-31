name := "FreasyTesting"

version := "1.0"

scalaVersion := "2.12.1"

resolvers ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype snapshots"  at "http://oss.sonatype.org/content/repositories/snapshots/")

addCompilerPlugin("org.scalameta" %% "paradise" % "3.0.0-M7" cross CrossVersion.full)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")

resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= {
  val akkaVersion = "2.4.17"
  Seq(
    "com.github.thangiee" %% "freasy-monad" % "0.5.0",
    "org.typelevel" %% "cats" % "0.9.0",
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-query-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
  )
}