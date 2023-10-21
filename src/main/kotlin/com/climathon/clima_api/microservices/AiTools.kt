package com.climathon.clima_api.microservices

class AiTools {
  public fun isOffensive(text: String): Boolean {
    return true
  }
  public fun getSustainability(text: String): Float {
    return 0.5f
  }
  public fun getSimilarity(newText: String, oldTexts: String): Float {
    return 0.5f
  }
}
