package org.granite.grails.gas3;

import groovy.lang.GroovyObject;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.codehaus.groovy.grails.validation.ConstrainedProperty;
import org.codehaus.groovy.grails.validation.Constraint;
import org.codehaus.groovy.grails.validation.MatchesConstraint;
import org.codehaus.groovy.grails.validation.MaxConstraint;
import org.codehaus.groovy.grails.validation.MinConstraint;
import org.codehaus.groovy.grails.validation.NullableConstraint;
import org.codehaus.groovy.grails.validation.SizeConstraint;
import org.granite.generator.as3.ClientType;
import org.granite.generator.as3.reflect.JavaConstraint;
import org.granite.generator.as3.reflect.JavaEntityBean;
import org.granite.generator.as3.reflect.JavaProperty;
import org.granite.generator.as3.reflect.JavaTypeFactory;
import org.springframework.util.Assert;


public class GrailsDomainBean extends JavaEntityBean {
	
	private static final String[] META_CONSTRAINTS = { "inline", "inCreate", "inEdit" };

	public static final Set<String> EVENTS = new HashSet<String>();
	static {
		EVENTS.add("onLoad");
		EVENTS.add("onSave"); 
		EVENTS.add("beforeLoad");
		EVENTS.add("beforeInsert");
		EVENTS.add("afterInsert");
		EVENTS.add("beforeUpdate");
		EVENTS.add("afterUpdate");
		EVENTS.add("beforeDelete");
		EVENTS.add("afterDelete");
		EVENTS.add("afterLoad");
	};
	
	private Map<String, ClientType> hasMany = null;
	private Map<String, Map<String, String>> constraints = null;
	private Map<String, List<JavaConstraint>> clientConstraints = new HashMap<String, List<JavaConstraint>>();
	private org.codehaus.groovy.grails.commons.GrailsDomainClass domainClass = null;


    @SuppressWarnings("unchecked")
    public GrailsDomainBean(JavaTypeFactory provider, Class<?> type, URL url) {
        super(provider, type, url);
        
        domainClass = new DefaultGrailsDomainClass(type);
        
        Map<String, Class<?>> hasMany = domainClass.getAssociationMap();
    	if (hasMany != null && !hasMany.isEmpty()) {
    		this.hasMany = new HashMap<String, ClientType>();
        	for (Map.Entry<String, Class<?>> me : hasMany.entrySet()) {
        		this.hasMany.put(me.getKey(), getProvider().getAs3Type(me.getValue()));
        	}
    	}

        Map<String, ConstrainedProperty> constraints = domainClass.getConstrainedProperties();
        
    	if (constraints != null && !constraints.isEmpty()) {
    		List<GrailsDomainClassProperty> properties = new ArrayList<GrailsDomainClassProperty>();
    		for (String propertyName : constraints.keySet())
    			properties.add(domainClass.getPropertyByName(propertyName));
    		Collections.sort(properties, new DomainClassPropertyComparator(domainClass));
    		
    		this.constraints = new LinkedHashMap<String, Map<String, String>>();
    		for (GrailsDomainClassProperty property : properties) {
    			if ("uid".equals(property.getName()) || "version".equals(property.getName()) || "id".equals(property.getName()))
    				continue;
    			
    			ConstrainedProperty cp = constraints.get(property.getName());
    			Collection<Constraint> appliedConstraints = cp.getAppliedConstraints();
    	        
    			Map<String, String> c = new HashMap<String, String>();
    			if (!cp.isDisplay())
    				c.put("display", "\"false\"");
    			if (!cp.isEditable())
    				c.put("editable", "\"false\"");
    			if (cp.isPassword())
    				c.put("password", "\"true\"");
    			if (cp.getWidget() != null)
    				c.put("widget", "\"" + cp.getWidget() + "\"");
    			if (cp.getFormat() != null)
    				c.put("format", "\"" + cp.getFormat() + "\"");
    			
    			for (String mcName : META_CONSTRAINTS) {
    				if (cp.getMetaConstraintValue(mcName) != null)
    					c.put(mcName, "\"" + cp.getMetaConstraintValue(mcName) + "\"");
    			}
    			
    			for (Constraint constraint : appliedConstraints) {
    				if ("org.codehaus.groovy.grails.validation.NullableConstraint".equals(constraint.getClass().getName()))
    					continue;
    				String value = constraint.toString();
    				c.put(constraint.getName(), "\"" + value.substring(value.indexOf('[')+1, value.lastIndexOf(']')) + "\"");
    				
    				if (constraint instanceof SizeConstraint) {
    					SizeConstraint size = (SizeConstraint)constraint;
    					List<String[]> s = new ArrayList<String[]>();
    					s.add(new String[] { "min", String.valueOf(size.getRange().getFromInt()) });
    					s.add(new String[] { "max", String.valueOf(size.getRange().getToInt()) });
    					addClientConstraint(property.getName(), "Size", s);
    				}
    				if (constraint instanceof NullableConstraint) {
    					NullableConstraint nullable = (NullableConstraint)constraint;
    					List<String[]> s = new ArrayList<String[]>();
    					addClientConstraint(property.getName(), nullable.isNullable() ? "Null" : "NotNull", s);
    				}
    				if (constraint instanceof MatchesConstraint) {
    					MatchesConstraint matches = (MatchesConstraint)constraint;
    					List<String[]> s = new ArrayList<String[]>();
    					s.add(new String[] { "regexp", matches.getRegex() });
    					addClientConstraint(property.getName(), "Pattern", s);
    				}
    				if (constraint instanceof MinConstraint) {
    					MinConstraint min = (MinConstraint)constraint;
    					if (min.getMinValue() instanceof Number) {
	    					List<String[]> s = new ArrayList<String[]>();
	    					s.add(new String[] { "value", String.valueOf(min.getMinValue()) });
	    					addClientConstraint(property.getName(), 
	    							(min.getMinValue() instanceof Integer || min.getMinValue() instanceof Long ? "Min" : "DecimalMin"), s);
	    				}
    				}
    				if (constraint instanceof MaxConstraint) {
    					MaxConstraint max = (MaxConstraint)constraint;
    					if (max.getMaxValue() instanceof Number) {
	    					List<String[]> s = new ArrayList<String[]>();
	    					s.add(new String[] { "value", String.valueOf(max.getMaxValue()) });
	    					addClientConstraint(property.getName(), 
	    							(max.getMaxValue() instanceof Integer || max.getMaxValue() instanceof Long ? "Max" : "DecimalMax"), s);
	    				}
    				}
    			}
    			
    			if (property.isAssociation()) {
    				if (property.isOneToOne())
    					c.put("association", "\"oneToOne\"");
    				else if (property.isManyToOne())
    					c.put("association", "\"manyToOne\"");
    				else if (property.isOneToMany())
    					c.put("association", "\"oneToMany\"");
    				else if (property.isManyToMany())
    					c.put("association", "\"manyToMany\"");
    				
        			if (property.isBidirectional())
        				c.put("bidirectional", "\"true\"");
    				if (property.isOwningSide())
    					c.put("owningSide", "\"true\"");
    			}
    			
				this.constraints.put(property.getName(), c);
    		}
    	}
    }
    
    @Override
	protected SortedMap<String, JavaProperty> initProperties() {
    	SortedMap<String, JavaProperty> properties = super.initProperties();
    	
    	// domainClass not ready at this time
        if (!type.getSuperclass().equals(GroovyObject.class) &&
            !type.getSuperclass().equals(Object.class) &&
            !Modifier.isAbstract(type.getSuperclass().getModifiers())) {
    		properties.remove("id");
    		properties.remove("version");
    	}
        
		properties.remove("errors");
		
        for (String event : EVENTS)
        	properties.remove(event);
        
    	return properties;
	}

	@Override
    public boolean hasIdentifiers() {
    	if (!domainClass.isRoot())
    		return false;
        return super.hasIdentifiers();
    }
    
    public Map<String, ClientType> getHasMany() {
    	return hasMany;
    }

    public Map<String, Map<String, String>> getConstraints() {
    	return constraints;
    }
    
    public Map<String, List<JavaConstraint>> getClientConstraints() {
    	return clientConstraints;
    }
    
    private void addClientConstraint(String propertyName, String name, List<String[]> c) {
    	for (String[] ic : c)
    		ic[1] = escape(ic[1]);
    	
		List<JavaConstraint> list = (List<JavaConstraint>)clientConstraints.get(propertyName);
		if (list == null) {
			list = new ArrayList<JavaConstraint>();
			clientConstraints.put(propertyName, list);
		}
		list.add(new JavaConstraint("", name, c));
    }
	
	private static String escape(Object value) {
		
		if (value.getClass().isArray()) {
			StringBuilder sb = new StringBuilder();
			
			final int length = Array.getLength(value);
			boolean first = true;
			for (int i = 0; i < length; i++) {
				Object item = Array.get(value, i);
				if (item == null)
					continue;

				if (first)
					first = false;
				else
					sb.append(", ");
				
				sb.append(escape(item, true));
			}
			
			return sb.toString();
		}
		
		return escape(value, false);
	}
	
	private static String escape(Object value, boolean array) {
		if (value instanceof Class<?>)
			return ((Class<?>)value).getName();
		
		if (value.getClass().isEnum())
			return ((Enum<?>)value).name();
		
		value = value.toString().replace("&", "&amp;").replace("\"", "&quot;");
		if (array)
			value = ((String)value).replace(",", ",,");
		return (String)value;
	}


	private static class DomainClassPropertyComparator implements Comparator<Object> {

	    @SuppressWarnings("rawtypes")
		private Map constrainedProperties;
	    private GrailsDomainClass domainClass;

	    public DomainClassPropertyComparator(GrailsDomainClass domainClass) {
	        Assert.notNull(domainClass, "Argument 'domainClass' is required!");

	        constrainedProperties = domainClass.getConstrainedProperties();
	        this.domainClass = domainClass;
	    }

	    public int compare(Object o1, Object o2) {
	        if (o1.equals(domainClass.getIdentifier())) {
	            return -1;
	        }
	        if (o2.equals(domainClass.getIdentifier())) {
	            return 1;
	        }

	        GrailsDomainClassProperty prop1 = (GrailsDomainClassProperty)o1;
	        GrailsDomainClassProperty prop2 = (GrailsDomainClassProperty)o2;

	        ConstrainedProperty cp1 = (ConstrainedProperty)constrainedProperties.get(prop1.getName());
	        ConstrainedProperty cp2 = (ConstrainedProperty)constrainedProperties.get(prop2.getName());

	        if (cp1 == null & cp2 == null) {
	            return prop1.getName().compareTo(prop2.getName());
	        }

	        if (cp1 == null) {
	            return 1;
	        }

	        if (cp2 == null) {
	            return -1;
	        }

	        if (cp1.getOrder() > cp2.getOrder()) {
	            return 1;
	        }

	        if (cp1.getOrder() < cp2.getOrder()) {
	            return -1;
	        }

	        return 0;
	    }
	}

}
