package jgit

import jgit.MergeResult.MergeResult
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.gitective.core.filter.commit.{AllCommitFilter, CommitCountFilter, DiffFileCountFilter, DiffLineCountFilter}
import org.gitective.core.{CommitFinder, CommitUtils}

/**
 * Extensions for the JGit library
 */
object JGitExtensions {
  /**
   * Enrichment of the [[org.eclipse.jgit.lib.Repository]] class.
   * @param repo The repository object.
   */
  implicit class RichRepository(repo: Repository) {
    /**
     * Checks if `branch` can be merged into `head`. The merge is done in-memory.
     * @param branch The branch to be merged.
     * @param head The head branch, where `branch` is merged into.
     * @return True iff the merge was successful.
     */
    def isMergeable(branch: String, head: String): MergeResult = {
      val branchId = repo resolve branch
      val headId = repo resolve head

      if (branchId == null || headId == null)
        return MergeResult.Error

      isMergeable(branchId, headId)
    }

    /**
     * Checks if `branch` can be merged into `head`. The merge is done in-memory.
     * @param branch The branch to be merged.
     * @param head The head branch, where `branch` is merged into.
     * @return True iff the merge was successful.
     */
    def isMergeable(branch: ObjectId, head: ObjectId): MergeResult = {
      val revWalk = new RevWalk(repo)

      val branchCommit = revWalk.lookupCommit(branch)
      val headCommit = revWalk.lookupCommit(head)

      // Check if already up-to-date
      if (revWalk.isMergedInto(branchCommit, headCommit))
        return MergeResult.Merged

      // Check for fast-forward
      if (revWalk.isMergedInto(headCommit, branchCommit))
        return MergeResult.Merged

      try {
        // Do the actual merge here (in memory)
        val merger = new JGitMemoryMerger(repo)
        val result = merger.merge(headCommit, branchCommit)
        // merger.(getMergeResults|getFailingPaths|getUnmergedPaths)
        if (result) MergeResult.Merged else MergeResult.Conflict
      } catch {
        case _: Exception => MergeResult.Error
      }
    }

    /**
     * Calculates the number of commits between two commits.
     * @param objectId One end of the chain.
     * @param otherId The other end of the chain.
     * @return The distance.
     */
    def distance(objectId: ObjectId, otherId: ObjectId): Long = {
      val base: RevCommit = CommitUtils.getBase(repo, objectId, otherId)
      val count = new CommitCountFilter
      val finder = new CommitFinder(repo).setFilter(count)

      finder.findBetween(objectId, base)
      val num = count.getCount
      count.reset()

      finder.findBetween(otherId, base)
      num + count.getCount
    }

    /**
     * Calculates the number of diff lines between two commits.
     * @param objectId One end of the chain.
     * @param otherId The other end of the chain.
     * @return The number of added/edited/deleted lines.
     */
    def stats(objectId: ObjectId, otherId: ObjectId, detectRenames: Boolean = true): Stats = {
      val base = CommitUtils.getBase(repo, objectId, otherId)
      val diffCount = new DiffLineCountFilter(detectRenames)
      val fileNames = new DiffFileNameFilter(detectRenames)
      val commitCount = new CommitCountFilter()
      val filter = new AllCommitFilter(diffCount, fileNames, commitCount)
      val finder = new CommitFinder(repo).setFilter(filter)

      try {
        finder.findBetween(objectId, base)
        val statistics = Stats(diffCount.getAdded, diffCount.getEdited, diffCount.getDeleted, fileNames.getFiles, commitCount.getCount)

        if (otherId == base)
          return statistics

        filter.reset()
        finder.findBetween(otherId, base)
        statistics + Stats(diffCount.getAdded, diffCount.getEdited, diffCount.getDeleted, fileNames.getFiles, commitCount.getCount)
      } catch {
        case e: Throwable if detectRenames => stats(objectId, otherId, detectRenames = false)
      }
    }
  }

  case class Stats(addedLines: Long, editedLines: Long, deletedLines: Long, files: List[String], numCommits: Long) {
    def +(other: Stats) = Stats(addedLines + other.addedLines,
                                editedLines + other.editedLines,
                                deletedLines + other.deletedLines,
                                files ++ other.files,
                                numCommits + other.numCommits)
  }

  /**
   * Enrichment of the [[org.eclipse.jgit.lib.RefUpdate]] class.
   * @param update The ref update.
   */
  implicit class RichRefUpdate(update: RefUpdate) {
    /**
     * Forces the deletion of this [[org.eclipse.jgit.lib.RefUpdate]].
     * @return The result of the deletion.
     */
    def forceDelete(): RefUpdate.Result = {
      update.setForceUpdate(true)
      update.delete
    }
  }
}

/**
 * An enum type for merge results.
 */
object MergeResult extends Enumeration {
  type MergeResult = Value
  val Merged, Conflict, Error = Value

  def isSuccess(result: MergeResult) = result match {
    case Merged => true
    case _ => false
  }
}
