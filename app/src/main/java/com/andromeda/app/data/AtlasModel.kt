package com.andromeda.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** On-disk format of an .atlas file. Coordinates are in canvas units (not screen pixels). */
@Serializable
data class AtlasDoc(
    val items: List<AtlasItem> = emptyList(),
    val strokes: List<Stroke> = emptyList(),
    val shapes: List<Shape> = emptyList(),
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f
)

@Serializable
sealed class AtlasItem {
    abstract val id: String
    abstract val x: Float
    abstract val y: Float
    abstract val w: Float
    abstract val h: Float
    abstract fun moved(x: Float, y: Float, w: Float = this.w, h: Float = this.h): AtlasItem

    @Serializable @SerialName("text")
    data class Text(
        override val id: String, override val x: Float, override val y: Float,
        override val w: Float = 260f, override val h: Float = 120f,
        val text: String = "", val color: Long = 0xFF2B2B2B
    ) : AtlasItem() {
        override fun moved(x: Float, y: Float, w: Float, h: Float) = copy(x = x, y = y, w = w, h = h)
    }

    @Serializable @SerialName("image")
    data class Image(
        override val id: String, override val x: Float, override val y: Float,
        override val w: Float = 300f, override val h: Float = 220f,
        val file: String
    ) : AtlasItem() {
        override fun moved(x: Float, y: Float, w: Float, h: Float) = copy(x = x, y = y, w = w, h = h)
    }

    /** Link to another file in the project, stored by title. */
    @Serializable @SerialName("file")
    data class FileLink(
        override val id: String, override val x: Float, override val y: Float,
        override val w: Float = 220f, override val h: Float = 64f,
        val title: String
    ) : AtlasItem() {
        override fun moved(x: Float, y: Float, w: Float, h: Float) = copy(x = x, y = y, w = w, h = h)
    }

    /** Web link; title and thumbnail are cached once fetched. */
    @Serializable @SerialName("embed")
    data class Embed(
        override val id: String, override val x: Float, override val y: Float,
        override val w: Float = 320f, override val h: Float = 240f,
        val url: String, val title: String = "", val thumb: String = ""
    ) : AtlasItem() {
        override fun moved(x: Float, y: Float, w: Float, h: Float) = copy(x = x, y = y, w = w, h = h)
    }
}

@Serializable
data class Pt(val x: Float, val y: Float)

@Serializable
data class Stroke(val id: String, val pts: List<Pt>, val color: Long, val width: Float)

@Serializable
data class Shape(
    val id: String, val ellipse: Boolean,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val color: Long, val width: Float
)

object AtlasJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(doc: AtlasDoc): String = json.encodeToString(AtlasDoc.serializer(), doc)

    fun decode(text: String): AtlasDoc =
        if (text.isBlank()) AtlasDoc()
        else runCatching { json.decodeFromString(AtlasDoc.serializer(), text) }.getOrDefault(AtlasDoc())
}

fun newId(): String = java.util.UUID.randomUUID().toString().take(8)
