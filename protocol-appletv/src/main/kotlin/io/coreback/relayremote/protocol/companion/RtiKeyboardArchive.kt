package io.coreback.relayremote.protocol.companion

import com.dd.plist.BinaryPropertyListWriter
import com.dd.plist.NSArray
import com.dd.plist.NSData
import com.dd.plist.NSDictionary
import com.dd.plist.NSNull
import com.dd.plist.NSNumber
import com.dd.plist.NSObject
import com.dd.plist.NSString
import com.dd.plist.PropertyListParser
import com.dd.plist.UID
import java.math.BigInteger

data class RtiKeyboardSession(
    val uuid: ByteArray,
    val currentText: String,
)

/**
 * The RTI keyboard service exchanges NSKeyedArchiver binary property lists.
 * This builds only the two archive shapes used by Companion Link and resolves
 * only explicit key paths; no general-purpose object deserialization occurs.
 */
object RtiKeyboardArchive {
    fun readSession(payload: ByteArray): RtiKeyboardSession? {
        val uuid = readPath(payload, "sessionUUID") as? ByteArray ?: return null
        if (uuid.size != SESSION_UUID_BYTES) return null
        val text = readPath(
            payload,
            "documentState",
            "docSt",
            "contextBeforeInput",
        ) as? String ?: ""
        return RtiKeyboardSession(uuid, text)
    }

    fun clearText(sessionUuid: ByteArray): ByteArray {
        requireSessionUuid(sessionUuid)
        val objects = NSArray(
            NSString("\$null"),
            dictionary(
                "\$class" to uid(7),
                "targetSessionUUID" to uid(5),
                "keyboardOutput" to uid(2),
                "textToAssert" to uid(4),
            ),
            dictionary("\$class" to uid(3)),
            classDescription("TIKeyboardOutput"),
            NSString(""),
            dictionary(
                "NS.uuidbytes" to NSData(sessionUuid.copyOf()),
                "\$class" to uid(6),
            ),
            classDescription("NSUUID"),
            classDescription("RTITextOperations"),
        )
        return writeArchive(objects)
    }

    fun inputText(sessionUuid: ByteArray, text: String): ByteArray {
        requireSessionUuid(sessionUuid)
        val objects = NSArray(
            NSString("\$null"),
            dictionary(
                "keyboardOutput" to uid(2),
                "\$class" to uid(7),
                "targetSessionUUID" to uid(5),
            ),
            dictionary(
                "insertionText" to uid(3),
                "\$class" to uid(4),
            ),
            NSString(text),
            classDescription("TIKeyboardOutput"),
            dictionary(
                "NS.uuidbytes" to NSData(sessionUuid.copyOf()),
                "\$class" to uid(6),
            ),
            classDescription("NSUUID"),
            classDescription("RTITextOperations"),
        )
        return writeArchive(objects)
    }

    internal fun readPath(payload: ByteArray, vararg path: String): Any? {
        val root = PropertyListParser.parse(payload) as? NSDictionary ?: return null
        val objects = root.objectForKey("\$objects") as? NSArray ?: return null
        var current: NSObject = root.objectForKey("\$top") ?: return null
        for (key in path) {
            current = resolve(current, objects)
            val dictionary = current as? NSDictionary ?: return null
            current = dictionary.objectForKey(key) ?: return null
        }
        return unwrap(resolve(current, objects))
    }

    private fun writeArchive(objects: NSArray): ByteArray {
        val top = dictionary("textOperations" to uid(1))
        val root = dictionary(
            "\$version" to NSNumber(100_000),
            "\$archiver" to NSString("RTIKeyedArchiver"),
            "\$top" to top,
            "\$objects" to objects,
        )
        return BinaryPropertyListWriter.writeToArray(root)
    }

    private fun resolve(value: NSObject, objects: NSArray): NSObject {
        if (value !is UID) return value
        val index = BigInteger(1, value.bytes).toInt()
        require(index in 0 until objects.count()) { "Keyed archive UID is out of bounds" }
        return objects.objectAtIndex(index)
    }

    private fun unwrap(value: NSObject): Any? = when (value) {
        is NSData -> value.bytes()
        is NSString -> value.content
        is NSNumber -> value.toJavaObject()
        is NSNull -> null
        else -> value.toJavaObject()
    }

    private fun classDescription(className: String): NSDictionary =
        dictionary(
            "\$classname" to NSString(className),
            "\$classes" to NSArray(
                NSString(className),
                NSString("NSObject"),
            ),
        )

    private fun dictionary(vararg entries: Pair<String, NSObject>): NSDictionary =
        NSDictionary().apply {
            entries.forEach { (key, value) -> put(key, value) }
        }

    private fun uid(index: Int): UID = UID("CF\$UID", BigInteger.valueOf(index.toLong()))

    private fun requireSessionUuid(value: ByteArray) {
        require(value.size == SESSION_UUID_BYTES) { "RTI session UUID must be 16 bytes" }
    }

    private const val SESSION_UUID_BYTES = 16
}
