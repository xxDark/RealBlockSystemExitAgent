package dev.xdark.bsea;

import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

@SuppressWarnings("preview")
public record MethodInvocation(Opcode opcode, ClassDesc owner, String name, MethodTypeDesc desc, boolean isInterface) {
	public MethodInvocation {
		switch (opcode) {
			case INVOKESTATIC, INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE -> {
			}
			default -> throw new IllegalStateException("Illegal opcode %s".formatted(opcode));
		}
	}

	static MethodInvocation call(Opcode opcode, Class<?> owner, String name, MethodTypeDesc desc, boolean isInterface) {
		return new MethodInvocation(
				opcode,
				owner.describeConstable().orElseThrow(),
				name,
				desc,
				isInterface
		);
	}

	static MethodInvocation staticCall(Class<?> owner, String name, MethodTypeDesc desc) {
		return call(Opcode.INVOKESTATIC, owner, name, desc, owner.isInterface());
	}

	static MethodInvocation invokeVirtual(Class<?> owner, String name, MethodTypeDesc desc) {
		return call(Opcode.INVOKEVIRTUAL, owner, name, desc, false);
	}

	boolean matchesInvoke(InvokeInstruction e) {
		if (opcode != e.opcode())
			return false;
		if (isInterface != e.isInterface())
			return false;
		if (!e.name().equalsString(name))
			return false;
		if (!owner.equals(e.owner().asSymbol()))
			return false;
		return desc.equals(e.typeSymbol());
	}

	boolean matchesMethod(Method m) {
		var declaringClass = m.getDeclaringClass();
		if (isInterface != declaringClass.isInterface())
			return false;
		if (!owner.equals(declaringClass.describeConstable().orElseThrow()))
			return false;
		if (!name.equals(m.getName()))
			return false;
		var desc = this.desc;
		if (desc.returnType() != m.getReturnType().describeConstable().orElseThrow())
			return false;
		if (desc.parameterCount() != m.getParameterCount())
			return false;
		var iterator = desc.parameterList().iterator();
		for (var parameterType : m.getParameterTypes()) {
			if (iterator.next() != parameterType.describeConstable().orElseThrow())
				return false;
		}
		return true;
	}

	boolean matchesMethodHandle(MethodHandleInfo info) {
		var declaringClass = info.getDeclaringClass();
		if (isInterface != declaringClass.isInterface())
			return false;
		if (methodHandleKind().refKind != info.getReferenceKind())
			return false;
		if (!name.equals(info.getName()))
			return false;
		if (!owner.equals(declaringClass.describeConstable().orElseThrow()))
			return false;
		return desc.equals(info.getMethodType().describeConstable().orElseThrow());
	}

	boolean matchesMethodHandle(DirectMethodHandleDesc e) {
		if (methodHandleKind() != e.kind())
			return false;
		if (!owner.equals(e.owner()))
			return false;
		if (!name.equals(e.methodName()))
			return false;
		return desc.equals(e.invocationType());
	}

	DirectMethodHandleDesc replaceDirectMethodHandleDesc(DirectMethodHandleDesc desc) {
		return matchesMethodHandle(desc) ? toMethodHandleDesc() : desc;
	}

	InvokeInstruction replaceInvocation(ConstantPoolBuilder poolBuilder, InvokeInstruction i) {
		if (matchesInvoke(i))
			return toInvocation(poolBuilder);
		return i;
	}

	InvokeInstruction toInvocation(ConstantPoolBuilder poolBuilder) {
		return InvokeInstruction.of(Opcode.INVOKESTATIC, poolBuilder.methodRefEntry(agentClassDesc(), name, callDesc()));
	}

	DirectMethodHandleDesc toMethodHandleDesc() {
		return MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, agentClassDesc(), name, callDesc());
	}

	MethodHandle toMethodHandle() throws ReflectiveOperationException {
		return toMethodHandleDesc().resolveConstantDesc(MethodHandles.lookup());
	}

	Method toMethod() throws ReflectiveOperationException {
		var parameterList = desc.parameterList();
		Class<?>[] parameterArray = new Class[parameterList.size()];
		var iterator = parameterList.iterator();
		for (int i = 0; i < parameterArray.length; i++) {
			parameterArray[i] = iterator.next().resolveConstantDesc(MethodHandles.lookup());
		}
		return AgentHooks.class.getDeclaredMethod(name, parameterArray);
	}

	private MethodTypeDesc callDesc() {
		var desc = this.desc;
		if (opcode != Opcode.INVOKESTATIC) {
			desc = desc.insertParameterTypes(0, owner);
		}
		return desc;
	}

	private DirectMethodHandleDesc.Kind methodHandleKind() {
		return switch (opcode) {
			case INVOKEVIRTUAL -> DirectMethodHandleDesc.Kind.VIRTUAL;
			case INVOKESPECIAL -> {
				if ("<init>".equals(name)) {
					yield DirectMethodHandleDesc.Kind.CONSTRUCTOR;
				}
				if (isInterface) {
					yield DirectMethodHandleDesc.Kind.INTERFACE_SPECIAL;
				}
				yield DirectMethodHandleDesc.Kind.SPECIAL;
			}
			case INVOKESTATIC -> {
				if (isInterface) {
					yield DirectMethodHandleDesc.Kind.INTERFACE_STATIC;
				}
				yield DirectMethodHandleDesc.Kind.STATIC;
			}
			case INVOKEINTERFACE -> DirectMethodHandleDesc.Kind.INTERFACE_VIRTUAL;
			default -> throw new AssertionError();
		};
	}

	private static ClassDesc agentClassDesc() {
		return AgentHooks.class.describeConstable().orElseThrow();
	}
}
