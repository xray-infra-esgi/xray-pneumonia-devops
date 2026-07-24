package com.xray.detection

import com.xray.detection.consumer.ConsumerMain
import com.xray.detection.producer.ProducerMain
import com.xray.detection.router.ServiceRouter

object Main {

  private val services: Map[String, Array[String] => Int] = Map(
    "produce"    -> ProducerMain.run,
    "consume"    -> ConsumerMain.run
  )

  def main(args: Array[String]): Unit = {
    val exitCode = ServiceRouter.route(args, services, defaultService = "consume")
    if (exitCode != 0) {
      sys.exit(exitCode)
    }
  }
}
