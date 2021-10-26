package de.tinycodecrank.agent;

import static de.tinycodecrank.agent.SimpleLogging.*;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;

public class Agent
{
	public static void premain(String args, Instrumentation inst) throws Exception
	{
		LOGGER.logf("--premain: %s, %s\n", args, inst);
		inst.addTransformer(new ClassTransformer());
	}
	
	public static void agentmain(String args, Instrumentation inst) throws Exception
	{
		LOGGER.logf("--agentmain: %s, %s\n", args, inst);
		inst.addTransformer(new ClassTransformer());
	}
	
	public static void main(String[] args)
	{
		LOGGER.logf("--main: %s\n", Arrays.toString(args));
	}
}