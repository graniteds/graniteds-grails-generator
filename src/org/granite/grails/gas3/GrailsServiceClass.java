package org.granite.grails.gas3;

import groovy.lang.GroovyObject;

import java.lang.reflect.Method;
import java.net.URL;

import org.granite.generator.as3.reflect.JavaRemoteDestination;
import org.granite.generator.as3.reflect.JavaTypeFactory;


public class GrailsServiceClass extends JavaRemoteDestination {

    public GrailsServiceClass(JavaTypeFactory provider, Class<?> type, URL url) {
    	super(provider, type, url);
    }
    
    @Override
    protected boolean shouldGenerateMethod(Method method) {
    	return super.shouldGenerateMethod(method)
    		&& !method.getDeclaringClass().equals(Object.class)
    		&& !method.getDeclaringClass().equals(GroovyObject.class)
    		&& !method.getName().startsWith("this$")
    		&& !method.getName().startsWith("super$") 
    		&& !(method.getName().equals("getProperty") || method.getName().equals("setProperty") || method.getName().equals("invokeMethod"))
    		&& !(method.getName().equals("getMetaClass") || method.getName().equals("setMetaClass"));
    }
}
