package kryptonbutterfly.asm.cache.misc;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

public record CacheTarget(MethodNode method, AnnotationNode annotation)
{}
