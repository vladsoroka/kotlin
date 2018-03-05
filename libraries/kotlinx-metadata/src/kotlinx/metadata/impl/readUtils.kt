/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlinx.metadata.impl

import kotlinx.metadata.Annotation
import kotlinx.metadata.AnnotationArgument
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.ProtoBuf.Annotation.Argument.Value.Type.*
import org.jetbrains.kotlin.metadata.deserialization.NameResolver

fun ProtoBuf.Annotation.readAnnotation(strings: NameResolver): Annotation =
    Annotation(
        strings.getQualifiedClassName(id),
        argumentList.mapNotNull { argument ->
            argument.value.readAnnotationArgument(strings)?.let { value ->
                strings.getString(argument.nameId) to value
            }
        }.toMap()
    )

private fun ProtoBuf.Annotation.Argument.Value.readAnnotationArgument(strings: NameResolver): AnnotationArgument<*>? =
    when (type) {
        BYTE -> AnnotationArgument.ByteValue(intValue.toByte())
        CHAR -> AnnotationArgument.CharValue(intValue.toChar())
        SHORT -> AnnotationArgument.ShortValue(intValue.toShort())
        INT -> AnnotationArgument.IntValue(intValue.toInt())
        LONG -> AnnotationArgument.LongValue(intValue)
        FLOAT -> AnnotationArgument.FloatValue(floatValue)
        DOUBLE -> AnnotationArgument.DoubleValue(doubleValue)
        BOOLEAN -> AnnotationArgument.BooleanValue(intValue != 0L)
        STRING -> AnnotationArgument.StringValue(strings.getString(stringValue))
        CLASS -> AnnotationArgument.KClassValue(strings.getQualifiedClassName(classId))
        ENUM -> AnnotationArgument.EnumValue(strings.getQualifiedClassName(classId), strings.getString(enumValueId))
        ANNOTATION -> AnnotationArgument.AnnotationValue(annotation.readAnnotation(strings))
        ARRAY -> AnnotationArgument.ArrayValue(arrayElementList.mapNotNull { it.readAnnotationArgument(strings) })
        null -> null
    }
