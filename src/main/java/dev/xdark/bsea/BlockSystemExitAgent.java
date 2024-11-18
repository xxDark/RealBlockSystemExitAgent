package dev.xdark.bsea;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.security.ProtectionDomain;

public final class BlockSystemExitAgent {

	public static void premain(String agentArgs, Instrumentation inst) throws Exception {
		MethodHandles.lookup().ensureInitialized(ClassFileRewriter.class);

		inst.addTransformer(new ClassFileTransformer() {
			@Override
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				if (loader == null || loader == ClassLoader.getPlatformClassLoader())
					return null;
				return ClassFileRewriter.patchBytecode(classfileBuffer);
			}
		}, true);
	}
}
