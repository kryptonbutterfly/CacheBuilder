package de.tinycodecrank.asm.cache;

import static de.tinycodecrank.math.utils.range.Range.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import de.tinycodecrank.cache.CacheConstants;
import de.tinycodecrank.monads.opt.Opt;

public class ComponentWriter implements Opcodes
{
	static record CacheSpec(Type cacheType, int capacity)
	{}
	
	static CacheSpec getCacheSpecFromAnnotation(AnnotationNode node) throws ClassNotFoundException
	{
		final var	cacheField		= "cache";
		final var	capacityField	= "capacity";
		var			cacheType		= Type.getType(CacheConstants.DEFAULT_CACHE);
		int			capacity		= CacheConstants.DEFAULT_CAPACITY;
		
		for (int index : range(0, node.values.size(), 2))
		{
			final var	name	= node.values.get(index);
			final var	value	= node.values.get(index + 1);
			if (cacheField.equals(name) && value instanceof Type)
			{
				cacheType = (Type) value;
			}
			else if (capacityField.equals(name) && value instanceof Integer)
			{
				capacity = (int) value;
			}
		}
		final var cacheClass = Class.forName(cacheType.getClassName());
		
		final boolean hasCapacity = Arrays.stream(cacheClass.getConstructors())
			.flatMap(c -> Arrays.stream(c.getParameters()))
			.anyMatch(param -> int.class == param.getType());
		
		if (!hasCapacity)
		{
			capacity = -1;
		}
		return new CacheSpec(cacheType, capacity);
	}
	
	static MethodNode getOrCreateInitializer(ClassNode node, boolean isStatic)
	{
		final var	INIT		= isStatic ? "<clinit>" : "<init>";
		final var	matches		= findMethod(node, INIT);
		int			visibility	= isStatic
			? 0
				: node.access & ACC_PUBLIC | node.access & ACC_PROTECTED | node.access & ACC_PRIVATE;
		
		return Opt.of(matches).filter(m -> !m.isEmpty()).map(m -> m.get(0)).get(() ->
		{
			MethodNode init = new MethodNode((isStatic ? ACC_STATIC : 0) | visibility, INIT, "()V", null, null);
			node.methods.add(0, init);
			if (!isStatic)
			{
				LocalVariableNode instance = new LocalVariableNode(
					"this",
					node.signature,
					null,
					new LabelNode(),
					new LabelNode(),
					0);
				init.localVariables.add(instance);
				init.instructions.add(instance.start);
				init.instructions.add(new VarInsnNode(ALOAD, instance.index));
				init.instructions.add(new MethodInsnNode(INVOKESPECIAL, node.superName, "<init>", fDesc("V")));
				init.instructions.add(new InsnNode(RETURN));
				init.instructions.add(instance.end);
			}
			else
			{
				init.instructions.add(new InsnNode(RETURN));
			}
			return init;
		});
	}
	
	private static ArrayList<MethodNode> findMethod(ClassNode node, String methodName)
	{
		return Opt.of(node.methods)
			.map(
				methods -> methods.stream()
					.filter(m -> m.name.equals(methodName))
					.collect(Collectors.toCollection(ArrayList::new)))
			.get(ArrayList::new);
	}
	
	static Opt<LocalVariableNode> findLocal(MethodNode node, String name)
	{
		return Opt.of(node.localVariables)
			.map(
				vars -> vars.stream()
					.filter(l -> l.name.equals(name))
					.findAny())
			.flatmap(Opt::convert);
	}
	
	static String toDesc(Class<?> clazz)
	{
		return toDesc(toInternal(clazz));
	}
	
	static String toDesc(String name)
	{
		final var sb = new StringBuilder();
		while (name.endsWith("[]"))
		{
			sb.append("[");
			name = name.substring(0, name.length() - 2);
		}
		return "%sL%s;".formatted(sb, name);
	}
	
	static String toInternal(Class<?> clazz)
	{
		final var	name		= new StringBuilder(clazz.getPackageName()).append(".");
		var			innerHost	= clazz;
		var			outerHost	= innerHost;
		while ((outerHost = innerHost.getNestHost()) != innerHost)
		{
			innerHost = outerHost;
			name.append(outerHost.getSimpleName()).append("$");
		}
		return toInternal(name.append(clazz.getSimpleName()).toString());
	}
	
	private static String toInternal(String clazz)
	{
		return clazz.replace('.', '/');
	}
	
	static String fDesc(Object ret, Object... params)
	{
		final Function<Object, String> toDescriptor = obj -> obj instanceof Class<?>
			? toDesc((Class<?>) obj)
				: (String) obj;
		
		StringBuilder sb = new StringBuilder("(");
		Arrays.stream(params).map(toDescriptor).forEach(sb::append);
		return sb.append(")").append(toDescriptor.apply(ret)).toString();
	}
	
	static String generateUniqueMethodName(ClassNode node, String name)
	{
		return generateUnique(ComponentWriter::isUniqueMethodName, node, name);
	}
	
	static String generateUniqueFieldName(ClassNode node, String name)
	{
		return generateUnique(ComponentWriter::isUniqueFieldName, node, name);
	}
	
	private static String generateUnique(BiPredicate<ClassNode, String> unique, ClassNode node, String name)
	{
		String	result;
		int		index	= 0;
		do
			result = name + Integer.toHexString(index++);
		while (!unique.test(node, result));
		return result;
	}
	
	private static boolean isUniqueMethodName(ClassNode node, String name)
	{
		return node.methods == null || !node.methods.stream().map(m -> m.name).anyMatch(name::equals);
	}
	
	private static boolean isUniqueFieldName(ClassNode node, String name)
	{
		return node.fields == null || !node.fields.stream().map(m -> m.name).anyMatch(name::equals);
	}
	
	static void addConvertToPrimitive(Type type, InsnList insn, LocalVariableNode element)
	{
		final var desc = fDesc(element.desc);
		switch (type.getSort())
		{
			case Type.BOOLEAN -> toPrimitive(Boolean.class, "booleanValue", insn, desc);
			case Type.CHAR -> toPrimitive(Character.class, "charValue", insn, desc);
			case Type.BYTE -> toPrimitive(Byte.class, "byteValue", insn, desc);
			case Type.SHORT -> toPrimitive(Short.class, "shortValue", insn, desc);
			case Type.INT -> toPrimitive(Integer.class, "intValue", insn, desc);
			case Type.FLOAT -> toPrimitive(Float.class, "floatValue", insn, desc);
			case Type.LONG -> toPrimitive(Long.class, "longValue", insn, desc);
			case Type.DOUBLE -> toPrimitive(Double.class, "doubleValue", insn, desc);
			case Type.ARRAY, Type.OBJECT -> insn.add(new TypeInsnNode(CHECKCAST, type.getInternalName()));
			case Type.METHOD -> throw new IllegalStateException("Methods are not supported yet!");
			default -> throw new IllegalStateException("Parameter of type: " + type.getSort() + " are not supported");
		}
	}
	
	private static void toPrimitive(Class<?> clazz, String castMethod, InsnList instructions, String desc)
	{
		String owner = toInternal(clazz);
		instructions.add(new TypeInsnNode(CHECKCAST, owner));
		instructions.add(new MethodInsnNode(INVOKEVIRTUAL, owner, castMethod, desc));
	}
}