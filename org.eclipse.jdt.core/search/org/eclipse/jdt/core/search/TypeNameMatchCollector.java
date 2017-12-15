/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.core.search;

import java.util.Collection;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.JavaCore;

/**
 * @since 3.14
 */
public class TypeNameMatchCollector extends TypeNameMatchRequestor {

	private final Collection<TypeNameMatch> fCollection;
	private Pattern[] fStringMatchers;

	public TypeNameMatchCollector(Collection<TypeNameMatch> collection) {
		Assert.isNotNull(collection);
		fCollection= collection;
	}

	private boolean inScope(TypeNameMatch match) {
		return ! isFiltered(match);
	}

	private boolean isFiltered(TypeNameMatch match) {
		boolean filteredByPattern= filter(match.getFullyQualifiedName());
		if (filteredByPattern)
			return true;

		int accessibility= match.getAccessibility();
		switch (accessibility) {
			case IAccessRule.K_NON_ACCESSIBLE:
				return JavaCore.ENABLED.equals(JavaCore.getOption(JavaCore.CODEASSIST_FORBIDDEN_REFERENCE_CHECK));
			case IAccessRule.K_DISCOURAGED:
				return JavaCore.ENABLED.equals(JavaCore.getOption(JavaCore.CODEASSIST_DISCOURAGED_REFERENCE_CHECK));
			default:
				return false;
		}
	}

	private boolean filter(String fullTypeName) {
		Pattern[] matchers= getStringMatchers();
		for (int i= 0; i < matchers.length; i++) {
			Pattern curr= matchers[i];
			if (curr.matcher(fullTypeName).matches()) {
				return true;
			}
		}
		return false;
	}

	private synchronized Pattern[] getStringMatchers() {
		if (fStringMatchers == null) {
			String str= getPreference("org.eclipse.jdt.ui.typefilter.enabled"); //$NON-NLS-1$
			StringTokenizer tok= new StringTokenizer(str, ";"); //$NON-NLS-1$
			int nTokens= tok.countTokens();

			fStringMatchers= new Pattern[nTokens];
			for (int i= 0; i < nTokens; i++) {
				String curr= tok.nextToken();
				if (curr.length() > 0) {
					// Simulate '*', and '?' wildcards using '.*' and '.'
					curr = curr.replaceAll("\\*", ".*"); //$NON-NLS-1$ //$NON-NLS-2$
					curr = curr.replaceAll("\\?", "."); //$NON-NLS-1$ //$NON-NLS-2$
					fStringMatchers[i]= Pattern.compile(curr);
				}
			}
		}
		return fStringMatchers;
	}

	@Override
	public void acceptTypeNameMatch(TypeNameMatch match) {
		if (inScope(match)) {
			fCollection.add(match);
		}
	}

	private static String getPreference(String key) {
		String val;
		IEclipsePreferences node= InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui"); //$NON-NLS-1$
		if (node != null) {
			val= node.get(key, null);
			if (val != null) {
				return val;
			}
		}
		node= DefaultScope.INSTANCE.getNode("org.eclipse.jdt.ui"); //$NON-NLS-1$
		if (node != null) {
			return node.get(key, null);
		}
	    return null;
	}

}
