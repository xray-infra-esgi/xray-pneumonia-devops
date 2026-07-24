package com.xray.detection.router

import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.error.{ConfigReaderFailures, ConvertFailure, KeyNotFound}

import java.nio.file.{Files, Paths}

// Single access point to the unified config file. The path can be swapped per
// environment (XRAY_CONFIG=conf/other.conf).
object AppConfig {

  val path: String = sys.env.getOrElse("XRAY_CONFIG", "conf/application.conf")

  def exists: Boolean = Files.exists(Paths.get(path))

  def source: ConfigSource = ConfigSource.file(path)

  // Loads one section of the file into its case class. The policy:
  // - file absent OR section absent: the fallback (code defaults) applies;
  // - section present but malformed: fail loudly (typos must not pass silently).
  def section[A](name: String, fallback: A)(implicit reader: ConfigReader[A]): A = {
    if (!exists) {
      fallback
    } else {
      source.at(name).load[A] match {
        case Right(loaded) => loaded
        case Left(failures) if isOnlyMissingSection(failures) => fallback
        case Left(failures) =>
          throw new IllegalArgumentException(
            s"Invalid section '$name' in $path:\n${failures.prettyPrint()}")
      }
    }
  }

  private def isOnlyMissingSection(failures: ConfigReaderFailures): Boolean = {
    failures.toList match {
      case List(ConvertFailure(_: KeyNotFound, _, _)) => true
      case _ => false
    }
  }
}
