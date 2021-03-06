@file:JvmName("JvmBlockingBridgeUtils")
@file:Suppress("unused") // for public API

package net.mamoe.kjbb.compiler.backend.ir

import net.mamoe.kjbb.JvmBlockingBridge
import net.mamoe.kjbb.compiler.backend.jvm.GeneratedBlockingBridgeStubForResolution
import org.jetbrains.kotlin.backend.common.ir.allOverridden
import org.jetbrains.kotlin.backend.common.lower.parents
import org.jetbrains.kotlin.backend.jvm.codegen.psiElement
import org.jetbrains.kotlin.codegen.topLevelClassAsmType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.util.findAnnotation
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.originalFunction
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull


val JVM_BLOCKING_BRIDGE_FQ_NAME = FqName(JvmBlockingBridge::class.qualifiedName!!)

@Suppress(
    "INVISIBLE_REFERENCE",
    "EXPERIMENTAL_MARKER_CAN_ONLY_BE_USED_AS_ANNOTATION_OR_ARGUMENT_IN_USE_EXPERIMENTAL",
    "DEPRECATION_ERROR"
)
val GENERATED_BLOCKING_BRIDGE_FQ_NAME = FqName(net.mamoe.kjbb.GeneratedBlockingBridge::class.qualifiedName!!)

val JVM_BLOCKING_BRIDGE_ASM_TYPE = JVM_BLOCKING_BRIDGE_FQ_NAME.topLevelClassAsmType()
val GENERATED_BLOCKING_BRIDGE_ASM_TYPE = GENERATED_BLOCKING_BRIDGE_FQ_NAME.topLevelClassAsmType()

/**
 * For annotation class
 */
fun IrClass.isJvmBlockingBridge(): Boolean =
    symbol.owner.fqNameWhenAvailable?.asString() == JVM_BLOCKING_BRIDGE_FQ_NAME.asString()

/**
 * Filter by annotation `@JvmBlockingBridge`
 */
fun FunctionDescriptor.isJvmBlockingBridge(): Boolean = annotations.hasAnnotation(JVM_BLOCKING_BRIDGE_FQ_NAME)

/**
 * Filter by annotation `@JvmBlockingBridge`
 */
fun IrFunction.isJvmBlockingBridge(): Boolean = annotations.hasAnnotation(JVM_BLOCKING_BRIDGE_FQ_NAME)

@OptIn(ObsoleteDescriptorBasedAPI::class)
fun IrFunction.isGeneratedBlockingBridgeStub(): Boolean =
    this.descriptor.getUserData(GeneratedBlockingBridgeStubForResolution) == true

fun IrSimpleFunction.isUserDeclaredFunction(): Boolean {
    return originalFunction.psiElement != null
}

fun IrSimpleFunction.findOverriddenDescriptorsHierarchically(filter: (IrSimpleFunction) -> Boolean): IrSimpleFunction? {
    for (override in this.allOverridden(false)) {
        if (filter(override)) {
            return override
        }
        val find = override.findOverriddenDescriptorsHierarchically(filter)
        if (find != null) return find
    }
    return null
}

internal fun IrAnnotationContainer.jvmBlockingBridgeAnnotation(): IrConstructorCall? =
    annotations.findAnnotation(JVM_BLOCKING_BRIDGE_FQ_NAME)

fun IrFunction.jvmBlockingBridgeAnnotationOnContainingClass(): IrConstructorCall? {
    val containingClass = parent

    if (containingClass is IrAnnotationContainer) {
        val annotation = containingClass.annotations.findAnnotation(JVM_BLOCKING_BRIDGE_FQ_NAME)
        if (annotation != null) return annotation
    }

    if (containingClass is IrClass) {
        val file = containingClass.parents.firstIsInstanceOrNull<IrFile>()
        val annotation = file?.annotations?.findAnnotation(JVM_BLOCKING_BRIDGE_FQ_NAME)
        if (annotation != null) return annotation
    }

    return null
}

internal val IrFunction.isFinal get() = this is IrSimpleFunction && this.modality == Modality.FINAL
internal val IrFunction.isOpen get() = this is IrSimpleFunction && this.modality == Modality.OPEN
internal val IrFunction.isAbstract get() = this is IrSimpleFunction && this.modality == Modality.ABSTRACT
