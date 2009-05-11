import org.spearce.jgit.diff.EditList;
	/** @return a list describing the content edits performed on this file. */
	public EditList toEditList() {
		final EditList r = new EditList();
		for (final HunkHeader hunk : hunks)
			r.addAll(hunk.toEditList());
		return r;
	}
