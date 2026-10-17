package local.mylife.core.security.crypto

enum class KeyScope(val id: String) {
    MAIN("main"),
    VAULT("vault"),
    ;

    fun pinWrapAad(): ByteArray = "v=1;p=pin-wrap;id=$id".toByteArray(Charsets.UTF_8)

    fun devicePinWrapAad(): ByteArray = "v=1;p=device-pin-wrap;id=$id".toByteArray(Charsets.UTF_8)

    fun bioWrapAad(): ByteArray = "v=1;p=bio-wrap;id=$id".toByteArray(Charsets.UTF_8)
}
