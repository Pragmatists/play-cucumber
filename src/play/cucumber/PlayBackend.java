package play.cucumber;

import cucumber.runtime.Glue;
import cucumber.runtime.Utils;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.java.JavaBackend;
import play.Play;

import java.lang.reflect.Method;
import java.util.List;

public class PlayBackend extends JavaBackend {
	public PlayBackend(ResourceLoader resourceLoader) {
		super(resourceLoader);
	}

	@Override
	public void loadGlue(Glue glue, List<String> gluePaths) {
		super.loadGlue(glue, gluePaths);
		for (Class<?> glueCodeClass : Play.classloader.getAllClasses()) {
			while (glueCodeClass != Object.class 
					&& !glueCodeClass.isInterface() 					
					&& !Utils.isInstantiable(glueCodeClass)) {
				// those can't be instantiated without container class present.
				glueCodeClass = glueCodeClass.getSuperclass();
			}			
			for (Method method : glueCodeClass.getMethods()) {				
				loadGlue(glue, method, glueCodeClass);
			}				
		}
	}

}
