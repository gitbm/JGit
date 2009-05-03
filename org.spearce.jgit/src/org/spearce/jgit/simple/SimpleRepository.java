/*
 * Copyright (C) 2009, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2009, Mark Struberg <struberg@yahoo.de>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Git Development Community nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.spearce.jgit.simple;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.spearce.jgit.errors.NotSupportedException;
import org.spearce.jgit.errors.RevisionSyntaxException;
import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.ignore.IgnoreRules;
import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.GitIndex;
import org.spearce.jgit.lib.NullProgressMonitor;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefComparator;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.RepositoryConfig;
import org.spearce.jgit.lib.Tree;
import org.spearce.jgit.lib.WorkDirCheckout;
import org.spearce.jgit.lib.GitIndex.Entry;
import org.spearce.jgit.lib.RefUpdate.Result;
import org.spearce.jgit.transport.FetchResult;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.RemoteConfig;
import org.spearce.jgit.transport.Transport;
import org.spearce.jgit.transport.URIish;
import org.spearce.jgit.util.Validate;

/**
 * High level operations to work with a {@code Repository}
 * This class contains a lot  
 */
public class SimpleRepository {

	/**
	 * the Repository all the operations should work on
	 */
	private Repository db;

	/**
	 * handle all ignore rules for this repository
	 */
	private IgnoreRules ignores;
	
	/**
	 * Factory method for creating a SimpleRepository analog to git-init
	 * in a working directory. 
	 * @param repoName name of the repository
	 * @return the freshly initialised {@link SimpleRepository}
	 * @throws IOException 
	 */
	public static SimpleRepository init(String repoName) 
	throws IOException {
		Validate.notNull(repoName, "workdir must not be null!");
		SimpleRepository repo = new SimpleRepository();
		repo.initRepository(repoName, ".git");
		return repo;
	}
	
	/**
	 * Create a SimpleRepository for an already existing local git 
	 * repository structure.
	 * 
	 * @param repoName name of the existing git repository 
	 * @return {@link SimpleRepository} or <code>null</code> if the given repoName doesn't contain a git repository
	 * @throws Exception 
	 */
	public static SimpleRepository existing(String repoName)
	throws Exception {
		Validate.notNull(repoName, "workdir must not be null!");
		
		final File repoDir = new File(repoName, ".git");
		if (!repoDir.exists()) {
			return null;
		}
		
		SimpleRepository repo = new SimpleRepository();

		repo.db = new Repository(repoDir);
		repo.ignores = new IgnoreRules(repo.db);

		return repo;
	}
	
	/**
	 * Factory method for creating a SimpleRepository analog to git-clone
	 * in a working directory. This will also checkout the content.  
	 * 
	 * @param repoName
	 * @param remoteName 
	 * @param uri 
	 * @param branch 
	 * @param monitor for showing the progress. If <code>null</code> a {@code NullProgressMonitor} will be used
	 * @return the freshly cloned {@link SimpleRepository}
	 * @throws IOException 
	 * @throws URISyntaxException 
	 */
	public static SimpleRepository clone(String repoName, String remoteName, URIish uri, String branch, ProgressMonitor monitor) 
	throws IOException, URISyntaxException {
		SimpleRepository repo = new SimpleRepository();
		repo.initRepository(repoName, ".git");
		
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}

		repo.addRemote(remoteName, uri, branch, true, null);
		Ref head = repo.fetch(remoteName, monitor);
		repo.checkout(head.getObjectId(), head.getName());
		return repo;
	}
	
	/**
	 * Factory method for creating a SimpleRepository based on a 
	 * existing {@link Repository}
	 * @param db the {@link Repository} to wrap
	 * @return a new SimpleRepository which uses the given {@link Repository}
	 */
	public static SimpleRepository wrap(Repository db) {
		SimpleRepository repo = new SimpleRepository();
		repo.db = db;
		return repo;
	}

	/**
	 * A SimpleRepository may only be created with one of the factory methods.
	 * @see #init(String)
	 * @see #clone(String, String, URIish, String, ProgressMonitor)
	 * @see #wrap(Repository)
	 */
	private SimpleRepository() {
		// private ct to disallow external object creation
	}
		
	/**
	 * @return the underlying {@link Repository}
	 */
	public Repository getRepository() {
		return db;
	}
	
	/**
	 * Close the underlying repository
	 */
	public void close() {
		db.close();
	}
	
	/**
	 * Init a freshl local {@code Repository} int the gitDir
	 * @param repoName of the repository
	 * @param gitDir usually <code>.git</code>
	 * @throws IOException 
	 */
	private void initRepository(String repoName, String gitDir) 
	throws IOException {
		final File repoDir = new File(repoName, gitDir);
		db = new Repository(repoDir);
		db.create();

		db.getConfig().setBoolean("core", null, "bare", false);
		db.getConfig().save();
		
		ignores = new IgnoreRules(db);
	}
	
	/**
	 * Setup a new remote reference.
	 * 
	 * @param remoteName like 'origin'
	 * @param uri to clone from 
	 * @param branchName to clone initially, e.g. <code>master</code>
	 * @param allSelected
	 *            true when all branches have to be fetched (indicates wildcard
	 *            in created fetch refspec), false otherwise.
	 * @param selectedBranches
	 *            collection of branches to fetch. Ignored when allSelected is
	 *            true.
	 * @throws URISyntaxException 
	 * @throws IOException 
	 */
	public void addRemote(final String remoteName, final URIish uri, final String branchName, 
			              final boolean allSelected, final Collection<Ref> selectedBranches) 
	throws URISyntaxException, IOException {
		Validate.notNull(branchName, "cannot checkout; no HEAD advertised by remote {0}", uri);
		
		// add remote configuration
		final RemoteConfig rc = new RemoteConfig(db.getConfig(), remoteName);
		rc.addURI(uri);

		final String dst = Constants.R_REMOTES + rc.getName();
		RefSpec wcrs = new RefSpec();
		wcrs = wcrs.setForceUpdate(true);
		wcrs = wcrs.setSourceDestination(Constants.R_HEADS + "*", dst + "/*");

		if (allSelected) {
			rc.addFetchRefSpec(wcrs);
		} else {
			for (final Ref ref : selectedBranches)
				if (wcrs.matchSource(ref))
					rc.addFetchRefSpec(wcrs.expandFromSource(ref));
		}

		rc.update(db.getConfig());
		db.getConfig().save();

		// setup the default remote branch for branchName
		db.getConfig().setString(RepositoryConfig.BRANCH_SECTION,
				branchName, "remote", remoteName);
		db.getConfig().setString(RepositoryConfig.BRANCH_SECTION,
				branchName, "merge", Constants.R_HEADS + branchName);

		db.getConfig().save();
	}

	/**
	 * Fetch all new objects from the given branches/tags (want) 
	 * from the foreign uri.
	 * 
	 * @param uri either a foreign git uri 
	 * @param monitor for showing the progress. If <code>null</code> a {@code NullProgressMonitor} will be used
	 * @throws IOException 
	 * @throws URISyntaxException 
	 */
	  public void fetch(final URIish uri, ProgressMonitor monitor) 
	  throws URISyntaxException, IOException {
		Set<String> want = Collections.emptySet();
	    fetch(uri, want, monitor);
	  }
	  
	/**
	 * Fetch all new objects from the given branches/tags (want) 
	 * from the foreign uri.
	 * 
	 * @param uri either a foreign git uri 
	 * @param want Set of branches, tags, etc which should be fetched
	 * @param monitor for showing the progress. If <code>null</code> a {@code NullProgressMonitor} will be used
	 * @throws URISyntaxException 
	 * @throws IOException 
	 */
	public void fetch(final URIish uri, Set<String> want, ProgressMonitor monitor) 
	throws URISyntaxException, IOException {
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		
		final Transport tn = Transport.open(db, uri);
		
		FetchResult fetchResult;
		try {
			fetchResult = tn.fetch(monitor, null);
		} finally {
			tn.close();
		}
		final Ref head = fetchResult.getAdvertisedRef(Constants.HEAD);
		if (head == null || head.getObjectId() == null) {
			return;
		}
	}

	
	/**
	 * Fetch the current branch from the given remoteName and merge the changes
	 * into the actual HEAD. 
	 * @param remoteName
	 */
	public void pull(String remoteName) {
		//X TODO
	}
	
	/**
	 * Fetch the indicated branch from the given remoteName and merge the changes
	 * into the actual HEAD. 
	 * @param uri
	 * @param branch
	 */
	public void pull(URIish uri, String branch) {
		//X TODO
	}

	/**
	 * Checkout the given branch from the local repository.
	 * This command makes no remote connections!
	 * 
	 * @param branchName or refspec, e.g. &quot;master&quot;
	 * @param monitor for showing the progress. If <code>null</code> a {@code NullProgressMonitor} will be used
	 * @throws IOException 
	 */
	public void checkout(String branchName, ProgressMonitor monitor) throws IOException {
		Validate.notNull(branchName, "branch must not be null");
		
		if (!Constants.HEAD.equals(branchName)) {
			db.writeSymref(Constants.HEAD, branchName);
		}
		
		ObjectId headId = db.resolve(branchName);

		if (headId == null) {
			throw new RevisionSyntaxException(branchName, "cannot find head of branch ");
		}
		
		checkout(headId, branchName);
	}


	/**
	 * Add a given file or directory to the index.
	 * @param toAdd file or directory which should be added
	 * @return a List with all added files
	 * @throws Exception
	 */
	public List<File> add(File toAdd) 
	throws Exception {
		List<File> addedFiles = new ArrayList<File>();
		
		if  (toAdd == null) {
			throw new IllegalArgumentException("toAdd must not be null!");
		}
		
		if (!toAdd.getAbsolutePath().startsWith(db.getWorkDir().getAbsolutePath())) {
			throw new IllegalArgumentException("toAdd must be within repository " + db.getWorkDir());
		}
		
		// the relative path inside the repo
		String repoPath =  toAdd.getAbsolutePath().substring(db.getWorkDir().getAbsolutePath().length());
		
		GitIndex index = db.getIndex();
		
		//check for ignored files!
		if (ignores.isIgnored(toAdd)) {
			return addedFiles;
		}

		if (toAdd.isDirectory()) {
			for(File f : toAdd.listFiles()) {
				// recursively add files
				addedFiles.addAll(add(f));
			}
		} else {
			Entry entry = index.getEntry(repoPath);
			if (entry != null) {
				if (!entry.isAssumedValid()) {
					System.out.println("Already tracked - skipping");
					return addedFiles;
				}
			}
		}

		//X TODO this should be implemented using DirCache!
		Entry entry = index.add(db.getWorkDir(), toAdd);
		entry.setAssumeValid(false);
		
		index.write();
		
		addedFiles.add(toAdd);
		
		return addedFiles;
	}

	/**
	 * Fetch from the given remote and try to detect the advertised head.
	 * This function is used by {@code #clone(File, String, URIish, String, ProgressMonitor)} 
	 * @param remoteName
	 * @param monitor
	 * @return Ref with the detected head
	 * @throws NotSupportedException
	 * @throws URISyntaxException
	 * @throws TransportException
	 */
	private Ref fetch(final String remoteName, ProgressMonitor monitor) 
	throws NotSupportedException, URISyntaxException, TransportException {
		final Transport tn = Transport.open(db, remoteName);
		
		FetchResult fetchResult;
		try {
			fetchResult = tn.fetch(monitor, null);
		} finally {
			tn.close();
		}
		return guessHEAD(fetchResult);
	}
	
	/**
	 * guess the head from the advertised Ref of the FetchResult
	 * @param result
	 * @return Ref with the detected head
	 */
	private Ref guessHEAD(final FetchResult result) {
		final Ref idHEAD = result.getAdvertisedRef(Constants.HEAD);
		final List<Ref> availableRefs = new ArrayList<Ref>();
		Ref head = null;
		for (final Ref r : result.getAdvertisedRefs()) {
			final String n = r.getName();
			if (!n.startsWith(Constants.R_HEADS))
				continue;
			availableRefs.add(r);
			if (idHEAD == null || head != null)
				continue;
			if (r.getObjectId().equals(idHEAD.getObjectId()))
				head = r;
		}
		Collections.sort(availableRefs, RefComparator.INSTANCE);
		if (idHEAD != null && head == null)
			head = idHEAD;
		return head;
	}

	/**
	 * Checkout the headId into the working directory
	 * @param headId
	 * @param branch internal branch name, e.g. refs/heads/master
	 * @throws IOException
	 */
	private void checkout(ObjectId headId, String branch) throws IOException {
		if (!Constants.HEAD.equals(branch))
			db.writeSymref(Constants.HEAD, branch);

		final Commit commit = db.mapCommit(headId);
		final RefUpdate u = db.updateRef(Constants.HEAD);
		u.setNewObjectId(commit.getCommitId());
		Result result = u.forceUpdate();
		
		//X TODO REMOVE DEBUGGING OUTPUT!
		System.out.println("updateRef " + u + " returned Result=" + result);

		final GitIndex index = new GitIndex(db);
		final Tree tree = commit.getTree();
		final WorkDirCheckout co;

		co = new WorkDirCheckout(db, db.getWorkDir(), index, tree);
		co.checkout();
		index.write();
	}

}