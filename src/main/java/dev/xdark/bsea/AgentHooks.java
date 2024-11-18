package dev.xdark.bsea;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

@SuppressWarnings("unused")
public final class AgentHooks {

	private AgentHooks() {
	}

	public static void exit(int code) {
		System.out.printf("System::exit(%d) called by %s%n", code, callerFrame());
		System.exit(code);
	}

	public static void exit(Runtime runtimeInstance, int code) {
		System.out.printf("Runtime::exit(%d) called by %s%n", code, callerFrame());
		runtimeInstance.exit(code);
	}

	public static void halt(Runtime runtimeInstance, int code) {
		System.out.printf("Runtime::halt(%d) called by %s%n", code, callerFrame());
		runtimeInstance.halt(code);
	}

	public static Method getDeclaredMethod(Class<?> owner, String name, Class<?>... parameterTypes) throws ReflectiveOperationException {
		return filterMethod(owner.getDeclaredMethod(name, parameterTypes));
	}

	public static Method getMethod(Class<?> owner, String name, Class<?>... parameterTypes) throws ReflectiveOperationException {
		return filterMethod(owner.getMethod(name, parameterTypes));
	}

	public static Method[] getDeclaredMethods(Class<?> owner) throws ReflectiveOperationException {
		return filterMethods(owner.getDeclaredMethods());
	}

	public static Method[] getMethods(Class<?> owner) throws ReflectiveOperationException {
		return filterMethods(owner.getMethods());
	}

	public static MethodHandle findVirtual(MethodHandles.Lookup caller, Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
		return filterMethodHandle(caller, caller.findVirtual(owner, name, type));
	}

	public static MethodHandle findStatic(MethodHandles.Lookup caller, Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
		return filterMethodHandle(caller, caller.findStatic(owner, name, type));
	}

	private static MethodHandle filterMethodHandle(MethodHandles.Lookup lookup, MethodHandle methodHandle) throws ReflectiveOperationException {
		var info = lookup.revealDirect(methodHandle);
		for (var invocation : AgentData.invocationSet()) {
			if (invocation.matchesMethodHandle(info))
				return invocation.toMethodHandle();
		}
		return methodHandle;
	}

	private static Method filterMethod(Method method) throws ReflectiveOperationException {
		for (var invocation : AgentData.invocationSet()) {
			if (invocation.matchesMethod(method))
				return invocation.toMethod();
		}
		return method;
	}

	private static Method[] filterMethods(Method[] methods) throws ReflectiveOperationException {
		for (int i = 0; i < methods.length; i++) {
			methods[i] = filterMethod(methods[i]);
		}
		return methods;
	}

	private static StackWalker.StackFrame callerFrame() {
		return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
				.walk(s -> s.skip(2L).findFirst().orElseThrow());
	}
}
