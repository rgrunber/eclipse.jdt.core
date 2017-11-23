/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat - Initial Contribution
 *******************************************************************************/
package org.eclipse.jdt.core.tests.model;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaModelManager;

import junit.framework.Test;

public class IndexerPerformanceTest extends AbstractJavaModelTests {

	private static final int NUMBER_OF_RUNS = 50;
	private static final int NUMBER_OF_PROJECTS = 1;
	private static final String LOCATION = Paths.get(System.getProperty("user.home"), ".indexer_performance_data").toString();
	private static final boolean ALLOW_MODEL_CACHE = false;

	public static Test suite() {
		return buildModelTestSuite(IndexerPerformanceTest.class, BYTECODE_DECLARATION_ORDER);
	}

	public IndexerPerformanceTest(String name) {
		super(name);
	}

	public void testSharedIndexBuild () throws Exception {
		ClasspathEntry.setSharedIndexLocation(Paths.get(System.getProperty("user.home"), ".java_runtime_index").toString(), JavaIndexTests.class);
		testOldIndexBuild();
		ClasspathEntry.setSharedIndexLocation(null, JavaIndexTests.class);
	}

	public void testOldIndexBuild () throws Exception {
		System.out.println("Testing " + getName());

		long totalTime = 0;
		for (int i = 1; i <= NUMBER_OF_RUNS; i++) {
			String projName = getName();
			long start = System.currentTimeMillis();
			setupIndex(projName,NUMBER_OF_PROJECTS);
			waitUntilOldIndexReady();
			long elapsed = System.currentTimeMillis() - start;
			totalTime += elapsed;
			System.out.println("Building index took " + elapsed + "ms.");
			deleteAllProjects(projName);
			clearIndex();
		}
		System.out.println("Building index took on average " + (totalTime / NUMBER_OF_RUNS) + "ms.");
	}

	public void testSharedIndexSearch() throws Exception {
		ClasspathEntry.setSharedIndexLocation(Paths.get(System.getProperty("user.home"), ".java_runtime_index").toString(), JavaIndexTests.class);
		testOldIndexSearch();
		ClasspathEntry.setSharedIndexLocation(null, JavaIndexTests.class);
	}

	public void testOldIndexSearch() throws Exception {
		System.out.println("Testing " + getName());
		String projName = getName();
		if (ALLOW_MODEL_CACHE) {
			setupIndex(projName, NUMBER_OF_PROJECTS);
			waitUntilOldIndexReady();
		}
		performIndexHierarchySearch(projName, projName + "_" + String.valueOf(getRandomInRange(1, NUMBER_OF_PROJECTS)));
	}


	private void performIndexHierarchySearch(String projectPrefix, String projectName) throws Exception {
		long totalTime = 0;
		for (int i = 1; i <= NUMBER_OF_RUNS; i++) {
			if (!ALLOW_MODEL_CACHE) {
				setupIndex(projectPrefix, NUMBER_OF_PROJECTS);
				waitUntilOldIndexReady();
			}
			IType type = getClassFile(projectName, getExternalJCLPathString(), "java.lang", "Object.class").getType();
			long start = System.currentTimeMillis();
			ITypeHierarchy hierarchy = type.newTypeHierarchy(null);
			long elapsed = System.currentTimeMillis() - start;
			totalTime += elapsed;
			System.out.println("Searching index took " + elapsed + "ms. (" + hierarchy.getSubclasses(type).length + " classes)");
			if (!ALLOW_MODEL_CACHE) {
				deleteAllProjects(projectPrefix);
				clearIndex();
			}
		}

		System.out.println("Searching index took on average " + (totalTime / NUMBER_OF_RUNS) + "ms.");
	}

	private void setupIndex(String projectNamePrefix, int numOfProjects) throws Exception {
		List<File> sysJars = new ArrayList<>();
		getAllFiles(new File(LOCATION), sysJars);

		int j = 0;
		for (int i = 1; i <= numOfProjects; i++) {
			IJavaProject jProject = createJavaProject(projectNamePrefix + "_" + i);

			// Add the binary jar to the classpath of the java project
			List<IClasspathEntry> newCPE = new ArrayList<>(Arrays.asList(jProject.getRawClasspath()));

			while (j < sysJars.size()) {
				IPath binJarPath = Path.fromOSString(sysJars.get(j).getAbsolutePath());
				IClasspathEntry jarCPE = JavaCore.newLibraryEntry(binJarPath, null, null);
				newCPE.add(jarCPE);
				j++;

				if ((j % (sysJars.size() / NUMBER_OF_PROJECTS)) == 0) {
					break;
				}
			}

			jProject.setRawClasspath(newCPE.toArray(new IClasspathEntry[0]), new NullProgressMonitor());
		}

	}

	private static void getAllFiles (File root, List<File> res) {
		for (File f : root.listFiles()) {
			if (f.getName().endsWith(".jar")) {
				res.add(f);
			} else if (f.isDirectory() && f.canRead()) {
				getAllFiles(f, res);
			}
		}
	}

	private static void clearIndex () {
		JavaModelManager.getIndexManager().deleteIndexFiles(null);
		JavaModelManager.getIndexManager().reset();
	}

	private static void waitUntilOldIndexReady() {
		// JavaCore.rebuildIndex()
		SearchEngine engine = new SearchEngine();
		IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
		try {
			engine.searchAllTypeNames(
				null,
				SearchPattern.R_EXACT_MATCH,
				"!@$#!@".toCharArray(),
				SearchPattern.R_PATTERN_MATCH | SearchPattern.R_CASE_SENSITIVE,
				IJavaSearchConstants.CLASS,
				scope,
				new TypeNameRequestor() {
					public void acceptType(
						int modifiers,
						char[] packageName,
						char[] simpleTypeName,
						char[][] enclosingTypeNames,
						String path) {}
				},
				IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
				null);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private void deleteAllProjects(String projName) throws Exception {
		for (int i = 1; i <= NUMBER_OF_PROJECTS; i++) {
			deleteProject(projName + "_" + i);
		}
	}

	private static int getRandomInRange (int lower, int upper) {
		return (int) (((upper - lower) * Math.random()) + lower);
	}

}
