/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kotlinp

import kotlinx.metadata.*
import kotlinx.metadata.Annotation
import kotlinx.metadata.jvm.*

private fun visitFunction(sb: StringBuilder, flags: Int, name: String, ext: FunctionVisitor.Extensions): FunctionVisitor =
    object : FunctionVisitor() {
        val typeParams = mutableListOf<String>()
        val params = mutableListOf<String>()
        var receiverParameterType: String? = null
        var returnType: String? = null

        override fun visitReceiverParameterType(flags: Int, ext: TypeVisitor.Extensions): TypeVisitor? =
            printType(flags, ext) { receiverParameterType = it }

        override fun visitTypeParameter(
            flags: Int, name: String, id: Int, variance: Variance, ext: TypeParameterVisitor.Extensions
        ): TypeParameterVisitor? =
            printTypeParameter(flags, name, id, variance, ext) { typeParams.add(it) }

        override fun visitValueParameter(flags: Int, name: String): ValueParameterVisitor? =
            printValueParameter(flags, name) { params.add(it) }

        override fun visitReturnType(flags: Int, ext: TypeVisitor.Extensions): TypeVisitor? =
            printType(flags, ext) { returnType = it }

        // TODO: version requirement

        override fun visitEnd() {
            sb.appendln()
            ext.jvmSignature?.let { desc ->
                sb.appendln("  // $desc")
            }
            sb.append("  ")
            sb.appendFlags(flags, FUNCTION_FLAGS_MAP)
            sb.append("fun ")
            if (typeParams.isNotEmpty()) {
                typeParams.joinTo(sb, prefix = "<", postfix = ">")
                sb.append(" ")
            }
            if (receiverParameterType != null) {
                sb.append(receiverParameterType).append(".")
            }
            sb.append(name)
            params.joinTo(sb, prefix = "(", postfix = ")")
            if (returnType != null) {
                sb.append(": ").append(returnType)
            }
            sb.appendln()
        }
    }

private fun visitProperty(
    sb: StringBuilder,
    flags: Int,
    name: String,
    getterFlags: Int,
    setterFlags: Int,
    ext: PropertyVisitor.Extensions
): PropertyVisitor = object : PropertyVisitor() {
    val typeParams = mutableListOf<String>()
    var receiverParameterType: String? = null
    var returnType: String? = null

    override fun visitReceiverParameterType(flags: Int, ext: TypeVisitor.Extensions): TypeVisitor? =
        printType(flags, ext) { receiverParameterType = it }

    override fun visitTypeParameter(
        flags: Int, name: String, id: Int, variance: Variance, ext: TypeParameterVisitor.Extensions
    ): TypeParameterVisitor? =
        printTypeParameter(flags, name, id, variance, ext) { typeParams.add(it) }

    override fun visitReturnType(flags: Int, ext: TypeVisitor.Extensions): TypeVisitor? =
        printType(flags, ext) { returnType = it }

    // TODO: setter parameter, version requirement

    override fun visitEnd() {
        sb.appendln()
        ext.jvmFieldName?.let { name ->
            sb.append("  // $name")
            ext.jvmFieldType?.let { type ->
                sb.append(":$type")
            }
            sb.appendln()
        }
        ext.jvmGetterSignature?.let { desc ->
            sb.appendln("  // getter: $desc")
        }
        ext.jvmSetterSignature?.let { desc ->
            sb.appendln("  // setter: $desc")
        }
        ext.jvmSyntheticMethodForAnnotationsSignature?.let { desc ->
            sb.appendln("  // synthetic method for annotations: $desc")
        }
        sb.append("  ")
        sb.appendFlags(flags, PROPERTY_FLAGS_MAP)
        sb.append(if (Flags.Property.isVar(flags)) "var " else "val ")
        if (typeParams.isNotEmpty()) {
            typeParams.joinTo(sb, prefix = "<", postfix = ">")
            sb.append(" ")
        }
        if (receiverParameterType != null) {
            sb.append(receiverParameterType).append(".")
        }
        sb.append(name)
        if (returnType != null) {
            sb.append(": ").append(returnType)
        }
        if (Flags.Property.hasConstant(flags)) {
            sb.append(" /* = ... */")
        }
        sb.appendln()
        if (Flags.Property.hasGetter(flags)) {
            sb.append("    ")
            sb.appendFlags(getterFlags, PROPERTY_ACCESSOR_FLAGS_MAP)
            sb.appendln("get")
        }
        if (Flags.Property.hasSetter(flags)) {
            sb.append("    ")
            sb.appendFlags(setterFlags, PROPERTY_ACCESSOR_FLAGS_MAP)
            sb.appendln("set")
        }
    }
}

private fun visitTypeAlias(sb: StringBuilder, flags: Int, name: String): TypeAliasVisitor =
    object : TypeAliasVisitor() {
        val annotations = mutableListOf<Annotation>()
        val typeParams = mutableListOf<String>()
        var underlyingType: String? = null
        var expandedType: String? = null

        override fun visitTypeParameter(
            flags: Int, name: String, id: Int, variance: Variance, ext: TypeParameterVisitor.Extensions
        ): TypeParameterVisitor? =
            printTypeParameter(flags, name, id, variance, ext) { typeParams.add(it) }

        override fun visitUnderlyingType(flags: Int, ext: TypeVisitor.Extensions): TypeVisitor? =
            printType(flags, ext) { underlyingType = it }

        override fun visitExpandedType(flags: Int, ext: TypeVisitor.Extensions): TypeVisitor? =
            printType(flags, ext) { expandedType = it }

        override fun visitAnnotation(annotation: Annotation) {
            annotations += annotation
        }

        // TODO: version requirement

        override fun visitEnd() {
            sb.appendln()
            for (annotation in annotations) {
                sb.append("  ").append("@")
                sb.append(renderAnnotation(annotation))
                sb.appendln()
            }
            sb.append("  ")
            sb.appendFlags(flags, VISIBILITY_FLAGS_MAP)
            sb.append("typealias ").append(name)
            if (typeParams.isNotEmpty()) {
                typeParams.joinTo(sb, prefix = "<", postfix = ">")
            }
            if (underlyingType != null) {
                sb.append(" = ").append(underlyingType)
            }
            if (expandedType != null) {
                sb.append(" /* = ").append(expandedType).append(" */")
            }
            sb.appendln()
        }
    }

private fun printType(flags: Int, @Suppress("UNUSED_PARAMETER") ext: TypeVisitor.Extensions, output: (String) -> Unit): TypeVisitor =
    object : TypeVisitor() {
        private val sb = StringBuilder().apply {
            // TODO: "/* raw */"
            // TODO: use ext to render annotations
            appendFlags(flags, TYPE_FLAGS_MAP)
        }

        private val arguments = mutableListOf<String>()
        private var abbreviatedType: String? = null

        override fun visitClass(fqName: String) {
            sb.append(fqName)
        }

        override fun visitTypeParameter(id: Int) {
            sb.append("T#$id")
        }

        override fun visitTypeAlias(fqName: String) {
            sb.append(fqName).append("^")  // TODO: document, provide option
        }

        override fun visitAbbreviatedType(flags: Int, ext: TypeVisitor.Extensions): TypeVisitor? =
            printType(flags, ext) { abbreviatedType = it }

        override fun visitArgument(flags: Int, variance: Variance, ext: TypeVisitor.Extensions): TypeVisitor? =
            printType(flags, ext) { argumentTypeString ->
                arguments += buildString {
                    if (variance != Variance.INVARIANT) {
                        append(variance.name.toLowerCase()).append(" ")
                    }
                    append(argumentTypeString)
                }
            }

        override fun visitStarProjection() {
            arguments += "*"
        }

        // TODO: outer type, platform type upper bound

        override fun visitEnd() {
            if (arguments.isNotEmpty()) {
                arguments.joinTo(sb, prefix = "<", postfix = ">")
            }
            if (Flags.Type.isNullable(flags)) {
                sb.append("?")
            }
            if (abbreviatedType != null) {
                sb.append(" /* = ").append(abbreviatedType).append(" */")
            }
            output(sb.toString())
        }
    }

private fun printTypeParameter(
    flags: Int, name: String, id: Int, variance: Variance, ext: TypeParameterVisitor.Extensions,
    output: (String) -> Unit
): TypeParameterVisitor =
    object : TypeParameterVisitor() {
        // TODO: upper bounds

        override fun visitEnd() {
            output(buildString {
                appendFlags(flags, TYPE_PARAMETER_FLAGS_MAP)
                for (annotation in ext.jvmAnnotations) {
                    append("@").append(renderAnnotation(annotation)).append(" ")
                }
                if (variance != Variance.INVARIANT) {
                    append(variance.name.toLowerCase()).append(" ")
                }
                append("T#$id /* $name */")
            })
        }
    }

private fun printValueParameter(parameterFlags: Int, name: String, output: (String) -> Unit): ValueParameterVisitor =
    object : ValueParameterVisitor() {
        var varargElementType: String? = null
        var type: String? = null

        override fun visitType(flags: Int, ext: TypeVisitor.Extensions): TypeVisitor? =
            printType(flags, ext) { type = it }

        override fun visitVarargElementType(flags: Int, ext: TypeVisitor.Extensions): TypeVisitor? =
            printType(flags, ext) { varargElementType = it }

        override fun visitEnd() {
            output(buildString {
                appendFlags(parameterFlags, VALUE_PARAMETER_FLAGS_MAP)
                if (varargElementType != null) {
                    append("vararg ").append(name).append(": ").append(varargElementType).append(" /* ").append(type).append(" */")
                } else {
                    append(name).append(": ").append(type)
                }
                if (Flags.ValueParameter.declaresDefaultValue(parameterFlags)) {
                    append(" /* = ... */")
                }
            })
        }
    }

private fun renderAnnotation(annotation: Annotation): String =
    annotation.classFqName + if (annotation.arguments.isEmpty()) "" else
        annotation.arguments.entries.joinToString(prefix = "(", postfix = ")") { (name, argument) ->
            "$name = ${renderAnnotationArgument(argument)}"
        }

private fun renderAnnotationArgument(arg: AnnotationArgument<*>): String =
    when (arg) {
        is AnnotationArgument.ByteValue -> arg.value.toString() + ".toByte()"
        is AnnotationArgument.CharValue -> "'${arg.value}'"
        is AnnotationArgument.ShortValue -> arg.value.toString() + ".toShort()"
        is AnnotationArgument.IntValue -> arg.value.toString()
        is AnnotationArgument.LongValue -> arg.value.toString() + "L"
        is AnnotationArgument.FloatValue -> arg.value.toString() + "f"
        is AnnotationArgument.DoubleValue -> arg.value.toString()
        is AnnotationArgument.BooleanValue -> arg.value.toString()
        is AnnotationArgument.StringValue -> "\"${arg.value}\""
        is AnnotationArgument.KClassValue -> "${arg.value}::class"
        is AnnotationArgument.EnumValue -> arg.value
        is AnnotationArgument.AnnotationValue -> arg.value.let { annotation ->
            val args = annotation.arguments.entries.joinToString { (name, argument) ->
                "$name = ${renderAnnotationArgument(argument)}"
            }
            "${annotation.classFqName}($args)"
        }
        is AnnotationArgument.ArrayValue -> arg.value.joinToString(prefix = "[", postfix = "]", transform = ::renderAnnotationArgument)
    }

private fun StringBuilder.appendFlags(flags: Int, map: Map<MetadataFlag, String>) {
    for ((modifier, string) in map) {
        if (modifier(flags)) {
            append(string)
            if (string.isNotEmpty()) append(" ")
        }
    }
}

interface AbstractPrinter<in T : KotlinClassFile> {
    fun print(klass: T): String
}

class ClassPrinter : ClassVisitor(), AbstractPrinter<KotlinClassFile.Class> {
    private val sb = StringBuilder()
    private val result = StringBuilder()

    private val typeParams = mutableListOf<String>()
    private val supertypes = mutableListOf<String>()

    override fun visit(flags: Int, fqName: String) {
        result.appendFlags(flags, CLASS_FLAGS_MAP)
        result.append(fqName)
    }

    override fun visitEnd() {
        if (typeParams.isNotEmpty()) {
            typeParams.joinTo(result, prefix = "<", postfix = ">")
        }
        if (supertypes.isNotEmpty()) {
            result.append(" : ")
            supertypes.joinTo(result)
        }
        result.appendln(" {")
        result.append(sb)
        result.appendln("}")
    }

    override fun visitTypeParameter(
        flags: Int, name: String, id: Int, variance: Variance, ext: TypeParameterVisitor.Extensions
    ): TypeParameterVisitor? =
        printTypeParameter(flags, name, id, variance, ext) { typeParams.add(it) }

    override fun visitSupertype(flags: Int, ext: TypeVisitor.Extensions): TypeVisitor? {
        return printType(flags, ext) { supertypes.add(it) }
    }

    override fun visitConstructor(flags: Int, ext: ConstructorVisitor.Extensions): ConstructorVisitor? =
        object : ConstructorVisitor() {
            val params = mutableListOf<String>()

            override fun visitValueParameter(flags: Int, name: String): ValueParameterVisitor? =
                printValueParameter(flags, name) { params.add(it) }

            override fun visitEnd() {
                sb.appendln()
                ext.jvmSignature?.let { desc ->
                    sb.appendln("  // $desc")
                }
                sb.append("  ")
                sb.appendFlags(flags, CONSTRUCTOR_FLAGS_MAP)
                sb.append("constructor(")
                params.joinTo(sb)
                sb.appendln(")")
            }
        }

    override fun visitFunction(flags: Int, name: String, ext: FunctionVisitor.Extensions): FunctionVisitor? =
        visitFunction(sb, flags, name, ext)

    override fun visitProperty(
        flags: Int,
        name: String,
        getterFlags: Int,
        setterFlags: Int,
        ext: PropertyVisitor.Extensions
    ): PropertyVisitor? =
        visitProperty(sb, flags, name, getterFlags, setterFlags, ext)

    override fun visitTypeAlias(flags: Int, name: String): TypeAliasVisitor? =
        visitTypeAlias(sb, flags, name)

    override fun visitCompanionObject(name: String) {
        sb.appendln()
        sb.appendln("  // companion object: $name")
    }

    override fun visitNestedClass(name: String) {
        sb.appendln()
        sb.appendln("  // nested class: $name")
    }

    override fun visitEnumEntry(name: String) {
        sb.appendln()
        sb.appendln("  $name,")
    }

    override fun visitSealedSubclass(fqName: String) {
        sb.appendln()
        sb.appendln("  // sealed subclass: $fqName")
    }

    override fun print(klass: KotlinClassFile.Class): String {
        klass.accept(this)
        return result.toString()
    }
}

abstract class PackagePrinter : PackageVisitor() {
    internal val sb = StringBuilder().apply {
        appendln("package {")
    }

    override fun visitEnd() {
        sb.appendln("}")
    }

    override fun visitFunction(flags: Int, name: String, ext: FunctionVisitor.Extensions): FunctionVisitor? =
        visitFunction(sb, flags, name, ext)

    override fun visitProperty(
        flags: Int,
        name: String,
        getterFlags: Int,
        setterFlags: Int,
        ext: PropertyVisitor.Extensions
    ): PropertyVisitor? =
        visitProperty(sb, flags, name, getterFlags, setterFlags, ext)

    override fun visitTypeAlias(flags: Int, name: String): TypeAliasVisitor? =
        visitTypeAlias(sb, flags, name)
}

class FileFacadePrinter : PackagePrinter(), AbstractPrinter<KotlinClassFile.FileFacade> {
    override fun print(klass: KotlinClassFile.FileFacade): String {
        klass.accept(this)
        return sb.toString()
    }
}

class LambdaPrinter : LambdaVisitor(), AbstractPrinter<KotlinClassFile.Lambda> {
    private val sb = StringBuilder().apply {
        appendln("lambda {")
    }

    override fun visitFunction(flags: Int, name: String, ext: FunctionVisitor.Extensions): FunctionVisitor? =
        visitFunction(sb, flags, name, ext)

    override fun visitEnd() {
        sb.appendln("}")
    }

    override fun print(klass: KotlinClassFile.Lambda): String {
        klass.accept(this)
        return sb.toString()
    }
}

class MultiFileClassPartPrinter : PackagePrinter(), AbstractPrinter<KotlinClassFile.MultiFileClassPart> {
    override fun print(klass: KotlinClassFile.MultiFileClassPart): String {
        sb.appendln("  // facade: ${klass.facadeClassName}")
        klass.accept(this)
        return sb.toString()
    }
}

class MultiFileClassFacadePrinter : AbstractPrinter<KotlinClassFile.MultiFileClassFacade> {
    override fun print(klass: KotlinClassFile.MultiFileClassFacade): String =
        buildString {
            appendln("multi-file class {")
            for (part in klass.partClassNames) {
                appendln("  // $part")
            }
            appendln("}")
        }
}

class ModuleFilePrinter : ModuleVisitor() {
    private val sb = StringBuilder().apply {
        appendln("module {")
    }

    override fun visitPackageParts(fqName: String, fileFacades: List<String>, multiFileClassParts: Map<String, String>) {
        val presentableFqName = if (fqName.isEmpty()) "<root>" else fqName
        sb.appendln("  package $presentableFqName {")
        for (fileFacade in fileFacades) {
            sb.appendln("    $fileFacade")
        }
        for ((multiFileClassPart, facade) in multiFileClassParts) {
            sb.appendln("    $multiFileClassPart ($facade)")
        }
        sb.appendln("  }")
    }

    override fun visitAnnotation(annotation: Annotation) {
        // TODO
    }

    override fun visitEnd() {
        sb.appendln("}")
    }

    fun print(file: KotlinModuleFile): String {
        file.accept(this)
        return sb.toString()
    }
}

private val VISIBILITY_FLAGS_MAP = mapOf(
    Flags.isInternal to "internal",
    Flags.isPrivate to "private",
    Flags.isPrivateToThis to "private",
    Flags.isProtected to "protected",
    Flags.isPublic to "public"
)

private val COMMON_FLAGS_MAP = VISIBILITY_FLAGS_MAP + mapOf(
    Flags.isFinal to "final",
    Flags.isOpen to "open",
    Flags.isAbstract to "abstract",
    Flags.isSealed to "sealed"
)

private val CLASS_FLAGS_MAP = COMMON_FLAGS_MAP + mapOf(
    Flags.Class.isInner to "inner",
    Flags.Class.isData to "data",
    Flags.Class.isExternal to "external",
    Flags.Class.isExpect to "expect",
    Flags.Class.isInline to "inline",

    Flags.Class.isClass to "class",
    Flags.Class.isInterface to "interface",
    Flags.Class.isEnumClass to "enum class",
    Flags.Class.isEnumEntry to "enum entry",
    Flags.Class.isAnnotationClass to "annotation class",
    Flags.Class.isObject to "object",
    Flags.Class.isCompanionObject to "companion object"
)

private val CONSTRUCTOR_FLAGS_MAP = COMMON_FLAGS_MAP + mapOf(
    Flags.Constructor.isPrimary to "/* primary */"
)

private val FUNCTION_FLAGS_MAP = COMMON_FLAGS_MAP + mapOf(
    Flags.Function.isDeclaration to "",
    Flags.Function.isFakeOverride to "/* fake override */",
    Flags.Function.isDelegation to "/* delegation */",
    Flags.Function.isSynthesized to "/* synthesized */",

    Flags.Function.isOperator to "operator",
    Flags.Function.isInfix to "infix",
    Flags.Function.isInline to "inline",
    Flags.Function.isTailrec to "tailrec",
    Flags.Function.isExternal to "external",
    Flags.Function.isSuspend to "suspend",
    Flags.Function.isExpect to "expect"
)

private val PROPERTY_FLAGS_MAP = COMMON_FLAGS_MAP + mapOf(
    Flags.Property.isDeclaration to "",
    Flags.Property.isFakeOverride to "/* fake override */",
    Flags.Property.isDelegation to "/* delegation */",
    Flags.Property.isSynthesized to "/* synthesized */",

    Flags.Property.isConst to "const",
    Flags.Property.isLateinit to "lateinit",
    Flags.Property.isExternal to "external",
    Flags.Property.isDelegated to "/* delegated */",
    Flags.Property.isExpect to "expect"
)

private val PROPERTY_ACCESSOR_FLAGS_MAP = COMMON_FLAGS_MAP + mapOf(
    Flags.PropertyAccessor.isNotDefault to "/* non-default */",
    Flags.PropertyAccessor.isExternal to "external",
    Flags.PropertyAccessor.isInline to "inline"
)

private val VALUE_PARAMETER_FLAGS_MAP = mapOf(
    Flags.ValueParameter.isCrossinline to "crossinline",
    Flags.ValueParameter.isNoinline to "noinline"
)

private val TYPE_PARAMETER_FLAGS_MAP = mapOf(
    Flags.TypeParameter.isReified to "reified"
)

private val TYPE_FLAGS_MAP = mapOf(
    Flags.Type.isSuspend to "suspend"
)
