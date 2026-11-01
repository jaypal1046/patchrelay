package dev.patchrelay.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(ExperimentalCompilerApi::class)
class PatchRelayCompilerRegistrar : CompilerPluginRegistrar() {
    override val supportsK2 = false
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(SuspendInterception())
    }
}

/** Declaration instrumentation also covers callers compiled in other JVM/Android modules. */
private class SuspendInterception : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val originals = moduleFragment.files.flatMap { it.declarations.filterIsInstance<IrSimpleFunction>() }
            .filter { it.isSuspend && it.hasAnnotation(FqName("PatchFunction")) }
        val fallbacks = originals.associateWith { original ->
            original.deepCopyWithSymbols(original.parent).apply {
                name = Name.identifier("__patchrelay_original_${original.name}")
                visibility = DescriptorVisibilities.INTERNAL
                annotations = emptyList()
                attributeOwnerId = this
                metadata = null
            }
        }
        val fallbackCalls = object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid()
                val fallback = fallbacks[expression.symbol.owner] ?: return expression
                return IrCallImpl.fromSymbolOwner(expression.startOffset, expression.endOffset, expression.type, fallback.symbol).apply {
                    for (i in 0 until expression.valueArgumentsCount) putValueArgument(i, expression.getValueArgument(i))
                }
            }
        }
        for ((original, fallback) in fallbacks) {
            fallback.body?.transformChildren(fallbackCalls, null)
            (original.parent as IrFile).declarations.add(fallback)
            val wrapper = pluginContext.referenceFunctions(CallableId(FqName.ROOT,
                Name.identifier("patchRelayIntercept_${original.name}"))).singleOrNull()
                ?: error("PatchRelay: missing generated suspend bridge for ${original.name}; apply dev.patchrelay.functions")
            original.body = DeclarationIrBuilder(pluginContext, original.symbol).irBlockBody {
                +irReturn(irCall(wrapper).apply {
                    original.valueParameters.forEachIndexed { index, parameter -> putValueArgument(index, irGet(parameter)) }
                })
            }
        }
        // Generated callbacks own native fallback: they must bypass the instrumented entrypoint.
        moduleFragment.files.filter { it.fileEntry.name.endsWith("/PatchRelayFunctions.kt") ||
            it.fileEntry.name.endsWith("\\PatchRelayFunctions.kt") }.forEach {
            it.transformChildren(fallbackCalls, null)
        }
    }
}
