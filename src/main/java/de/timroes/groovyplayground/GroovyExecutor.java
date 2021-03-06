package de.timroes.groovyplayground;

import com.google.apphosting.api.DeadlineExceededException;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.codehaus.groovy.control.CompilationFailedException;

/**
 *
 * @author Tim Roes - mail@timroes.de
 */
public class GroovyExecutor {
	
	public static final String SCRIPT_NAME = "_GroovyUserScript_";
	
	private static final Pattern COMPILATION_LINE_PATTERN = Pattern.compile(".*line ([0-9]+).*");
	
	public static int causingLineInStacktrace(StackTraceElement[] stacktrace) {
		int line = -1;
		for(StackTraceElement el : stacktrace) {
			if (GroovyExecutor.SCRIPT_NAME.equals(el.getFileName())) {
				line = el.getLineNumber();
				break;
			}
		}
		return line;
	}
	
	public ExecutionResult execute(ExecutionRequest request, String remoteIp) {
		
		ExecutionResult.Builder result = ExecutionResult.create();
		
		GroovyShell shell = new GroovyShell(new Binding());

		long startTime = System.currentTimeMillis();

		Script script;

		try {
			script = shell.parse(request.getSource(), SCRIPT_NAME);
			script.setProperty("out", new CapturingPrintStream(result));
			result.scriptReturn(script.run());
		} catch(CompilationFailedException ex) {
			Matcher matcher = COMPILATION_LINE_PATTERN.matcher(ex.getMessage());
			int line = -1;
			if(matcher.find()) {
				line = Integer.valueOf(matcher.group(1));
			}
			result.addCompilationException(ex.getMessage(), line);
		} catch(Throwable ex) {
			if(ex instanceof DeadlineExceededException) {
				// We don't want to add DeadlineExceededException to output,
				// but let the controller handle it
				throw ex;
			}
			String output = ex.getMessage();
			if(output == null || output.isEmpty()) {
				output = String.format("%s has been thrown without a message.", ex.getClass().getName());
			}
			result.addException(output, causingLineInStacktrace(ex.getStackTrace()));
		}

		result.executionTime(System.currentTimeMillis() - startTime);
		
		return result.build();
		
	}
	
}
