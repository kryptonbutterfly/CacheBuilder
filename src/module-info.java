module kryptonbutterfly.CacheASM
{
	exports kryptonbutterfly.asm.cache;
	
	requires transitive java.xml;
	
	requires maven.plugin.annotations;
	requires maven.plugin.api;
	
	requires kryptonbutterfly.Cache;
	requires kryptonbutterfly.System;
	requires kryptonbutterfly.mathUtils;
	requires org.objectweb.asm;
	requires org.objectweb.asm.tree;
}