package de.tinycodecrank.agent;

import static de.tinycodecrank.agent.SimpleLogging.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import de.tinycodecrank.asm.cache.DecoratorCache;
import de.tinycodecrank.cache.Cache;

public class ClassTransformer implements ClassFileTransformer
{
	@Override
	public byte[] transform(
		ClassLoader loader,
		String className,
		Class<?> classBeingRedefined,
		ProtectionDomain protectionDomain,
		byte[] classfileBuffer)
		throws IllegalClassFormatException
	{
		LOGGER.logf("starting transformer on class: %s\n", className);
		ClassReader reader = new ClassReader(classfileBuffer);
		return DecoratorCache.adapt(Cache.class, reader).map(cn ->
		{
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			cn.accept(cw);
			LOGGER.logf("writing %s.class to byte[]\n", className);
			return cw.toByteArray();
		}).get(() -> classfileBuffer);
	}
}