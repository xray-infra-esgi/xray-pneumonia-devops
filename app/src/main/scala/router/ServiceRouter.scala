package com.xray.detection.router

import scala.collection.mutable.ArrayBuffer


object ServiceRouter {

  type ServiceRunner = Array[String] => Int

  private final case class RouteDecision(
    success: Boolean,
    serviceName: String,
    serviceArgs: Array[String],
    errorMessage: String
  )

  def route(
    args: Array[String],
    services: Map[String, ServiceRunner],
    defaultService: String
  ): Int = {
    val decision = parseRouting(args, defaultService)

    if (!decision.success) {
      println(s"Invalid arguments: ${decision.errorMessage}")
      println()
      println(helpText(services, defaultService))
      return 1
    }

    services.get(decision.serviceName) match {
      case Some(runner) =>
        runner(decision.serviceArgs)
      case None =>
        println(s"Unknown service: ${decision.serviceName}")
        println()
        println(helpText(services, defaultService))
        1
    }
  }


  private def helpText(services: Map[String, ServiceRunner], defaultService: String): String = {
    val names = services.keys.toSeq.sorted.mkString("|")
    s"""
      |Service router usage:
      |  sbt --batch "runMain com.xray.detection.Main [--service <$names>] [service options]"
      |
      |Notes:
      |  - Default service is $defaultService.
      |  - Service-specific options are forwarded to the selected service.
      |  - Each service documents its own options via --help.
      |
      |Examples:
      |  sbt --batch "runMain com.xray.detection.Main"
      |  sbt --batch "runMain com.xray.detection.Main --service $defaultService"
      |  sbt --batch "runMain com.xray.detection.Main --service produce --help"
      |""".stripMargin.trim
  }

  private def parseRouting(args: Array[String], defaultService: String): RouteDecision = {
    def failure(message: String): RouteDecision = {
      RouteDecision(
        success = false,
        serviceName = defaultService,
        serviceArgs = Array.empty[String],
        errorMessage = message
      )
    }

    val serviceOccurrences = args.count(arg => arg == "--service")
    if (serviceOccurrences > 1) {
      return failure("--service can only be provided once")
    }

    val forwardedArgs = ArrayBuffer[String]()
    var serviceName = defaultService

    var remaining = args.toList
    while (remaining.nonEmpty) {
      remaining match {
        case "--service" :: value :: tail =>
          val trimmed = value.trim
          if (trimmed.isEmpty) {
            return failure("Empty value for --service")
          }
          serviceName = trimmed.toLowerCase
          remaining = tail

        case "--service" :: Nil =>
          return failure("Missing value for --service")

        case head :: tail =>
          forwardedArgs += head
          remaining = tail

        case Nil =>
        // unreachable: the while guard ensures remaining is non-empty
      }
    }

    RouteDecision(
      success = true,
      serviceName = serviceName,
      serviceArgs = forwardedArgs.toArray,
      errorMessage = ""
    )
  }
}
