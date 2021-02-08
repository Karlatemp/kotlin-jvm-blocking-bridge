package net.mamoe.kjbb.compiler.backend.ir

import net.mamoe.kjbb.compiler.backend.jvm.BlockingBridgeAnalyzeResult
import net.mamoe.kjbb.compiler.backend.jvm.BlockingBridgeAnalyzeResult.*
import net.mamoe.kjbb.compiler.backend.jvm.isJvm8OrHigher
import net.mamoe.kjbb.compiler.extensions.IJvmBlockingBridgeCodegenJvmExtension
import org.jetbrains.kotlin.backend.common.ir.allParameters
import org.jetbrains.kotlin.backend.jvm.codegen.psiElement
import org.jetbrains.kotlin.descriptors.effectiveVisibility
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.isInlined
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.module
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi


/**
 * Check whether a function is allowed to generate bridges with.
 *
 * The functions must
 * - be `final` or `open`
 * - have parent [IrClass]
 */
@OptIn(ObsoleteDescriptorBasedAPI::class)
fun IrFunction.analyzeCapabilityForGeneratingBridges(ext: IJvmBlockingBridgeCodegenJvmExtension): BlockingBridgeAnalyzeResult {
    var annotationFromContainingClass = false

    val jvmBlockingBridgeAnnotationIr =
        jvmBlockingBridgeAnnotation()
            ?: jvmBlockingBridgeAnnotationOnContainingClass().also { annotationFromContainingClass = true }
            ?: kotlin.run {
                if (ext.enableForModule) null
                else return MissingAnnotationPsi
            }


    val jvmBlockingBridgeAnnotation =
        if (jvmBlockingBridgeAnnotationIr == null) null else
            jvmBlockingBridgeAnnotationIr.psiElement
                ?: psiElement
                ?: descriptor.findPsi()
                ?: return MissingAnnotationPsi

    if (this !is IrSimpleFunction) return Inapplicable(jvmBlockingBridgeAnnotation ?: return EnableForModule)

    fun impl(): BlockingBridgeAnalyzeResult {
        // fun must be suspend and applied to member function
        if (!isSuspend || name.isSpecial) {
            return Inapplicable(jvmBlockingBridgeAnnotation ?: return EnableForModule)
        }

        if (isGeneratedBlockingBridgeStub()) {
            // @JvmBlockingBridge and @GeneratedBlockingBridge both present
            return FromStub
        }

        if (!visibility.normalize().effectiveVisibility(descriptor, true).publicApi) {
            // effectively internal api
            return RedundantForNonPublicDeclarations(jvmBlockingBridgeAnnotation ?: return EnableForModule)
        }

        val containingClass = parentClassOrNull
        if (containingClass?.isInline == true) {
            // inside inline class not supported
            return InlineClassesNotSupported(jvmBlockingBridgeAnnotation ?: return EnableForModule,
                containingClass.descriptor)
        }

        allParameters.firstOrNull { it.type.isInlined() }?.let { param ->
            // inline class param not yet supported
            return InlineClassesNotSupported(
                param.psiElement ?: jvmBlockingBridgeAnnotation ?: return EnableForModule, param.descriptor)
        }

        if (containingClass?.isInterface == true) { // null means top-level, which is also accepted
            if (module.platform?.isJvm8OrHigher() != true) {
                // inside interface and JVM under 8
                return InterfaceNotSupported(jvmBlockingBridgeAnnotation ?: return EnableForModule)
            }
        }

        val overridden = this.findOverriddenDescriptorsHierarchically {
            it.analyzeCapabilityForGeneratingBridges(ext).shouldGenerate
        }

        if (overridden != null) {
            // super function has @
            // generate only if this function has @, or implied from @ on class, which concluded as 'isReal'
            return OverridesSuper(isUserDeclaredFunction())
        }

        // super function no @
        // this function may has @ or implied from
        return if (isUserDeclaredFunction()) {
            // explicit 'override' then generate for it.
            Allowed
        } else {
            // implicit override by compiler, don't generate.
            BridgeAnnotationFromContainingDeclaration(null)
        }
    }

    val result = impl()
    if (annotationFromContainingClass) {
        if (!result.diagnosticPassed) {
            return BridgeAnnotationFromContainingDeclaration(result)
        }
    }
    return result
}