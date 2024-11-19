package dev.xdark.bsea;

import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.invoke.MethodHandles;

@SuppressWarnings("preview")
public final class ClassFileRewriter {
	private ClassFileRewriter() {
	}

	static byte[] patchBytecode(byte[] classfileBytes) {
		var cf = ClassFile.of();
		var model = cf.parse(classfileBytes);

		var rewriteSystemExit = new CodeTransform() {
			boolean modified;

			@Override
			public void accept(CodeBuilder codeBuilder, CodeElement e) {
				var poolBuilder = codeBuilder.constantPool();
				var newElement = switch (e) {
					case InvokeInstruction i -> rewriteInvokeInstruction(poolBuilder, i);
					case InvokeDynamicInstruction i -> rewriteInvokeDynamicInstruction(poolBuilder, i);
					case ConstantInstruction.LoadConstantInstruction i -> rewriteLDC(poolBuilder, i);
					default -> e;
				};
				if (e != newElement) {
					modified = true;
					codeBuilder.with(newElement);
					return;
				}
				codeBuilder.with(e);
			}
		};

		var ct = ClassTransform.transformingMethodBodies(rewriteSystemExit);
		var newClassBytes = cf.transform(model, ct);
		if (rewriteSystemExit.modified) {
			return newClassBytes;
		}
		return null;
	}

	static ConstantDesc rewriteConstantDesc(ConstantDesc desc) {
		return switch (desc) {
			case DirectMethodHandleDesc mh -> rewriteDirectMethodHandleDesc(mh);
			case DynamicConstantDesc<?> dc -> rewriteDynamicConstantDesc(dc);
			default -> desc;
		};
	}

	static MethodHandleEntry rewriteMethodHandleEntry(ConstantPoolBuilder poolBuilder, MethodHandleEntry desc) {
		var symbol = desc.asSymbol();
		for (var invocation : AgentData.invocationSet()) {
			var replacement = invocation.replaceDirectMethodHandleDesc(symbol);
			if (replacement != symbol)
				return poolBuilder.methodHandleEntry(replacement);
		}
		return desc;
	}

	static DirectMethodHandleDesc rewriteDirectMethodHandleDesc(DirectMethodHandleDesc desc) {
		for (var invocation : AgentData.invocationSet()) {
			var replacement = invocation.replaceDirectMethodHandleDesc(desc);
			if (replacement != desc) return replacement;
		}
		return desc;
	}

	static <T> DynamicConstantDesc<T> rewriteDynamicConstantDesc(DynamicConstantDesc<T> desc) {
		return DynamicConstantDesc.ofNamed(
				rewriteDirectMethodHandleDesc(desc.bootstrapMethod()),
				desc.constantName(),
				desc.constantType(),
				desc.bootstrapArgsList()
						.stream()
						.map(ClassFileRewriter::rewriteConstantDesc)
						.toArray(ConstantDesc[]::new)
		);
	}

	static LoadableConstantEntry rewriteLoadableConstantEntry(ConstantPoolBuilder poolBuilder, LoadableConstantEntry entry) {
		return switch (entry) {
			case ConstantDynamicEntry cd -> poolBuilder.constantDynamicEntry(
					rewriteBootstrapMethodEntry(poolBuilder, cd.bootstrap()),
					cd.nameAndType()
			);
			case MethodHandleEntry mh -> poolBuilder.methodHandleEntry(rewriteDirectMethodHandleDesc(mh.asSymbol()));
			default -> entry;
		};
	}

	static BootstrapMethodEntry rewriteBootstrapMethodEntry(ConstantPoolBuilder poolBuilder, BootstrapMethodEntry entry) {
		return poolBuilder.bsmEntry(
				rewriteMethodHandleEntry(poolBuilder, entry.bootstrapMethod()),
				entry.arguments()
						.stream()
						.map(arg -> rewriteLoadableConstantEntry(poolBuilder, arg))
						.toList()
		);
	}

	static InvokeInstruction rewriteInvokeInstruction(ConstantPoolBuilder poolBuilder, InvokeInstruction i) {
		for (var invocation : AgentData.invocationSet()) {
			var replacement = invocation.replaceInvocation(poolBuilder, i);
			if (replacement != i) return replacement;
		}
		return i;
	}

	static InvokeDynamicInstruction rewriteInvokeDynamicInstruction(ConstantPoolBuilder poolBuilder, InvokeDynamicInstruction i) {
		var invokedynamic = i.invokedynamic();
		var bootstrap = invokedynamic.bootstrap();
		var replacement = rewriteBootstrapMethodEntry(poolBuilder, bootstrap);
		if (bootstrap == replacement)
			return i;
		return InvokeDynamicInstruction.of(poolBuilder.invokeDynamicEntry(
				replacement,
				invokedynamic.nameAndType()
		));
	}

	static ConstantInstruction.LoadConstantInstruction rewriteLDC(ConstantPoolBuilder poolBuilder, ConstantInstruction.LoadConstantInstruction i) {
		var entry = i.constantEntry();
		var replacement = rewriteLoadableConstantEntry(poolBuilder, entry);
		if (entry == replacement)
			return i;
		return ConstantInstruction.ofLoad(
				i.opcode(),
				replacement
		);
	}

	static {
		try {
			MethodHandles.lookup().ensureInitialized(AgentHooks.class);
		} catch (IllegalAccessException ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}
}
