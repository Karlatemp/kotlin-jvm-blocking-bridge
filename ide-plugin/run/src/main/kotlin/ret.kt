import net.mamoe.kjbb.JvmBlockingBridge

open class TestRet {
    @JvmBlockingBridge
    @MyDeprecated("asd")
    open suspend fun test(): String = ""
}

typealias MyDeprecated = Deprecated