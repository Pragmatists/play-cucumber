package play.cucumber;

import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.io.FileResourceLoader;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.model.CucumberFeature;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import play.Play;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.libs.IO;
import play.templates.Template;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.*;
import static java.util.Collections.*;

public class CucumberService {

	public static List<CucumberFeature> loadFeatures() {
        return CucumberFeature.load(new FileResourceLoader(), asList("features"), emptyList());
	}

	public static CucumberFeature findFeatureByUri(String uri) {
		for (CucumberFeature feature : loadFeatures()) {
			if (uri.equals(feature.getPath())) {
				return feature;
			}
		}
		return null;
	}

	public static List<RunResult> runAllFeatures(PrintStream consoleStream) {
		List<CucumberFeature> features = CucumberService.loadFeatures();
		consoleStream.println("~");
		consoleStream.println("~ " + features.size() + " Cucumber tests to run:");
		consoleStream.println("~");
		ArrayList<RunResult> runResults = new ArrayList<RunResult>();
		int maxLength = 0;
		for (CucumberFeature feature : features) {
			if (feature.getPath().length() > maxLength) {
				maxLength = feature.getPath().length();
			}
		}
        for (CucumberFeature feature : features) {
            Formatter jUnitFormatter = createJUnitFormatter(feature);
            RunResult runResult = runFeature(feature, jUnitFormatter);
            consoleStream.print("~ " + feature.getPath() + " : ");
            for (int i = 0; i < maxLength - feature.getPath().length(); i++) {
				consoleStream.print(" ");
			}
			if (runResult.passed) {
				consoleStream.println("  PASSED");
			} else {
				if (runResult.snippets.size() > 0) {
					consoleStream.println("  SKIPPED !  ");
				} else {
					consoleStream.println("  FAILED  !  ");
				}
			}
			runResults.add(runResult);
		}
		consoleStream.println("~");
		return runResults;
	}
	
	public static RunResult runFeature(String uri) {
		CucumberFeature cucumberFeature = CucumberService.findFeatureByUri(uri);
		Formatter jUnitFormatter = createJUnitFormatter(cucumberFeature);
        return runFeature(cucumberFeature, jUnitFormatter);
    }

	private final static String CUCUMBER_RESULT_PATH = "test-result/cucumber/";

    private static RunResult runFeature(CucumberFeature cucumberFeature, Formatter... formatters) {

        RuntimeOptions runtimeOptions = new RuntimeOptions(createOptions(CUCUMBER_RESULT_PATH));

		// StringWriter prettyWriter = null;
		// prettyWriter = addPrettyFormatter(runtimeOptions);
		addPrettyFormatter(runtimeOptions);
		StringWriter jsonWriter = addJSONFormatter(runtimeOptions);
		for (Formatter formatter : formatters) {
			runtimeOptions.addFormatter(formatter);
		}
		// Exec Feature
		final ClassLoader classLoader = Play.classloader;
        ResourceLoader resourceLoader = new MultiLoader(classLoader);
        final PlayBackend backend = new PlayBackend(resourceLoader);
        final Runtime runtime = new Runtime(resourceLoader, classLoader, asList(backend), runtimeOptions);
		Formatter formatter = runtimeOptions.formatter(classLoader);
		Reporter reporter = runtimeOptions.reporter(classLoader);
		cucumberFeature.run(formatter, reporter, runtime);
		formatter.done();
		// String prettyResult = prettyWriter.toString();
		// System.out.println(prettyResult);
		// }
		String jsonResult = jsonWriter.toString();
		formatter.close();

		// Serialize the execution Result
		File targetFile = Play.getFile(CUCUMBER_RESULT_PATH + cucumberFeature.getPath() + ".html");
		createDirectory(targetFile.getParentFile());
		List<ErrorDetail> errorDetails = buildErrors(runtime.getErrors());
		Template template = play.templates.TemplateLoader.load("Cucumber/runFeature.html");
		HashMap<String, Object> args = new HashMap<String, Object>();
		args.put("feature", cucumberFeature);
		args.put("runtime", runtime);
		args.put("jsonResult", jsonResult);
		args.put("errorDetails", errorDetails);
		String result = template.render(args);

		IO.write(result.getBytes(), targetFile);
		return new RunResult(cucumberFeature, (errorDetails.size() + runtime.getSnippets().size() == 0)/*
																										 * ,
																										 * prettyResult
																										 */, errorDetails, runtime.getSnippets());
	}

    private static List<String> createOptions(String dotCucumber) {
        return asList("--dotcucumber", dotCucumber);
    }

    private static void addPrettyFormatter(RuntimeOptions runtimeOptions) {
		Appendable consoleStream = new Appendable() {
			@Override
			public Appendable append(CharSequence csq, int start, int end) throws IOException {
				System.out.append(csq, start, end);
				return this;
			}

			@Override
			public Appendable append(char c) throws IOException {
				System.out.append(c);
				return this;
			}

			@Override
			public Appendable append(CharSequence csq) throws IOException {
				System.out.append(csq);
				return this;
			}
		};
        Formatter prettyFormatter = createPrettyFormatter(consoleStream);
        runtimeOptions.addFormatter(prettyFormatter);
	}

    @SuppressWarnings("unchecked")
    private static Formatter createPrettyFormatter(Appendable consoleStream) {
        Formatter prettyFormatter = null;
        try {
            Class prettyFormatterClass = Class.forName("cucumber.runtime.formatter.CucumberPrettyFormatter");
            Constructor<Formatter> constructor = prettyFormatterClass.getDeclaredConstructor(Appendable.class);
            constructor.setAccessible(true);
            prettyFormatter = constructor.newInstance(consoleStream);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return prettyFormatter;
    }

    private static StringWriter addJSONFormatter(RuntimeOptions runtimeOptions) {
        StringWriter jsonWriter = new StringWriter();
        // JSONFormatter jsonFormatter = new JSONFormatter(jsonWriter);
		// JSONPrettyFormatter jsonFormatter = new
		// JSONPrettyFormatter(jsonWriter);
		Formatter jsonFormatter = new CustomJSONFormatter(jsonWriter);
		runtimeOptions.addFormatter(jsonFormatter);
		return jsonWriter;
	}
	
	private static Formatter createJUnitFormatter(CucumberFeature cucumberFeature) {
		String reportFileName = escapeSlashAndBackSlash(cucumberFeature.getPath())+"-junit-report.xml";
		Play.getFile(CUCUMBER_RESULT_PATH).mkdir();
        return new CustomJUnitFormatter(Play.getFile(CUCUMBER_RESULT_PATH + reportFileName));
    }
	
	private static String escapeSlashAndBackSlash(String s){
		return s.replaceAll("\\\\","_").replaceAll("/", "_");
	}

	private static void createDirectory(File dir) {
		if (!dir.getParentFile().exists()) {
			createDirectory(dir.getParentFile());
		}
		if (!dir.exists()) {
			dir.mkdir();
		}
	}

	public static class RunResult {
		CucumberFeature feature;
		boolean passed;
		// String prettyResult;
		List<ErrorDetail> errorDetails;
		HashSet<String> snippets;

		public RunResult(CucumberFeature cucumberFeature, boolean passed, /*
																		 * String
																		 * prettyResult
																		 * ,
																		 */List<ErrorDetail> errorDetails, List<String> snippets) {
			this.feature = cucumberFeature;
			this.passed = passed;
			// this.prettyResult = prettyResult;
			this.errorDetails = errorDetails;
			this.snippets = new HashSet<String>();
			this.snippets.addAll(snippets);
		}

	}

	private static List<ErrorDetail> buildErrors(List<Throwable> failures) {
		List<ErrorDetail> errorDetails = new ArrayList<ErrorDetail>();
		for (Throwable failure : failures) {
			ErrorDetail errorDetail = new ErrorDetail();
			errorDetail.failure = failure;
			for (StackTraceElement stackTraceElement : failure.getStackTrace()) {
				String className = stackTraceElement.getClassName();
				ApplicationClass applicationClass = Play.classes.getApplicationClass(className);
				if (applicationClass != null) {
					errorDetail.sourceFile = Play.classes.getApplicationClass(className).javaFile.relativePath();
					errorDetail.addSourceCode(Play.classes.getApplicationClass(className).javaSource, stackTraceElement.getLineNumber());
				}
			}
			errorDetails.add(errorDetail);
		}
		return errorDetails;
	}

	public static class ErrorDetail {
		public String sourceFile;
		public int errorLine;
		public List<SourceLine> sourceCode;
		public Throwable failure;

		public void addSourceCode(String javaSource, int errorLine) {
			this.sourceCode = new ArrayList<SourceLine>();
			this.errorLine = errorLine;
			String[] lines = javaSource.split("\n");
			int from = lines.length - 5 >= 0 && errorLine <= lines.length ? errorLine - 5 : 0;
			if (from > 0) {
				int to = errorLine + 5 < lines.length ? errorLine + 5 : lines.length - 1;
				for (int i = from; i < to; i++) {
					SourceLine sourceLine = new SourceLine();
					sourceLine.code = lines[i];
					sourceLine.lineNumber = i + 1;
					if (sourceLine.lineNumber == errorLine) {
						sourceLine.isInError = true;
					}
					sourceCode.add(sourceLine);
				}
			}
		}
	}

	public static class SourceLine {
		String code;
		int lineNumber;
		boolean isInError = false;
	}

}
