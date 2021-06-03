package com.kyubot.rkt

enum class ExchangeType {
  Direct,
  Fanout;

  val asString: String get() = name.lowercase()
}
