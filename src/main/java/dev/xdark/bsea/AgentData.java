package dev.xdark.bsea;

import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Set;

import static dev.xdark.bsea.MethodInvocation.invokeVirtual;
import static dev.xdark.bsea.MethodInvocation.staticCall;

public final class AgentData {
	private static final Set<MethodInvocation> INVOCATIONS = Set.of(
			staticCall(System.class, "exit", methodDesc(void.class, int.class)),
			invokeVirtual(Runtime.class, "exit", methodDesc(void.class, int.class)),
			invokeVirtual(Runtime.class, "halt", methodDesc(void.class, int.class)),

			invokeVirtual(Class.class, "getDeclaredMethod", methodDesc(Method.class, String.class, Class[].class)),
			invokeVirtual(Class.class, "getMethod", methodDesc(Method.class, String.class, Class[].class)),
			invokeVirtual(Class.class, "getDeclaredMethods", methodDesc(Method[].class)),
			invokeVirtual(Class.class, "getMethods", methodDesc(Method[].class)),

			invokeVirtual(MethodHandles.Lookup.class, "findVirtual", methodDesc(MethodHandle.class, Class.class, String.class, MethodType.class)),
			invokeVirtual(MethodHandles.Lookup.class, "findStatic", methodDesc(MethodHandle.class, Class.class, String.class, MethodType.class))
	);

	private AgentData() {
	}

	static Set<MethodInvocation> invocationSet() {
		return INVOCATIONS;
	}

	private static MethodTypeDesc methodDesc(Class<?> returnType, Class<?>... parameterTypes) {
		return MethodType.methodType(returnType, parameterTypes).describeConstable().orElseThrow();
	}
}
