/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.dltk.core.tests;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.dltk.core.DLTKCore;
import org.eclipse.dltk.core.IAccessRule;
import org.eclipse.dltk.core.IBuildpathAttribute;
import org.eclipse.dltk.core.IBuildpathEntry;
import org.eclipse.dltk.core.IScriptModelMarker;
import org.eclipse.dltk.core.IScriptProject;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.internal.core.BuildpathEntry;
import org.eclipse.dltk.internal.core.ScriptProject;

public class TestingEnvironment {

	private boolean isOpen = false;

	private IWorkspace workspace = null;
	private Hashtable projects = null;

	public void addProject(IProject project) {
		this.projects.put(project.getName(), project);
	}

	public IPath addProject(String projectName) {
		return addProject(projectName, "1.4");
	}

	public IPath addProject(String projectName, String compliance) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		IProject project = createProject(projectName);
		int requiredComplianceFlag = 0;

		IScriptProject ScriptProject = DLTKCore.create(project);

		return project.getFullPath();
	}

	public void addRequiredProject(IPath projectPath, IPath requiredProjectPath)
			throws ModelException {
		addRequiredProject(projectPath, requiredProjectPath,
				new IPath[] {}/* include all */, new IPath[] {}/* exclude none */,
				false);
	}

	/**
	 * Adds a project to the classpath of a project.
	 */
	public void addRequiredProject(IPath projectPath,
			IPath requiredProjectPath, IPath[] accessibleFiles,
			IPath[] nonAccessibleFiles, boolean isExported)
			throws ModelException {
		checkAssertion(
				"required project must not be in project", !projectPath.isPrefixOf(requiredProjectPath)); //$NON-NLS-1$
		IAccessRule[] accessRules = BuildpathEntry.getAccessRules(
				accessibleFiles, nonAccessibleFiles);
		addEntry(projectPath, DLTKCore.newProjectEntry(requiredProjectPath,
				accessRules, true, new IBuildpathAttribute[0], isExported));
	}

	public void addRequiredProject(IPath projectPath,
			IPath requiredProjectPath, IPath rule, int ruleKind)
			throws ModelException {
		checkAssertion(
				"required project must not be in project", !projectPath.isPrefixOf(requiredProjectPath)); //$NON-NLS-1$
		IAccessRule accessRule = DLTKCore.newAccessRule(rule, ruleKind);
		addEntry(projectPath, DLTKCore.newProjectEntry(requiredProjectPath,
				new IAccessRule[] { accessRule }, true,
				new IBuildpathAttribute[0], false));
	}

	public void addRequiredProject(IPath projectPath,
			IPath requiredProjectPath, boolean isOptional)
			throws ModelException {
		checkAssertion(
				"required project must not be in project", !projectPath.isPrefixOf(requiredProjectPath)); //$NON-NLS-1$
		IBuildpathAttribute[] attributes = isOptional ? new IBuildpathAttribute[] { DLTKCore
				.newBuildpathAttribute(IBuildpathAttribute.OPTIONAL, "true") }
				: new IBuildpathAttribute[0];
		addEntry(projectPath, DLTKCore.newProjectEntry(requiredProjectPath,
				null, true, attributes, false));
	}

	public void addExternalFolders(IPath projectPath, String[] folders)
			throws ModelException {
		addExternalFolders(projectPath, folders, false);
	}

	public void addExternalFolders(IPath projectPath, String[] folders,
			boolean isExported) throws ModelException {
		for (int i = 0, max = folders.length; i < max; i++) {
			String folder = folders[i];
			checkAssertion(
					"folder name must not end with .zip or .jar", !folder.endsWith(".zip") && !folder.endsWith(".jar")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			addEntry(projectPath, DLTKCore.newLibraryEntry(new Path(folder),
					null, null, isExported, true));
		}
	}

	public void addLibrary(IPath projectPath, IPath libraryPath,
			IPath sourceAttachmentPath, IPath sourceAttachmentRootPath)
			throws ModelException {
		addEntry(projectPath, DLTKCore.newLibraryEntry(libraryPath));
	}

	public void addEntry(IPath projectPath, IBuildpathEntry entryPath)
			throws ModelException {
		IBuildpathEntry[] Buildpath = getBuildpath(projectPath);
		IBuildpathEntry[] newClaspath = new IBuildpathEntry[Buildpath.length + 1];
		System.arraycopy(Buildpath, 0, newClaspath, 0, Buildpath.length);
		newClaspath[Buildpath.length] = entryPath;
		setBuildpath(projectPath, newClaspath);
	}

	/**
	 * Adds a file.
	 */
	public IPath addFile(IPath root, String fileName, String contents) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		IPath filePath = root.append(fileName);
		try {
			createFile(filePath, contents.getBytes("UTF8")); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			checkAssertion("e1", false); //$NON-NLS-1$
		}
		return filePath;
	}

	/**
	 * Adds a folder.
	 */
	public IPath addFolder(IPath root, String folderName) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		IPath folderPath = root.append(folderName);
		createFolder(folderPath);
		return folderPath;
	}

	private void checkAssertion(String message, boolean b) {
		Assert.isTrue(b, message);
	}

	public void cleanBuild() {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		try {
			getWorkspace().build(IncrementalProjectBuilder.CLEAN_BUILD, null);
		} catch (CoreException e) {
			handle(e);
		}
	}

	/**
	 * Closes the testing environment and frees up any resources. Once the
	 * testing environment is closed, it shouldn't be used any more.
	 */
	public void close() {
		try {
			if (this.projects != null) {
				Enumeration projectNames = this.projects.keys();
				while (projectNames.hasMoreElements()) {
					String projectName = (String) projectNames.nextElement();
					getScriptProject(projectName).getModel().close();
				}
			}
			closeWorkspace();
		} catch (ModelException e) {
			e.printStackTrace();
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Close a project from the workspace.
	 */
	public void closeProject(IPath projectPath) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		try {
			getScriptProject(projectPath).getProject().close(null);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private void closeWorkspace() {
		this.isOpen = false;
	}

	private IFile createFile(IPath path, byte[] contents) {
		try {
			IFile file = this.workspace.getRoot().getFile(path);

			ByteArrayInputStream is = new ByteArrayInputStream(contents);
			if (file.exists()) {
				file.setContents(is, true, false, null);
			} else {
				file.create(is, true, null);
			}
			return file;
		} catch (CoreException e) {
			handle(e);
		}
		return null;
	}

	private IFolder createFolder(IPath path) {
		checkAssertion("root", !path.isRoot()); //$NON-NLS-1$

		/* don't create folders for projects */
		if (path.segmentCount() <= 1) {
			return null;
		}

		IFolder folder = this.workspace.getRoot().getFolder(path);
		if (!folder.exists()) {
			/* create the parent folder if necessary */
			createFolder(path.removeLastSegments(1));

			try {
				folder.create(true, true, null);
			} catch (CoreException e) {
				handle(e);
			}
		}
		return folder;
	}

	private IProject createProject(String projectName) {
		final IProject project = this.workspace.getRoot().getProject(
				projectName);
		try {
			IWorkspaceRunnable create = new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					project.create(null, null);
					project.open(null);
				}
			};
			this.workspace.run(create, null);
			this.projects.put(projectName, project);
		} catch (CoreException e) {
			handle(e);
		}
		return project;
	}

	/**
	 * Batch builds the workspace. A workspace must be open.
	 */
	public void fullBuild() {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		try {
			getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
		} catch (CoreException e) {
			handle(e);
		}
	}

	/**
	 * Batch builds a project. A workspace must be open.
	 */
	public void fullBuild(IPath projectPath) {
		fullBuild(projectPath.lastSegment());
	}

	/**
	 * Batch builds a project. A workspace must be open.
	 */
	public void fullBuild(String projectName) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		try {
			getProject(projectName).build(IncrementalProjectBuilder.FULL_BUILD,
					null);
		} catch (CoreException e) {
			handle(e);
		}
	}

	/**
	 * Returns the class path.
	 */
	public IBuildpathEntry[] getBuildpath(IPath projectPath) {
		try {
			checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
			ScriptProject ScriptProject = (ScriptProject) DLTKCore
					.create(getProject(projectPath));
			return ScriptProject.getExpandedBuildpath(true);
			// IPath[] packageFragmentRootsPath = new IPath[entries.length];
			// for (int i = 0; i < entries.length; ++i)
			// packageFragmentRootsPath[i] = entries[i].getPath();
			// return packageFragmentRootsPath;
		} catch (ModelException e) {
			e.printStackTrace();
			checkAssertion("ModelException", false); //$NON-NLS-1$
			return null; // not reachable
		}
	}

	/**
	 * Returns the Java Model element for the project.
	 */
	public IScriptProject getScriptProject(IPath projectPath) {
		IScriptProject ScriptProject = DLTKCore.create(getProject(projectPath));
		Assert.isNotNull(ScriptProject);
		return ScriptProject;
	}

	/**
	 * Returns the Java Model element for the project.
	 */
	public IScriptProject getScriptProject(String projectName) {
		IScriptProject ScriptProject = DLTKCore.create(getProject(projectName));
		Assert.isNotNull(ScriptProject);
		return ScriptProject;
	}

	/**
	 * Return all problems with workspace.
	 */
	public Problem[] getProblems() {
		return getProblemsFor(getWorkspaceRootPath());
	}

	/**
	 * Return all problems with the specified element.
	 */
	public Problem[] getProblemsFor(IPath path) {
		return getProblemsFor(path, null);
	}

	/**
	 * Return all problems with the specified element.
	 */
	public Problem[] getProblemsFor(IPath path, String additionalMarkerType) {
		IResource resource;
		if (path.equals(getWorkspaceRootPath())) {
			resource = getWorkspace().getRoot();
		} else {
			IProject p = getProject(path);
			if (p != null && path.equals(p.getFullPath())) {
				resource = getProject(path.lastSegment());
			} else if (path.getFileExtension() == null) {
				resource = getWorkspace().getRoot().getFolder(path);
			} else {
				resource = getWorkspace().getRoot().getFile(path);
			}
		}
		try {
			ArrayList problems = new ArrayList();
			IMarker[] markers = resource.findMarkers(
					IScriptModelMarker.DLTK_MODEL_PROBLEM_MARKER, true,
					IResource.DEPTH_INFINITE);
			for (int i = 0; i < markers.length; i++)
				problems.add(new Problem(markers[i]));

			markers = resource.findMarkers(
					IScriptModelMarker.BUILDPATH_PROBLEM_MARKER, true,
					IResource.DEPTH_INFINITE);
			for (int i = 0; i < markers.length; i++)
				problems.add(new Problem(markers[i]));

			markers = resource.findMarkers(IScriptModelMarker.TASK_MARKER,
					true, IResource.DEPTH_INFINITE);
			for (int i = 0; i < markers.length; i++)
				problems.add(new Problem(markers[i]));

			if (additionalMarkerType != null) {
				markers = resource.findMarkers(additionalMarkerType, true,
						IResource.DEPTH_INFINITE);
				for (int i = 0; i < markers.length; i++)
					problems.add(new Problem(markers[i]));
			}

			Problem[] result = new Problem[problems.size()];
			problems.toArray(result);
			return result;
		} catch (CoreException e) {
			// ignore
		}
		return new Problem[0];
	}

	/**
	 * Return all problems with the specified element.
	 */
	public IMarker[] getTaskMarkersFor(IPath path) {
		IResource resource = null;
		if (path.equals(getWorkspaceRootPath())) {
			resource = getWorkspace().getRoot();
		} else {
			IProject p = getProject(path);
			if (p != null && path.equals(p.getFullPath())) {
				resource = getProject(path.lastSegment());
			} else if (path.getFileExtension() == null) {
				resource = getWorkspace().getRoot().getFolder(path);
			} else {
				resource = getWorkspace().getRoot().getFile(path);
			}
		}
		try {
			if (resource != null) {
				final IMarker[] markers = resource.findMarkers(
						IScriptModelMarker.TASK_MARKER, true,
						IResource.DEPTH_INFINITE);
				if (markers.length > 1) {
					Arrays.sort(markers, new Comparator() {
						public int compare(Object o1, Object o2) {
							IMarker marker1 = (IMarker) o1;
							IMarker marker2 = (IMarker) o2;
							try {
								final int start1 = ((Integer) marker1
										.getAttribute(IMarker.CHAR_START))
										.intValue();
								final int start2 = ((Integer) marker2
										.getAttribute(IMarker.CHAR_START))
										.intValue();
								return start1 - start2;
							} catch (CoreException e) {
								return 0;
							}
						}
					});
				}
				return markers;
			}
		} catch (CoreException e) {
			// ignore
		}
		return new IMarker[0];
	}

	/**
	 * Return the path of the package with the given name. A workspace must be
	 * open, and the package must exist.
	 */
	public IPath getPackagePath(IPath root, String packageName) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		if (packageName.length() == 0) {
			return root;
		}
		return root.append(packageName.replace('.', IPath.SEPARATOR));
	}

	/**
	 * Return the path of the package fragment root with the given name. A
	 * workspace must be open, and the package fragment root must exist.
	 */
	public IPath getPackageFragmentRootPath(IPath projectPath, String name) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		if (name.length() == 0) {
			return projectPath;
		}
		return projectPath.append(name);
	}

	/**
	 * Returns the core project.
	 */
	public IProject getProject(String projectName) {
		return (IProject) this.projects.get(projectName);
	}

	/**
	 * Returns the core project.
	 */
	public IProject getProject(IPath projectPath) {
		return (IProject) this.projects.get(projectPath.lastSegment());
	}

	private File tmpDirectory;

	File getTmpDirectory() {
		if (this.tmpDirectory == null) {
			this.tmpDirectory = new File(System.getProperty("java.io.tmpdir")
					+ File.separator + "org.eclipse.jdt.core.builder.tests.tmp");
			if (this.tmpDirectory.exists() && !this.tmpDirectory.isDirectory()) {
				// Util.delete(this.tmpDirectory);
				this.tmpDirectory.delete();
			}
			this.tmpDirectory.mkdir();
		}
		return this.tmpDirectory;
	}

	void deleteTmpDirectory() {
		if (this.tmpDirectory != null) {
			this.tmpDirectory.delete();
			this.tmpDirectory = null;
		}
	}

	/**
	 * Returns the workspace.
	 */
	public IWorkspace getWorkspace() {
		return this.workspace;
	}

	/**
	 * Returns the path of workspace root.
	 */
	public IPath getWorkspaceRootPath() {
		return getWorkspace().getRoot().getLocation();
	}

	private IPath getJarRootPath(IPath projectPath) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		return getProject(projectPath).getFullPath();
	}

	void handle(Exception e) {
		if (e instanceof CoreException) {
			handleCoreException((CoreException) e);
		} else {
			e.printStackTrace();
			Assert.isTrue(false);
		}
	}

	/**
	 * Handles a core exception thrown during a testing environment operation
	 */
	private void handleCoreException(CoreException e) {
		e.printStackTrace();
		IStatus status = e.getStatus();
		String message = e.getMessage();
		if (status.isMultiStatus()) {
			MultiStatus multiStatus = (MultiStatus) status;
			IStatus[] children = multiStatus.getChildren();
			StringBuffer buffer = new StringBuffer();
			for (int i = 0, max = children.length; i < max; i++) {
				IStatus child = children[i];
				if (child != null) {
					buffer.append(child.getMessage());
					buffer.append(System.getProperty("line.separator"));//$NON-NLS-1$
					Throwable childException = child.getException();
					if (childException != null) {
						childException.printStackTrace();
					}
				}
			}
			message = buffer.toString();
		}
		Assert.isTrue(false,
				"Core exception in testing environment: " + message); //$NON-NLS-1$
	}

	/**
	 * Incrementally builds the workspace. A workspace must be open.
	 */
	public void incrementalBuild() {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		try {
			getWorkspace().build(IncrementalProjectBuilder.INCREMENTAL_BUILD,
					null);
		} catch (CoreException e) {
			handle(e);
		}
	}

	/**
	 * Incrementally builds a project. A workspace must be open.
	 */
	public void incrementalBuild(IPath projectPath) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		try {
			getProject(projectPath).build(
					IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
		} catch (CoreException e) {
			handle(e);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public boolean isAutoBuilding() {
		IWorkspace w = getWorkspace();
		IWorkspaceDescription d = w.getDescription();
		return d.isAutoBuilding();
	}

	/**
	 * Open an empty workspace.
	 */
	public void openEmptyWorkspace() {
		close();
		openWorkspace();
		this.projects = new Hashtable(10);
		setup();
	}

	/**
	 * Close a project from the workspace.
	 */
	public void openProject(IPath projectPath) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		try {
			getScriptProject(projectPath).getProject().open(null);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private void openWorkspace() {
		try {
			closeWorkspace();

			this.workspace = ResourcesPlugin.getWorkspace();

			// turn off auto-build -- the tests determine when builds occur
			IWorkspaceDescription description = this.workspace.getDescription();
			description.setAutoBuilding(false);
			this.workspace.setDescription(description);
		} catch (Exception e) {
			handle(e);
		}
	}

	/**
	 * Renames a compilation unit int the given package in the workspace. A
	 * workspace must be open.
	 */
	public void renameCU(IPath packagePath, String cuName, String newName) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		IFolder packageFolder = this.workspace.getRoot().getFolder(packagePath);
		try {
			packageFolder.getFile(cuName).move(
					packageFolder.getFile(newName).getFullPath(), true, null);
		} catch (CoreException e) {
			handle(e);
		}
	}

	/**
	 * Removes a binary class from the given package in the workspace. A
	 * workspace must be open, and the given class name must not end with
	 * ".class".
	 */
	public void removeBinaryClass(IPath packagePath, String className) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		className += ".class"; //$NON-NLS-1$
		IFolder packageFolder = this.workspace.getRoot().getFolder(packagePath);
		try {
			packageFolder.getFile(className).delete(true, null);
		} catch (CoreException e) {
			handle(e);
		}
	}

	/**
	 * Removes a class from the given package in the workspace. A workspace must
	 * be open, and the given class name must not end with ".java".
	 */
	public void removeClass(IPath packagePath, String className) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		className += ".java"; //$NON-NLS-1$
		IFolder packageFolder = this.workspace.getRoot().getFolder(packagePath);
		try {
			packageFolder.getFile(className).delete(true, null);
		} catch (CoreException e) {
			handle(e);
		}
	}

	/**
	 * Removes a package from the given package fragment root in the workspace.
	 * A workspace must be open.
	 */
	public void removePackage(IPath packageFragmentRootPath, String packageName) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		IPath path = packageFragmentRootPath.append(packageName.replace('.',
				IPath.SEPARATOR));
		IFolder folder = this.workspace.getRoot().getFolder(path);
		try {
			folder.delete(false, null);
		} catch (CoreException e) {
			handle(e);
		}
	}

	/**
	 * Removes the given package fragment root from the the workspace. A
	 * workspace must be open.
	 */
	public void removePackageFragmentRoot(IPath projectPath,
			String packageFragmentRootName) throws ModelException {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		if (packageFragmentRootName.length() > 0) {
			IFolder folder = getProject(projectPath).getFolder(
					packageFragmentRootName);
			if (folder.exists()) {
				try {
					folder.delete(false, null);
				} catch (CoreException e) {
					handle(e);
				}
			}
		}
		IPath rootPath = getPackageFragmentRootPath(projectPath,
				packageFragmentRootName);
		removeEntry(projectPath, rootPath);
	}

	/**
	 * Remove a project from the workspace.
	 */
	public void removeProject(IPath projectPath) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		try {
			getScriptProject(projectPath).close();
		} catch (ModelException e) {
			e.printStackTrace();
		}
		IProject project = getProject(projectPath);
		try {
			project.delete(true, null);
		} catch (CoreException e) {
			handle(e);
		}

	}

	/**
	 * Remove a required project from the Buildpath
	 */
	public void removeRequiredProject(IPath projectPath, IPath requiredProject)
			throws ModelException {
		removeEntry(projectPath, requiredProject);
	}

	/**
	 * Remove all elements in the workspace.
	 */
	public void resetWorkspace() {
		if (this.projects != null) {
			Enumeration projectNames = this.projects.keys();
			while (projectNames.hasMoreElements()) {
				String projectName = (String) projectNames.nextElement();
				removeProject(getProject(projectName).getFullPath());
			}
		}
	}

	private void removeEntry(IPath projectPath, IPath entryPath)
			throws ModelException {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		IBuildpathEntry[] oldEntries = getBuildpath(projectPath);
		for (int i = 0; i < oldEntries.length; ++i) {
			if (oldEntries[i].getPath().equals(entryPath)) {
				IBuildpathEntry[] newEntries = new IBuildpathEntry[oldEntries.length - 1];
				System.arraycopy(oldEntries, 0, newEntries, 0, i);
				System.arraycopy(oldEntries, i + 1, newEntries, i,
						oldEntries.length - i - 1);
				setBuildpath(projectPath, newEntries);
			}
		}
	}

	/**
	 * Remove a file
	 */
	public void removeFile(IPath filePath) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		try {
			this.workspace.getRoot().getFile(filePath).delete(true, null);
		} catch (CoreException e) {
			handle(e);
		}
	}

	/**
	 * Remove a folder
	 */
	public void removeFolder(IPath folderPath) {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		IFolder folder = this.workspace.getRoot().getFolder(folderPath);
		try {
			folder.delete(true, null);
		} catch (CoreException e) {
			handle(e);
		}
	}

	/**
	 * Sets the Buildpath to the given package fragment roots. The builder
	 * searches the Buildpath to find the java files it needs during a build.
	 */
	// public void setBuildpath(IPath projectPath, IPath[]
	// packageFragmentRootsPath) {
	// try {
	//			checkAssertion("a workspace must be open", fIsOpen); //$NON-NLS-1$
	// IScriptProject ScriptProject = DLTKCore.create(getProject(projectPath));
	// IBuildpathEntry[] entries =
	// new IBuildpathEntry[packageFragmentRootsPath.length];
	// for (int i = 0; i < packageFragmentRootsPath.length; ++i) {
	// IPath path = packageFragmentRootsPath[i];
	//				if ("jar".equals(path.getFileExtension()) //$NON-NLS-1$
	//					|| "zip".equals(path.getFileExtension())) { //$NON-NLS-1$
	// entries[i] = DLTKCore.newLibraryEntry(path, null, null, isExported);
	// } else if (projectPath.isPrefixOf(packageFragmentRootsPath[i])) {
	// entries[i] = DLTKCore.newSourceEntry(path, IPath[] exclusionPatterns,
	// IPath specificOutputLocation)
	// } else {
	// entries[i] = DLTKCore.newProjectEntry(path, isExported);
	// }
	// }
	// ScriptProject.setRawBuildpath(entries, null);
	// } catch (ModelException e) {
	// e.printStackTrace();
	//			checkAssertion("ModelException", false); //$NON-NLS-1$
	// }
	// }
	public void setAutoBuilding(boolean value) {
		try {
			IWorkspace w = getWorkspace();
			IWorkspaceDescription d = w.getDescription();
			d.setAutoBuilding(value);
			w.setDescription(d);
		} catch (CoreException e) {
			e.printStackTrace();
			checkAssertion("CoreException", false); //$NON-NLS-1$
		}
	}

	public void setBuildOrder(String[] projects) {
		try {
			IWorkspace w = getWorkspace();
			IWorkspaceDescription d = w.getDescription();
			d.setBuildOrder(projects);
			w.setDescription(d);
		} catch (CoreException e) {
			e.printStackTrace();
			checkAssertion("CoreException", false); //$NON-NLS-1$
		}
	}

	public void setBuildpath(IPath projectPath, IBuildpathEntry[] entries)
			throws ModelException {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		IScriptProject ScriptProject = DLTKCore.create(getProject(projectPath));
		ScriptProject.setRawBuildpath(entries, null);
	}

	public IPath setExternalOutputFolder(IPath projectPath, String name,
			IPath externalOutputLocation) {
		IPath result = null;
		try {
			checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
			IProject p = getProject(projectPath);
			IFolder f = p.getFolder(name);
			f.createLink(externalOutputLocation, IResource.ALLOW_MISSING_LOCAL,
					null);

			result = f.getFullPath();
			IScriptProject ScriptProject = DLTKCore.create(p);
		} catch (CoreException e) {
			e.printStackTrace();
			checkAssertion("CoreException", false); //$NON-NLS-1$
		}
		return result;
	}

	private void setup() {
		this.isOpen = true;
	}

	/**
	 * Wait for autobuild notification to occur
	 */
	public void waitForAutoBuild() {
		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
		boolean wasInterrupted = false;
		do {
			try {
				Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD,
						null);
				wasInterrupted = false;
			} catch (OperationCanceledException e) {
				handle(e);
			} catch (InterruptedException e) {
				wasInterrupted = true;
			}
		} while (wasInterrupted);
	}

//	public void waitForManualRefresh() {
//		checkAssertion("a workspace must be open", this.isOpen); //$NON-NLS-1$
//		boolean wasInterrupted = false;
//		do {
//			try {
//				Job.getJobManager().join(ResourcesPlugin.FAMILY_MANUAL_REFRESH,
//						null);
//				wasInterrupted = false;
//			} catch (OperationCanceledException e) {
//				handle(e);
//			} catch (InterruptedException e) {
//				wasInterrupted = true;
//			}
//		} while (wasInterrupted);
//	}

}
