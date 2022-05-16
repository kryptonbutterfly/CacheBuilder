package de.tinycodecrank.asm.cache;

import static de.tinycodecrank.math.utils.range.Range.range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import de.tinycodecrank.cache.CacheConstants;
import de.tinycodecrank.collections.data.Tuple;
import de.tinycodecrank.monads.Opt;

public class ComponentWriter implements Opcodes
{
	public static Tuple<Type, Integer> getCacheSpecFromAnnotation(AnnotationNode node) throws ClassNotFoundException
	{
		String cacheField = "cache";
		String capacityField = "capacity";
		Type cacheType = Type.getType(CacheConstants.DEFAULT_CACHE);
		int capacity = CacheConstants.DEFAULT_CAPACITY;
		int count = node.values.size() / 2;
		for (int i : range(count))
		{
			int index = i * 2;
			Object name = node.values.get(index);
			Object value = node.values.get(index + 1);
			if (cacheField.equals(name))
			{
				if (value instanceof Type)
				{
					cacheType = (Type) value;
				}
			}
			else if (capacityField.equals(name))
			{
				if (value instanceof Integer)
				{
					capacity = (int) value;
				}
			}
		}
		boolean hasCapacity = false;
		final var cacheClass = Class.forName(cacheType.getClassName());
		outer: for (final var constr : cacheClass.getConstructors())
		{
			for (final var param : constr.getParameters())
			{
				if (int.class == param.getType())
				{
					hasCapacity = true;
					break outer;
				}
			}
		}
		if (!hasCapacity)
		{
			capacity = -1;
		}

		return new Tuple<>(cacheType, capacity);
	}

	public static MethodNode getOrCreateInitializer(ClassNode node, boolean isStatic)
	{
		final var INIT = isStatic ? "<clinit>" : "<init>";
		final var matches = findMethod(node, INIT);
		int visibility = isStatic ? 0
				: node.access & ACC_PUBLIC | node.access & ACC_PROTECTED | node.access & ACC_PRIVATE;

		return Opt.of(matches).filter(m -> !m.isEmpty()).map(m -> m.get(0)).get(() ->
		{
			MethodNode init = new MethodNode((isStatic ? ACC_STATIC : 0) | visibility, INIT, "()V", null, null);
			node.methods.add(0, init);
			if (!isStatic)
			{
				LocalVariableNode instance = new LocalVariableNode("this", node.signature, null, new LabelNode(),
						new LabelNode(), 0);
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
		final var matches = new ArrayList<MethodNode>();
		if (node.methods != null)
		{
			for (final var method : node.methods)
			{
				if (methodName.equals(method.name))
				{
					matches.add(method);
				}
			}
		}
		return matches;
	}

	public static Opt<LocalVariableNode> findLocal(MethodNode node, String name)
	{
		if (node.localVariables != null)
		{
			for (final var local : node.localVariables)
			{
				if (name.equals(local.name))
				{
					return Opt.of(local);
				}
			}
		}
		return Opt.empty();
	}

	public static String toDesc(Class<?> clazz)
	{
		return toDesc(toInternal(clazz));
	}

	public static String toDesc(String name)
	{
		name = "L" + name;
		while (name.endsWith("[]"))
		{
			name = "[" + name.substring(0, name.length() - 2);
		}
		return name + ";";
	}

	public static String toInternal(Class<?> clazz)
	{
		return toInternal(clazz.getCanonicalName());
	}

	private static String toInternal(String clazz)
	{
		return clazz.replace('.', '/');
	}

	public static String fDesc(Object ret, Object... params)
	{
		final Function<Object, String> toDescriptor = (obj) ->
		{
			if (obj instanceof Class<?>)
			{
				return toDesc((Class<?>) obj);
			}
			else
			{
				return (String) obj;
			}
		};

		StringBuilder sb = new StringBuilder();
		sb.append("(");
		Arrays.stream(params).map(toDescriptor).forEach(sb::append);
		sb.append(")");
		sb.append(toDescriptor.apply(ret));
		return sb.toString();
	}

	public static String generateUniqueName(Iterable<?> iterable, String name)
	{
		int index = 0;
		for (final var element : iterable)
		{
			if (element instanceof MethodNode)
			{
				if (((MethodNode) element).name.startsWith(name))
				{
					index++;
				}
			}
			else if (element instanceof FieldNode)
			{
				if (((FieldNode) element).name.startsWith(name))
				{
					index++;
				}
			}
			else if (element instanceof LocalVariableNode)
			{
				if (((LocalVariableNode) element).name.startsWith(name))
				{
					index++;
				}
			}
		}
		return name + Integer.toHexString(index);
	}
}