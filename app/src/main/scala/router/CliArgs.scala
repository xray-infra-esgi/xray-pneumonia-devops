package com.xray.detection.router

// Shared CLI args parser helper.
object CliArgs {

  def optionValue(args: Array[String], flag: String): Option[String] = {
    var i = 0
    while (i < args.length - 1) {
      if (args(i) == flag) {
        return Some(args(i + 1))
      }
      i += 1
    }
    None
  }
}
