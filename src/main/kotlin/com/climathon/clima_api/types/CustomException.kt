package eu.bavenir.databroker.types

enum class StdErrorMsg {
  UNKNOWN,
  ADAPTER_ERROR,
  DESTINATION_OFFLINE,
  OID_IID_NOT_FOUND,
  WRONG_PARAMETERS,
  WRONG_BODY,
  FUNCTION_NOT_IMPLEMENTED,
  METHOD_NOT_ALLOWED
}

data class CustomException(
  override val message: String,
  val stdErrorMsg: Enum<StdErrorMsg> = StdErrorMsg.UNKNOWN,
  val statusCode: Int = 400,
  val logId: String? = null,
  val detail: String? = null
) : Exception(message) {
  override fun toString(): String {
    return "EXCEPTION: $message [${stdErrorMsg}:$statusCode] "
  }
}
