package de.tinycodecrank.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import lombok.SneakyThrows;

public class SimpleLogging implements AutoCloseable
{
	public static final SimpleLogging LOGGER = new SimpleLogging(new File("./cacheASM.log"));
	
	private final OutputStreamWriter writer;
	
	@SneakyThrows
	private SimpleLogging(File logFile)
	{
		OutputStream oStream = new FileOutputStream(logFile);
		this.writer = new OutputStreamWriter(oStream);
	}
	
	@SneakyThrows
	public void log(String message)
	{
		writer.append(message).append('\n');
		writer.flush();
	}
	
	@SneakyThrows
	public void logf(String message, Object... params)
	{
		writer.append(String.format(message, params));
		writer.flush();
	}
	
	@Override
	public void close() throws Exception
	{
		this.writer.close();
	}
}