package jgit

import git.{PullRequestProvider, Provider}
import org.slf4j.LoggerFactory
import java.io.File
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.api.Git
import jgit.data.JGitDataProvider
import jgit.merge.JGitMergeProvider

/**
 * A provider implementation for the JGit library.
 * @param repoDirectory The path to the directory of the git repository. It can either be a working directory or a bare git directory.
 * @param remote The name of the GitHub remote.
 * @param inMemoryMerge Whether to merge tester has to simulate merges on disk or in-memory.
 */
class JGitProvider(repoDirectory: String, val remote: String = "origin", inMemoryMerge: Boolean = true) extends Provider {
  val logger = LoggerFactory.getLogger(this.getClass)
  val dotGit = ".git"
  val gitDir = if (repoDirectory.endsWith(dotGit)) repoDirectory else repoDirectory + File.separator + dotGit
  val repository = new FileRepositoryBuilder().setGitDir(new File(gitDir))
    .readEnvironment // scan environment GIT_* variables
    .findGitDir // scan the file system tree
    .build

  // Create git client
  val git: Git = new Git(repository)

  override def pullRequests: Option[PullRequestProvider] = None
  override def merger: Option[JGitMergeProvider] =
    Some(new JGitMergeProvider(git, remote, inMemoryMerge))
  override def data: Option[JGitDataProvider] =
    Some(new JGitDataProvider(git, remote))

  override def dispose(): Unit = {
    for (m <- merger)
      m.clean(force = true)

    git.close()
    repository.close()
  }
}