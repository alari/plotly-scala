package io.circe.altgeneric
package derive

import cats.data.Xor
import io.circe.{ ACursor, Decoder, HCursor, Json }

abstract class JsonProductCodec {
  def encodeEmpty: Json
  def encodeField(field: (String, Json), obj: Json, default: => Option[Json]): Json
  
  def decodeEmpty(cursor: HCursor): Decoder.Result[Unit]
  def decodeField[A](name: String, cursor: HCursor, decode: Decoder[A], default: Option[A]): Decoder.Result[(A, ACursor)]
}

object JsonProductCodec {
  val obj: JsonProductCodec = new JsonProductObjCodec
  def adapt(f: String => String): JsonProductCodec = new JsonProductObjCodec {
    override def toJsonName(name: String) = f(name)
  }
}

abstract class JsonProductCodecFor[P] {
  def codec: JsonProductCodec
}

object JsonProductCodecFor {
  def apply[S](codec0: JsonProductCodec): JsonProductCodecFor[S] =
    new JsonProductCodecFor[S] {
      def codec = codec0
    }

  implicit def default[T]: JsonProductCodecFor[T] =
    JsonProductCodecFor(JsonProductCodec.obj)
}

class JsonProductObjCodec extends JsonProductCodec {

  def toJsonName(name: String): String = name

  val encodeEmpty: Json = Json.obj()
  def encodeField(field: (String, Json), obj: Json, default: => Option[Json]): Json = {
    val (name, content) = field
    if (default.toSeq.contains(content))
      obj
    else
      obj.mapObject((toJsonName(name) -> content) +: _)
  }

  def decodeEmpty(cursor: HCursor): Decoder.Result[Unit] = Xor.right(())
  def decodeField[A](name: String, cursor: HCursor, decode: Decoder[A], default: Option[A]): Decoder.Result[(A, ACursor)] = {
    val c = cursor.downField(toJsonName(name))
    def result = c.as(decode).map((_, ACursor.ok(cursor)))

    default match {
      case None => result
      case Some(d) =>
        if (c.succeeded)
          result
        else
          Xor.right((d, ACursor.ok(cursor)))
    }
  }
}
