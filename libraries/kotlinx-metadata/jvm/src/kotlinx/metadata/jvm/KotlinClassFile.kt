/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlinx.metadata.jvm

import kotlinx.metadata.ClassVisitor
import kotlinx.metadata.InconsistentKotlinMetadataException
import kotlinx.metadata.LambdaVisitor
import kotlinx.metadata.PackageVisitor
import kotlinx.metadata.impl.*
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.NameResolver
import org.jetbrains.kotlin.metadata.deserialization.TypeTable
import org.jetbrains.kotlin.metadata.deserialization.VersionRequirementTable
import org.jetbrains.kotlin.metadata.jvm.deserialization.BitEncoding
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmNameResolver
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.metadata.jvm.serialization.JvmStringTable

sealed class KotlinClassFile(val metadata: KotlinMetadata) {
    class Class internal constructor(
        metadata: KotlinMetadata,
        private val proto: ProtoBuf.Class,
        private val nameResolver: NameResolver
    ) : KotlinClassFile(metadata) {
        fun accept(v: ClassVisitor) {
            proto.accept(
                v,
                ReadContext(nameResolver, TypeTable(proto.typeTable), VersionRequirementTable.create(proto.versionRequirementTable))
            )
        }

        class Writer : ClassWriter() {
            fun write(
                metadataVersion: IntArray = KotlinMetadata.COMPATIBLE_METADATA_VERSION,
                bytecodeVersion: IntArray = KotlinMetadata.COMPATIBLE_BYTECODE_VERSION,
                extraInt: Int = 0
            ): KotlinClassFile.Class {
                val proto = t.build()
                val d1 = BitEncoding.encodeBytes(proto.toByteArray())
                val strings = c.strings as JvmStringTable
                val d2 = strings.strings.toTypedArray()
                val metadata = KotlinMetadata(KotlinMetadata.CLASS_KIND, metadataVersion, bytecodeVersion, d1, d2, "", "", extraInt)
                return KotlinClassFile.Class(metadata, proto, JvmNameResolver(strings.serialize(), strings.strings.toTypedArray()))
            }
        }
    }

    class FileFacade internal constructor(
        metadata: KotlinMetadata,
        private val proto: ProtoBuf.Package,
        private val nameResolver: NameResolver
    ) : KotlinClassFile(metadata) {
        fun accept(v: PackageVisitor) {
            proto.accept(
                v,
                ReadContext(nameResolver, TypeTable(proto.typeTable), VersionRequirementTable.create(proto.versionRequirementTable))
            )
        }

        class Writer : PackageWriter() {
            fun write(
                metadataVersion: IntArray = KotlinMetadata.COMPATIBLE_METADATA_VERSION,
                bytecodeVersion: IntArray = KotlinMetadata.COMPATIBLE_BYTECODE_VERSION,
                extraInt: Int = 0
            ): KotlinClassFile.FileFacade {
                val proto = t.build()
                val d1 = BitEncoding.encodeBytes(proto.toByteArray())
                val strings = c.strings as JvmStringTable
                val d2 = strings.strings.toTypedArray()
                val metadata = KotlinMetadata(KotlinMetadata.FILE_FACADE_KIND, metadataVersion, bytecodeVersion, d1, d2, "", "", extraInt)
                return KotlinClassFile.FileFacade(metadata, proto, JvmNameResolver(strings.serialize(), strings.strings.toTypedArray()))
            }
        }
    }

    class Lambda internal constructor(
        metadata: KotlinMetadata,
        private val proto: ProtoBuf.Function,
        private val nameResolver: NameResolver
    ) : KotlinClassFile(metadata) {
        fun accept(v: LambdaVisitor) {
            proto.accept(v, ReadContext(nameResolver, TypeTable(proto.typeTable), VersionRequirementTable.EMPTY))
        }

        class Writer : LambdaWriter() {
            fun write(
                metadataVersion: IntArray = KotlinMetadata.COMPATIBLE_METADATA_VERSION,
                bytecodeVersion: IntArray = KotlinMetadata.COMPATIBLE_BYTECODE_VERSION,
                extraInt: Int = 0
            ): KotlinClassFile.Lambda {
                val proto = t?.build() ?: error("LambdaVisitor.visitFunction has not been called")
                val d1 = BitEncoding.encodeBytes(proto.toByteArray())
                val strings = c.strings as JvmStringTable
                val d2 = strings.strings.toTypedArray()
                val metadata = KotlinMetadata(
                    KotlinMetadata.SYNTHETIC_CLASS_KIND, metadataVersion, bytecodeVersion, d1, d2, "", "", extraInt
                )
                return KotlinClassFile.Lambda(metadata, proto, JvmNameResolver(strings.serialize(), strings.strings.toTypedArray()))
            }
        }
    }

    class SyntheticClass internal constructor(
        metadata: KotlinMetadata
    ) : KotlinClassFile(metadata) {
        class Writer {
            fun write(
                metadataVersion: IntArray = KotlinMetadata.COMPATIBLE_METADATA_VERSION,
                bytecodeVersion: IntArray = KotlinMetadata.COMPATIBLE_BYTECODE_VERSION,
                extraInt: Int = 0
            ): KotlinClassFile.SyntheticClass {
                val metadata = KotlinMetadata(
                    KotlinMetadata.SYNTHETIC_CLASS_KIND, metadataVersion, bytecodeVersion, emptyArray(), emptyArray(), "", "", extraInt
                )
                return KotlinClassFile.SyntheticClass(metadata)
            }
        }
    }

    class MultiFileClassFacade internal constructor(
        metadata: KotlinMetadata
    ) : KotlinClassFile(metadata) {
        val partClassNames: List<String> = metadata.data1.asList()

        class Writer {
            fun write(
                partClassNames: List<String>,
                metadataVersion: IntArray = KotlinMetadata.COMPATIBLE_METADATA_VERSION,
                bytecodeVersion: IntArray = KotlinMetadata.COMPATIBLE_BYTECODE_VERSION,
                extraInt: Int = 0
            ): KotlinClassFile.MultiFileClassFacade {
                val metadata = KotlinMetadata(
                    KotlinMetadata.MULTI_FILE_CLASS_FACADE_KIND, metadataVersion, bytecodeVersion, partClassNames.toTypedArray(),
                    emptyArray(), "", "", extraInt
                )
                return KotlinClassFile.MultiFileClassFacade(metadata)
            }
        }
    }

    class MultiFileClassPart internal constructor(
        metadata: KotlinMetadata,
        private val proto: ProtoBuf.Package,
        private val nameResolver: NameResolver
    ) : KotlinClassFile(metadata) {
        val facadeClassName: String
            get() = metadata.extraString

        fun accept(v: PackageVisitor) {
            proto.accept(
                v,
                ReadContext(nameResolver, TypeTable(proto.typeTable), VersionRequirementTable.create(proto.versionRequirementTable))
            )
        }

        class Writer : PackageWriter() {
            fun write(
                facadeClassName: String,
                metadataVersion: IntArray = KotlinMetadata.COMPATIBLE_METADATA_VERSION,
                bytecodeVersion: IntArray = KotlinMetadata.COMPATIBLE_BYTECODE_VERSION,
                extraInt: Int = 0
            ): KotlinClassFile.MultiFileClassPart {
                val proto = t.build()
                val d1 = BitEncoding.encodeBytes(proto.toByteArray())
                val strings = c.strings as JvmStringTable
                val d2 = strings.strings.toTypedArray()
                val metadata = KotlinMetadata(
                    KotlinMetadata.MULTI_FILE_CLASS_PART_KIND, metadataVersion, bytecodeVersion, d1, d2, facadeClassName, "", extraInt
                )
                return KotlinClassFile.MultiFileClassPart(
                    metadata, proto, JvmNameResolver(strings.serialize(), strings.strings.toTypedArray())
                )
            }
        }
    }

    class Unknown internal constructor(
        metadata: KotlinMetadata
    ) : KotlinClassFile(metadata)

    companion object {
        /**
         * Reads and parses the given Kotlin metadata and returns the correct type of [KotlinClassFile] encoded by this metadata,
         * or `null` if this metadata encodes an unsupported kind of Kotlin classes or has an unsupported metadata version.
         *
         * Throws [InconsistentKotlinMetadataException] if the metadata has inconsistencies which signal that it may have been
         * modified by a separate tool.
         */
        @JvmStatic
        fun read(metadata: KotlinMetadata): KotlinClassFile? {
            // We only support metadata of version 1.1.* (this is Kotlin from 1.0 until today)
            val version = metadata.metadataVersion
            if (version.size < 2 || version[0] != 1 || version[1] != 1) return null

            fun KotlinMetadata.readData(): Array<String> =
                data1.takeIf(Array<*>::isNotEmpty) ?: throw InconsistentKotlinMetadataException("No d1 in metadata")

            return try {
                when (metadata.kind) {
                    KotlinMetadata.CLASS_KIND -> {
                        val (nameResolver, proto) = JvmProtoBufUtil.readClassDataFrom(metadata.readData(), metadata.data2)
                        KotlinClassFile.Class(metadata, proto, nameResolver)
                    }
                    KotlinMetadata.FILE_FACADE_KIND -> {
                        val (nameResolver, proto) = JvmProtoBufUtil.readPackageDataFrom(metadata.readData(), metadata.data2)
                        KotlinClassFile.FileFacade(metadata, proto, nameResolver)
                    }
                    KotlinMetadata.SYNTHETIC_CLASS_KIND -> {
                        if (metadata.data1.isNotEmpty()) {
                            val (nameResolver, proto) = JvmProtoBufUtil.readFunctionDataFrom(metadata.data1, metadata.data2)
                            KotlinClassFile.Lambda(metadata, proto, nameResolver)
                        } else {
                            KotlinClassFile.SyntheticClass(metadata)
                        }
                    }
                    KotlinMetadata.MULTI_FILE_CLASS_FACADE_KIND -> {
                        KotlinClassFile.MultiFileClassFacade(metadata)
                    }
                    KotlinMetadata.MULTI_FILE_CLASS_PART_KIND -> {
                        val (nameResolver, proto) = JvmProtoBufUtil.readPackageDataFrom(metadata.readData(), metadata.data2)
                        KotlinClassFile.MultiFileClassPart(metadata, proto, nameResolver)
                    }
                    else -> {
                        KotlinClassFile.Unknown(metadata)
                    }
                }
            } catch (e: InconsistentKotlinMetadataException) {
                throw e
            } catch (e: Throwable) {
                throw InconsistentKotlinMetadataException("Exception occurred when reading Kotlin metadata", e)
            }
        }
    }
}
