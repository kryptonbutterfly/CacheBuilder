package kryptonbutterfly.asm.cache;

import static kryptonbutterfly.asm.cache.misc.WriterUtils.*;
import static kryptonbutterfly.math.utils.range.Range.*;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
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

import kryptonbutterfly.asm.cache.misc.CacheSpec;
import kryptonbutterfly.asm.cache.misc.CacheTarget;
import kryptonbutterfly.asm.cache.misc.WriterUtils;
import kryptonbutterfly.cache.Cache;
import kryptonbutterfly.io.FileSystemUtils;
import kryptonbutterfly.monads.opt.Opt;

@Mojo(name = "decorate", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class DecoratorCache extends AbstractMojo implements Opcodes, WriterUtils
{
	private static final String	CACHE_CAP_CONST_DESC	= fDesc("V", Function.class, "I");
	private static final String	CACHE_CONST_DESC		= fDesc("V", Function.class);
	private static final String	METAFACTORY_DESC		= fDesc(
		CallSite.class,
		MethodHandles.Lookup.class,
		String.class,
		MethodType.class,
		MethodType.class,
		MethodHandle.class,
		MethodType.class);
	
	@Parameter(property = "project.builder.outputDirectory", defaultValue = "target/classes")
	private String outputDirectory;
	
	@Parameter(property = "classFileExtension", defaultValue = ".class")
	private String classFileExtension;
	
	private final String annotationDescriptor;
	
	public DecoratorCache()
	{
		super();
		this.annotationDescriptor = getDesc(Cache.class);
	}
	
	private DecoratorCache(String outputDirectory, String classFileExtension, Class<?> annotation)
	{
		super();
		this.outputDirectory		= outputDirectory;
		this.classFileExtension		= classFileExtension;
		this.annotationDescriptor	= getDesc(annotation);
	}
	
	public void execute()
	{
		final var outputDir = new File(outputDirectory);
		if (!outputDir.exists())
		{
			getLog().warn(
				"Unable to decorate any class files: The directory/file %s does not exists!"
					.formatted(outputDirectory));
			return;
		}
		
		if (outputDir.isDirectory()
			? new FileSystemUtils().performRecursive(
				outputDir,
				file -> file.isFile() ? adapt(file) : false,
				Boolean::logicalOr)
				: adapt(outputDir))
			getLog().info("No files with extension %s decorated.".formatted(classFileExtension));
	}
	
	public static void main(String[] args)
	{
		final var outputDir = args.length > 0 ? args[0] : ".";
		new DecoratorCache(outputDir, ".class", Cache.class).execute();
	}
	
	/**
	 * @param annotation
	 *            the annotation to search for
	 * @param reader
	 *            the class reader
	 * @return the adapted Opt<ClassNode> or Opt.empty() if nothing changed.
	 */
	private Opt<ClassNode> adapt(ClassReader reader)
	{
		final var classNode = new ClassNode();
		reader.accept(classNode, 0);
		
		final var toCache = new ArrayList<CacheTarget>();
		
		classNode.methods.forEach(method -> {
			if (method.invisibleAnnotations != null)
			{
				for (final var annotation : method.invisibleAnnotations)
				{
					if (annotation.desc.equals(annotationDescriptor))
					{
						method.invisibleAnnotations.remove(annotation);
						toCache.add(new CacheTarget(method, annotation));
						return;
					}
				}
			}
			if (method.visibleAnnotations != null)
			{
				for (final var annotation : method.visibleAnnotations)
				{
					if (annotation.desc.equals(annotationDescriptor))
					{
						method.visibleAnnotations.remove(annotation);
						toCache.add(new CacheTarget(method, annotation));
						return;
					}
				}
			}
		});
		
		toCache.forEach(target -> createCachedVersion(classNode, target.method(), target.annotation()));
		return Opt.of(classNode).filter(_c -> !toCache.isEmpty());
	}
	
	private boolean adapt(File classFile)
	{
		if (!classFile.getName().endsWith(classFileExtension))
		{
			getLog().debug("Skipping: Not a '%s' file: %s".formatted(classFileExtension, classFile));
			return false;
		}
		
		try (final var iStream = new FileInputStream(classFile))
		{
			final var reader = new ClassReader(iStream);
			return adapt(reader).filter(classNode -> compile(classFile, classNode)).isPresent();
		}
		catch (IllegalArgumentException e)
		{
			getLog().error("Unable to process %s".formatted(classFile), e);
		}
		catch (IOException e)
		{
			getLog().error(e);
		}
		return false;
	}
	
	private void createCachedVersion(ClassNode classNode, MethodNode original, AnnotationNode annotation)
	{
		try
		{
			final var	SPEC			= getCacheSpecFromAnnotation(annotation);
			final var	originalName	= original.name;
			
			original.name = generateUniqueMethodName(classNode, "ω" + original.name + "$");
			final var addWrapped = generateWrapped(classNode, original, originalName);
			
			final var cacheField = createCache(classNode, addWrapped, SPEC);
			
			final var cached = generateCached(
				classNode,
				original,
				cacheField,
				originalName,
				SPEC.cacheType().getInternalName());
			
			transferAnnotations(original, cached);
			
			original.access = original.access & ~ACC_PUBLIC & ~ACC_PROTECTED | ACC_PRIVATE | ACC_FINAL;
		}
		catch (ClassNotFoundException e)
		{
			getLog().error(e);
		}
	}
	
	private static <E> List<E> copyList(List<E> orig, List<E> targ)
	{
		targ = Opt.of(targ).get(ArrayList::new);
		if (orig != null)
		{
			for (final var i = orig.iterator(); i.hasNext();)
			{
				targ.add(i.next());
				i.remove();
			}
		}
		return targ;
	}
	
	private static void transferAnnotations(MethodNode origin, MethodNode target)
	{
		target.visibleAnnotations	= copyList(origin.visibleAnnotations, target.visibleAnnotations);
		target.invisibleAnnotations	= copyList(origin.invisibleAnnotations, target.invisibleAnnotations);
	}
	
	private MethodNode generateWrapped(ClassNode classNode, MethodNode original, String originalName)
	{
		final int	access		= original.access & ~ACC_PUBLIC & ~ACC_PROTECTED | ACC_PRIVATE | ACC_FINAL;
		final var	returnType	= Type.getReturnType(original.desc);
		final var	wrapped		= new MethodNode(
			access,
			generateUniqueMethodName(classNode, "¢" + originalName),
			fDesc(returnType.getDescriptor(), Object[].class),
			null,
			null);
		classNode.methods.add(wrapped);
		
		final var	start	= new LabelNode();
		final var	end		= new LabelNode();
		int			index	= 0;
		
		LocalVariableNode inst = null;
		if (!isStatic(original.access))
		{
			inst = new LocalVariableNode("this", getDesc(classNode.name), null, start, end, index++);
			wrapped.localVariables.add(inst);
		}
		final var array = new LocalVariableNode("key", getDesc(Object[].class), null, start, end, index++);
		wrapped.localVariables.add(array);
		
		// setup Local variables
		final var	offset		= isStatic(original.access) ? 0 : -1;
		final int	argsCount	= Type.getArgumentTypes(original.desc).length;
		final var	params		= new LocalVariableNode[argsCount];
		for (final var element : range(original.localVariables))
		{
			if (element.element().name.equals("this"))
				continue;
			final var i = element.index() + offset;
			if (i == argsCount)
				break;
			final var loc = element.element();
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
			final var type = Type.getType(element.element().desc);
			
			wrapped.instructions.add(new VarInsnNode(ALOAD, array.index));
			wrapped.instructions.add(new LdcInsnNode(element.index()));
			wrapped.instructions.add(new InsnNode(AALOAD));
			// adapt for primitives and cast
			addConvertToPrimitive(type, wrapped.instructions, element.element());
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
			final var type = Type.getType(element.desc);
			wrapped.instructions.add(new VarInsnNode(type.getOpcode(ILOAD), element.index));
		}
		wrapped.instructions.add(
			new MethodInsnNode(
				isStatic(original.access) ? INVOKESTATIC : INVOKESPECIAL,
				classNode.name,
				original.name,
				original.desc));
		wrapped.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));
		wrapped.instructions.add(end);
		return wrapped;
	}
	
	private static FieldNode createCache(ClassNode classNode, MethodNode function, CacheSpec SPEC)
	{
		final boolean isStatic = isStatic(function.access);
		// calculate field access modifiers
		int access = ACC_PRIVATE | ACC_FINAL;
		if (isStatic)
		{
			access |= ACC_STATIC;
		}
		
		final String	fieldName	= WriterUtils.generateUniqueFieldName(classNode, function.name);
		final FieldNode	field		= new FieldNode(access, fieldName, SPEC.cacheType().getDescriptor(), null, null);
		classNode.fields.add(field);
		
		final var	init		= getOrCreateInitializer(classNode, isStatic);
		boolean		isRecord	= false;
		final var	buffer		= new ArrayList<AbstractInsnNode>();
		for (final var i = init.instructions.iterator(); i.hasNext();)
		{
			final var node = i.next();
			if (isRecord || node.getOpcode() == RETURN)
			{
				isRecord = true;
				i.remove();
				buffer.add(node);
			}
		}
		
		LocalVariableNode instance = null;
		if (!isStatic)
		{
			instance = findLocal(init, "this").get(() -> null);
			init.instructions.add(new VarInsnNode(ALOAD, instance.index));
		}
		init.instructions.add(new TypeInsnNode(NEW, SPEC.cacheType().getInternalName()));
		init.instructions.add(new InsnNode(DUP));
		if (!isStatic)
		{
			init.instructions.add(new VarInsnNode(ALOAD, instance.index));
		}
		
		final int invokeTag = isStatic ? H_INVOKESTATIC : H_INVOKESPECIAL;
		
		init.instructions.add(
			new InvokeDynamicInsnNode(
				"apply",
				isStatic ? fDesc(Function.class) : fDesc(Function.class, getDesc(classNode.name)),
				new Handle(H_INVOKESTATIC, toInternal(LambdaMetafactory.class), "metafactory", METAFACTORY_DESC, false),
				Type.getMethodType(fDesc(Object.class, Object.class)),
				new Handle(invokeTag, classNode.name, function.name, function.desc, false),
				Type.getMethodType(function.desc)));
		
		final String invokeDesc;
		if (SPEC.capacity() > 0)
		{
			init.instructions.add(new LdcInsnNode(SPEC.capacity()));
			invokeDesc = CACHE_CAP_CONST_DESC;
		}
		else
		{
			invokeDesc = CACHE_CONST_DESC;
		}
		
		init.instructions
			.add(new MethodInsnNode(INVOKESPECIAL, SPEC.cacheType().getInternalName(), "<init>", invokeDesc));
		init.instructions
			.add(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, classNode.name, field.name, field.desc));
		buffer.forEach(init.instructions::add);
		return field;
	}
	
	private static MethodNode generateCached(
		ClassNode classNode,
		MethodNode original,
		FieldNode cacheField,
		String originalName,
		String CACHE_TYPE)
	{
		final var cached = new MethodNode(original.access, originalName, original.desc, null, null);
		classNode.methods.add(cached);
		final var	start	= new LabelNode();
		final var	end		= new LabelNode();
		final var	array	= new LabelNode();
		
		int					index	= 0;
		LocalVariableNode	inst	= null;
		if (!isStatic(original.access))
		{
			inst = new LocalVariableNode("this", getDesc(classNode.name), null, start, end, index++);
			cached.localVariables.add(inst);
		}
		
		// generate locals from original
		final int	offset		= isStatic(original.access) ? 0 : -1;
		final int	argCount	= Type.getArgumentTypes(original.desc).length;
		final var	params		= new LocalVariableNode[argCount];
		for (final var element : range(original.localVariables))
		{
			if (element.element().name.equals("this"))
			{
				continue;
			}
			final var	loc	= element.element();
			final int	i	= element.index() + offset;
			if (i == argCount)
			{
				break;
			}
			params[i] = new LocalVariableNode(loc.name, loc.desc, null, start, end, index++);
			cached.localVariables.add(params[i]);
		}
		
		final var argArray = new LocalVariableNode("key", getDesc(Object[].class), null, array, end, index++);
		cached.localVariables.add(argArray);
		
		cached.instructions.add(start);
		cached.instructions.add(new LdcInsnNode(params.length));
		cached.instructions.add(new TypeInsnNode(ANEWARRAY, toInternal(Object.class)));
		
		for (final var element : range(params))
		{
			final var	loc			= element.element();
			final int	arrayIndex	= element.index();
			cached.instructions.add(new InsnNode(DUP));
			final var type = Type.getType(loc.desc);
			cached.instructions.add(new LdcInsnNode(arrayIndex));
			cached.instructions.add(new VarInsnNode(type.getOpcode(ILOAD), loc.index));
			final Consumer<Class<?>> toObject = (c) -> cached.instructions
				.add(new MethodInsnNode(INVOKESTATIC, toInternal(c), "valueOf", fDesc(c, loc.desc)));
			switch (type.getSort())
			{
			case Type.BOOLEAN -> toObject.accept(Boolean.class);
			case Type.CHAR -> toObject.accept(Character.class);
			case Type.BYTE -> toObject.accept(Byte.class);
			case Type.SHORT -> toObject.accept(Short.class);
			case Type.INT -> toObject.accept(Integer.class);
			case Type.FLOAT -> toObject.accept(Float.class);
			case Type.LONG -> toObject.accept(Long.class);
			case Type.DOUBLE -> toObject.accept(Double.class);
			case Type.ARRAY, Type.OBJECT, Type.METHOD ->
					{} /* nothing to do here */
			case Type.VOID ->
					{}
			default -> throw new IllegalStateException(
				"Parameter of type: " + type.getSort() + " are not supported");
			}
			cached.instructions.add(new InsnNode(AASTORE));
		}
		
		cached.instructions.add(new VarInsnNode(ASTORE, argArray.index));
		cached.instructions.add(argArray.start);
		if (inst != null)
		{
			cached.instructions.add(new VarInsnNode(ALOAD, inst.index));
		}
		cached.instructions.add(
			new FieldInsnNode(
				inst == null ? GETSTATIC : GETFIELD,
				classNode.name,
				cacheField.name,
				cacheField.desc));
		cached.instructions.add(new VarInsnNode(ALOAD, argArray.index));
		cached.instructions
			.add(new MethodInsnNode(INVOKEVIRTUAL, CACHE_TYPE, "get", fDesc(Object.class, Object.class)));
		
		final var returnType = Type.getReturnType(original.desc);
		
		final BiConsumer<Class<?>, String> toPrimitive = (clazz, methodName) -> {
			cached.instructions.add(new TypeInsnNode(CHECKCAST, toInternal(clazz)));
			cached.instructions.add(
				new MethodInsnNode(
					INVOKEVIRTUAL,
					toInternal(clazz),
					methodName,
					fDesc(returnType.getDescriptor())));
		};
		switch (returnType.getSort())
		{
		case Type.BOOLEAN -> toPrimitive.accept(Boolean.class, "booleanValue");
		case Type.CHAR -> toPrimitive.accept(Character.class, "charValue");
		case Type.BYTE -> toPrimitive.accept(Byte.class, "byteValue");
		case Type.SHORT -> toPrimitive.accept(Short.class, "shortValue");
		case Type.INT -> toPrimitive.accept(Integer.class, "intValue");
		case Type.FLOAT -> toPrimitive.accept(Float.class, "floatValue");
		case Type.LONG -> toPrimitive.accept(Long.class, "longValue");
		case Type.DOUBLE -> toPrimitive.accept(Double.class, "doubleValue");
		case Type.ARRAY, Type.OBJECT, Type.METHOD -> cached.instructions
			.add(new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
		case Type.VOID ->
				{}
		default -> throw new IllegalStateException("ReturnType: " + returnType.getSort() + " is not supported");
	
		}
		cached.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));
		cached.instructions.add(end);
		return cached;
	}
	
	private static boolean compile(File classFile, ClassNode classNode)
	{
		final var cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		classNode.accept(cw);
		final var parentFolder = classFile.getParentFile();
		if (parentFolder.exists() || parentFolder.mkdirs())
		{
			try (final var oStream = new DataOutputStream(new FileOutputStream(classFile)))
			{
				oStream.write(cw.toByteArray());
				oStream.flush();
				return true;
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		return false;
	}
}