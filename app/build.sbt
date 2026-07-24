import scala.collection.immutable.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"
ThisBuild / scalacOptions ++= Seq("-encoding", "UTF-8")

lazy val root = (project in file("."))
  .settings(
    name := "X-Ray-Pneumonia-Detection",
    fork := true,
    Compile / run / mainClass := Some("com.xray.detection.Main"),

    // Fat jar packaging (Docker / spark-submit deliverable).
    assembly / assemblyJarName := "xray.jar",
    assembly / mainClass := Some("com.xray.detection.Main"),
    // Test sources (the learning sandbox) never reach the fat jar.
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      // Service registries must be MERGED, not discarded: Spark discovers its
      // data sources and filesystems through these files.
      case PathList("META-INF", "services", _ @ _*) => MergeStrategy.filterDistinctLines
      case PathList("META-INF", _ @ _*)             => MergeStrategy.discard
      // Library default configs (Typesafe config) must be concatenated.
      case "reference.conf"                         => MergeStrategy.concat
      case "module-info.class"                      => MergeStrategy.discard
      case _                                        => MergeStrategy.first
    }
  )

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.1",
  "org.apache.spark" %% "spark-sql" % "3.5.1",
  "com.sksamuel.scrimage" % "scrimage-core" % "4.0.32",
  "com.sksamuel.scrimage" % "scrimage-formats-extra" % "4.0.32",
  "com.github.pureconfig" %% "pureconfig" % "0.17.6",
  // Linux-only TensorFlow natives: the deployment target is a Linux container
  // (and local dev runs in WSL), so the Windows/macOS binaries that the
  // "platform" meta-package bundles (~600 MB) are dead weight.
  "org.tensorflow" % "tensorflow-core-api" % "1.1.0",
  "org.tensorflow" % "tensorflow-core-native" % "1.1.0" classifier "linux-x86_64"
)
