package de.tinycodecrank.asm.cache;

import static de.tinycodecrank.asm.cache.ComponentWriter.fDesc;
import static de.tinycodecrank.asm.cache.ComponentWriter.findLocal;
import static de.tinycodecrank.asm.cache.ComponentWriter.generateUniqueName;
import static de.tinycodecrank.asm.cache.ComponentWriter.getCacheSpecFromAnnotation;
import static de.tinycodecrank.asm.cache.ComponentWriter.getOrCreateInitializer;
import static de.tinycodecrank.asm.cache.ComponentWriter.toDesc;
import static de.tinycodecrank.asm.cache.ComponentWriter.toInternal;
import static de.tinycodecrank.math.utils.range.ArrayRange.range;
import static de.tinycodecrank.math.utils.range.ListRange.range;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import de.tinycodecrank.cache.Cache;
import de.tinycodecrank.collections.data.Tuple;
import de.tinycodecrank.io.FileSystemUtils;
import de.tinycodecrank.monads.Opt;

public class DecoratorCache implements Opcodes
{
	public static void main(String[] args)
	{
		File binDir;
		if (args.length > 0)
		{
			binDir = new File(args[0]);
		}
		else
		{
			binDir = new File(".");
		}
		if (binDir.isDirectory())
		{
			new FileSystemUtils().performRecursive(binDir, f ->
			{
				if (f.isFile())
				{
					return adapt(Cache.class, f);
				}
				return true;
			}, Boolean::logicalAnd);

		}
		else
		{
			adapt(Cache.class, binDir);
		}
	}

	/**
	 * @param annotation
	 *            the annotation to search for
	 * @param reader
	 *            the class reader
	 * @return the adapted Opt<ClassNode> or Opt.empty() if nothing changed.
	 */
	public static Opt<ClassNode> adapt(Class<?> annotation, ClassReader reader)
	{
		final var annotationDesc = toDesc(annotation);
		final var classNode = new ClassNode();
		reader.accept(classNode, 0);
		final var toCache = new ArrayList<Tuple<MethodNode, AnnotationNode>>();

		methods: for (final var method : classNode.methods)
		{
			if (method.invisibleAnnotations != null)
			{
				for (final var a : method.invisibleAnnotations)
				{
					if (a.desc.equals(annotationDesc))
					{
						method.invisibleAnnotations.remove(a);
						toCache.add(new Tuple<>(method, a));
						continue methods;
					}
				}
			}
			if (method.visibleAnnotations != null)
			{
				for (final var a : method.visibleAnnotations)
				{
					if (a.desc.equals(annotationDesc))
					{
						method.visibleAnnotations.remove(a);
						toCache.add(new Tuple<>(method, a));
						break;
					}
				}
			}
		}
		for (final var t : toCache)
		{
			createCachedVersion(classNode, t.first(), t.second());
		}
		if (toCache.isEmpty())
		{
			return Opt.empty();
		}
		else
		{
			return Opt.of(classNode);
		}
	}

	private static boolean adapt(Class<?> annotation, File classFile)
	{
		try (InputStream iStream = new FileInputStream(classFile))
		{
			ClassReader reader = new ClassReader(iStream);
			return adapt(annotation, reader).filter(classNode -> compile(classFile, classNode)).isPresent();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return false;
	}

	private static void createCachedVersion(ClassNode classNode, MethodNode original, AnnotationNode annotation)
	{
		try
		{
			final var SPEC = getCacheSpecFromAnnotation(annotation);
			final var originalName = original.name;

			original.name = generateUniqueName(classNode.methods, "ω" + original.name + "$");
			MethodNode addWrapped = generateWrapped(classNode, original, originalName);

			FieldNode cacheField = createCache(classNode, addWrapped, SPEC);

			MethodNode cached = generateCached(classNode, original, cacheField, originalName,
					SPEC.first().getInternalName());

			transferAnnotations(original, cached);

			original.access = original.access & ~ACC_PUBLIC & ~ACC_PROTECTED | ACC_PRIVATE | ACC_FINAL;
		}
		catch (ClassNotFoundException e)
		{
			e.printStackTrace();
		}
	}

	private static void transferAnnotations(MethodNode origin, MethodNode target)
	{
		BiFunction<List<AnnotationNode>, List<AnnotationNode>, List<AnnotationNode>> copy = (orig, targ) ->
		{
			if (targ == null)
			{
				targ = new ArrayList<>();
			}
			if (orig != null)
			{

				for (Iterator<AnnotationNode> i = orig.iterator(); i.hasNext();)
				{
					final var annotation = i.next();
					i.remove();
					targ.add(annotation);
				}
			}
			return targ;
		};

		target.visibleAnnotations = copy.apply(origin.visibleAnnotations, target.visibleAnnotations);
		target.invisibleAnnotations = copy.apply(origin.invisibleAnnotations, target.invisibleAnnotations);
	}

	private static MethodNode generateWrapped(ClassNode classNode, MethodNode original, String originalName)
	{
		int access = original.access & ~ACC_PUBLIC & ~ACC_PROTECTED | ACC_PRIVATE | ACC_FINAL;
		Type returnType = Type.getReturnType(original.desc);
		final var wrapped = new MethodNode(access, generateUniqueName(classNode.methods, "¢" + originalName),
				fDesc(returnType.getDescriptor(), Object[].class), null, null);
		classNode.methods.add(wrapped);

		final var start = new LabelNode();
		final var end = new LabelNode();
		int index = 0;

		LocalVariableNode inst = null;
		if (!isStatic(original.access))
		{
			inst = new LocalVariableNode("this", toDesc(classNode.name), null, start, end, index++);
			wrapped.localVariables.add(inst);
		}
		final var array = new LocalVariableNode("key", toDesc(Object[].class), null, start, end, index++);
		wrapped.localVariables.add(array);

		// setup Local variables
		final var offset = isStatic(original.access) ? 0 : -1;
		int argsCount = Type.getArgumentTypes(original.desc).length;
		final var params = new LocalVariableNode[argsCount];
		for (final var element : range(original.localVariables))
		{
			if (element.element().name.equals("this"))
			{
				continue;
			}
			final var loc = element.element();
			final var i = element.index() + offset;
			if (i == argsCount)
			{
				break;
			}
			params[i] = new LocalVariableNode(loc.name, loc.desc, null, new LabelNode(), end, index++);
			wrapped.localVariables.add(params[i]);
		}

		wrapped.instructions.add(start);
		if (!isStatic(original.access))
		{
			wrapped.instructions.add(new VarInsnNode(ALOAD, inst.index));
		}
		// store into local variables
		for (final var element : range(params))
		{
			wrapped.instructions.add(new VarInsnNode(ALOAD, array.index));
			Type type = Type.getType(element.element().desc);
			wrapped.instructions.add(new LdcInsnNode(element.index()));
			wrapped.instructions.add(new InsnNode(AALOAD));
			// adapt for primitives and cast
			BiConsumer<Class<?>, String> toPrimitive = (c, method) ->
			{
				String owner = toInternal(c);
				wrapped.instructions.add(new TypeInsnNode(CHECKCAST, owner));
				wrapped.instructions
						.add(new MethodInsnNode(INVOKEVIRTUAL, owner, method, fDesc(element.element().desc)));
			};

			switch (type.getSort())
			{
				case Type.BOOLEAN: // 1
					toPrimitive.accept(Boolean.class, "booleanValue");
					break;
				case Type.CHAR: // 2
					toPrimitive.accept(Character.class, "charValue");
					break;
				case Type.BYTE: // 3
					toPrimitive.accept(Byte.class, "byteValue");
					break;
				case Type.SHORT: // 4
					toPrimitive.accept(Short.class, "shortValue");
					break;
				case Type.INT: // 5
					toPrimitive.accept(Integer.class, "intValue");
					break;
				case Type.FLOAT: // 6
					toPrimitive.accept(Float.class, "floatValue");
					break;
				case Type.LONG: // 7
					toPrimitive.accept(Long.class, "longValue");
					break;
				case Type.DOUBLE: // 8
					toPrimitive.accept(Double.class, "doubleValue");
					break;
				case Type.ARRAY: // 9
				case Type.OBJECT: // 10
					wrapped.instructions.add(new TypeInsnNode(CHECKCAST, type.getInternalName()));
					break;
				case Type.METHOD: // 11
					throw new IllegalStateException("Methods are not supported yet!");
				case Type.VOID: // 0
				default:
					throw new IllegalStateException("Parameter of type: " + type.getSort() + " are not supported");
			}
			// store value
			wrapped.instructions.add(new VarInsnNode(type.getOpcode(ISTORE), element.element().index));
			wrapped.instructions.add(element.element().start);
		}

		if (inst != null)
		{
			wrapped.instructions.add(new VarInsnNode(ALOAD, inst.index));
		}
		// push to stack from local variables
		for (var element : range(params).element())
		{
			Type type = Type.getType(element.desc);
			wrapped.instructions.add(new VarInsnNode(type.getOpcode(ILOAD), element.index));
		}
		wrapped.instructions.add(new MethodInsnNode(isStatic(original.access) ? INVOKESTATIC : INVOKESPECIAL,
				classNode.name, original.name, original.desc));
		wrapped.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));
		wrapped.instructions.add(end);
		return wrapped;
	}

	@SuppressWarnings("deprecation")
	private static FieldNode createCache(ClassNode classNode, MethodNode function, Tuple<Type, Integer> SPEC)
	{
		boolean isStatic = isStatic(function.access);
		// calculate field access modifiers
		int access = ACC_PRIVATE | ACC_FINAL;
		if (isStatic)
		{
			access |= ACC_STATIC;
		}

		// calculate field name;
		String fieldName = function.name + "$";
		int index = 0;

		for (FieldNode field : classNode.fields)
		{
			if (field.name.startsWith(fieldName))
			{
				index++;
			}
		}
		FieldNode field = new FieldNode(access, fieldName + index, SPEC.first().getDescriptor(), null, null);
		classNode.fields.add(field);

		MethodNode init = getOrCreateInitializer(classNode, isStatic);
		var record = false;
		final var buffer = new ArrayList<AbstractInsnNode>();
		for (Iterator<AbstractInsnNode> i = init.instructions.iterator(); i.hasNext();)
		{
			final var node = i.next();
			if (record || node.getOpcode() == RETURN)
			{
				record = true;
				i.remove();
				buffer.add(node);
			}
		}

		LocalVariableNode instance = null;
		if (!isStatic)
		{
			instance = findLocal(init, "this").get();
			init.instructions.add(new VarInsnNode(ALOAD, instance.index));
		}
		init.instructions.add(new TypeInsnNode(NEW, SPEC.first().getInternalName()));
		init.instructions.add(new InsnNode(DUP));
		if (!isStatic)
		{
			init.instructions.add(new VarInsnNode(ALOAD, instance.index));
		}
		init.instructions.add(new InvokeDynamicInsnNode("apply",
				isStatic ? fDesc(Function.class) : fDesc(Function.class, toDesc(classNode.name)),
				new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
						"(Ljava/lang/invoke/MethodHandles$Lookup;" + toDesc(String.class)
								+ "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
						false),
				Type.getMethodType(fDesc(Object.class, Object.class)),
				new Handle(isStatic ? H_INVOKESTATIC : H_INVOKESPECIAL, classNode.name, function.name, function.desc,
						false),
				Type.getMethodType(function.desc)));
		String invokeDesc;
		if (SPEC.second() > 0)
		{
			init.instructions.add(new LdcInsnNode(SPEC.second()));
			invokeDesc = fDesc("V", Function.class, "I");
		}
		else
		{
			invokeDesc = fDesc("V", Function.class);
		}
		init.instructions.add(new MethodInsnNode(INVOKESPECIAL, SPEC.first().getInternalName(), "<init>", invokeDesc));
		init.instructions
				.add(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, classNode.name, field.name, field.desc));
		buffer.forEach(init.instructions::add);
		return field;
	}

	private static MethodNode generateCached(ClassNode classNode, MethodNode original, FieldNode cacheField,
			String originalName, String CACHE_TYPE)
	{
		MethodNode cached = new MethodNode(original.access, originalName, original.desc, null, null);
		classNode.methods.add(cached);
		final var start = new LabelNode();
		final var end = new LabelNode();
		var array = new LabelNode();

		var index = 0;
		LocalVariableNode inst = null;
		if (!isStatic(original.access))
		{
			inst = new LocalVariableNode("this", toDesc(classNode.name), null, start, end, index++);
			cached.localVariables.add(inst);
		}

		// generate locals from original
		int offset = isStatic(original.access) ? 0 : -1;
		int argCount = Type.getArgumentTypes(original.desc).length;
		final var params = new LocalVariableNode[argCount];
		for (final var element : range(original.localVariables))
		{
			if (element.element().name.equals("this"))
			{
				continue;
			}
			final var loc = element.element();
			final var i = element.index() + offset;
			if (i == argCount)
			{
				break;
			}
			params[i] = new LocalVariableNode(loc.name, loc.desc, null, start, end, index++);
			cached.localVariables.add(params[i]);
		}

		LocalVariableNode argArray = new LocalVariableNode("key", toDesc(Object[].class), null, array, end, index++);
		cached.localVariables.add(argArray);

		cached.instructions.add(start);
		cached.instructions.add(new LdcInsnNode(params.length));
		cached.instructions.add(new TypeInsnNode(ANEWARRAY, toInternal(Object.class)));

		for (final var element : range(params))
		{
			final var loc = element.element();
			final var arrayIndex = element.index();
			cached.instructions.add(new InsnNode(DUP));
			Type type = Type.getType(loc.desc);
			cached.instructions.add(new LdcInsnNode(arrayIndex));
			cached.instructions.add(new VarInsnNode(type.getOpcode(ILOAD), loc.index));
			Consumer<Class<?>> toObject = (c) -> cached.instructions
					.add(new MethodInsnNode(INVOKESTATIC, toInternal(c), "valueOf", fDesc(c, loc.desc)));
			switch (type.getSort())
			{
				case Type.BOOLEAN: // 1
					toObject.accept(Boolean.class);
					break;
				case Type.CHAR: // 2
					toObject.accept(Character.class);
					break;
				case Type.BYTE: // 3
					toObject.accept(Byte.class);
					break;
				case Type.SHORT: // 4
					toObject.accept(Short.class);
					break;
				case Type.INT: // 5
					toObject.accept(Integer.class);
					break;
				case Type.FLOAT: // 6
					toObject.accept(Float.class);
					break;
				case Type.LONG: // 7
					toObject.accept(Long.class);
					break;
				case Type.DOUBLE: // 8
					toObject.accept(Double.class);
					break;
				case Type.ARRAY: // 9
				case Type.OBJECT: // 10
				case Type.METHOD: // 11
					// nothing to do here
					break;
				case Type.VOID: // 0
				default:
					throw new IllegalStateException("Parameter of type: " + type.getSort() + " are not supported");
			}
			cached.instructions.add(new InsnNode(AASTORE));
		}

		cached.instructions.add(new VarInsnNode(ASTORE, argArray.index));
		cached.instructions.add(argArray.start);
		if (inst != null)
		{
			cached.instructions.add(new VarInsnNode(ALOAD, inst.index));
		}
		cached.instructions.add(new FieldInsnNode(inst == null ? GETSTATIC : GETFIELD, classNode.name, cacheField.name,
				cacheField.desc));
		cached.instructions.add(new VarInsnNode(ALOAD, argArray.index));
		cached.instructions
				.add(new MethodInsnNode(INVOKEVIRTUAL, CACHE_TYPE, "get", fDesc(Object.class, Object.class)));

		Type returnType = Type.getReturnType(original.desc);

		BiConsumer<Class<?>, String> toPrimitive = (clazz, methodName) ->
		{
			cached.instructions.add(new TypeInsnNode(CHECKCAST, toInternal(clazz)));
			cached.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, toInternal(clazz), methodName,
					fDesc(returnType.getDescriptor())));
		};
		switch (returnType.getSort())
		{
			case Type.BOOLEAN: // 1
				toPrimitive.accept(Boolean.class, "booleanValue");
				break;
			case Type.CHAR: // 2
				toPrimitive.accept(Character.class, "charValue");
				break;
			case Type.BYTE: // 3
				toPrimitive.accept(Byte.class, "byteValue");
				break;
			case Type.SHORT: // 4
				toPrimitive.accept(Short.class, "shortValue");
				break;
			case Type.INT: // 5
				toPrimitive.accept(Integer.class, "intValue");
				break;
			case Type.FLOAT: // 6
				toPrimitive.accept(Float.class, "floatValue");
				break;
			case Type.LONG: // 7
				toPrimitive.accept(Long.class, "longValue");
				break;
			case Type.DOUBLE: // 8
				toPrimitive.accept(Double.class, "doubleValue");
				break;
			case Type.ARRAY: // 9
			case Type.OBJECT: // 10
			case Type.METHOD: // 11
				cached.instructions.add(new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
				break;
			case Type.VOID: // 0
			default:
				throw new IllegalStateException("ReturnType: " + returnType.getSort() + " is not supported");

		}
		cached.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));
		cached.instructions.add(end);
		return cached;
	}

	private static boolean compile(File classFile, ClassNode classNode)
	{
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		classNode.accept(cw);

		classFile.getParentFile().mkdirs();
		try (DataOutputStream oStream = new DataOutputStream(new FileOutputStream(classFile)))
		{
			oStream.write(cw.toByteArray());
			oStream.flush();
			return true;
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return false;
	}

	private static boolean isStatic(int access)
	{
		return (access & ACC_STATIC) != 0;
	}
}